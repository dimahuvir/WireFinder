package com.wirefinder.fivecm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class Engine {

    val mains = Mains()

    var sens = 0
    var smoothMag = 0f
    var slowBase = 0f
    var metalDev = 0f
    var metalLevel = 0f
    var wireLevel = 0f
    var rateHz = 0f
    var calibrating = true
    var muteUntilMs = 0L
    var hint = ""

    private val thrArr = floatArrayOf(1.8f, 1.4f, 1.2f)
    private val gateArr = floatArrayOf(0.8f, 0.5f, 0.3f)
    private val fullArr = floatArrayOf(8f, 4f, 2f)

    private var started = false
    private var warmUntilMs = 0L
    private var lastTsNs = 0L
    private var wasMuted = false
    private var lastMainsMs = 0L

    private var peakVal = 0f
    private var peakTimeMs = 0L
    private var prevCombined = 0f
    private var lastTrendMs = 0L
    private var trend = 0

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
        peakVal = 0f
    }

    fun onSample(x: Float, y: Float, z: Float, tsNs: Long, nowMs: Long) {

        if (nowMs < muteUntilMs) {
            wasMuted = true
            lastTsNs = 0L
            return
        }

        if (wasMuted) {
            wasMuted = false
            mains.clear()
        }

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

        smoothMag += (mag - smoothMag) * (dt / (0.08f + dt))

        val tau = if (nowMs < warmUntilMs) 0.5f else 4f
        slowBase += (smoothMag - slowBase) * (dt / (tau + dt))

        calibrating = nowMs < warmUntilMs

        mains.push(x, y, z)

        if (nowMs - lastMainsMs >= 150) {
            lastMainsMs = nowMs
            updateWire()
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

        val thr = thrArr[sens]
        val target = (ln(mains.ratio / thr) / 2.5f).coerceIn(0f, 1f)

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

        val target = ((metalDev - gateArr[sens]) / fullArr[sens]).coerceIn(0f, 1f)

        metalLevel = if (target > metalLevel) {
            target
        } else {
            metalLevel * 0.995f
        }
    }

    fun tick(nowMs: Long) {

        val c = if (wireLevel > metalLevel) wireLevel else metalLevel

        if (calibrating) {
            hint = "Держи телефон неподвижно вдали от металла."
            trend = 0
            peakVal = 0f
            return
        }

        if (c > peakVal || nowMs - peakTimeMs > 4000) {
            peakVal = c
            peakTimeMs = nowMs
        }

        if (nowMs - lastTrendMs >= 500) {
            val d = c - prevCombined
            trend = if (d > 0.05f) 1 else if (d < -0.05f) -1 else 0
            prevCombined = c
            lastTrendMs = nowMs
        }

        val trendText = when (trend) {
            1 -> "  ▲ растёт"
            -1 -> "  ▼ падает"
            else -> ""
        }

        hint = when {
            peakVal > 0.4f && c < peakVal * 0.6f && nowMs - peakTimeMs < 3500 ->
                "Пик пройден. Вернись немного назад."
            c >= 0.8f ->
                "ПРЯМО НАД ИСТОЧНИКОМ"
            c >= 0.5f ->
                "Близко. Ищи максимум." + trendText
            c >= 0.25f ->
                "Ближе." + trendText
            c >= 0.12f ->
                "Слабый сигнал, далеко." + trendText
            else ->
                "Двигай телефон медленно вдоль стены."
        }
    }
}

// конец файла
