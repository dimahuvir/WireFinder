package com.wirefinder.fivecm

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class Ui(private val ctx: Context) {

    lateinit var cardTitle: TextView
    lateinit var cardSub: TextView
    lateinit var wireLabel: TextView
    lateinit var metalLabel: TextView
    lateinit var hintText: TextView
    lateinit var infoText: TextView
    lateinit var sensorInfoText: TextView
    lateinit var wireBar: LedBar
    lateinit var metalBar: LedBar

    lateinit var zeroButton: Button
    lateinit var sensButton: Button
    lateinit var soundButton: Button
    lateinit var testButton: Button

    private lateinit var cardBg: GradientDrawable

    fun setCard(title: String, sub: String, color: Int) {
        cardTitle.text = title
        cardSub.text = sub
        cardBg.setColor(color)
    }

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

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(22), dp(16), dp(22))
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        cardBg = GradientDrawable()
        cardBg.cornerRadius = dp(18).toFloat()
        cardBg.setColor(0xFF64748B.toInt())
        card.background = cardBg

        cardTitle = label("ПОДГОТОВКА", 28, true, 0xFFFFFFFF.toInt())
        cardTitle.gravity = Gravity.CENTER
        card.addView(cardTitle)

        cardSub = label("", 14, false, 0xFFFFFFFF.toInt())
        cardSub.gravity = Gravity.CENTER
        card.addView(cardSub)

        content.addView(card)
        content.addView(gap(12))

        wireLabel = label("ПРОВОД 50 Гц", 14, true, 0xFF14263D.toInt())
        content.addView(wireLabel)

        wireBar = LedBar(ctx)
        wireBar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(34)
        )
        content.addView(wireBar)

        content.addView(gap(8))

        metalLabel = label("МЕТАЛЛ", 14, true, 0xFF14263D.toInt())
        content.addView(metalLabel)

        metalBar = LedBar(ctx)
        metalBar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(34)
        )
        content.addView(metalBar)

        content.addView(gap(10))

        hintText = label("", 18, true, 0xFF14263D.toInt())
        hintText.gravity = Gravity.CENTER
        content.addView(hintText)

        infoText = label("", 12, false, 0xFF536477.toInt())
        content.addView(infoText)

        content.addView(gap(8))
    }

    private fun addButtons(content: LinearLayout) {

        zeroButton = button("Обнулить (на чистом месте)")
        content.addView(zeroButton)

        sensButton = button("Чувствительность: обычная")
        content.addView(sensButton)

        soundButton = button("Звук: включён")
        content.addView(soundButton)

        testButton = button("Тест звука")
        content.addView(testButton)

        content.addView(gap(14))
    }

    private fun addInfo(content: LinearLayout) {

        content.addView(label("Как пользоваться", 17, true, 0xFF14263D.toInt()))

        content.addView(
            label(
                "1. Для поиска провода включи на этой линии мощную нагрузку: чайник, обогреватель, фен.\n" +
                "2. Дай прибору 2 секунды подготовки, держа телефон неподвижно вдали от металла.\n" +
                "3. Прижми телефон к стене и веди медленно, не вращая. Датчик обычно в верхней части телефона.\n" +
                "4. Следи за плашкой и шкалами. Когда сигнал растёт, ты приближаешься, а когда выше всего, ты над источником.\n" +
                "5. Писк раздаётся раз в секунду: динамик мешает измерению, поэтому чаще нельзя.\n" +
                "6. Если провод не ловится, выбери «Чувствительность: максимальная» и держи телефон вплотную.",
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
                "Это экспериментальный магнитометр, а не детектор скрытой проводки. Расстояние до провода он не измеряет, только показывает, ближе ты или дальше. В двухжильном кабеле токи идут навстречу друг другу, и поле почти гасится, поэтому провод может быть не виден. Арматура, трубы, магниты и динамики дают похожие сигналы. Не вскрывай провода и не сверли стену по показаниям приложения. Для проверки используй профессиональный детектор.",
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

        tv.setPadding(0, dp(4), 0, dp(4))
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
