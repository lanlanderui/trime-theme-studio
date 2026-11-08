/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.preview

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout
import com.mikepenz.iconics.IconicsDrawable
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import com.trime.editor.ui.R
import com.trime.editor.ui.dp
import kotlin.math.roundToInt

/**
 * 一整块输入区：候选栏 + 键盘 + 底部内边距。
 *
 * 只画键盘会误导人。候选栏不在 [com.osfans.trime.ime.keyboard.Keyboard] 里，
 * `keyboard_padding_bottom` 那一条更是空的——两者都由输入法的 `InputView` 摆。
 * 调「键盘高度」时看不见它们在总高里占多少，就判断不了自己改出来的是不是想要的样子。
 *
 * 候选栏按主题真实画：底色 `candidate_background`、边框 `candidate_border_color`、
 * 圆角 `candidate_border_round`、边宽 `candidate_border`，与输入法的 `InputBarDelegate`
 * 用的是同一组数。工具条按 `tool_bar` 画：按钮位置与尺寸照 `primary_button` /
 * `buttons` / `button_spacing`，按钮里的内容照 `foreground`——`ic@` 用输入法同一套
 * `cmd_` 图标字体，其余按文字画（中英切换那个键写的就是「中」）。
 * 按钮本身只有背景色、没有边框，`ToolBar.Button.Background` 里也没有这一项。
 *
 * 底部那条带子是纯示意：淡填充 + 名字 + 高度，高度为 0 时只留一条虚线。
 */
class PreviewChrome(context: Context) : LinearLayout(context) {
    private val candidateBar = CandidateBar(context)

    private val bottomPad = Band(context, context.getString(R.string.preview_band_bottom))

    /** 上一次摆带子用的主题；切配色时要拿它重新解析颜色。 */
    private var applied: Theme? = null

    init {
        orientation = VERTICAL
        addView(candidateBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bottomPad, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** 把键盘放进两条带子之间；再调用只替换中间那个。 */
    fun wrap(board: View) {
        if (childCount == 3) removeViewAt(1)
        addView(board, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * N-5：只画键盘（设计稿 B3 的样子），或连候选栏 / 底部留白一起画。
     *
     * 只切两条带子的可见性，中间那块键盘的**宽度和绘制一个字节都不动**——
     * D2 像素闸门要求预览宽度与输入法一致，缩进或重排都会让它作废。
     * 默认完整（所见即所得），「仅键盘」是给想看稿子那种干净舞台的人用的视图。
     */
    fun setBandsVisible(visible: Boolean) {
        val state = if (visible) VISIBLE else GONE
        if (candidateBar.visibility != state) candidateBar.visibility = state
        if (bottomPad.visibility != state) bottomPad.visibility = state
    }

    val bandsVisible: Boolean get() = candidateBar.visibility == VISIBLE

    /**
     * 按 [theme] 摆两条带子。
     *
     * 候选栏高度取 `candidate_view_height + comment_height`：输入法的
     * `InputBarDelegate` 就是这两个数相加当整条的高度。
     */
    fun applyStyle(theme: Theme) {
        applied = theme
        styleBands(theme)
    }

    /**
     * 重新取色，不动尺寸。
     *
     * 颜色是在 [applyStyle] 里解析好存下来的，切配色时只重画键盘板不会带上它——
     * 候选栏会一直是切之前那个颜色。输入法的 `InputBar.refreshColors()` 做的也是这件事。
     */
    fun refreshColors() {
        // 两条带子都要刷。只刷候选栏的话，切配色后「键盘下方那条留白」会停在
        // 上一套方案的颜色上，直到离开这一页再回来（它只在 applyStyle 里取色）。
        applied?.let(::styleBands)
    }

    /** 两条带子的尺寸与颜色都在这里取，改一处就够。 */
    private fun styleBands(theme: Theme) {
        candidateBar.show(theme)
        bottomPad.show(
            context.dp(theme.style.keyboardPaddingBottom),
            theme.style.keyboardPaddingBottom,
            // 这块留白在输入法里就是键盘背景的一部分，不是「编辑器画的一条灰带」。
            runCatching { ColorManager.getColor("keyboard_back_color") }.getOrNull(),
        )
    }

    /**
     * 候选栏：真实底色 + 边框 + 工具条 + 几组示例候选。
     *
     * 示例是固定的四个字，各带一条编码：作用是把 `candidate_text_color`、
     * `hilited_candidate_text_color`、`hilited_candidate_back_color`、
     * `comment_text_color` 摆到真实底色上看——只看色块判断不出对比度，得看字。
     * 第一个画成高亮态，其余是常态。
     */
    private class CandidateBar(context: Context) : View(context) {
        private class Sample(
            val text: String,
            val comment: String,
        )

        private val samples =
            listOf(
                Sample("同", "〔门口 kk〕"),
                Sample("通", "〔乚用 zy〕"),
                Sample("统", "〔纟 允 sy〕"),
                Sample("痛", "〔广用 by〕"),
            )

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private val line =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = context.dp(1).toFloat()
            }

        private val word = Paint(Paint.ANTI_ALIAS_FLAG)
        private val comment = Paint(Paint.ANTI_ALIAS_FLAG)

        private val content =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
            }

        /** 图标按「样式 + 字号」缓存：每次重画都新建一个 drawable 太浪费。 */
        private val icons = mutableMapOf<String, IconicsDrawable?>()

        private val rect = RectF()
        private val box = RectF()

        private var heightPx = 0
        private var background = 0xFFF2F2F2.toInt()
        private var borderColor = 0
        private var glyphColor = 0xFF333333.toInt()
        private var wordColor = 0xFF333333.toInt()
        private var commentColor = 0xFF888888.toInt()
        private var hilitedWord = 0
        private var hilitedComment = 0
        private var hilitedBack = 0
        private var borderWidth = 0f
        private var radius = 0f
        private var itemRadius = 0f
        private var itemPadding = 0f
        private var itemSpacing = 0f

        private var primary: ToolBar.Button? = null
        private var buttons: List<ToolBar.Button> = emptyList()
        private var spacing = 0
        private var buttonSize = 0

        fun show(theme: Theme) {
            val style = theme.style
            val bar = style.candidateViewHeight + style.commentHeight
            heightPx = context.dp(bar)
            contentDescription = context.getString(R.string.preview_band_candidate) + " " + bar
            background = color("candidate_background", 0xFFF2F2F2.toInt())
            borderColor = color("candidate_border_color", 0x22000000)
            glyphColor = color("candidate_text_color", 0xFF333333.toInt())
            wordColor = glyphColor
            commentColor = color("comment_text_color", 0xFF888888.toInt())
            hilitedWord = color("hilited_candidate_text_color", wordColor)
            hilitedComment = color("hilited_comment_text_color", hilitedWord)
            hilitedBack = color("hilited_candidate_back_color", 0)
            borderWidth = context.dp(style.candidateBorder).toFloat()
            radius = context.dp(style.candidateBorderRound.roundToInt()).toFloat()
            itemRadius = context.dp(style.candidateCornerRadius.roundToInt()).toFloat()
            itemPadding = context.dp(style.candidatePadding).toFloat()
            itemSpacing = style.candidateSpacing * resources.displayMetrics.density
            word.textSize = style.candidateTextSize * resources.displayMetrics.scaledDensity
            comment.textSize = style.commentTextSize * resources.displayMetrics.scaledDensity

            primary = theme.toolBar.primaryButton
            buttons = theme.toolBar.buttons
            spacing = context.dp(theme.toolBar.buttonSpacing)
            buttonSize = context.dp(bar)
            requestLayout()
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx.coerceAtLeast(context.dp(2)))
        }

        override fun onDraw(canvas: Canvas) {
            val inset = borderWidth / 2f
            rect.set(inset, inset, width - inset, height - inset)
            fill.color = background
            canvas.drawRoundRect(rect, radius, radius, fill)
            if (borderWidth > 0f) {
                line.strokeWidth = borderWidth
                line.color = borderColor
                canvas.drawRoundRect(rect, radius, radius, line)
            }

            // 右侧按钮从右往左排（输入法的 ButtonsBarUi 就是 ROW_REVERSE）。
            var right = width - spacing
            buttons.forEach { config ->
                val size = sizeOf(config)
                drawChip(canvas, (right - size).toFloat(), size, config)
                right -= size + spacing
            }
            val primaryButton = primary
            val primarySize = primaryButton?.let { sizeOf(it) }
            if (primaryButton != null && primarySize != null) {
                drawChip(canvas, spacing.toFloat(), primarySize, primaryButton)
            }

            drawSamples(canvas, (spacing + (primarySize?.plus(spacing) ?: 0)).toFloat(), (right + spacing).toFloat())
        }

        /** 候选词和它的编码；第一个是高亮态，画到放不下为止。 */
        private fun drawSamples(
            canvas: Canvas,
            leftEdge: Float,
            rightEdge: Float,
        ) {
            var x = leftEdge + itemPadding
            samples.forEachIndexed { index, sample ->
                val wordWidth = word.measureText(sample.text)
                val itemWidth = wordWidth + comment.measureText(sample.comment) + itemPadding * 2
                if (x + itemWidth > rightEdge) return
                if (index == 0 && Color.alpha(hilitedBack) > 0) {
                    box.set(x, 0f, x + itemWidth, height.toFloat())
                    fill.color = hilitedBack
                    canvas.drawRoundRect(box, itemRadius, itemRadius, fill)
                }
                word.color = if (index == 0) hilitedWord else wordColor
                comment.color = if (index == 0) hilitedComment else commentColor
                canvas.drawText(sample.text, x + itemPadding, baselineOf(word), word)
                canvas.drawText(sample.comment, x + itemPadding + wordWidth, baselineOf(comment), comment)
                x += itemWidth + itemSpacing
            }
        }

        private fun baselineOf(paint: Paint): Float = height / 2f - (paint.descent() + paint.ascent()) / 2f

        private fun drawChip(
            canvas: Canvas,
            left: Float,
            size: Int,
            config: ToolBar.Button,
        ) {
            val top = (height - size) / 2f
            box.set(left, top, left + size, top + size)
            val circle = config.background.type == ToolBar.Button.Background.Type.CIRCLE
            // 只有背景色，没有边框：`ToolBar.Button.Background` 里就没有边宽这一项，
            // 输入法也不描边。背景是 0x00 时按钮只剩图标本身，那正是主题的意思。
            val chip = config.background.normal.takeIf { it.isNotEmpty() }?.let { color(it, Color.TRANSPARENT) }
            if (chip != null && Color.alpha(chip) > 0) {
                fill.color = chip
                if (circle) canvas.drawCircle(box.centerX(), box.centerY(), size / 2f, fill) else canvas.drawRoundRect(box, cornerOf(config), cornerOf(config), fill)
            }
            drawContent(canvas, box, size, config)
        }

        /**
         * 按钮里的东西。
         *
         * `ic@` 是图标，用的就是输入法那套 `community-material-typeface` 的 `cmd_`
         * 前缀，所以预览和真机画的是同一个字形；其余按文字画（中英切换那个键写的就是
         * 「中」）。`*.png` 这类主题自带图片读不到，只留边框——这是预览，不是渲染器。
         */
        private fun drawContent(
            canvas: Canvas,
            box: RectF,
            size: Int,
            config: ToolBar.Button,
        ) {
            val foreground = config.foreground
            // 有 option_styles 的按钮按状态二选一；预览没有状态，取第一个（未开启）。
            val style = if (foreground.optionStyles.size == 2) foreground.optionStyles[0] else foreground.style
            val tint =
                foreground.normal.takeIf { it.isNotEmpty() }?.let { color(it, glyphColor) } ?: glyphColor
            when {
                style.startsWith("ic@") -> {
                    val icon = icon(style, foreground.fontSize) ?: return
                    val w = icon.intrinsicWidth.takeIf { it > 0 } ?: size / 2
                    val h = icon.intrinsicHeight.takeIf { it > 0 } ?: size / 2
                    icon.colorList = ColorStateList.valueOf(tint)
                    icon.setBounds(
                        (box.centerX() - w / 2f).roundToInt(),
                        (box.centerY() - h / 2f).roundToInt(),
                        (box.centerX() + w / 2f).roundToInt(),
                        (box.centerY() + h / 2f).roundToInt(),
                    )
                    icon.draw(canvas)
                }

                style.isNotEmpty() && !style.contains('.') -> {
                    content.color = tint
                    content.textSize = foreground.fontSize * resources.displayMetrics.scaledDensity
                    canvas.drawText(style, box.centerX(), box.centerY() - (content.descent() + content.ascent()) / 2f, content)
                }
            }
        }

        private fun icon(
            style: String,
            fontSize: Float,
        ): IconicsDrawable? = icons.getOrPut("$style@$fontSize") {
            runCatching {
                val px = context.dp(fontSize.roundToInt())
                IconicsDrawable(context, "cmd_" + style.substring(3)).apply {
                    sizeXPx = px
                    sizeYPx = px
                }
            }.getOrNull()
        }

        private fun cornerOf(config: ToolBar.Button): Float = context.dp(config.background.cornerRadius.toInt()).toFloat()

        /** 与 `ButtonsBarUi.getButtonSize` 同规则：写了 `size` 就用写的，否则正方形。 */
        private fun sizeOf(config: ToolBar.Button): Int {
            val declared = config.size.takeIf { it.size == 2 } ?: return buttonSize
            return context.dp(declared[0].coerceAtLeast(0))
        }

        private fun color(
            key: String,
            fallback: Int,
        ): Int = runCatching { ColorManager.getColor(key) }.getOrDefault(fallback)
    }

    /**
     * 底部内边距的示意带子。
     *
     * 高度为 0 时仍留 [line] 高的一条虚线：主题没写 `keyboard_padding_bottom` 时
     * 「这里没有东西」本身就是要看见的信息。
     */
    private class Band(
        context: Context,
        private val title: String,
    ) : View(context) {
        private val fill = Paint().apply { color = 0x14808080 }

        private val rule =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = context.dp(1).toFloat()
                color = 0x40808080
                pathEffect = DashPathEffect(floatArrayOf(context.dp(4).toFloat(), context.dp(4).toFloat()), 0f)
            }

        private val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = context.dp(9).toFloat()
                color = 0xFF6B6B6B.toInt()
            }

        private val line = context.dp(2)
        private val labelMin = context.dp(16)
        private val inset = context.dp(6).toFloat()

        private var heightPx = 0
        private var valueDp = 0

        fun show(
            px: Int,
            dp: Int,
            background: Int? = null,
        ) {
            heightPx = px
            valueDp = dp
            // 有键盘底色就用它填：这块留白在输入法里露出来的就是键盘背景，
            // 画成灰带会让人以为「这里不属于键盘」。虚线框仍留着，说明它是留白不是键。
            if (background != null) {
                fill.color = background
                // 亮度用整数近似就够：这里只要判断「深底配浅字」还是反过来。
                val luminance = (299 * Color.red(background) + 587 * Color.green(background) + 114 * Color.blue(background)) / 1000
                val dark = luminance < 128
                label.color = if (dark) 0xFFEDEDED.toInt() else 0xFF6B6B6B.toInt()
                rule.color = if (dark) 0x50FFFFFF else 0x40808080
            } else {
                // 取不到键盘底色就回到中性灰，不要留着上一套方案的颜色装成这一套。
                fill.color = 0x14808080
                label.color = 0xFF6B6B6B.toInt()
                rule.color = 0x40808080
            }
            requestLayout()
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx.coerceAtLeast(line))
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            canvas.drawLine(0f, height - 1f, width.toFloat(), height - 1f, rule)
            if (height >= labelMin) {
                val baseline = height / 2f - (label.descent() + label.ascent()) / 2f
                canvas.drawText("$title $valueDp", inset, baseline, label)
            }
        }
    }
}
