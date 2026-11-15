package com.wirefinder.fivecm

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

class Mains {

    var mainsHz = 50f
    var raw = 0f
    var noise = 0f
    var ratio = 0f
    var effHz = 0f
    var valid = false

    private val cap = 512
    private val bx = FloatArray(cap)
    private val by = FloatArray(cap)
    private val bz = FloatArray(cap)
    private val tx = FloatArray(cap)
    private val ty = FloatArray(cap)
    private val tz = FloatArray(cap)
    private var count = 0
    private var head = 0
    private val offsets = floatArrayOf(-14f, -10f, -7f, 7f, 10f, 14f)

    fun clear() {
        count = 0
        head = 0
    }

    fun push(x: Float, y: Float, z: Float) {
        bx[head] = x
        by[head] = y
        bz[head] = z
        head = (head + 1) % cap
        if (count < cap) {
            count++
        }
    }

    private fun prepare(n: Int) {
        var mx = 0f
        var my = 0f
        var mz = 0f
        val start = (head - n + cap) % cap

        for (k in 0 until n) {
            val i = (start + k) % cap
            mx += bx[i]
            my += by[i]
            mz += bz[i]
        }
        mx /= n
        my /= n
        mz /= n

        for (k in 0 until n) {
            val i = (start + k) % cap
            val w = 0.5f - 0.5f * cos(2.0 * PI * k / (n - 1)).toFloat()
            tx[k] = (bx[i] - mx) * w
            ty[k] = (by[i] - my) * w
            tz[k] = (bz[i] - mz) * w
        }
    }

    private fun goertzel(a: FloatArray, n: Int, f: Float, fs: Float): Float {
        val w = 2.0 * PI * f / fs
        val coeff = 2.0 * cos(w)
        var s1 = 0.0
        var s2 = 0.0

        for (i in 0 until n) {
            val s = a[i] + coeff * s1 - s2
            s2 = s1
            s1 = s
        }

        return (s1 * s1 + s2 * s2 - coeff * s1 * s2).toFloat()
    }

    private fun ampSq(f: Float, n: Int, fs: Float): Float {
        val p = goertzel(tx, n, f, fs) +
            goertzel(ty, n, f, fs) +
            goertzel(tz, n, f, fs)
        val v = p * 16f / (n.toFloat() * n.toFloat())
        return if (v < 0f) 0f else v
    }

    fun analyze(fs: Float): Boolean {

        var f0 = mainsHz % fs
        if (f0 > fs / 2f) {
            f0 = fs - f0
        }
        effHz = f0
        valid = f0 >= 4f

        val minN = (fs * 0.45f).toInt().coerceIn(48, cap)
        if (count < minN) {
            return false
        }

        if (!valid) {
            return true
        }

        val want = (fs * 0.8f).toInt().coerceIn(64, cap)
        val n = if (count < want) count else want

        prepare(n)

        val sig = ampSq(f0, n, fs)

        var sum = 0f
        var k = 0
        for (o in offsets) {
            val f = f0 + o
            if (f >= 2f && f <= fs / 2f - 1f) {
                sum += ampSq(f, n, fs)
                k++
            }
        }

        val nz = if (k > 0) sum / k else 0.00000001f

        raw = sqrt(sig)
        noise = sqrt(nz)

        val den = if (noise < 0.00001f) 0.00001f else noise
        ratio = raw / den

        return true
    }
}

// конец файла
