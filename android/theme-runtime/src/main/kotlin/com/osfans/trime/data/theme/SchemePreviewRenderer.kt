/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.ColorUtils

/**
 * Draws a small keyboard thumbnail for one color scheme.
 *
 * The row structure comes from the theme's own `qwerty` preset keyboard, so the
 * preview reflects the layout the user will actually get when they switch
 * schemes instead of a hard-coded key grid. Only solid colors are drawn: a key
 * backed by an image falls back to its solid fallback chain.
 */
object SchemePreviewRenderer {
    /** Fallback row sizes when the theme declares no `qwerty` keyboard. */
    private val DEFAULT_ROWS = listOf(10, 9, 9, 7, 3)

    private const val KEYBOARD_ID = "qwerty"

    fun render(
        theme: Theme,
        scheme: ColorScheme,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val table =
            ColorTable.resolve(scheme, theme.fallbackColors) {
                runCatching { ColorUtils.parseColor(it) }.getOrNull()
            }
        fun color(
            key: ColorKey,
            fallback: Int,
        ): Int = (table[key] as? ColorTable.Value.Color)?.argb ?: fallback

        val background = color(ColorKey.KEYBOARD_BACK_COLOR, 0xFFECEFF1.toInt())
        val candidate = color(ColorKey.CANDIDATE_BACKGROUND, background)
        val keyBack = color(ColorKey.KEY_BACK_COLOR, 0xFFFFFFFF.toInt())
        val keyHighlight = color(ColorKey.HILITED_KEY_BACK_COLOR, keyBack)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val radius = heightPx * 0.10f
        val round = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())

        // Clip the whole drawing to the rounded card, so the candidate strip
        // does not paint over the corners.
        val clip = Path().apply { addRoundRect(round, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        paint.color = background
        canvas.drawRect(round, paint)

        // Candidate strip on top: the most recognizable part of a scheme.
        val barHeight = heightPx * 0.20f
        paint.color = candidate
        canvas.drawRect(0f, 0f, widthPx.toFloat(), barHeight, paint)

        val rows = rowStructure(theme)
        val padding = widthPx * 0.03f
        val gap = widthPx * 0.008f
        val top = barHeight + gap * 2
        val rowSpace = (heightPx - top - padding) / rows.size
        val keyHeight = rowSpace - gap

        rows.forEachIndexed { rowIndex, widths ->
            val total = widths.sum().coerceAtLeast(1f)
            val available = widthPx - padding * 2
            var x = padding
            widths.forEachIndexed { keyIndex, weight ->
                val keyWidth = available * (weight / total) - gap
                val y = top + rowIndex * rowSpace
                paint.color = if (rowIndex == 1 && keyIndex == 0) keyHighlight else keyBack
                canvas.drawRoundRect(
                    x,
                    y,
                    x + keyWidth,
                    y + keyHeight,
                    gap,
                    gap,
                    paint,
                )
                x += keyWidth + gap
            }
        }

        canvas.restore()
        return bitmap
    }

    /** Relative key widths per row, taken from the theme's `qwerty` keyboard. */
    private fun rowStructure(theme: Theme): List<List<Float>> {
        val keyboard: TextKeyboard? = theme.presetKeyboards[KEYBOARD_ID]
        val keys = keyboard?.keys
        if (keys.isNullOrEmpty()) return DEFAULT_ROWS.map { row -> List(row) { 1f } }

        val columns = if (keyboard.columns > 0) keyboard.columns.toFloat() else 30f
        val rows = mutableListOf<MutableList<Float>>()
        var current = mutableListOf<Float>()
        var used = 0f
        keys.forEach { key ->
            val weight = if (key.width > 0f) key.width else 1f
            if (used + weight > columns + 0.01f) {
                rows += current
                current = mutableListOf()
                used = 0f
            }
            current += weight
            used += weight
        }
        if (current.isNotEmpty()) rows += current
        return rows.filter { it.isNotEmpty() }
    }
}
