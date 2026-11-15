package com.wirefinder.fivecm

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var linSensor: Sensor? = null
    private var accSensor: Sensor? = null
    private var rotSensor: Sensor? = null
    private lateinit var ui: Ui

    private val engine = Engine()
    private val beeper = Beeper()
    private val rotBuf = FloatArray(9)

    private val sensNames = arrayOf("обычная", "высокая", "максимальная")

    private var soundEnabled = true
    private var infoBase = ""
    private var lastUiMs = 0L
    private var lastBeepMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        magneticSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        linSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        accSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        ui = Ui(this)
        setContentView(ui.build())
        setupButtons()

        val sensor = magneticSensor

        infoBase = if (sensor == null) {
            "Магнитометр не найден. Телефон не может выполнять магнитные измерения."
        } else {
            "Датчик: ${sensor.name}\n" +
            "Производитель: ${sensor.vendor}\n" +
            "Разрешение: ${sensor.resolution} µT"
        }

        val nfcAvailable = NfcAdapter.getDefaultAdapter(this) != null
        val nfcText = if (nfcAvailable) "обнаружен" else "не обнаружен"
        infoBase = infoBase + "\nNFC: " + nfcText
        ui.sensorInfoText.text = infoBase
    }

    private fun setupButtons() {

        ui.sensButton.text = "Чувствительность: " + sensNames[engine.sens]

        ui.zeroButton.setOnClickListener {
            engine.zero()
        }

        ui.sensButton.setOnClickListener {
            engine.sens = (engine.sens + 1) % 3
            ui.sensButton.text = "Чувствительность: " + sensNames[engine.sens]
        }

        ui.soundButton.setOnClickListener {
            soundEnabled = !soundEnabled
            ui.soundButton.text = if (soundEnabled) {
                "Звук: включён"
            } else {
                "Звук: выключен"
            }
        }

        ui.testButton.setOnClickListener {
            engine.muteUntilMs = SystemClock.elapsedRealtime() + 700
            beeper.beep(880, 400)
        }
    }

    override fun onResume() {
        super.onResume()

        engine.restart()

        magneticSensor?.let {
            sensorManager.registerListener(this, it, 5000)
        }

        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        val lin = linSensor
        if (lin != null) {
            sensorManager.registerListener(this, lin, SensorManager.SENSOR_DELAY_GAME)
        } else {
            accSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        rotSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        beeper.shutdown()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {

        val nowMs = SystemClock.elapsedRealtime()
        val v = event.values

        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> onMagnetic(event, nowMs)

            Sensor.TYPE_GYROSCOPE -> {
                val g = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                engine.onGyro(g, nowMs)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val a = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                engine.onLinear(a, nowMs)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val a = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                engine.onLinear(abs(a - 9.81f), nowMs)
            }

            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotBuf, v)
                engine.onRotation(rotBuf, nowMs)
            }
        }
    }

    private fun onMagnetic(event: SensorEvent, nowMs: Long) {

        val e = engine

        e.onSample(
            event.values[0],
            event.values[1],
            event.values[2],
            event.timestamp,
            nowMs
        )

        liveBeep(nowMs)

        if (nowMs - lastUiMs >= 100) {
            lastUiMs = nowMs
            e.tick(nowMs)
            refreshUi()
        }
    }

    private fun liveBeep(nowMs: Long) {

        val e = engine

        if (!soundEnabled || e.calibrating || e.shaking) {
            return
        }

        if (!e.wireOn && !e.metalOn) {
            return
        }

        if (nowMs - lastBeepMs < 1100) {
            return
        }

        lastBeepMs = nowMs

        if (e.wireOn) {
            e.muteUntilMs = nowMs + 280
            beeper.beep((1200f + 800f * e.wireLevel).toInt(), 120)
        } else {
            e.muteUntilMs = nowMs + 350
            beeper.beep((350f + 400f * e.metalLevel).toInt(), 200)
        }
    }

    private fun refreshUi() {

        val e = engine

        ui.lampWait.set(e.calibrating || e.shaking)
        ui.lampClean.set(e.cleanOn)
        ui.lampMetal.set(e.metalOn)
        ui.lampWire.set(e.wireOn)
        ui.lampOver.set(e.overOn)

        ui.wireBar.level = e.wireLevel
        ui.wireBar.invalidate()
        ui.metalBar.level = e.metalLevel
        ui.metalBar.invalidate()

        ui.wireLabel.text = if (e.rateHz > 0f && !e.mains.valid) {
            "ПРОВОД 50 Гц: недоступен при такой частоте датчика"
        } else {
            String.format(
                Locale.US,
                "ПРОВОД 50 Гц: x%.1f к фону",
                e.wireRel
            )
        }

        ui.metalLabel.text = String.format(
            Locale.US,
            "МЕТАЛЛ: отклонение %.1f µT (порог %.1f)",
            e.metalDev, e.metalThr
        )

        ui.hintText.text = e.hint

        val rotText = if (e.usingRot) "да" else "нет"
        val moveText = if (e.shaking) "ТРЯСКА" else "спокойно"

        var info = String.format(
            Locale.US,
            "Опрос %.0f Гц. Поворот учитывается: %s. Движение: %s.\nПровод: %.3f µT, фон %.3f µT, шум %.3f µT.",
            e.rateHz, rotText, moveText, e.wireAmp, e.wireBase, e.wireNoise
        )
        if (e.rateHz in 1f..89f) {
            info += "\nДатчик отдаёт мало отсчётов: поиск провода может работать плохо."
        }
        ui.infoText.text = info

        ui.sensorInfoText.text = infoBase
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // не используется
    }
}

// конец файла
