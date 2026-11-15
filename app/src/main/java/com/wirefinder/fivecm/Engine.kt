package com.wirefinder.fivecm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class Engine {

    val mains = Mains()

    var sens = 1
    var smoothMag = 0f
    var slowBase = 0f
    var metalDev = 0f
    var metalLevel = 0f
    var wireLevel = 0f
    var rateHz = 0f
    var calibrating = true
    var muteUntilMs = 0L
    var hint = ""

    var wireOn = false
    var metalOn = false
    var overOn = false
    var cleanOn = false

    private val metalOnThr = floatArrayOf(1.5f, 0.8f, 0.45f)
    private val metalOffThr = floatArrayOf(0.9f, 0.5f, 0.3f)
    private val metalFull = floatArrayOf(10f, 5f, 3f)
    private val wireOnThr = floatArrayOf(2.6f, 2.2f, 1.9f)
    private val wireOffThr = floatArrayOf(1.6f, 1.4f, 1.3f)

    private var started = false
    private var warmUntilMs = 0L
    private var lastTsNs = 0L
    private var wasMuted = false
    private var lastMainsMs = 0L

    private var wireCandSince = 0L
    private var wireLastHigh = 0L
    private var metalCandSince = 0L
    private var metalLastHigh = 0L
    private var metalOnSince = 0L
    private var overUntilMs = 0L
    private var hintMs = 0L
    private var prevCombined = 0f

    fun restart() {
        started = false
        lastTsNs = 0L
        wasMuted = false
        mains.clear()
    }

    fun zero(nowMs: Long) {
        warmUntilMs = nowMs + 1500
        calibrating = true
        slowBase = smoothMag
        mains.clear()
        wireLevel = 0f
        metalLevel = 0f
        wireOn = false
        metalOn = false
        overOn = false
        cleanOn = false
        wireCandSince = 0L
        metalCandSince = 0L
    }

    fun onSample(x: Float, y: Float, z: Float, tsNs: Long, nowMs: Long) {

        var dt = 0.005f
        var dtValid = false
        if (lastTsNs != 0L) {
            val d = (tsNs - lastTsNs) / 1.0e9f
            if (d > 0.0005f && d < 0.1f) {
                dt = d
                dtValid = true
            }
        }
        lastTsNs = tsNs

        if (dtValid) {
            rateHz = if (rateHz == 0f) {
                1f / dt
            } else {
                rateHz * 0.98f + (1f / dt) * 0.02f
            }
        }

        val mag = sqrt(x * x + y * y + z * z)

        if (!started) {
            started = true
            warmUntilMs = nowMs + 2000
            smoothMag = mag
            slowBase = mag
        }

        smoothMag += (mag - smoothMag) * (dt / (0.06f + dt))

        val tau = when {
            nowMs < warmUntilMs -> 0.5f
            !metalOn -> 12f
            nowMs - metalOnSince > 12000 -> 5f
            else -> 60f
        }
        slowBase += (smoothMag - slowBase) * (dt / (tau + dt))

        calibrating = nowMs < warmUntilMs

        val muted = nowMs < muteUntilMs

        if (muted) {
            wasMuted = true
        } else {
            if (wasMuted) {
                wasMuted = false
                mains.clear()
            }
            mains.push(x, y, z)

            if (nowMs - lastMainsMs >= 150) {
                lastMainsMs = nowMs
                updateWire()
            }
        }

        updateMetal()
    }

    private fun updateWire() {

        if (rateHz < 20f) {
            return
        }

        if (!mains.analyze(rateHz)) {
            return
        }

        if (!mains.valid || calibrating) {
            wireLevel = 0f
            return
        }

        val target = (ln(mains.ratio / 1.3f) / 2.0f).coerceIn(0f, 1f)

        wireLevel = if (target > wireLevel) {
            target
        } else {
            wireLevel * 0.9f
        }
    }

    private fun updateMetal() {

        metalDev = abs(smoothMag - slowBase)

        if (calibrating) {
            metalLevel = 0f
            return
        }

        val gate = metalOffThr[sens] * 0.5f
        val target = ((metalDev - gate) / metalFull[sens]).coerceIn(0f, 1f)

        metalLevel = if (target > metalLevel) {
            target
        } else {
            metalLevel * 0.995f
        }
    }

    private fun updateMetalLamp(nowMs: Long) {

        val dev = metalDev

        if (!metalOn) {
            if (dev >= metalOnThr[sens]) {
                if (metalCandSince == 0L) {
                    metalCandSince = nowMs
                }
                if (nowMs - metalCandSince >= 150) {
                    metalOn = true
                    metalOnSince = nowMs
                    metalLastHigh = nowMs
                }
            } else {
                metalCandSince = 0L
            }
        } else {
            if (dev >= metalOffThr[sens]) {
                metalLastHigh = nowMs
            } else if (nowMs - metalLastHigh >= 1500) {
                metalOn = false
                metalCandSince = 0L
            }
        }
    }

    private fun updateWireLamp(nowMs: Long) {

        if (!mains.valid) {
            wireOn = false
            wireCandSince = 0L
            return
        }

        val r = mains.ratio

        if (!wireOn) {
            if (r >= wireOnThr[sens]) {
                if (wireCandSince == 0L) {
                    wireCandSince = nowMs
                }
                if (nowMs - wireCandSince >= 450) {
                    wireOn = true
                    wireLastHigh = nowMs
                }
            } else {
                wireCandSince = 0L
            }
        } else {
            if (r >= wireOffThr[sens]) {
                wireLastHigh = nowMs
            } else if (nowMs - wireLastHigh >= 1500) {
                wireOn = false
                wireCandSince = 0L
            }
        }
    }

    private fun updateHint(nowMs: Long) {

        if (nowMs - hintMs < 600) {
            return
        }
        hintMs = nowMs

        val cw = if (wireOn) wireLevel else 0f
        val cm = if (metalOn) metalLevel else 0f
        val c = if (cw > cm) cw else cm

        hint = when {
            overOn ->
                "ПРЯМО НАД ИСТОЧНИКОМ"
            wireOn || metalOn ->
                if (c > prevCombined + 0.05f) {
                    "Сигнал растёт: ты ближе."
                } else if (c < prevCombined - 0.05f) {
                    "Сигнал падает: вернись назад."
                } else {
                    "Ищи максимум сигнала."
                }
            else ->
                "Двигай телефон медленно вдоль стены."
        }

        prevCombined = c
    }

    fun tick(nowMs: Long) {

        if (calibrating) {
            wireOn = false
            metalOn = false
            overOn = false
            cleanOn = false
            wireCandSince = 0L
            metalCandSince = 0L
            hint = "Подготовка: держи телефон неподвижно вдали от металла."
            return
        }

        updateMetalLamp(nowMs)
        updateWireLamp(nowMs)

        if ((wireOn && wireLevel >= 0.7f) || (metalOn && metalLevel >= 0.7f)) {
            overUntilMs = nowMs + 1200
        }

        overOn = nowMs < overUntilMs
        cleanOn = !wireOn && !metalOn

        updateHint(nowMs)
    }
}

// конец файла
