package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class Ui(private val ctx: Context) {

    lateinit var lampWait: LampView
    lateinit var lampClean: LampView
    lateinit var lampMetal: LampView
    lateinit var lampWire: LampView
    lateinit var lampOver: LampView

    lateinit var wireLabel: TextView
    lateinit var metalLabel: TextView
    lateinit var hintText: TextView
    lateinit var infoText: TextView
    lateinit var sensorInfoText: TextView
    lateinit var wireBar: LedBar
    lateinit var metalBar: LedBar

    lateinit var zeroButton: Button
    lateinit var sensButton: Button
    lateinit var acButton: Button
    lateinit var soundButton: Button
    lateinit var testButton: Button

    fun build(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        root.setBackgroundColor(0xFFF3F6FA.toInt())

        val scroll = ScrollView(ctx)
        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL

        addMain(content)
        addButtons(content)
        addInfo(content)

        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun addMain(content: LinearLayout) {
        content.addView(label("WIREFINDER 5CM", 18, true, 0xFF14263D.toInt()))

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92))

        lampWait = LampView(ctx, "ЖДИ", 0xFF3B82F6.toInt())
        lampClean = LampView(ctx, "ЧИСТО", 0xFF2FA84F.toInt())
        lampMetal = LampView(ctx, "МЕТАЛЛ", 0xFFFF8C00.toInt())
        lampWire = LampView(ctx, "ПРОВОД", 0xFFE02424.toInt())
        lampOver = LampView(ctx, "НАД ЦЕЛЬЮ", 0xFFF2C200.toInt())

        for (lamp in listOf(lampWait, lampClean, lampMetal, lampWire, lampOver)) {
            lamp.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_MATCH_PARENT, 1f)
            row.addView(lamp)
        }
        content.addView(row)
        content.addView(gap(10))

        hintText = label("", 18, true, 0xFF14263D.toInt())
        hintText.gravity = Gravity.CENTER
        content.addView(hintText)
        content.addView(gap(10))

        wireLabel = label("ПРОВОД 50 Гц", 14, true, 0xFF14263D.toInt())
        content.addView(wireLabel)
        wireBar = LedBar(ctx)
        wireBar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))
        content.addView(wireBar)

        content.addView(gap(8))
        metalLabel = label("МЕТАЛЛ", 14, true, 0xFF14263D.toInt())
        content.addView(metalLabel)
        metalBar = LedBar(ctx)
        metalBar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))
        content.addView(metalBar)

        infoText = label("", 12, false, 0xFF536477.toInt())
        content.addView(infoText)
        content.addView(gap(8))
    }

    private fun addButtons(content: LinearLayout) {
        zeroButton = button("Обнулить и откалибровать")
        sensButton = button("Чувствительность")
        acButton = button("Сеть: 50 Гц")
        soundButton = button("Звук: включён")
        testButton = button("Тест звука")

        for (b in listOf(zeroButton, sensButton, acButton, soundButton, testButton)) {
            content.addView(b)
        }
        content.addView(gap(14))
    }

    private fun addInfo(content: LinearLayout) {
        content.addView(label("Как пользоваться", 17, true, 0xFF14263D.toInt()))
        content.addView(label(
            "1. Сначала нажми «Обнулить» вдали от металла и держи телефон неподвижно 3 секунды.\n" +
            "2. Для проверки провода включи мощную нагрузку: чайник или обогреватель. Выбери 50 Гц или 60 Гц.\n" +
            "3. Веди телефон плавно вдоль стены, не вращая его. У датчика обычно верхняя часть корпуса.\n" +
            "4. ЛАМПА МЕТАЛЛ реагирует на локальное отклонение магнитного поля. Это не означает определённый металл.\n" +
            "5. Алюминий, медь и глубина объекта в сантиметрах встроенным магнитометром надёжно не определяются.",
            13, false, 0xFF536477.toInt()
        ))

        content.addView(gap(14))
        content.addView(label("Информация о датчике", 17, true, 0xFF14263D.toInt()))
        sensorInfoText = label("Проверка...", 13, false, 0xFF536477.toInt())
        content.addView(sensorInfoText)

        content.addView(gap(14))
        content.addView(label("ВАЖНО О БЕЗОПАСНОСТИ", 16, true, 0xFF9A3412.toInt()))
        content.addView(label(
            "Это экспериментальный анализ встроенного магнитометра, а не профессиональный детектор скрытой проводки. Перед сверлением используй сертифицированный прибор.",
            13, false, 0xFF8A3B24.toInt()
        ))
    }

    private fun button(text: String): Button {
        val b = Button(ctx)
        b.text = text
        b.isAllCaps = false
        return b
    }

    private fun label(value: String, size: Int, bold: Boolean, color: Int): TextView {
        val tv = TextView(ctx)
        tv.text = value
        tv.textSize = size.toFloat()
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dp(4), 0, dp(4))
        return tv
    }

    private fun gap(height: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(height))
        return v
    }

    private fun dp(value: Int): Int = (value * ctx.resources.displayMetrics.density).toInt()
}
