/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.trime.editor.ui.skin.UiTokens
import com.trime.editor.ui.R as UiR

/**
 * A row of colour pills: what a theme looks like before it is drawn in full.
 *
 * Not a shrunken keyboard. `ColorManager` holds one theme at a time, so a list
 * of live boards would either re-attach the theme on every row — turning a
 * scroll into a reload — or paint every row with the last theme loaded. Colour
 * pills read straight from the scheme map, so each row shows its own theme.
 */
class SwatchStripView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            // 一个 token 就够：亮色下是深边、暗色下 value-night 换成浅边。
            color = UiTokens.color(context, UiR.attr.ui_pill_stroke)
        }
    private val rect = RectF()

    @ColorInt
    private var colors: List<Int> = emptyList()

    /**
     * 圆角（px）。0 表示胶囊形（高度一半）——列表里用胶囊，英雄区用 10dp 的方块，
     * 这是设计稿 B1 与旧卡片两种用法的唯一差别。
     */
    var cornerRadiusPx: Float = 0f

    fun setColors(@ColorInt value: List<Int>) {
        colors = value
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // 列表里是一根细带（10dp），英雄区是 34dp 的色块排：高度由布局决定，
        // 没给（UNSPECIFIED）才退回默认值。
        val given = MeasureSpec.getSize(heightMeasureSpec)
        val height =
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) given else dp(HEIGHT_DP)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (colors.isEmpty() || width <= 0) return
        val total = width.toFloat()
        // 色多的时候（一个方案四十多个色）3dp 的缝会把整条吃掉，所以缝随密度收缩，
        // 窄到画不下就直接不留缝——宁可连成一条，也不要一堆看不见的碎片。
        val rawSlot = total / colors.size
        val gap = if (rawSlot > dp(GAP_DP) * 4) dp(GAP_DP).toFloat() else 0f
        val slot = (total - gap * (colors.size - 1)) / colors.size
        val radius = if (cornerRadiusPx > 0f) cornerRadiusPx else height / 2f
        colors.forEachIndexed { index, color ->
            val left = index * (slot + gap)
            rect.set(left, 0f, left + slot, height.toFloat())
            paint.color = color
            canvas.drawRoundRect(rect, radius, radius, paint)
            // 描边让深色键在炭纸上也有边界；颜色本身随主题翻转，所以只取一个 token。
            if (slot > 3f) canvas.drawRoundRect(rect, radius, radius, edge)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val HEIGHT_DP = 10
        const val GAP_DP = 3
    }
}
