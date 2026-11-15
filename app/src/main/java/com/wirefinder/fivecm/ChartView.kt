package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.abs

class ChartView(
    context: Context,
    private val seriesCount: Int,
    private val capacity: Int,
    private val symmetric: Boolean
) : View(context) {

    var minRange = 0.05f

    private val data = Array(seriesCount) { FloatArray(capacity) }
    private var count = 0
    private var head = 0
    private val colors = intArrayOf(
        0xFFD9381E.toInt(),
        0xFF147D64.toInt(),
        0xFF2B6CB0.toInt()
    )
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint()
    private val path = Path()

    init {
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 3f
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 2f
        framePaint.color = 0xFFCBD5E0.toInt()
        setBackgroundColor(0xFFFFFFFF.toInt())
    }

    fun push(values: FloatArray) {
        for (s in 0 until seriesCount) {
            data[s][head] = values[s]
        }
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    fun clear() {
        count = 0
        head = 0
    }

    fun peak(): Float {
        var m = 0f
        for (s in 0 until seriesCount) {
            for (i in 0 until count) {
                val v = abs(data[s][i])
                if (v > m) {
                    m = v
                }
            }
        }
        return m
    }

    fun scale(): Float {
        val m = peak()
        return if (m < minRange) minRange else m
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(1f, 1f, w - 1f, h - 1f, framePaint)

        if (symmetric) {
            canvas.drawLine(0f, h / 2f, w, h / 2f, framePaint)
        }

        if (count < 2) {
            return
        }

        val sc = scale()
        val start = if (count < capacity) 0 else head

        for (s in 0 until seriesCount) {
            path.reset()
            for (k in 0 until count) {
                val idx = (start + k) % capacity
                val v = data[s][idx]
                val px = w * k / (capacity - 1).toFloat()
                val py = if (symmetric) {
                    h / 2f - v / sc * (h / 2f - 4f)
                } else {
                    h - 4f - v / sc * (h - 8f)
                }
                if (k == 0) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
            }
            linePaint.color = colors[s % colors.size]
            canvas.drawPath(path, linePaint)
        }
    }
}
