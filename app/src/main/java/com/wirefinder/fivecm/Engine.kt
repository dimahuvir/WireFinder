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
    var wireRel = 1f
    var wireNoise = 0f

    private val metalMin = floatArrayOf(1.2f, 0.7f, 0.4f)
    private val metalK = floatArrayOf(7f, 5f, 3f)
    private val wireRelOn = floatArrayOf(2.4f, 1.8f, 1.45f)
    private val wireRelOff = floatArrayOf(1.6f, 1.35f, 1.22f)

    private val rot = FloatArray(9)
    private var hasRot = false
    private var rotMs = 0L
    private var lastShakeMs = -10000L
    private var started = false
    private var warmUntilMs = 0L
    private var lastTsNs = 0L

    private var smX = 0f
    private var smY = 0f
    private var smZ = 0f
    private var refX = 0f
    private var refY = 0f
    private var refZ = 0f
    private var devNoise = 0.15f
    private var devPeak = 0f

    private var wireBaseReady = false
    private var wireCandSince = 0L
    private var wireLastHigh = 0L
    private var metalCandSince = 0L
    private var metalLastHigh = 0L
    private var overUntilMs = 0L

    fun restart() {
        started = false
        lastTsNs = 0L
        warmUntilMs = 0L
        wireBaseReady = false
        devPeak = 0f
        devNoise = 0.15f
        mains.clear()
        wireCandSince = 0L
        metalCandSince = 0L
        wireOn = false
        metalOn = false
        overOn = false
        cleanOn = false
        wireLevel = 0f
        metalLevel = 0f
    }

    fun zero() = restart()

    fun setMainsHz(hz: Float) {
        if (hz != mains.mainsHz) {
            mains.mainsHz = hz
            wireBaseReady = false
            mains.clear()
        }
    }

    fun onRotation(r: FloatArray, nowMs: Long) {
        for (i in rot.indices) rot[i] = r[i]
        hasRot = true
        rotMs = nowMs
    }

    fun onGyro(g: Float, nowMs: Long) {
        if (g > 0.7f) lastShakeMs = nowMs
    }

    fun onLinear(a: Float, nowMs: Long) {
        if (a > 1.8f) lastShakeMs = nowMs
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
            val instant = 1f / dt
            rateHz = if (rateHz == 0f) instant else rateHz * 0.98f + instant * 0.02f
        }

        shaking = nowMs - lastShakeMs < 600
        var vx = x
        var vy = y
        var vz = z
        val useRot = hasRot && nowMs - rotMs < 150

        if (useRot) {
            vx = rot[0] * x + rot[1] * y + rot[2] * z
            vy = rot[3] * x + rot[4] * y + rot[5] * z
            vz = rot[6] * x + rot[7] * y + rot[8] * z
        }
        usingRot = useRot

        if (!started) {
            started = true
            warmUntilMs = nowMs + 3000
            smX = vx
            smY = vy
            smZ = vz
            refX = vx
            refY = vy
            refZ = vz
        }

        val ks = dt / (0.06f + dt)
        smX += (vx - smX) * ks
        smY += (vy - smY) * ks
        smZ += (vz - smZ) * ks

        calibrating = nowMs < warmUntilMs
        val tau = if (calibrating) 0.5f else if (shaking) 0.25f else 3f
        val kr = dt / (tau + dt)
        refX += (smX - refX) * kr
        refY += (smY - refY) * kr
        refZ += (smZ - refZ) * kr

        val dx = smX - refX
        val dy = smY - refY
        val dz = smZ - refZ
        val dev = sqrt(dx * dx + dy * dy + dz * dz)

        metalDev = dev
        if (dev > devPeak) devPeak = dev

        if (!calibrating && !shaking && !metalOn) {
            val cap = devNoise * 3f
            val d = if (dev > cap) cap else dev
            devNoise += (d - devNoise) * (dt / (20f + dt))
            if (devNoise < 0.1f) devNoise = 0.1f
        }

        val minThr = metalMin[sens]
        val kThr = metalK[sens] * devNoise
        metalThr = if (minThr > kThr) minThr else kThr

        val target = if (calibrating || shaking) 0f
        else ((dev - metalThr * 0.6f) / (metalThr * 2f)).coerceIn(0f, 1f)
        metalLevel = if (target > metalLevel) target else metalLevel * 0.995f

        if (nowMs >= muteUntilMs) mains.push(vx, vy, vz)
        if (mains.countReady() && nowMs % 150 < 15) updateWire(shakeNow = shaking)
    }

    private fun updateWire(shakeNow: Boolean) {
        if (rateHz < 20f || !mains.analyze(rateHz) || !mains.valid) {
            wireLevel = 0f
            return
        }

        wireAmp = mains.raw
        wireNoise = if (mains.noise < 0.01f) 0.01f else mains.noise

        if (!wireBaseReady) {
            wireBase = wireAmp
            wireBaseReady = true
        }

        if (calibrating) {
            wireBase += (wireAmp - wireBase) * 0.25f
            wireRel = 1f
            wireLevel = 0f
            return
        }

        val base = if (wireBase < 0.02f) 0.02f else wireBase
        wireRel = wireAmp / base

        if (!wireOn && !shakeNow) {
            wireBase += (wireAmp - wireBase) * 0.025f
        }

        val target = if (shakeNow) 0f
        else ((wireRel - 1f) / 3f).coerceIn(0f, 1f)
        wireLevel = if (target > wireLevel) target else wireLevel * 0.9f
    }

    fun tick(nowMs: Long) {
        if (calibrating) {
            if (shaking) warmUntilMs = nowMs + 2000
            wireOn = false
            metalOn = false
            overOn = false
            cleanOn = false
            devPeak = 0f
            hint = "Подготовка: держи телефон неподвижно вдали от металла."
            return
        }

        if (!metalOn) {
            if (devPeak >= metalThr && !shaking) {
                if (metalCandSince == 0L) metalCandSince = nowMs
                if (nowMs - metalCandSince >= 150) {
                    metalOn = true
                    metalLastHigh = nowMs
                }
            } else {
                metalCandSince = 0L
            }
        } else {
            if (devPeak >= metalThr * 0.6f) metalLastHigh = nowMs
            else if (nowMs - metalLastHigh >= 1500) {
                metalOn = false
                metalCandSince = 0L
            }
        }

        if (!mains.valid) {
            wireOn = false
            wireCandSince = 0L
        } else if (shaking) {
            wireCandSince = 0L
        } else {
            val strong = wireRel >= wireRelOn[sens] &&
                    wireAmp - wireBase >= wireNoise * 3f
            if (!wireOn) {
                if (strong) {
                    if (wireCandSince == 0L) wireCandSince = nowMs
                    if (nowMs - wireCandSince >= 600) {
                        wireOn = true
                        wireLastHigh = nowMs
                    }
                } else wireCandSince = 0L
            } else {
                if (wireRel >= wireRelOff[sens]) wireLastHigh = nowMs
                else if (nowMs - wireLastHigh >= 1500) {
                    wireOn = false
                    wireCandSince = 0L
                }
            }
        }

        devPeak = 0f
        if ((wireOn && wireLevel >= 0.7f) || (metalOn && metalLevel >= 0.7f)) {
            overUntilMs = nowMs + 1200
        }
        overOn = nowMs < overUntilMs
        cleanOn = !wireOn && !metalOn && !shaking

        hint = when {
            shaking -> "Слишком резко: веди телефон плавно."
            overOn -> "ПРЯМО НАД ИСТОЧНИКОМ"
            wireOn -> "Обнаружено изменение поля под сетью."
            metalOn -> "Обнаружено локальное отклонение магнитного поля."
            else -> "Двигай телефон медленно вдоль поверхности."
        }
    }
}
