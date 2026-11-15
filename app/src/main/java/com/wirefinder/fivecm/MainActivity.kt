package com.wirefinder.fivecm

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null

    private lateinit var magnitudeText: TextView
    private lateinit var deltaText: TextView
    private lateinit var statusText: TextView
    private lateinit var sensorInfoText: TextView
    private lateinit var historyText: TextView
    private lateinit var calibrateButton: Button

    private var baseline: Float? = null
    private var latestMagnitude = 0f
    private var lastAlertTime = 0L

    private val samples = ArrayDeque<Float>()
    private var alertEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        magneticSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        buildScreen()

        val sensor = magneticSensor

        if (sensor == null) {
            sensorInfoText.text =
                "Магнитометр не найден. Телефон не может выполнять магнитные измерения."

            calibrateButton.isEnabled = false
            statusText.text = "Датчик недоступен"

        } else {
            sensorInfoText.text =
                "Датчик: ${sensor.name}\n" +
                "Производитель: ${sensor.vendor}\n" +
                "Разрешение: ${sensor.resolution} µT"

            statusText.text =
                "Готово. Убери телефон от металла и нажми «Калибровка»."
        }

        val nfcAvailable = NfcAdapter.getDefaultAdapter(this) != null

        sensorInfoText.append(
            "\nNFC: " +
            if (nfcAvailable) "обнаружен" else "не обнаружен"
        )
    }

    private fun buildScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(0xFFF3F6FA.toInt())
        }

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(
            label(
                "WIREFINDER 5CM",
                26,
                true,
                0xFF14263D.toInt()
            )
        )

        content.addView(
            label(
                "Поиск изменений магнитного поля встроенными датчиками телефона",
                14,
                false,
                0xFF536477.toInt()
            )
        )

        content.addView(gap(22))

        magnitudeText = label(
            "--.- µT",
            42,
            true,
            0xFF147D64.toInt()
        ).apply {
            gravity = Gravity.CENTER
        }

        content.addView(magnitudeText)

        content.addView(
            label(
                "Модуль магнитного поля",
                13,
                false,
                0xFF536477.toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        content.addView(gap(16))

        deltaText = label(
            "Отклонение: нет калибровки",
            19,
            true,
            0xFF14263D.toInt()
        )

        content.addView(deltaText)
        content.addView(gap(8))

        statusText = label(
            "Ожидание датчика...",
            16,
            true,
            0xFF536477.toInt()
        )

        content.addView(statusText)
        content.addView(gap(14))

        calibrateButton = Button(this).apply {
            text = "Калибровка в текущем месте"
            isAllCaps = false

            setOnClickListener {
                baseline = latestMagnitude

                statusText.text =
                    "База сохранена. Медленно перемещай телефон вдоль стены."

                updateDisplay(latestMagnitude)
            }
        }

        content.addView(calibrateButton)

        Button(this).apply {
            text = "Сбросить калибровку"
            isAllCaps = false

            setOnClickListener {
                baseline = null

                deltaText.text =
                    "Отклонение: нет калибровки"

                statusText.text =
                    "Калибровка сброшена."
            }
        }.also {
            content.addView(it)
        }

        Button(this).apply {
            text = "Вибрация: включена"
            isAllCaps = false

            setOnClickListener {
                alertEnabled = !alertEnabled

                text = if (alertEnabled) {
                    "Вибрация: включена"
                } else {
                    "Вибрация: выключена"
                }
            }
        }.also {
            content.addView(it)
        }

        content.addView(gap(18))

        content.addView(
            label(
                "Последние измерения",
                17,
                true,
                0xFF14263D.toInt()
            )
        )

        historyText = label(
            "Пока нет данных",
            13,
            false,
            0xFF536477.toInt()
        )

        content.addView(historyText)
        content.addView(gap(18))

        content.addView(
            label(
                "Информация о датчике",
                17,
                true,
                0xFF14263D.toInt()
            )
        )

        sensorInfoText = label(
            "Проверка...",
            13,
            false,
            0xFF536477.toInt()
        )

        content.addView(sensorInfoText)
        content.addView(gap(18))

        content.addView(
            label(
                "Почему NFC не ищет медь",
                17,
                true,
                0xFF14263D.toInt()
            )
        )

        content.addView(
            label(
                "NFC работает с совместимыми метками на очень небольшом расстоянии. Это радиосвязь, а не металлодетектор. Медь за штукатуркой не создаёт надёжного сигнала обнаружения. NFC здесь отображается только как характеристика телефона.",
                13,
                false,
                0xFF536477.toInt()
            )
        )

        content.addView(gap(14))

        content.addView(
            label(
                "ВАЖНО О БЕЗОПАСНОСТИ",
                16,
                true,
                0xFF9A3412.toInt()
            )
        )

        content.addView(
            label(
                "Это экспериментальный магнитометр, а не детектор скрытой проводки. Он не распознаёт кабель напрямую и не измеряет глубину. Арматура, трубы, динамики, магниты и другие предметы могут давать похожие изменения. Ненагруженный кабель или провод с малым током может вообще не обнаруживаться. Не сверли стену по показаниям приложения. Для проверки используй профессиональный детектор скрытой проводки.",
                13,
                false,
                0xFF8A3B24.toInt()
            )
        )

        scroll.addView(content)
        root.addView(scroll)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()

        magneticSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        latestMagnitude = sqrt(x*x + y*y + z*z)

        updateDisplay(latestMagnitude)
    }

    private fun updateDisplay(value: Float) {

        magnitudeText.text =
            String.format(Locale.US, "%.1f µT", value)

        val base = baseline

        if (base == null) {

            deltaText.text =
                "Отклонение: нет калибровки"

            statusText.text =
                "Сначала выполни калибровку вдали от металла."

        } else {

            val delta = abs(value - base)

            deltaText.text =
                String.format(
                    Locale.US,
                    "Отклонение от базы: %.1f µT",
                    delta
                )

            statusText.text = when {

                delta >= 20f ->
                    "Сильное изменение поля. Возможен металл или магнит."

                delta >= 8f ->
                    "Заметное изменение поля. Повтори проход."

                delta >= 3f ->
                    "Небольшое изменение поля."

                else ->
                    "Заметной магнитной аномалии не видно."
            }

            if (delta >= 8f && alertEnabled) {
                vibrateOnce()
            }
        }

        samples.addLast(value)

        while (samples.size > 15) {
            samples.removeFirst()
        }

        historyText.text =
            samples.reversed().joinToString("\n") {
                String.format(Locale.US, "%.1f µT", it)
            }
    }

    private fun vibrateOnce() {

        val now = System.currentTimeMillis()

        if (now - lastAlertTime < 1200) {
            return
        }

        lastAlertTime = now

        try {

            val vibrator =
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (Build.VERSION.SDK_INT >= 26) {

                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        100,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )

            } else {

                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }

        } catch (_: Exception) {
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {

        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {

            statusText.text =
                "Низкая точность датчика. Удали телефон от магнитов и повтори калибровку."
        }
    }

    private fun label(
        value: String,
        size: Int,
        bold: Boolean,
        color: Int
    ) = TextView(this).apply {

        text = value
        textSize = size.toFloat()
        setTextColor(color)

        if (bold) {
            setTypeface(typeface, Typeface.BOLD)
        }

        setPadding(0, dp(5), 0, dp(5))
    }

    private fun gap(height: Int) =
        View(this).apply {
            layoutParams =
                LinearLayout.LayoutParams(1, dp(height))
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}
