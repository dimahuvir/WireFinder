package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class ScanView(context: Context) : View(context) {

    var map: ScanMap? = null
    var scaleMin = 0.05f

    private val cellPaint = Paint()
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = FloatArray(3)

    init {
        cursorPaint.color = 0xFFFFFFFF.toInt()
        cursorPaint.strokeWidth = 4f
        markPaint.color = 0xFFFFFFFF.toInt()
        markPaint.style = Paint.Style.STROKE
        markPaint.strokeWidth = 5f
        setBackgroundColor(0xFFE2E8F0.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val m = map ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val cw = w / ScanMap.COLS
        val ch = h / ScanMap.ROWS

        var vmax = m.maxValue()
        if (vmax < scaleMin) {
            vmax = scaleMin
        }

        for (r in 0 until ScanMap.ROWS) {
            val top = h - (r + 1) * ch
            for (c in 0 until ScanMap.COLS) {
                val recorded =
                    r < m.row || (r == m.row && m.state == 2 && c <= m.cursorCol)
                if (!recorded) {
                    continue
                }
                val t = (m.cells[r][c] / vmax).coerceIn(0f, 1f)
                hsv[0] = 240f * (1f - t)
                hsv[1] = 1f
                hsv[2] = 0.35f + 0.65f * t
                cellPaint.color = Color.HSVToColor(hsv)
                canvas.drawRect(c * cw, top, (c + 1) * cw, top + ch, cellPaint)
            }
        }

        if (m.state == 2 && m.cursorCol >= 0 && m.row < ScanMap.ROWS) {
            val x = (m.cursorCol + 0.5f) * cw
            val top = h - (m.row + 1) * ch
            canvas.drawLine(x, top, x, top + ch, cursorPaint)
        }

        if (m.peakRow >= 0 && m.peakCol >= 0) {
            val cx = (m.peakCol + 0.5f) * cw
            val cy = h - (m.peakRow + 0.5f) * ch
            canvas.drawCircle(cx, cy, ch * 0.4f, markPaint)
        }
    }
}

// конец файла
