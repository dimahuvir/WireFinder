package com.wirefinder.fivecm

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private enum class Mode { DC, AC }

    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null

    private lateinit var magnitudeText: TextView
    private lateinit var deltaText: TextView
    private lateinit var statusText: TextView
    private lateinit var sensorInfoText: TextView
    private lateinit var historyText: TextView
    private lateinit var calibrateButton: Button
    private lateinit var modeButton: Button
    private lateinit var levelBar: ProgressBar

    private var mode = Mode.DC
    private var soundEnabled = true
    private var alertEnabled = false

    private var infoBase = ""

    // Постоянное поле (металл)
    private var smoothMag = 0f
    private var smoothInit = false
    private var baseValue: Float? = null

    // Калибровка
    private var calibrating = false
    private var autoCalibDone = false
    private var calibStartMs = 0L
    private var calibUntilMs = 0L
    private var calibSumMag = 0.0
    private var calibSumAc = 0.0
    private var calibCount = 0
    private var calibAcCount = 0
    private var calibMin = 0f
    private var calibMax = 0f
    private var notice = ""

    // Переменное поле (провод под нагрузкой)
    private val hpRc = 0.008f
    private var lastTsNs = 0L
    private var hasPrev = false
    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var hpX = 0f
    private var hpY = 0f
    private var hpZ = 0f
    private var acPower = 0f
    private var acRms = 0f
    private var acFloor = 0.02f
    private var muteAcUntilMs = 0L
    private var rateHz = 0f

    private var level = 0f
    private var lastUiMs = 0L
    private var lastBeepMs = 0L
    private var lastAlertMs = 0L

    private var toneGen: ToneGenerator? = null
    private val history = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        magneticSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            toneGen = null
        }

        buildScreen()

        val sensor = magneticSensor

        if (sensor == null) {
            infoBase =
                "Магнитометр не найден. Телефон не может выполнять магнитные измерения."
            calibrateButton.isEnabled = false
            statusText.text = "Датчик недоступен"
        } else {
            infoBase =
                "Датчик: ${sensor.name}\n" +
                "Производитель: ${sensor.vendor}\n" +
                "Разрешение: ${sensor.resolution} µT"
        }

        val nfcAvailable = NfcAdapter.getDefaultAdapter(this) != null
        val nfcText = if (nfcAvailable) "обнаружен" else "не обнаружен"
        infoBase = infoBase + "\nNFC: " + nfcText
        sensorInfoText.text = infoBase
    }

    private fun buildScreen() {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(18), dp(18), dp(18))
        root.setBackgroundColor(0xFFF3F6FA.toInt())

        val scroll = ScrollView(this)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        content.addView(label("WIREFINDER 5CM", 26, true, 0xFF14263D.toInt()))

        content.addView(
            label(
                "Поиск изменений магнитного поля встроенным датчиком телефона",
                14, false, 0xFF536477.toInt()
            )
        )

        content.addView(gap(16))

        magnitudeText = label("--.- µT", 42, true, 0xFF147D64.toInt())
        magnitudeText.gravity = Gravity.CENTER
        content.addView(magnitudeText)

        val magCaption =
            label("Модуль магнитного поля", 13, false, 0xFF536477.toInt())
        magCaption.gravity = Gravity.CENTER
        content.addView(magCaption)

        content.addView(gap(10))

        levelBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        )
        levelBar.max = 100
        levelBar.progress = 0
        levelBar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(14)
        )
        content.addView(levelBar)

        content.addView(gap(10))

        deltaText = label("Калибровка...", 18, true, 0xFF14263D.toInt())
        content.addView(deltaText)

        statusText = label("Ожидание датчика...", 16, true, 0xFF536477.toInt())
        content.addView(statusText)
        content.addView(gap(10))

        calibrateButton = Button(this)
        calibrateButton.text = "Перекалибровать (держи неподвижно)"
        calibrateButton.isAllCaps = false
        calibrateButton.setOnClickListener {
            notice = ""
            startCalibration()
        }
        content.addView(calibrateButton)

        modeButton = Button(this)
        modeButton.text = "Режим: МЕТАЛЛ (постоянное поле)"
        modeButton.isAllCaps = false
        modeButton.setOnClickListener {
            if (mode == Mode.DC) {
                mode = Mode.AC
                modeButton.text = "Режим: ПРОВОД ПОД НАГРУЗКОЙ (50 Гц)"
            } else {
                mode = Mode.DC
                modeButton.text = "Режим: МЕТАЛЛ (постоянное поле)"
            }
            history.clear()
        }
        content.addView(modeButton)

        val soundButton = Button(this)
        soundButton.text = "Звук: включён"
        soundButton.isAllCaps = false
        soundButton.setOnClickListener {
            soundEnabled = !soundEnabled
            soundButton.text = if (soundEnabled) {
                "Звук: включён"
            } else {
                "Звук: выключен"
            }
        }
        content.addView(soundButton)

        val vibrationButton = Button(this)
        vibrationButton.text = "Вибрация: выключена"
        vibrationButton.isAllCaps = false
        vibrationButton.setOnClickListener {
            alertEnabled = !alertEnabled
            vibrationButton.text = if (alertEnabled) {
                "Вибрация: включена"
            } else {
                "Вибрация: выключена"
            }
        }
        content.addView(vibrationButton)

        content.addView(gap(14))

        content.addView(label("Как искать провод", 17, true, 0xFF14263D.toInt()))
        content.addView(
            label(
                "1. Подержи телефон неподвижно 2 секунды вдали от металла.\n" +
                "2. Для режима «Провод» включи на этой линии мощную нагрузку: чайник, обогреватель, фен.\n" +
                "3. Не вращай телефон, держи его в одном положении.\n" +
                "4. Двигай медленно поперёк предполагаемой линии. Датчик обычно в верхней части телефона.\n" +
                "5. Во время работы режима «Провод» не трогай экран и не двигай телефон резко.",
                13, false, 0xFF536477.toInt()
            )
        )

        content.addView(gap(14))

        content.addView(label("Последние измерения", 17, true, 0xFF14263D.toInt()))
        historyText = label("Пока нет данных", 13, false, 0xFF536477.toInt())
        content.addView(historyText)
        content.addView(gap(14))

        content.addView(label("Информация о датчике", 17, true, 0xFF14263D.toInt()))
        sensorInfoText = label("Проверка...", 13, false, 0xFF536477.toInt())
        content.addView(sensorInfoText)
        content.addView(gap(14))

        content.addView(label("ВАЖНО О БЕЗОПАСНОСТИ", 16, true, 0xFF9A3412.toInt()))
        content.addView(
            label(
                "Это экспериментальный магнитометр, а не детектор скрытой проводки. Он не измеряет глубину и не распознаёт кабель напрямую. В двухжильном кабеле токи идут навстречу друг другу, и поле почти гасится, поэтому провод может быть не виден. Арматура, трубы, магниты и динамики дают похожие сигналы. Не сверли стену по показаниям приложения. Для проверки используй профессиональный детектор.",
                13, false, 0xFF8A3B24.toInt()
            )
        )

        scroll.addView(content)
        root.addView(scroll)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()

        lastTsNs = 0L
        hasPrev = false
        smoothInit = false
        autoCalibDone = false

        magneticSensor?.let {
            sensorManager.registerListener(this, it, 5000)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        try {
            toneGen?.release()
        } catch (e: Exception) {
            // ничего
        }
        super.onDestroy()
    }

    private fun startCalibration() {
        calibrating = true
        calibStartMs = SystemClock.elapsedRealtime()
        calibUntilMs = calibStartMs + 2000
        calibSumMag = 0.0
        calibSumAc = 0.0
        calibCount = 0
        calibAcCount = 0
        calibMin = 1.0e9f
        calibMax = -1.0e9f
    }

    private fun finishCalibration() {
        if (calibCount == 0) {
            startCalibration()
            return
        }

        if (calibMax - calibMin > 5f) {
            notice = "\nТелефон двигался или рядом металл. Повторяю..."
            startCalibration()
            return
        }

        baseValue = (calibSumMag / calibCount).toFloat()

        val avgAc = if (calibAcCount > 0) {
            (calibSumAc / calibAcCount).toFloat()
        } else {
            0.02f
        }
        acFloor = if (avgAc < 0.01f) 0.01f else avgAc

        notice = ""
        calibrating = false
    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val nowMs = SystemClock.elapsedRealtime()
        val ts = event.timestamp

        var dt = 0.005f
        if (lastTsNs != 0L) {
            val d = (ts - lastTsNs) / 1.0e9f
            if (d > 0.0005f && d < 0.1f) {
                dt = d
            }
        }
        lastTsNs = ts

        if (rateHz == 0f) {
            rateHz = 1f / dt
        } else {
            rateHz = rateHz * 0.98f + (1f / dt) * 0.02f
        }

        val mag = sqrt(x * x + y * y + z * z)

        if (!smoothInit) {
            smoothMag = mag
            smoothInit = true
        } else {
            val k = dt / (0.08f + dt)
            smoothMag += (mag - smoothMag) * k
        }

        // Фильтр высоких частот для переменного поля
        if (hasPrev) {
            val a = hpRc / (hpRc + dt)
            hpX = a * (hpX + x - prevX)
            hpY = a * (hpY + y - prevY)
            hpZ = a * (hpZ + z - prevZ)
        }
        prevX = x
        prevY = y
        prevZ = z
        hasPrev = true

        if (nowMs >= muteAcUntilMs) {
            val e2 = hpX * hpX + hpY * hpY + hpZ * hpZ
            val k2 = dt / (0.3f + dt)
            acPower += (e2 - acPower) * k2
            acRms = sqrt(acPower)
        }

        // Калибровка
        if (!autoCalibDone) {
            autoCalibDone = true
            startCalibration()
        }

        if (calibrating) {
            calibSumMag += mag
            calibCount++
            if (mag < calibMin) calibMin = mag
            if (mag > calibMax) calibMax = mag

            if (nowMs - calibStartMs >= 600) {
                calibSumAc += acRms
                calibAcCount++
            }

            if (nowMs >= calibUntilMs) {
                finishCalibration()
            }
        }

        // Уровень сигнала 0..1
        val base = baseValue
        level = if (calibrating || base == null) {
            0f
        } else if (mode == Mode.DC) {
            ((abs(smoothMag - base) - 1.5f) / 20f).coerceIn(0f, 1f)
        } else {
            val ratio = acRms / acFloor
            ((ratio - 1.4f) / 6f).coerceIn(0f, 1f)
        }

        if (!calibrating && level > 0.03f) {
            beepIfNeeded(nowMs)
            if (alertEnabled && level > 0.3f) {
                vibrateOnce(nowMs)
            }
        }

        if (nowMs - lastUiMs >= 100) {
            lastUiMs = nowMs
            refreshUi()
        }
    }

    private fun refreshUi() {

        magnitudeText.text =
            String.format(Locale.US, "%.1f µT", smoothMag)

        levelBar.progress = (level * 100f).toInt()

        val base = baseValue

        if (calibrating) {
            deltaText.text = "Калибровка..."
            statusText.text =
                "Держи телефон неподвижно вдали от металла." + notice
        } else if (base == null) {
            deltaText.text = "Нет калибровки"
            statusText.text = "Нажми «Перекалибровать»."
        } else if (mode == Mode.DC) {
            val delta = smoothMag - base
            deltaText.text =
                String.format(Locale.US, "Отклонение от базы: %.1f µT", delta)

            statusText.text = when {
                level > 0.6f ->
                    "Сильное изменение поля: металл или магнит рядом."
                level > 0.25f ->
                    "Заметное изменение. Повтори проход."
                level > 0.03f ->
                    "Небольшое изменение поля."
                else ->
                    "Аномалии не видно."
            }

            addHistory(String.format(Locale.US, "%.1f µT", smoothMag))
        } else {
            val ratio = acRms / acFloor
            deltaText.text =
                String.format(
                    Locale.US,
                    "Переменное поле: %.3f µT (x%.1f к фону)",
                    acRms, ratio
                )

            statusText.text = when {
                level > 0.6f ->
                    "Сильное переменное поле: возможно, рядом провод под током."
                level > 0.25f ->
                    "Заметное переменное поле. Проведи медленно поперёк."
                level > 0.03f ->
                    "Слабое переменное поле."
                else ->
                    "Переменного поля не видно. Включи нагрузку на этой линии."
            }

            addHistory(String.format(Locale.US, "%.3f µT (x%.1f)", acRms, ratio))
        }

        sensorInfoText.text =
            infoBase + "\n" +
            String.format(Locale.US, "Частота опроса: ~%.0f Гц", rateHz)
    }

    private fun addHistory(value: String) {
        history.addLast(value)
        while (history.size > 15) {
            history.removeFirst()
        }
        historyText.text = history.reversed().joinToString("\n")
    }

    private fun beepIfNeeded(nowMs: Long) {

        if (!soundEnabled) {
            return
        }

        val interval = (800f - 550f * level).toLong()

        if (nowMs - lastBeepMs < interval) {
            return
        }

        lastBeepMs = nowMs
        muteAcUntilMs = nowMs + 100

        try {
            val tone = if (level > 0.6f) {
                ToneGenerator.TONE_PROP_BEEP2
            } else {
                ToneGenerator.TONE_PROP_BEEP
            }
            toneGen?.startTone(tone, 60)
        } catch (e: Exception) {
            // звук недоступен
        }
    }

    private fun vibrateOnce(nowMs: Long) {

        if (nowMs - lastAlertMs < 1200) {
            return
        }

        lastAlertMs = nowMs
        muteAcUntilMs = nowMs + 250

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
        } catch (e: Exception) {
            // вибрация недоступна
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            notice = "\nНизкая точность датчика. Удали телефон от магнитов."
        }
    }

    private fun label(
        value: String,
        size: Int,
        bold: Boolean,
        color: Int
    ): TextView {
        val tv = TextView(this)
        tv.text = value
        tv.textSize = size.toFloat()
        tv.setTextColor(color)

        if (bold) {
            tv.setTypeface(tv.typeface, Typeface.BOLD)
        }

        tv.setPadding(0, dp(5), 0, dp(5))
        return tv
    }

    private fun gap(height: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(height))
        return v
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}
