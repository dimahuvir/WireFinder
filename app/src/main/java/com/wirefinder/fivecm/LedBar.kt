package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class LedBar(context: Context) : View(context) {

    var level = 0f

    private val segments = 12
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 6f
        val sw = (w - gap * (segments - 1)) / segments

        var lit = (level * segments + 0.5f).toInt()
        if (level > 0.03f && lit < 1) {
            lit = 1
        }

        for (i in 0 until segments) {
            paint.color = if (i >= lit) {
                0xFFD5DCE6.toInt()
            } else if (i < 7) {
                0xFF2FA84F.toInt()
            } else if (i < 10) {
                0xFFE6B800.toInt()
            } else {
                0xFFD62828.toInt()
            }

            val left = i * (sw + gap)
            canvas.drawRoundRect(left, 0f, left + sw, h, 8f, 8f, paint)
        }
    }
}

// конец файла
