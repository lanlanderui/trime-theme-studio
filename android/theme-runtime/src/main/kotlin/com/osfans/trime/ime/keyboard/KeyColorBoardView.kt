/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.enterLabelOf
import com.osfans.trime.data.theme.resolveMainKeyboardConfig
import splitties.dimensions.dp

/**
 * A read-only rendering of a real [Keyboard].
 *
 * This is not a mock: the layout comes from the same `Keyboard` class the IME
 * instantiates and the colours come from each [Key]'s own getters, so a theme
 * that reflows its keys or drives a colour through a custom key looks here
 * exactly as it will on screen.
 *
 * It lives beside [Keyboard] and [KeyFaceRenderer] rather than in a UI module
 * for one reason: the in-app editing screens and the standalone editor both
 * preview themes, and two copies of this class would be two answers to "what
 * will the keyboard look like". Subclasses add interaction (selection, drag
 * handles) but never their own drawing.
 *
 * The height is the keyboard's own height — deliberately not scaled. Shrinking
 * it would make the key shapes and the gaps between them a different size from
 * the ones the user is adjusting.
 */
open class KeyColorBoardView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

    private val symbolPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

    /** The rect of the key being drawn, reused so a draw allocates nothing. */
    private val faceFrame = RectF()

    /** Reused in the same way; refilled from the three sizes below on each draw. */
    private val faceStyle = KeyFaceRenderer.Style()

    /** The layout to draw. Setting it re-measures, because its height is the keyboard's. */
    var keyboard: Keyboard? = null
        set(value) {
            field = value
            // 高度直接写进 layoutParams，不只靠 requestLayout()：
            // 在布局过程中发出的 requestLayout 会被丢掉（父容器正在算），
            // 视图就留着兜底高度，最底下一排被裁掉——画出来只剩半排，点也点不中。
            // 写进 layoutParams 后，下一帧一定会按这个高度测量。
            val wanted = value?.height ?: 0
            val params = layoutParams
            if (wanted > 0 && params != null && params.height != wanted) {
                params.height = wanted
                layoutParams = params
            }
            requestLayout()
            invalidate()
        }

    /** The label and symbol sizes the theme draws keys with, in sp. */
    var keyTextSize = 0f

    /** Size for labels of more than one character; the theme draws those smaller. */
    var keyLongTextSize = 0f
    var symbolTextSize = 0f

    /**
     * What to draw for a key whose theme label is the `enter_labels` placeholder.
     *
     * The real keyboard resolves that placeholder against the editor's action
     * (go / done / send …), which a preview cannot know. The theme's own
     * `enter_labels.default` is the label the keyboard shows in a plain text
     * field, so that is what the preview uses; a fallback glyph covers themes
     * that do not declare one.
     */
    var enterLabel: String = RETURN_GLYPH

    /**
     * Draws the main keyboard of [theme] at [width].
     *
     * Returns false when the theme declares no keyboard with any key in it, so
     * a caller can say so instead of showing an empty band.
     */
    fun showTheme(
        theme: Theme,
        width: Int,
    ): Boolean {
        val config = resolveMainKeyboardConfig(theme) ?: return false
        if (width <= 0) return false
        applyThemeMetrics(theme)
        keyboard = Keyboard(context, theme, width, config)
        return true
    }

    /**
     * Takes the label sizes and the Enter label from [theme].
     *
     * Split out of [showTheme] because the layout editor builds its own
     * [Keyboard] from a draft and still has to draw text the way the theme
     * asks for.
     */
    fun applyThemeMetrics(theme: Theme) {
        keyTextSize = theme.style.keyTextSize
        keyLongTextSize = theme.style.keyLongTextSize
        symbolTextSize = theme.style.symbolTextSize
        enterLabel = enterLabelOf(theme)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // The keyboard's own height, not a guess: a scaled-down copy would
        // show different key shapes from the ones being adjusted.
        val wanted = keyboard?.height?.takeIf { it > 0 } ?: context.dp(DEFAULT_HEIGHT_DP)
        setMeasuredDimension(width, wanted)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kb = keyboard ?: return
        if (width <= 0 || height <= 0) return

        // Painted whole first: it is the area a tap cannot reach, so seeing
        // it exactly as it sits behind the keys is the only way to judge it.
        fill.color =
            runCatching { ColorManager.getColor("keyboard_back_color") }
                .getOrDefault(Color.TRANSPARENT)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        kb.keys.forEach { key ->
            drawKey(canvas, kb, key)
        }
    }

    private fun drawKey(
        canvas: Canvas,
        kb: Keyboard,
        key: Key,
    ) {
        val left = key.x + kb.horizontalGap / 2f + key.extraWidthLeft
        val top = key.y + kb.verticalGap / 2f
        val right = key.x + key.width - kb.horizontalGap / 2f - key.extraWidthRight
        val bottom = key.y + key.height - kb.verticalGap / 2f
        if (right <= left || bottom <= top) return

        val background = runCatching { key.getBackgroundDrawable() }.getOrNull()
        if (background != null) {
            // Same treatment KeyView gives it: the drawable carries only the
            // colour, the shape and outline come from the key's geometry.
            if (background is GradientDrawable) {
                if (key.roundCorner > 0f) background.cornerRadius = context.dp(key.roundCorner.toInt()).toFloat()
                if (key.keyBorder > 0) {
                    background.setStroke(context.dp(key.keyBorder), key.getBorderColor())
                }
            }
            background.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            background.draw(canvas)
        } else {
            // A key whose background resolved to nothing is still a key:
            // outline it so the shape stays visible.
            line.color = runCatching { key.getBorderColor() }.getOrDefault(Color.GRAY)
            line.strokeWidth = context.dp(1).toFloat()
            canvas.drawRect(left, top, right, bottom, line)
        }

        // Everything the key says comes from the same renderer the keyboard on
        // screen uses, so a size, a colour rule or an offset cannot drift apart
        // between the two again.
        faceStyle.keyTextSize = keyTextSize
        faceStyle.keyLongTextSize = keyLongTextSize
        faceStyle.symbolTextSize = symbolTextSize
        faceStyle.spScale = resources.displayMetrics.scaledDensity
        // The board draws a whole keyboard into a page that may squeeze a row
        // down to half height, so text is capped at the band it has to live in.
        // The keyboard itself renders at the height it asked for and is not.
        faceStyle.labelCap = LABEL_CAP
        faceStyle.symbolCap = SYMBOL_CAP
        faceFrame.set(left, top, right, bottom)
        // A theme with a broken colour reference must not take the whole board
        // down; the outline above already showed where the key is.
        runCatching {
            KeyFaceRenderer.draw(canvas, key, labelOf(key), faceFrame, faceStyle, labelPaint, symbolPaint)
        }
    }

    /**
     * The label a key shows, read without asking rime.
     *
     * [Key.getLabel] consults rime state to pick between the Chinese and
     * ASCII labels, but a preview can open with the IME never having run. The
     * theme's own label is what the layout declares.
     */
    private fun labelOf(key: Key): String {
        val own = key.themeLabel
        if (own.isNotEmpty()) {
            return if (own == ENTER_LABELS) enterLabel else own
        }
        val layout = keyboard ?: return ""
        val fallback = runCatching { key.click?.getLabel(layout) }.getOrNull().orEmpty()
        return if (fallback == ENTER_LABELS) enterLabel else fallback
    }

    private companion object {
        const val DEFAULT_HEIGHT_DP = 220
        const val RETURN_GLYPH = "\u23CE"
        const val ENTER_LABELS = "enter_labels"

        /** Text caps for the board only; the keyboard renders at full size. */
        const val LABEL_CAP = 0.9f
        const val SYMBOL_CAP = 0.28f
    }
}
