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

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null
    private lateinit var ui: Ui

    private val engine = Engine()
    private val beeper = Beeper()

    private val sensNames = arrayOf("обычная", "высокая", "максимальная")

    private var soundEnabled = true
    private var infoBase = ""
    private var lastUiMs = 0L
    private var lastBeepMs = 0L

    private val colorGrey = 0xFF64748B.toInt()
    private val colorGreen = 0xFF2E8B57.toInt()
    private val colorOrange = 0xFFE07B00.toInt()
    private val colorRed = 0xFFC62828.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        magneticSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

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

        ui.zeroButton.setOnClickListener {
            engine.zero(SystemClock.elapsedRealtime())
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

        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) {
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
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

        if (!soundEnabled || e.calibrating) {
            return
        }

        val wire = e.wireLevel
        val metal = e.metalLevel
        val lvl = if (wire > metal) wire else metal

        if (lvl <= 0.12f || nowMs - lastBeepMs < 1100) {
            return
        }

        lastBeepMs = nowMs
        e.muteUntilMs = nowMs + 450

        if (wire >= metal) {
            beeper.beep((1200f + 800f * lvl).toInt(), 120)
        } else {
            beeper.beep((350f + 400f * lvl).toInt(), 200)
        }
    }

    private fun refreshUi() {

        val e = engine
        val wire = e.wireLevel
        val metal = e.metalLevel

        if (e.calibrating) {
            ui.setCard(
                "ПОДГОТОВКА",
                "Держи телефон неподвижно вдали от металла",
                colorGrey
            )
        } else if (wire >= 0.15f && metal >= 0.15f) {
            ui.setCard(
                "ПРОВОД + МЕТАЛЛ",
                "Переменное поле 50 Гц и металл рядом",
                colorRed
            )
        } else if (wire >= 0.15f) {
            ui.setCard(
                "ПРОВОД ПОД НАПРЯЖЕНИЕМ",
                "Найдено переменное поле 50 Гц",
                colorRed
            )
        } else if (metal >= 0.15f) {
            ui.setCard(
                "МЕТАЛЛ",
                "Коробка, арматура, труба или магнит",
                colorOrange
            )
        } else {
            ui.setCard(
                "ЧИСТО",
                "Двигай телефон медленно вдоль стены",
                colorGreen
            )
        }

        ui.wireBar.level = wire
        ui.wireBar.invalidate()
        ui.metalBar.level = metal
        ui.metalBar.invalidate()

        ui.wireLabel.text = if (e.rateHz > 0f && !e.mains.valid) {
            "ПРОВОД 50 Гц: недоступен при такой частоте датчика"
        } else {
            String.format(
                Locale.US,
                "ПРОВОД 50 Гц: %.3f µT, x%.1f над шумом",
                e.mains.amp, e.mains.ratio
            )
        }

        ui.metalLabel.text =
            String.format(Locale.US, "МЕТАЛЛ: отклонение %.1f µT", e.metalDev)

        ui.hintText.text = e.hint

        var info = String.format(
            Locale.US,
            "Частота опроса датчика: %.0f Гц. 50 Гц виден как %.0f Гц.",
            e.rateHz, e.mains.effHz
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
