package com.wirefinder.fivecm

import java.util.Locale

class ScanMap {

    companion object {
        const val COLS = 40
        const val ROWS = 8
        const val EV_NONE = 0
        const val EV_TICK = 1
        const val EV_START = 2
        const val EV_END = 3
    }

    val cells = Array(ROWS) { FloatArray(COLS) }
    var row = 0
    var state = 0
    var widthCm = 50
    var rowCm = 10
    var cursorCol = -1
    var secondsLeft = 0
    var peakRow = -1
    var peakCol = -1
    val durationMs = 6000L

    private var stateStartMs = 0L
    private var lastSec = -1

    fun clear() {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                cells[r][c] = 0f
            }
        }
        row = 0
        state = 0
        cursorCol = -1
        peakRow = -1
        peakCol = -1
    }

    fun startPass(nowMs: Long): Boolean {
        if (row >= ROWS || state != 0) {
            return false
        }
        for (c in 0 until COLS) {
            cells[row][c] = 0f
        }
        state = 1
        stateStartMs = nowMs
        lastSec = -1
        secondsLeft = 3
        return true
    }

    fun update(nowMs: Long, sig: Float): Int {

        if (state == 1) {
            val el = nowMs - stateStartMs

            if (el >= 3000) {
                state = 2
                stateStartMs = nowMs
                lastSec = -1
                cursorCol = 0
                return EV_START
            }

            secondsLeft = 3 - (el / 1000).toInt()
            val sec = (el / 1000).toInt()
            if (sec != lastSec) {
                lastSec = sec
                return EV_TICK
            }
            return EV_NONE
        }

        if (state == 2) {
            val el = nowMs - stateStartMs

            if (el >= durationMs) {
                row++
                state = 0
                cursorCol = -1
                return EV_END
            }

            secondsLeft = ((durationMs - el) / 1000).toInt() + 1

            val col = (el * COLS / durationMs).toInt().coerceIn(0, COLS - 1)
            cursorCol = col

            if (sig > cells[row][col]) {
                cells[row][col] = sig
            }

            val sec = (el / 1000).toInt()
            if (sec != lastSec) {
                lastSec = sec
                return EV_TICK
            }
        }

        return EV_NONE
    }

    fun maxValue(): Float {
        var m = 0f
        for (r in 0 until ROWS) {
            if (r < row || (r == row && state == 2)) {
                for (c in 0 until COLS) {
                    if (cells[r][c] > m) {
                        m = cells[r][c]
                    }
                }
            }
        }
        return m
    }

    private fun smooth(src: FloatArray): FloatArray {
        val n = src.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            val a = if (i > 0) src[i - 1] else src[i]
            val b = if (i < n - 1) src[i + 1] else src[i]
            out[i] = (a + src[i] * 2f + b) / 4f
        }
        return out
    }

    private fun fwhm(p: FloatArray, n: Int, peakIdx: Int): Float? {
        val half = p[peakIdx] / 2f

        var l = peakIdx
        while (l > 0 && p[l] >= half) {
            l--
        }
        if (p[l] >= half) {
            return null
        }

        var r = peakIdx
        while (r < n - 1 && p[r] >= half) {
            r++
        }
        if (p[r] >= half) {
            return null
        }

        val lf = l + (half - p[l]) / (p[l + 1] - p[l])
        val rf = r - 1 + (p[r - 1] - half) / (p[r - 1] - p[r])
        return rf - lf
    }

    private fun depthTitle(widthCmValue: Float): String {
        val lo = widthCmValue / 3.46f
        val hi = widthCmValue / 2f
        if (hi < 1f) {
            return "< 1 см"
        }
        return String.format(Locale.US, "≈ %.0f–%.0f см", lo, hi)
    }

    fun analyze(floor: Float, ac: Boolean): Pair<String, String> {

        if (row == 0) {
            return Pair("—", "Сделай хотя бы один проход.")
        }

        val sm = Array(row) { smooth(cells[it]) }

        var peak = 0f
        var pr = 0
        var pc = 0
        var sum = 0f

        for (r in 0 until row) {
            for (c in 0 until COLS) {
                val v = sm[r][c]
                sum += v
                if (v > peak) {
                    peak = v
                    pr = r
                    pc = c
                }
            }
        }

        val mean = sum / (row * COLS)
        val gate = if (ac) floor * 0.6f else 3f

        peakRow = -1
        peakCol = -1

        if (peak < gate) {
            return Pair(
                "не найден",
                "Сигнал на уровне шума. Включи мощную нагрузку на линии (чайник, обогреватель), прижми телефон к стене и повтори."
            )
        }

        if (peak < mean * 2.5f) {
            return Pair(
                "нет пика",
                "Сигнал везде примерно одинаковый, выраженной линии не видно. Повтори проход ближе к стене."
            )
        }

        peakRow = pr
        peakCol = pc

        val x = (pc + 0.5f) * widthCm / COLS
        val sb = StringBuilder()

        sb.append(
            String.format(
                Locale.US,
                "Максимум в ряду %d, примерно %.0f см от начала прохода.\n",
                pr + 1, x
            )
        )

        if (ac && floor > 0f) {
            sb.append(
                String.format(
                    Locale.US,
                    "Сигнал в максимуме: x%.1f от уровня шума.\n",
                    peak / floor
                )
            )
        }

        var title = "—"

        val w1 = fwhm(sm[pr], COLS, pc)
        if (w1 != null) {
            val wc = w1 * widthCm / COLS
            title = depthTitle(wc)
            sb.append(
                String.format(
                    Locale.US,
                    "Пик по проходу узкий (ширина ~%.0f см): провод идёт поперёк прохода.\n",
                    wc
                )
            )
        } else {
            sb.append("Вдоль прохода пик не сужается: провод может идти вдоль прохода.\n")
        }

        if (row >= 3) {
            val q = FloatArray(row) { sm[it][pc] }
            val w2 = fwhm(q, row, pr)
            if (w2 != null) {
                val wc = w2 * rowCm
                if (title == "—") {
                    title = depthTitle(wc)
                }
                sb.append(
                    String.format(
                        Locale.US,
                        "Между проходами пик узкий (ширина ~%.0f см): провод идёт вдоль проходов, около ряда %d.\n",
                        wc, pr + 1
                    )
                )
            } else {
                sb.append("Между проходами пик не сужается.\n")
            }
        } else {
            sb.append("Для второй оценки сделай 3 прохода и больше.\n")
        }

        sb.append("Грубая оценка, погрешность 50% и больше.")

        return Pair(title, sb.toString())
    }
}

// конец файла
