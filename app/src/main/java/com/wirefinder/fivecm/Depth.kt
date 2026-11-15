package com.wirefinder.fivecm

import java.util.Locale
import kotlin.math.sqrt

class Depth {

    var offsetCm = 5
    var stage = 0
    var msg = "Сделай замер 1: прижми телефон к стене над максимумом сигнала."

    private var s0: Float? = null
    private var s1: Float? = null
    private var hNear: Float? = null
    private var hFar: Float? = null
    private var startMs = 0L
    private var untilMs = 0L
    private var sum = 0.0
    private var count = 0

    fun hasFirst(): Boolean = s0 != null

    fun reset(acFloor: Float) {
        s0 = null
        s1 = null
        stage = 0
        recompute(acFloor)
    }

    fun cancel() {
        stage = 0
    }

    fun nextOffset(acFloor: Float) {
        offsetCm = when (offsetCm) {
            3 -> 5
            5 -> 10
            else -> 3
        }
        s1 = null
        recompute(acFloor)
    }

    fun start(newStage: Int, nowMs: Long) {
        if (newStage == 1) {
            s0 = null
        }
        s1 = null
        hNear = null
        hFar = null
        stage = newStage
        startMs = nowMs
        untilMs = nowMs + 2000
        sum = 0.0
        count = 0
    }

    fun feed(sig: Float, nowMs: Long, acFloor: Float) {
        if (stage == 0) {
            return
        }
        if (nowMs - startMs >= 500) {
            sum += sig
            count++
        }
        if (nowMs >= untilMs) {
            finish(acFloor)
        }
    }

    private fun finish(acFloor: Float) {
        val avg = if (count > 0) (sum / count).toFloat() else 0.0001f

        if (stage == 1) {
            s0 = avg
            s1 = null
        } else {
            s1 = avg
        }

        stage = 0
        recompute(acFloor)
    }

    private fun recompute(acFloor: Float) {
        hNear = null
        hFar = null

        val a = s0
        val b = s1

        if (a == null) {
            msg = "Сделай замер 1: прижми телефон к стене над максимумом сигнала."
            return
        }

        if (b == null) {
            msg = "Замер 1 готов. Теперь замер 2: отодвинь телефон от стены на $offsetCm см."
            return
        }

        if (a < acFloor) {
            msg = "Сигнал провода слабый. Включи нагрузку мощнее и повтори замер 1."
            return
        }

        if (b > a * 0.95f) {
            msg = "Сигнал не упал при отодвигании: это не похоже на провод. Повтори оба замера."
            return
        }

        val d = offsetCm.toFloat()
        val bb = if (b < 0.0001f) 0.0001f else b
        val ratio = a / bb

        hNear = d / (ratio - 1f)
        hFar = d / (sqrt(ratio) - 1f)
        msg = "Готово."
    }

    private fun fmtCm(v: Double): String {
        return if (v > 100.0) {
            ">100"
        } else {
            String.format(Locale.US, "%.0f", v)
        }
    }

    fun texts(sig: Float, acFloor: Float, acMode: Boolean): Pair<String, String> {

        if (stage != 0) {
            return Pair("Замер...", "Держи телефон неподвижно 2 секунды.")
        }

        if (!acMode) {
            return Pair("—", "Включи режим «Провод под нагрузкой».")
        }

        val near = hNear
        val far = hFar
        val a = s0

        if (near == null || far == null || a == null) {
            return Pair("—", msg)
        }

        if (sig < acFloor * 0.3f) {
            return Pair("не слышно", "Сигнала провода сейчас нет или он очень далеко.")
        }

        val ratio = (a / sig).toDouble()
        val r1 = near.toDouble() * ratio
        val r2 = far.toDouble() * sqrt(ratio)

        val lo = if (r1 < r2) r1 else r2
        val hi = if (r1 < r2) r2 else r1

        val title = if (hi - lo < 1.0) {
            "≈ " + fmtCm(lo) + " см"
        } else {
            "≈ " + fmtCm(lo) + "–" + fmtCm(hi) + " см"
        }

        return Pair(
            title,
            "Расстояние от датчика до провода по силе сигнала. Грубая оценка, погрешность 50% и больше."
        )
    }
}

// конец файла
