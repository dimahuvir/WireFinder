package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class LampView(
    context: Context,
    private val title: String,
    private val onColor: Int
) : View(context) {

    var on = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
    }

    fun set(value: Boolean) {
        if (on != value) {
            on = value
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density
        val w = width.toFloat()

        var r = w / 2f - 8f * d
        if (r > 24f * d) {
            r = 24f * d
        }

        val cx = w / 2f
        val cy = r + 8f * d

        if (on) {
            paint.color = (onColor and 0x00FFFFFF) or 0x55000000
            canvas.drawCircle(cx, cy, r + 7f * d, paint)
        }

        paint.color = if (on) onColor else 0xFFCBD5E0.toInt()
        canvas.drawCircle(cx, cy, r, paint)

        textPaint.color = if (on) 0xFF14263D.toInt() else 0xFF8A97A8.toInt()
        textPaint.textSize = 10f * d
        canvas.drawText(title, cx, cy + r + 18f * d, textPaint)
    }
}

// конец файла
