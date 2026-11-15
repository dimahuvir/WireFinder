package com.wirefinder.fivecm

import kotlin.math.abs
import kotlin.math.sqrt

class Engine {

    var acMode = true
    var smoothMag = 0f
    var baseValue: Float? = null
    var calibrating = false
    var notice = ""
    var acRms = 0f
    var acFloor = 0.02f
    var rateHz = 0f
    var level = 0f
    var muteAcUntilMs = 0L

    private val hpRc = 0.008f
    private var smoothInit = false
    private var autoCalibDone = false
    private var lastTsNs = 0L
    private var hasPrev = false
    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var hpX = 0f
    private var hpY = 0f
    private var hpZ = 0f
    private var acPower = 0f
    private var gainRef = 0f

    private var calibStartMs = 0L
    private var calibUntilMs = 0L
    private var calibSumMag = 0.0
    private var calibSumAc = 0.0
    private var calibCount = 0
    private var calibAcCount = 0
    private var calibMin = 0f
    private var calibMax = 0f

    fun restart() {
        lastTsNs = 0L
        hasPrev = false
        smoothInit = false
        autoCalibDone = false
    }

    fun resetGain() {
        gainRef = 0f
    }

    fun signal(): Float {
        if (calibrating) {
            return 0f
        }

        if (acMode) {
            val p = acPower - acFloor * acFloor
            return if (p > 0f) sqrt(p) else 0f
        }

        val b = baseValue
        return if (b == null) 0f else abs(smoothMag - b)
    }

    fun startCalibration(nowMs: Long) {
        calibrating = true
        calibStartMs = nowMs
        calibUntilMs = nowMs + 2000
        calibSumMag = 0.0
        calibSumAc = 0.0
        calibCount = 0
        calibAcCount = 0
        calibMin = 1.0e9f
        calibMax = -1.0e9f
    }

    private fun finishCalibration(nowMs: Long) {

        if (calibMax - calibMin > 5f) {
            notice = "\nТелефон двигался или рядом металл. Повторяю..."
            startCalibration(nowMs)
            return
        }

        baseValue = (calibSumMag / calibCount).toFloat()

        val avgAc = if (calibAcCount > 0) {
            (calibSumAc / calibAcCount).toFloat()
        } else {
            0.02f
        }
        acFloor = if (avgAc < 0.01f) 0.01f else avgAc

        gainRef = 0f
        notice = ""
        calibrating = false
    }

    private fun calibrateStep(mag: Float, nowMs: Long) {
        calibSumMag += mag
        calibCount++

        if (mag < calibMin) calibMin = mag
        if (mag > calibMax) calibMax = mag

        if (nowMs - calibStartMs >= 600) {
            calibSumAc += acRms
            calibAcCount++
        }

        if (nowMs >= calibUntilMs) {
            finishCalibration(nowMs)
        }
    }

    private fun updateLevel() {
        val sig = signal()
        val gate = if (acMode) acFloor * 0.5f else 2f
        val minRef = if (acMode) acFloor * 2f else 10f

        gainRef *= 0.9995f
        if (sig > gainRef) {
            gainRef = sig
        }
        if (gainRef < minRef) {
            gainRef = minRef
        }

        level = if (calibrating || baseValue == null) {
            0f
        } else {
            ((sig - gate) / (gainRef - gate)).coerceIn(0f, 1f)
        }
    }

    fun onSample(x: Float, y: Float, z: Float, tsNs: Long, nowMs: Long) {

        var dt = 0.005f
        if (lastTsNs != 0L) {
            val d = (tsNs - lastTsNs) / 1.0e9f
            if (d > 0.0005f && d < 0.1f) {
                dt = d
            }
        }
        lastTsNs = tsNs

        rateHz = if (rateHz == 0f) {
            1f / dt
        } else {
            rateHz * 0.98f + (1f / dt) * 0.02f
        }

        val mag = sqrt(x * x + y * y + z * z)

        if (!smoothInit) {
            smoothMag = mag
            smoothInit = true
        } else {
            smoothMag += (mag - smoothMag) * (dt / (0.08f + dt))
        }

        if (hasPrev) {
            val a = hpRc / (hpRc + dt)
            hpX = a * (hpX + x - prevX)
            hpY = a * (hpY + y - prevY)
            hpZ = a * (hpZ + z - prevZ)
        }
        prevX = x
        prevY = y
        prevZ = z
        hasPrev = true

        if (nowMs >= muteAcUntilMs) {
            val e2 = hpX * hpX + hpY * hpY + hpZ * hpZ
            acPower += (e2 - acPower) * (dt / (0.2f + dt))
            acRms = sqrt(acPower)
        }

        if (!autoCalibDone) {
            autoCalibDone = true
            startCalibration(nowMs)
        }

        if (calibrating) {
            calibrateStep(mag, nowMs)
        }

        updateLevel()
    }
}

// конец файла
