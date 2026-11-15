package com.wirefinder.fivecm

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private lateinit var scopeChart: ChartView
    private lateinit var trendChart: ChartView
    private lateinit var scopeCaption: TextView
    private lateinit var peakText: TextView
    private lateinit var depthText: TextView
    private lateinit var depthHint: TextView
    private lateinit var m2Button: Button
    private lateinit var offsetButton: Button

    private var mode = Mode.AC
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

    private val scopeBuf = FloatArray(3)
    private val trendBuf = FloatArray(1)

    // Замеры глубины
    private var s0: Float? = null
    private var s1: Float? = null
    private var offsetCm = 5
    private var hNear: Float? = null
    private var hFar: Float? = null
    private var depthMsg = "Сделай замер 1: прижми телефон к стене над максимумом сигнала."
    private var measureStage = 0
    private var measureStartMs = 0L
    private var measureUntilMs = 0L
    private var measureSum = 0.0
    private var measureCount = 0

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

        content.addView(gap(12))

        // Расстояние до провода
        content.addView(
            label("Расстояние до провода", 15, true, 0xFF536477.toInt())
        )
        depthText = label("—", 34, true, 0xFF147D64.toInt())
        content.addView(depthText)
        depthHint = label("", 12, false, 0xFF536477.toInt())
        content.addView(depthHint)

        content.addView(gap(8))

        magnitudeText = label("--.- µT", 30, true, 0xFF14263D.toInt())
        magnitudeText.gravity = Gravity.CENTER
        content.addView(magnitudeText)

        val magCaption =
            label("Модуль магнитного поля", 12, false, 0xFF536477.toInt())
        magCaption.gravity = Gravity.CENTER
        content.addView(magCaption)

        content.addView(gap(6))

        levelBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        )
        levelBar.max = 100
        levelBar.progress = 0
        levelBar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(14)
        )
        content.addView(levelBar)

        content.addView(gap(6))

        deltaText = label("Калибровка...", 16, true, 0xFF14263D.toInt())
        content.addView(deltaText)

        statusText = label("Ожидание датчика...", 15, true, 0xFF536477.toInt())
        content.addView(statusText)

        content.addView(gap(10))

        // Осциллограф
        scopeCaption = label(
            "Колебания поля по осям X, Y, Z", 12, false, 0xFF536477.toInt()
        )
        content.addView(scopeCaption)

        scopeChart = ChartView(this, 3, 200, true)
        scopeChart.minRange = 0.05f
        scopeChart.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)
        )
        content.addView(scopeChart)

        content.addView(gap(8))

        // График силы сигнала
        content.addView(
            label(
                "Сила сигнала за последние 15 секунд",
                12, false, 0xFF536477.toInt()
            )
        )

        trendChart = ChartView(this, 1, 150, false)
        trendChart.minRange = 0.05f
        trendChart.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(100)
        )
        content.addView(trendChart)

        peakText = label("Пик: --", 13, true, 0xFF14263D.toInt())
        content.addView(peakText)

        content.addView(gap(10))

        // Кнопки замеров
        val m1Button = Button(this)
        m1Button.text = "Замер 1: прижми к стене на максимуме"
        m1Button.isAllCaps = false
        m1Button.setOnClickListener {
            startMeasure(1)
        }
        content.addView(m1Button)

        m2Button = Button(this)
        m2Button.text = "Замер 2: отодвинь от стены на $offsetCm см"
        m2Button.isAllCaps = false
        m2Button.setOnClickListener {
            startMeasure(2)
        }
        content.addView(m2Button)

        offsetButton = Button(this)
        offsetButton.text = "Отступ для замера 2: $offsetCm см"
        offsetButton.isAllCaps = false
        offsetButton.setOnClickListener {
            offsetCm = when (offsetCm) {
                3 -> 5
                5 -> 10
                else -> 3
            }
            offsetButton.text = "Отступ для замера 2: $offsetCm см"
            m2Button.text = "Замер 2: отодвинь от стены на $offsetCm см"
            s1 = null
            recomputeDepth()
        }
        content.addView(offsetButton)

        content.addView(gap(8))

        calibrateButton = Button(this)
        calibrateButton.text = "Перекалибровать (держи неподвижно)"
        calibrateButton.isAllCaps = false
        calibrateButton.setOnClickListener {
            notice = ""
            startCalibration()
        }
        content.addView(calibrateButton)

        modeButton = Button(this)
        modeButton.text = "Режим: ПРОВОД ПОД НАГРУЗКОЙ (50 Гц)"
        modeButton.isAllCaps = false
        modeButton.setOnClickListener {
            if (mode == Mode.DC) {
                setMode(Mode.AC)
            } else {
                setMode(Mode.DC)
            }
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
                "2. Включи на этой линии мощную нагрузку: чайник, обогреватель, фен.\n" +
                "3. Не вращай телефон, держи его в одном положении.\n" +
                "4. Двигай медленно поперёк предполагаемой линии. Датчик обычно в верхней части телефона.\n" +
                "5. На максимуме сделай замер 1 у стены, затем замер 2 с отступом. После этого расстояние показывается вживую.\n" +
                "6. Графики показывают только колебания поля. Направление и силу тока телефон не определяет.",
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
                "Это экспериментальный магнитометр, а не детектор скрытой проводки. Расстояние показывается как грубая оценка с погрешностью 50% и больше. В двухжильном кабеле токи идут навстречу друг другу, и поле почти гасится, поэтому провод может быть не виден. Арматура, трубы, магниты и динамики дают похожие сигналы. Не сверли стену по показаниям приложения. Для проверки используй профессиональный детектор.",
                13, false, 0xFF8A3B24.toInt()
            )
        )

        scroll.addView(content)
        root.addView(scroll)

        setContentView(root)
    }

    private fun setMode(m: Mode) {
        mode = m
        modeButton.text = if (m == Mode.DC) {
            "Режим: МЕТАЛЛ (постоянное поле)"
        } else {
            "Режим: ПРОВОД ПОД НАГРУЗКОЙ (50 Гц)"
        }
        trendChart.clear()
        trendChart.minRange = if (m == Mode.DC) 2f else 0.05f
        history.clear()
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
        measureStage = 0
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

        s0 = null
        s1 = null
        recomputeDepth()

        notice = ""
        calibrating = false
    }

    private fun currentSignal(): Float {
        val p = acPower - acFloor * acFloor
        return if (p > 0f) sqrt(p) else 0.0001f
    }

    private fun startMeasure(stage: Int) {
        if (calibrating) {
            depthMsg = "Подожди, идёт калибровка."
            return
        }

        if (stage == 2 && s0 == null) {
            depthMsg = "Сначала сделай замер 1 у стены на максимуме."
            return
        }

        if (mode != Mode.AC) {
            setMode(Mode.AC)
        }

        if (stage == 1) {
            s0 = null
            s1 = null
            hNear = null
            hFar = null
        } else {
            s1 = null
            hNear = null
            hFar = null
        }

        measureStage = stage
        measureStartMs = SystemClock.elapsedRealtime()
        measureUntilMs = measureStartMs + 2000
        measureSum = 0.0
        measureCount = 0
    }

    private fun finishMeasure() {
        val avg = if (measureCount > 0) {
            (measureSum / measureCount).toFloat()
        } else {
            0.0001f
        }

        if (measureStage == 1) {
            s0 = avg
            s1 = null
        } else {
            s1 = avg
        }

        measureStage = 0
        recomputeDepth()
    }

    private fun recomputeDepth() {
        hNear = null
        hFar = null

        val a = s0
        val b = s1

        if (a == null) {
            depthMsg =
                "Сделай замер 1: прижми телефон к стене над максимумом сигнала."
            return
        }

        if (b == null) {
            depthMsg =
                "Замер 1 готов. Теперь замер 2: отодвинь телефон от стены на $offsetCm см."
            return
        }

        if (a < acFloor) {
            depthMsg =
                "Сигнал провода слабый. Включи нагрузку мощнее и повтори замер 1."
            return
        }

        if (b > a * 0.95f) {
            depthMsg =
                "Сигнал не упал при отодвигании: это не похоже на провод. Повтори оба замера."
            return
        }

        val d = offsetCm.toFloat()
        val bb = if (b < 0.0001f) 0.0001f else b
        val ratio = a / bb

        hNear = d / (ratio - 1f)
        hFar = d / (sqrt(ratio) - 1f)
        depthMsg = "Готово."
    }

    private fun fmtCm(v: Double): String {
        return if (v > 100.0) {
            ">100"
        } else {
            String.format(Locale.US, "%.0f", v)
        }
    }

    private fun updateDepthUi() {

        if (measureStage != 0) {
            depthText.text = "Замер..."
            depthHint.text = "Держи телефон неподвижно 2 секунды."
            return
        }

        val near = hNear
        val far = hFar
   
