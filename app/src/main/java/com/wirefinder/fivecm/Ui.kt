package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

class Ui(private val ctx: Context) {

    lateinit var depthText: TextView
    lateinit var depthHint: TextView
    lateinit var stateText: TextView
    lateinit var axisText: TextView
    lateinit var liveText: TextView
    lateinit var sensorInfoText: TextView
    lateinit var levelBar: ProgressBar
    lateinit var scanView: ScanView

    lateinit var startButton: Button
    lateinit var clearButton: Button
    lateinit var widthButton: Button
    lateinit var rowButton: Button
    lateinit var calibrateButton: Button
    lateinit var modeButton: Button
    lateinit var soundButton: Button
    lateinit var testButton: Button

    fun build(): View {

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(18), dp(18), dp(18))
        root.setBackgroundColor(0xFFF3F6FA.toInt())

        val scroll = ScrollView(ctx)

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL

        addScanner(content)
        addButtons(content)
        addInfo(content)

        scroll.addView(content)
        root.addView(scroll)

        return root
    }

    private fun addScanner(content: LinearLayout) {

        content.addView(label("WIREFINDER 5CM", 24, true, 0xFF14263D.toInt()))

        content.addView(label("Глубина провода (оценка)", 14, true, 0xFF536477.toInt()))

        depthText = label("—", 34, true, 0xFF147D64.toInt())
        content.addView(depthText)

        stateText = label("Ожидание датчика...", 15, true, 0xFF14263D.toInt())
        content.addView(stateText)

        content.addView(gap(6))

        scanView = ScanView(ctx)
        scanView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(260)
        )
        content.addView(scanView)

        axisText = label("", 12, false, 0xFF536477.toInt())
        content.addView(axisText)

        levelBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        levelBar.max = 100
        levelBar.progress = 0
        levelBar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(14)
        )
        content.addView(levelBar)

        liveText = label("", 13, true, 0xFF14263D.toInt())
        content.addView(liveText)

        depthHint = label("", 13, false, 0xFF536477.toInt())
        content.addView(depthHint)

        content.addView(gap(8))
    }

    private fun addButtons(content: LinearLayout) {

        startButton = button("Начать проход")
        content.addView(startButton)

        clearButton = button("Очистить карту")
        content.addView(clearButton)

        widthButton = button("Ширина прохода: 50 см")
        content.addView(widthButton)

        rowButton = button("Шаг между проходами: 10 см")
        content.addView(rowButton)

        content.addView(gap(8))

        calibrateButton = button("Перекалибровать (держи неподвижно)")
        content.addView(calibrateButton)

        modeButton = button("Режим: ПРОВОД ПОД НАГРУЗКОЙ (50 Гц)")
        content.addView(modeButton)

        soundButton = button("Звук: включён")
        content.addView(soundButton)

        testButton = button("Тест звука")
        content.addView(testButton)

        content.addView(gap(14))
    }

    private fun addInfo(content: LinearLayout) {

        content.addView(label("Как сканировать", 17, true, 0xFF14263D.toInt()))

        content.addView(
            label(
                "1. Включи на линии мощную нагрузку: чайник, обогреватель, фен.\n" +
                "2. Подожди калибровку, держа телефон неподвижно вдали от металла.\n" +
                "3. Нажми «Начать проход», прижми телефон к стене в начале отрезка.\n" +
                "4. После 3 писков веди телефон ровно, не вращая, на всю ширину отрезка за 6 секунд.\n" +
                "5. Следующий проход делай выше на выбранный шаг. Лучше 3–6 проходов.\n" +
                "6. Яркая линия на карте показывает, где сигнал сильнее. Белый круг: максимум.\n" +
                "7. Если звука нет, увеличь громкость мультимедиа и нажми «Тест звука».",
                13, false, 0xFF536477.toInt()
            )
        )

        content.addView(gap(14))

        content.addView(label("Информация о датчике", 17, true, 0xFF14263D.toInt()))
        sensorInfoText = label("Проверка...", 13, false, 0xFF536477.toInt())
        content.addView(sensorInfoText)

        content.addView(gap(14))

        content.addView(label("ВАЖНО О БЕЗОПАСНОСТИ", 16, true, 0xFF9A3412.toInt()))

        content.addView(
            label(
                "Это экспериментальный магнитометр, а не детектор скрытой проводки. Карта показывает силу магнитного сигнала, а не сам провод. Глубина оценивается грубо, погрешность 50% и больше. В двухжильном кабеле токи идут навстречу друг другу, и поле почти гасится, поэтому провод может быть не виден. Арматура, трубы, магниты и динамики дают похожие сигналы. Не сверли стену по показаниям приложения. Для проверки используй профессиональный детектор.",
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
