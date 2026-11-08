/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.kbd

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.osfans.trime.data.theme.kbd.LayoutEngine
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.orAbsent
import com.trime.editor.ui.R
import com.trime.editor.ui.dp
import com.trime.editor.ui.skin.UiTokens

/**
 * Draws the row structure of one layout, sized to the width it is given.
 *
 * Deliberately not a real `Keyboard`: the list pages through fifty layouts, and
 * building a keyboard for each would be wasted work. Rows come from
 * [LayoutEngine], which repeats the same split the IME performs, so the shape
 * on the card is the shape the user will get — a thumbnail drawn from a
 * different rule is a promise the editor cannot keep.
 */
class KeyboardThumbView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    /** Cap used when the parent does not constrain the height (a dialog). */
    var maxHeightPx: Int = 0

    var config: TextKeyboard? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    // 缩略图是纸上的一个小示意，不是一块屏幕：底色用规则线、键面用纸色。
    // 原来写死的是 Material 的浅灰白，在米白纸上会像贴了一张别的界面。
    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiTokens.color(context, R.attr.ui_sheet)
        }
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1).toFloat()
            color = UiTokens.color(context, R.attr.ui_rule_strong)
        }
    private val back =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiTokens.color(context, R.attr.ui_paper)
        }
    private val label =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiTokens.color(context, R.attr.ui_ink_2)
            textAlign = Paint.Align.CENTER
        }
    private val rect = RectF()
    private var rows: List<LayoutEngine.Row> = emptyList()
    private var rowHeight = 0f
    private val gap = context.dp(2).toFloat()
    private val padding = context.dp(6).toFloat()

    init {
        label.textSize = context.dp(9).toFloat()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cfg = config
        rows = if (cfg == null) emptyList() else LayoutEngine.rows(cfg, width)
        // Natural height keeps the proportion of a real keyboard: one
        // ten-weight key sets the scale. A card forces a height and a dialog
        // caps it — either way the rows are rescaled to fit, never clipped.
        val rowCount = rows.size.coerceAtLeast(1)
        val gaps = (rowCount - 1).coerceAtLeast(0) * gap
        val natural = padding * 2 + rowCount * ((width - padding * 2) / 10f * 0.95f) + gaps
        val available = MeasureSpec.getSize(heightMeasureSpec).toFloat()
        val height =
            when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY -> if (available > 0f) available else natural
                MeasureSpec.AT_MOST -> if (available > 0f) minOf(natural, available) else natural
                else -> if (maxHeightPx > 0) minOf(natural, maxHeightPx.toFloat()) else natural
            }
        rowHeight = ((height - padding * 2 - gaps) / rowCount).coerceAtLeast(context.dp(8).toFloat())
        setMeasuredDimension(width, height.toInt().coerceAtLeast(context.dp(32)))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, context.dp(8).toFloat(), context.dp(8).toFloat(), back)

        val cfg = config ?: return
        var y = padding
        rows.forEach { row ->
            val total = row.weightSum.takeIf { it > 0f } ?: 1f
            val available = w - padding * 2
            var x = padding
            row.indices.forEach { index ->
                val key = cfg.keys[index]
                val weight = key.weightOr(if (key.click.orAbsent != null) cfg.width else 0f)
                val keyWidth = available * (weight / total) - gap
                rect.set(x, y, x + keyWidth.coerceAtLeast(1f), y + rowHeight - gap)
                val radius = (rowHeight * 0.12f).coerceAtMost(context.dp(6).toFloat())
                canvas.drawRoundRect(rect, radius, radius, fill)
                canvas.drawRoundRect(rect, radius, radius, stroke)
                drawLabel(canvas, key, rect)
                x += keyWidth + gap
            }
            y += rowHeight
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        key: TextKeyboard.TextKey,
        box: RectF,
    ) {
        val text = labelOf(key) ?: return
        val metrics = label.fontMetrics
        val baseline = box.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, box.centerX(), baseline, label)
    }

    /** First character of the label, or a short stand-in for functional keys. */
    private fun labelOf(key: TextKeyboard.TextKey): String? {
        val raw = key.label.ifEmpty { tokenOf(key)?.let(::describeToken) ?: return null }
        return raw.take(1)
    }

    private fun tokenOf(key: TextKeyboard.TextKey): String? = when (val token = key.click) {
        is KeyActionToken.Plain -> token.token
        is KeyActionToken.Inline -> token.label ?: token.text
        null -> null
    }

    private fun describeToken(token: String): String = when (token) {
        "BackSpace" -> "⌫"
        "Return" -> "⏎"
        "Shift_L" -> "⇧"
        "space" -> "␣"
        else -> token
    }

    private fun TextKeyboard.TextKey.weightOr(fallback: Float): Float = if (width > 0f) width else fallback
}
