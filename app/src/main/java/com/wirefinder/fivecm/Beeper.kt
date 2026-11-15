package com.wirefinder.fivecm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

class Beeper {

    private val exec = Executors.newSingleThreadExecutor()

    @Volatile
    private var busy = false

    fun beep(freq: Int, ms: Int) {
        if (busy) {
            return
        }
        busy = true
        try {
            exec.execute {
                try {
                    play(freq, ms)
                } catch (e: Exception) {
                    // звук недоступен
                } finally {
                    busy = false
                }
            }
        } catch (e: Exception) {
            busy = false
        }
    }

    private fun play(freq: Int, ms: Int) {
        val rate = 22050
        val n = rate * ms / 1000
        val buf = ShortArray(n)
        val fade = rate / 200

        for (i in 0 until n) {
            var env = 1.0
            if (i < fade) {
                env = i.toDouble() / fade
            }
            if (i > n - fade) {
                env = (n - i).toDouble() / fade
            }
            val v = sin(2.0 * PI * freq * i / rate) * 30000.0 * env
            buf[i] = v.toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buf, 0, n)
        track.play()
        Thread.sleep(ms + 40L)
        track.release()
    }

    fun shutdown() {
        exec.shutdownNow()
    }
}

// конец файла
