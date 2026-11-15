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
    private val map = ScanMap()
    private val beeper = Beeper()

    private var soundEnabled = true
    private var infoBase = ""
    private var message = ""
    private var messageUntilMs = 0L

    private var lastUiMs = 0L
    private var lastBeepMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        magneticSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        ui = Ui(this)
        setContentView(ui.build())
        ui.scanView.map = map

        setupButtons()

        val sensor = magneticSensor

        if (sensor == null) {
            infoBase =
                "Магнитометр не найден. Телефон не может выполнять магнитные измерения."
            ui.startButton.isEnabled = false
        } else {
            infoBase =
                "Датчик: ${sensor.name}\n" +
                "Производитель: ${sensor.vendor}\n" +
                "Разрешение: ${sensor.resolution} µT"
        }

        val nfcAvailable = NfcAdapter.getDefaultAdapter(this) != null
        val nfcText = if (nfcAvailable) "обнаружен" else "не обнаружен"
        infoBase = infoBase + "\nNFC: " + nfcText
        ui.sensorInfoText.text = infoBase
    }

    private fun showMessage(text: String) {
        message = text
        messageUntilMs = SystemClock.elapsedRealtime() + 3000
    }

    private fun resetResult() {
        ui.depthText.text = "—"
        ui.depthHint.text = ""
    }

    private fun setupButtons() {

        ui.startButton.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (engine.calibrating) {
                showMessage("Подожди, идёт калибровка.")
            } else if (!map.startPass(now)) {
                if (map.state != 0) {
                    showMessage("Проход уже идёт.")
                } else {
                    showMessage("Карта заполнена. Нажми «Очистить карту».")
                }
            }
        }

        ui.clearButton.setOnClickListener {
            map.clear()
            resetResult()
        }

        ui.widthButton.setOnClickListener {
            map.widthCm = when (map.widthCm) {
                30 -> 50
                50 -> 80
                else -> 30
            }
            ui.widthButton.text = "Ширина прохода: ${map.widthCm} см"
        }

        ui.rowButton.setOnClickListener {
            map.rowCm = when (map.rowCm) {
                5 -> 10
                10 -> 15
                else -> 5
            }
            ui.rowButton.text = "Шаг между проходами: ${map.rowCm} см"
        }

        ui.calibrateButton.setOnClickListener {
            engine.notice = ""
            engine.startCalibration(SystemClock.elapsedRealtime())
        }

        ui.modeButton.setOnClickListener {
            engine.acMode = !engine.acMode
            engine.resetGain()
            map.clear()
            resetResult()
            ui.modeButton.text = if (engine.acMode) {
                "Режим: ПРОВОД ПОД НАГРУЗКОЙ (50 Гц)"
            } else {
                "Режим: МЕТАЛЛ (постоянное поле)"
            }
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

        engine.onSample(
            event.values[0],
            event.values[1],
            event.values[2],
            event.timestamp,
            nowMs
        )

        val ev = map.update(nowMs, engine.signal())
        handleEvent(ev, nowMs)

        if (map.state == 0 && !engine.calibrating && engine.level > 0.05f) {
            liveBeep(nowMs, engine.level)
        }

        if (nowMs - lastUiMs >= 100) {
            lastUiMs = nowMs
            refreshUi(nowMs)
        }
    }

    private fun handleEvent(ev: Int, nowMs: Long) {

        if (ev == ScanMap.EV_NONE) {
            return
        }

        if (ev == ScanMap.EV_END) {
            val r = map.analyze(engine.acFloor, engine.acMode)
            ui.depthText.text = r.first
            ui.depthHint.text = r.second
        }

        if (!soundEnabled) {
            return
        }

        engine.muteAcUntilMs = nowMs + 150

        when (ev) {
            ScanMap.EV_TICK -> beeper.beep(700, 80)
            ScanMap.EV_START -> beeper.beep(1500, 200)
            ScanMap.EV_END -> beeper.beep(1000, 300)
        }
    }

    private fun liveBeep(nowMs: Long, level: Float) {

        if (!soundEnabled) {
            return
        }

        val interval = (700f - 550f * level).toLong()

        if (nowMs - lastBeepMs < interval) {
            return
        }

        lastBeepMs = nowMs
        engine.muteAcUntilMs = nowMs + 120
        beeper.beep((500f + 1500f * level).toInt(), 70)
    }

    private fun stateLine(nowMs: Long): String {

        if (nowMs < messageUntilMs) {
            return message
        }

        if (engine.calibrating) {
            return "Калибровка: держи телефон неподвижно вдали от металла." +
                engine.notice
        }

        return when (map.state) {
            1 -> "Приготовься, старт через ${map.secondsLeft} с. Прижми телефон к стене в начале отрезка."
            2 -> "ВЕДИ телефон слева направо ровно и не вращай. Осталось ${map.secondsLeft} с."
            else -> "Готов. Проходов: ${map.row} из ${ScanMap.ROWS}. Нажми «Начать проход»."
        }
    }

    private fun refreshUi(nowMs: Long) {

        val e = engine

        ui.levelBar.progress = (e.level * 100f).toInt()
        ui.stateText.text = stateLine(nowMs)

        ui.liveText.text = if (e.acMode) {
            String.format(
                Locale.US,
                "Переменное поле: %.3f µT (шум %.3f µT)",
                e.acRms, e.acFloor
            )
        } else {
            String.format(
                Locale.US,
                "Поле: %.1f µT, отклонение %.1f µT",
                e.smoothMag, e.signal()
            )
        }

        ui.scanView.scaleMin = if (e.acMode) e.acFloor * 2f else 5f
        ui.scanView.invalidate()

        ui.axisText.text =
            "Слева направо: 0–${map.widthCm} см. Каждый следующий проход выше на ${map.rowCm} см. Нижний ряд — первый проход."

        ui.sensorInfoText.text =
            infoBase + "\n" +
            String.format(Locale.US, "Частота опроса: ~%.0f Гц", e.rateHz)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            engine.notice = "\nНизкая точность датчика. Удали телефон от магнитов."
        }
    }
}

// конец файла
