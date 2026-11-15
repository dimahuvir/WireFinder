package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class Ui(private val ctx: Context) {

    lateinit var magnitudeText: TextView
    lateinit var deltaText: TextView
    lateinit var statusText: TextView
    lateinit var sensorInfoText: TextView
    lateinit var historyText: TextView
    lateinit var depthText: TextView
    lateinit var depthHint: TextView
    lateinit var peakText: TextView
    lateinit var scopeCaption: TextView
    lateinit var levelBar: ProgressBar
    lateinit var scopeChart: ChartView
    lateinit var trendChart: ChartView

    lateinit var m1Button: Button
    lateinit var m2Button: Button
    lateinit var offsetButton: Button
    lateinit var calibrateButton: Button
    lateinit var modeButton: Button
    lateinit var soundButton: Button
    lateinit var vibrationButton: Button

    fun build(): View {

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(18), dp(18), dp(18))
        root.setBackgroundColor(0xFFF3F6FA.toInt())

        val scroll = ScrollView(ctx)

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL

        addTop(content)
        addCharts(content)
        addButtons(content)
        addInfo(content)

        scroll.addView(content)
        root.addView(scroll)

        return root
    }

    private fun addTop(content: LinearLayout) {

        content.addView(label("WIREFINDER 5CM", 26, true, 0xFF14263D.toInt()))

        content.addView(
            label(
                "Поиск изменений магнитного поля встроенным датчиком телефона",
                14, false, 0xFF536477.toInt()
            )
        )

        content.addView(gap(12))

        content.addView(label("Расстояние до провода", 15, true, 0xFF536477.toInt()))

        depthText = label("—", 34, true, 0xFF147D64.toInt())
        content.addView(depthText)

        depthHint = label("", 12, false, 0xFF536477.toInt())
        content.addView(depthHint)

        content.addView(gap(8))

        magnitudeText = label("--.- µT", 30, true, 0xFF14263D.toInt())
        magnitudeText.gravity = Gravity.CENTER
        content.addView(magnitudeText)

        val caption = label("Модуль магнитного поля", 12, false, 0xFF536477.toInt())
        caption.gravity = Gravity.CENTER
        content.addView(caption)

        content.addView(gap(6))

        levelBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
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
    }

    private fun addCharts(content: LinearLayout) {

        scopeCaption = label(
            "Колебания поля по осям X, Y, Z", 12, false, 0xFF536477.toInt()
        )
        content.addView(scopeCaption)

        scopeChart = ChartView(ctx, 3, 200, true)
        scopeChart.minRange = 0.05f
        scopeChart.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130)
        )
        content.addView(scopeChart)

        content.addView(gap(8))

        content.addView(
            label("Сила сигнала за последние 15 секунд", 12, false, 0xFF536477.toInt())
        )

        trendChart = ChartView(ctx, 1, 150, false)
        trendChart.minRange = 0.05f
        trendChart.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(100)
        )
        content.addView(trendChart)

        peakText = label("Пик: --", 13, true, 0xFF14263D.toInt())
        content.addView(peakText)

        content.addView(gap(10))
    }

    private fun addButtons(content: LinearLayout) {

        m1Button = button("Замер 1: прижми к стене на максимуме")
        content.addView(m1Button)

        m2Button = button("Замер 2: отодвинь от стены на 5 см")
        content.addView(m2Button)

        offsetButton = button("Отступ для замера 2: 5 см")
        content.addView(offsetButton)

        content.addView(gap(8))

        calibrateButton = button("Перекалибровать (держи неподвижно)")
        content.addView(calibrateButton)

        modeButton = button("Режим: ПРОВОД ПОД НАГРУЗКОЙ (50 Гц)")
        content.addView(modeButton)

        soundButton = button("Звук: включён")
        content.addView(soundButton)

        vibrationButton = button("Вибрация: выключена")
        content.addView(vibrationButton)

        content.addView(gap(14))
    }

    private fun addInfo(content: LinearLayout) {

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
    }

    private fun button(text: String): Button {
        val b = Button(ctx)
        b.text = text
        b.isAllCaps = false
        return b
    }

    private fun label(
        value: String,
        size: Int,
        bold: Boolean,
        color: Int
    ): TextView {
        val tv = TextView(ctx)
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
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(height))
        return v
    }

    private fun dp(value: Int): Int {
        return (value * ctx.resources.displayMetrics.density).toInt()
    }
}

// конец файла
