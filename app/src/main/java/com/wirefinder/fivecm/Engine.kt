package com.wirefinder.fivecm

import kotlin.math.sqrt

class Engine {

    val mains = Mains()

    var sens = 1
    var rateHz = 0f
    var calibrating = true
    var shaking = false
    var usingRot = false
    var muteUntilMs = 0L
    var hint = ""

    var wireOn = false
    var metalOn = false
    var overOn = false
    var cleanOn = false

    var wireLevel = 0f
    var metalLevel = 0f
    var metalDev = 0f
    var metalThr = 1f
    var wireAmp = 0f
    var wireBase = 0f
    var wireRel = 0f
    var wireNoise = 0f

    private val metalMin = floatArrayOf(1.2f, 0.8f, 0.5f)
    private val metalK = floatArrayOf(6f, 4.5f, 3f)
    private val wireRelOn = floatArrayOf(2.2f, 1.7f, 1.4f)
    private val wireRelOff = floatArrayOf(1.5f, 1.3f, 1.2f)
    private val wireExK = floatArrayOf(3f, 2.5f, 2f)

    private val rot = FloatArray(9)
    private var hasRot = false
    private var rotMs = 0L
    private var lastShakeMs = -10000L

    private var started = false
    private var warmUntilMs = 0L
    private var lastTsNs = 0L
    private var lastUseRot = false
    private var wasMuted = false
    private var lastMainsMs = 0L

    private var smX = 0f
    private var smY = 0f
    private var smZ = 0f
    private var refX = 0f
    private var refY = 0f
    private var refZ = 0f
    private var devNoise = 0.2f
    private var devPeak = 0f

    private var ambInit = false
    private var wireCandSince = 0L
    private var wireLastHigh = 0L
    private var metalCandSince = 0L
    private var metalLastHigh = 0L
    private var overUntilMs = 0L
    private var hintMs = 0L
    private var prevCombined = 0f

    fun restart() {
        started = false
        lastTsNs = 0L
        wasMuted = false
        ambInit = false
        mains.clear()
    }

    fun zero() {
        restart()
        wireLevel = 0f
        metalLevel = 0f
        wireOn = false
        metalOn = false
        overOn = false
        cleanOn = false
        wireCandSince = 0L
        metalCandSince = 0L
    }

    fun onRotation(r: FloatArray, nowMs: Long) {
        for (i in 0 until 9) {
            rot[i] = r[i]
        }
        hasRot = true
        rotMs = nowMs
    }

    fun onGyro(g: Float, nowMs: Long) {
        if (g > 0.7f) {
            lastShakeMs = nowMs
        }
    }

    fun onLinear(a: Float, nowMs: Long) {
        if (a > 1.8f) {
            lastShakeMs = nowMs
        }
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

        val shakeNow = nowMs - lastShakeMs < 600

        var vx = x
        var vy = y
        var vz = z
        val useRot = hasRot && nowMs - rotMs < 150

        if (useRot) {
            vx = rot[0] * x + rot[1] * y + rot[2] * z
            vy = rot[3] * x + rot[4] * y + rot[5] * z
            vz = rot[6] * x + rot[7] * y + rot[8] * z
        }

        if (!started || useRot != lastUseRot) {
            if (!started) {
                warmUntilMs = nowMs + 3000
            }
            started = true
            lastUseRot = useRot
            smX = vx
            smY = vy
            smZ = vz
            refX = vx
            refY = vy
            refZ = vz
        }
        usingRot = useRot

        val ks = dt / (0.06f + dt)
        smX += (vx - smX) * ks
        smY += (vy - smY) * ks
        smZ += (vz - smZ) * ks

        calibrating = nowMs < warmUntilMs

        val tau = if (calibrating) {
            0.5f
        } else if (shakeNow) {
            0.3f
        } else {
            3f
        }
        val kr = dt / (tau + dt)
        refX += (smX - refX) * kr
        refY += (smY - refY) * kr
        refZ += (smZ - refZ) * kr

        val dx = smX - refX
        val dy = smY - refY
        val dz = smZ - refZ
        val dev = sqrt(dx * dx + dy * dy + dz * dz)

        metalDev = dev
        if (dev > devPeak) {
            devPeak = dev
        }

        if (!calibrating && !shakeNow && !metalOn) {
            val cap = devNoise * 3f
            val d = if (dev > cap) cap else dev
            devNoise += (d - devNoise) * (dt / (20f + dt))
            if (devNoise < 0.1f) {
                devNoise = 0.1f
            }
        }

        val minThr = metalMin[sens]
        val kThr = metalK[sens] * devNoise
        metalThr = if (minThr > kThr) minThr else kThr

        val target = if (calibrating || shakeNow) {
            0f
        } else {
            ((dev - metalThr * 0.6f) / (metalThr * 2f)).coerceIn(0f, 1f)
        }
        metalLevel = if (target > metalLevel) target else metalLevel * 0.995f

        if (nowMs < muteUntilMs) {
            wasMuted = true
        } else {
            if (wasMuted) {
                wasMuted = false
                mains.clear()
            }
            mains.push(x, y, z)

            if (nowMs - lastMainsMs >= 150) {
                lastMainsMs = nowMs
                updateWire(shakeNow)
            }
        }
    }

    private fun updateWire(shakeNow: Boolean) {

        if (rateHz < 20f) {
            return
        }

        if (!mains.analyze(rateHz)) {
            return
        }

        if (!mains.valid) {
            wireLevel = 0f
            return
        }

        val a = mains.raw
        val n = mains.noise

        wireAmp = a
        wireNoise = if (n < 0.01f) 0.01f else n

        if (shakeNow) {
            wireLevel *= 0.9f
            return
        }

        if (!ambInit) {
            wireBase = a
            ambInit = true
        }

        if (calibrating) {
            wireBase += (a - wireBase) * 0.25f
            wireRel = 1f
            wireLevel = 0f
            return
        }

        val base = if (wireBase < 0.02f) 0.02f else wireBase
        wireRel = a / base

        if (!wireOn) {
            wireBase += (a - wireBase) * 0.025f
        }

        val target = ((wireRel - 1f) / 3f).coerceIn(0f, 1f)
        wireLevel = if (target > wireLevel) target else wireLevel * 0.9f
    }

    private fun updateMetalLamp(nowMs: Long) {

        val peak = devPeak

        if (!metalOn) {
            if (peak >= metalThr && !shaking) {
                if (metalCandSince == 0L) {
                    metalCandSince = nowMs
                }
                if (nowMs - metalCandSince >= 150) {
                    metalOn = true
                    metalLastHigh = nowMs
                }
            } else {
                metalCandSince = 0L
            }
        } else {
            if (peak >= metalThr * 0.6f) {
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

        if (shaking) {
            wireCandSince = 0L
            return
        }

        val r = wireRel
        val ex = wireAmp - wireBase
        val strong = r >= wireRelOn[sens] && ex >= wireExK[sens] * wireNoise

        if (!wireOn) {
            if (strong) {
                if (wireCandSince == 0L) {
                    wireCandSince = nowMs
                }
                if (nowMs - wireCandSince >= 600) {
                    wireOn = true
                    wireLastHigh = nowMs
                }
            } else {
                wireCandSince = 0L
            }
        } else {
            if (r >= wireRelOff[sens]) {
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
            shaking ->
                "Слишком резко. Веди телефон плавно и не крути."
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

        shaking = nowMs - lastShakeMs < 600

        if (calibrating) {
            if (shaking) {
                warmUntilMs = nowMs + 2000
            }
            wireOn = false
            metalOn = false
            overOn = false
            cleanOn = false
            wireCandSince = 0L
            metalCandSince = 0L
            devPeak = 0f
            hint = "Подготовка: держи телефон неподвижно вдали от металла."
            return
        }

        updateMetalLamp(nowMs)
        updateWireLamp(nowMs)
        devPeak = 0f

        if ((wireOn && wireLevel >= 0.7f) || (metalOn && metalLevel >= 0.7f)) {
            overUntilMs = nowMs + 1200
        }

        overOn = nowMs < overUntilMs
        cleanOn = !wireOn && !metalOn && !shaking

        updateHint(nowMs)
    }
}

// конец файла
