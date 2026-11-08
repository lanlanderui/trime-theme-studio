/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * Draws what a key says: its label, its symbol and its hint.
 *
 * These rules used to live twice — once in [KeyView] for the keyboard on
 * screen, once in `KeyColorBoardView` for the preview in the settings — and
 * they drifted: the preview drew labels at a size the keyboard never used and
 * flattened a two-line symbol into its first line. Everything that decides
 * *what* is drawn and *where* is here now. What stays with the callers is what
 * they legitimately own: which [Paint] to use (and therefore which typeface),
 * and how an icon-font name becomes pixels.
 *
 * The label is an input rather than something read from the key: the keyboard
 * resolves it against rime's state, while a settings screen can only use what
 * the theme declares. Passing it in is what lets one renderer serve both.
 */
object KeyFaceRenderer {
    /**
     * The sizes and switches for one draw, as resolved from the theme.
     *
     * Mutable and reused: a keyboard draws every key on every frame, and a
     * fresh holder per key would put an allocation in that path.
     */
    class Style {
        /** One-letter label size, in sp. */
        var keyTextSize = 0f

        /** Size for labels of more than one character, in sp. Themes set it smaller. */
        var keyLongTextSize = 0f

        /** Symbol and hint size, in sp. */
        var symbolTextSize = 0f

        /**
         * `displayMetrics.scaledDensity`, which turns the sp values above into
         * pixels. Taken from the drawing view, so a font-size change is picked
         * up with the next draw instead of with the next keyboard.
         */
        var spScale = 1f

        /** Cap labels at this fraction of the key height; 0 leaves them uncapped. */
        var labelCap = 0f

        /** Cap symbols and hints at this fraction of the key height; 0 = no cap. */
        var symbolCap = 0f

        var showSymbols = true
        var showHints = true
    }

    /** Turns an icon-font name into pixels; the caller owns the icon library. */
    fun interface IconPainter {
        fun draw(
            canvas: Canvas,
            name: String,
            sizePx: Float,
            color: Int,
            centerX: Float,
            centerY: Float,
        )
    }

    /**
     * Draws [key]'s face inside [frame], the area its content may occupy.
     *
     * [label] is already resolved by the caller (see the class comment). The
     * two paints must be configured by the caller — typeface, shadow layer —
     * and their text size is set here. [icons] may be null, in which case an
     * icon-font name is drawn as the text it is, which is what a preview with
     * no icon library can do.
     */
    fun draw(
        canvas: Canvas,
        key: Key,
        label: String,
        frame: RectF,
        style: Style,
        labelPaint: Paint,
        symbolPaint: Paint,
        icons: IconPainter? = null,
    ) {
        val band = frame.height()
        if (label.isNotEmpty()) {
            drawLabel(canvas, key, label, frame, band, style, labelPaint, icons)
        }
        if (style.showSymbols) {
            drawEdge(canvas, key, key.symbolLabel, frame, band, style, symbolPaint, icons, isTop = true)
        }
        if (style.showHints) {
            drawEdge(canvas, key, key.hint, frame, band, style, symbolPaint, icons, isTop = false)
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        key: Key,
        label: String,
        frame: RectF,
        band: Float,
        style: Style,
        paint: Paint,
        icons: IconPainter?,
    ) {
        // A multi-character label takes the long-label size, which themes set
        // smaller than the one-letter size.
        val long = label.length > 1 && !label.isIconFont
        val fromTheme = key.keyTextSize.takeIf { it > 0f } ?: if (long && style.keyLongTextSize > 0f) style.keyLongTextSize else style.keyTextSize
        val size = cap(fromTheme * style.spScale, band, style.labelCap)
        val offsetY = key.keyTextOffsetY * style.spScale
        val centerX = frame.centerX() + key.keyTextOffsetX * style.spScale
        val color = key.getTextColor()

        if (label.isIconFont && icons != null) {
            icons.draw(canvas, label, size, color, centerX, frame.centerY() + offsetY)
            return
        }

        paint.color = color
        paint.textSize = size
        val metrics = paint.fontMetrics
        // Centred on the cap height rather than the em box, which is what makes
        // a label look centred instead of sitting a little high.
        val baseline = frame.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, centerX, baseline + offsetY, paint)
    }

    private fun drawEdge(
        canvas: Canvas,
        key: Key,
        text: String,
        frame: RectF,
        band: Float,
        style: Style,
        paint: Paint,
        icons: IconPainter?,
        isTop: Boolean,
    ) {
        if (text.isEmpty()) return

        val fromTheme = key.symbolTextSize.takeIf { it > 0f } ?: style.symbolTextSize
        val size = cap(fromTheme * style.spScale, band, style.symbolCap)
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = (if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY) * style.spScale
        val centerX = frame.centerX() + offsetX * style.spScale
        val color = key.getSymbolColor()

        if (text.isIconFont && icons != null) {
            // The two anchors are not symmetric in the keyboard as it stands: the
            // top one measures from the icon's top edge, the bottom one from
            // `bottom - size`, which puts it half an icon higher than it reads.
            // Kept as it is, because "fixing" it would move every themed icon
            // hint on a real keyboard; see H3 in doc/code-diagnosis.md.
            val half = size / 2f
            val centerY = if (isTop) frame.top + half + offsetY else frame.bottom - size + offsetY
            icons.draw(canvas, text, size, color, centerX, centerY)
            return
        }

        paint.color = color
        paint.textSize = size
        val metrics = paint.fontMetrics
        val lines = text.split("\n")
        val lineHeight = metrics.descent - metrics.ascent
        // A two-line symbol hangs from the same edge as a one-line one, so the
        // first line is pushed up by the height of the lines below it.
        val below = lineHeight * (lines.size - 1) / 2f
        val firstBaseline =
            if (isTop) {
                frame.top - metrics.top + offsetY - below
            } else {
                frame.bottom - metrics.bottom + offsetY - below
            }
        for (i in lines.indices) {
            canvas.drawText(lines[i], centerX, firstBaseline + lineHeight * i, paint)
        }
    }

    /** Applies a cap only when the caller asked for one. */
    private fun cap(
        size: Float,
        band: Float,
        ratio: Float,
    ): Float = if (ratio > 0f) min(size, band * ratio) else size
}
