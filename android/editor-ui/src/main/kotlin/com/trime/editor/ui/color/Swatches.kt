/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.color

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.trime.editor.ui.dp

/**
 * A rounded swatch with a thin outline.
 *
 * A plain colour drawable has no intrinsic size, so a preference icon built from
 * one collapses to zero width; a bitmap keeps its dimensions. The outline is
 * what keeps a near-white colour visible on a white row.
 */
internal fun Context.colorSwatch(
    argb: Int,
    widthDp: Int = 40,
    heightDp: Int = 24,
): Drawable {
    val density = resources.displayMetrics.density
    val width = (widthDp * density).toInt().coerceAtLeast(1)
    val height = (heightDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val radius = height * 0.25f
    paint.color = argb
    canvas.drawRoundRect(rect, radius, radius, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = density
    paint.color = 0x33000000
    canvas.drawRoundRect(rect, radius, radius, paint)
    return BitmapDrawable(resources, bitmap)
}

/**
 * A swatch that shows transparency honestly: checkerboard underneath, the fill
 * on top.
 *
 * Border and shadow colours are semi-transparent by design, and a plain fill
 * would render them as solid black — the swatch would then contradict both the
 * row's text and the keyboard.
 */
internal fun Context.checkerSwatch(argb: Int): Drawable {
    val density = resources.displayMetrics.density
    val fill =
        GradientDrawable().apply {
            cornerRadius = 4f * density
            setColor(argb)
            setStroke(density.toInt().coerceAtLeast(1), 0x33000000)
        }
    val checker =
        BitmapDrawable(resources, checkerTile()).apply {
            tileModeX = Shader.TileMode.REPEAT
            tileModeY = Shader.TileMode.REPEAT
        }
    return LayerDrawable(arrayOf(checker, fill))
}

/** Two tones of grey, the smallest tile that reads as "transparent". */
private fun Context.checkerTile(): Bitmap {
    val cell = (6 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFCCCCCC.toInt())
    val light = Paint().apply { color = 0xFFF2F2F2.toInt() }
    canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), light)
    canvas.drawRect(cell.toFloat(), cell.toFloat(), (cell * 2).toFloat(), (cell * 2).toFloat(), light)
    return bitmap
}
