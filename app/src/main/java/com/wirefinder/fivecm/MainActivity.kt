
package com.wirefinder.fivecm

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null
    private lateinit var ui: Ui

    private var baseline = 0f
    private var calibrated = false
    private var soundEnabled = true
    private var vibrationEnabled = false

    private var sampleCount = 0
    private var peak = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = Ui(this)
        setContentView(ui.build())

        sensorManager = getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

        magneticSensor = sensorManager.getDefaultSensor(
            Sensor.TYPE_MAGNETIC_FIELD
        )

        if (magneticSensor == null) {
            ui.statusText.text = "Магнитометр не найден!"
            ui.sensorInfoText.text = "Телефон не поддерживает магнитный датчик."
            return
        }

        ui.sensorInfoText.text =
            "Магнитометр: ${magneticSensor!!.name}\n" +
            "Производитель: ${magneticSensor!!.vendor}\n" +
            "Максимальный диапазон: ${magneticSensor!!.maximumRange} µT"

        setupButtons()

        ui.statusText.text = "Датчик готов. Начни калибровку."
    }

    private fun setupButtons() {

        ui.calibrateButton.setOnClickListener {
            baseline = 0f
            calibrated = false
            sampleCount = 0
            ui.deltaText.text = "Калибровка..."
            ui.statusText.text = "Держи телефон неподвижно"
        }

        ui.m1Button.setOnClickListener {
            ui.statusText.text = "Замер 1: используй текущий сигнал"
            Toast.makeText(this, "Замер 1", Toast.LENGTH_SHORT).show()
        }

        ui.m2Button.setOnClickListener {
            ui.statusText.text = "Замер 2: используй текущий сигнал"
            Toast.makeText(this, "Замер 2", Toast.LENGTH_SHORT).show()
        }

        ui.offsetButton.setOnClickListener {
            Toast.makeText(
                this,
                "Отступ: 5 см",
                Toast.LENGTH_SHORT
            ).show()
        }

        ui.modeButton.setOnClickListener {
            Toast.makeText(
                this,
                "Режим переменного тока 50 Гц",
                Toast.LENGTH_SHORT
            ).show()
        }

        ui.soundButton.setOnClickListener {
            soundEnabled = !soundEnabled
            ui.soundButton.text =
                if (soundEnabled) "Звук: включён"
                else "Звук: выключен"
        }

        ui.vibrationButton.setOnClickListener {
            vibrationEnabled = !vibrationEnabled
            ui.vibrationButton.text =
                if (vibrationEnabled) "Вибрация: включена"
                else "Вибрация: выключена"
        }
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
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x*x + y*y + z*z)

        if (!calibrated) {
            sampleCount++
            baseline += magnitude

            if (sampleCount >= 40) {
                baseline /= sampleCount
                calibrated = true
                ui.deltaText.text = "Базовое поле: %.2f µT".format(baseline)
                ui.statusText.text = "Калибровка завершена"
            }
        }

        val delta = if (calibrated) {
            kotlin.math.abs(magnitude - baseline)
        } else {
            0f
        }

        if (delta > peak) peak = delta

        ui.magnitudeText.text = "%.2f µT".format(magnitude)
        ui.deltaText.text =
            if (calibrated) "Изменение: %.2f µT".format(delta)
            else "Калибровка: $sampleCount / 40"

        ui.levelBar.progress = (delta * 5).toInt().coerceIn(0, 100)

        ui.scopeChart.push(floatArrayOf(x, y, z))
        ui.trendChart.push(floatArrayOf(delta))

        ui.peakText.text = "Пик: %.2f µT".format(peak)

        ui.statusText.text =
            if (delta > 5f) "Сильное изменение поля"
            else if (delta > 2f) "Изменение магнитного поля"
            else "Поле относительно стабильное"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        ui.statusText.text = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Точность датчика: высокая"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Точность датчика: средняя"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Точность датчика: низкая"
            else -> "Точность датчика неизвестна"
        }
    }
}
