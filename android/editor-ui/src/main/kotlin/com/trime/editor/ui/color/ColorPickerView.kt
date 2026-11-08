/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.color

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.trime.editor.ui.dp

/**
 * Saturation/value square, hue strip and alpha strip in one view.
 *
 * The three regions are drawn and hit-tested here rather than split into child
 * views: they share one colour, and every drag has to update the other two
 * anyway. Coordinates are kept in the view's own space, so nothing depends on
 * the dialog width.
 *
 * A fully transparent colour carries no hue, and `Color.colorToHSV` reports
 * black for it — [alpha] is therefore kept alongside the HSV triple instead of
 * being read back from the colour, or clearing alpha to zero would silently
 * reset the hue to red.
 */
class ColorPickerView(context: Context) : View(context) {
    private var hue = 0f
    private var saturation = 0f
    private var value = 0f
    private var alpha = 255

    /** The colour these HSV values describe. */
    var color: Int = Color.BLACK
        private set

    /** Called on every change, including mid-drag. */
    var onColorChanged: ((Int) -> Unit)? = null

    private var activePiece = Piece.NONE

    private enum class Piece { NONE, SATURATION_VALUE, HUE, ALPHA }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    private val svRect = RectF()
    private val hueRect = RectF()
    private val alphaRect = RectF()

    private var svShader: Shader? = null
    private var hueShader: Shader? = null
    private var alphaShader: Shader? = null

    private val gutter = context.dp(10).toFloat()
    private val stripHeight = context.dp(26).toFloat()
    private val cursorRadius = context.dp(7).toFloat()

    /** Checkerboard behind the alpha strip, so transparency is visible. */
    private val checkerShader: BitmapShader?

    init {
        val cell = context.dp(6).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFCCCCCC.toInt())
        val light = Paint().apply { color = 0xFFF2F2F2.toInt() }
        canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), light)
        canvas.drawRect(cell.toFloat(), cell.toFloat(), (cell * 2).toFloat(), (cell * 2).toFloat(), light)
        checkerShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    /** Loads [argb] into the picker, keeping the hue of a colour that has none. */
    fun setColor(argb: Int) {
        val newAlpha = Color.alpha(argb)
        // Black, white and anything fully transparent all report hue 0; taking
        // that at face value would make the handles jump to red on every
        // "darken to nothing" drag.
        if (newAlpha > 0) {
            val hsv = FloatArray(3)
            Color.colorToHSV(argb, hsv)
            if (hsv[1] > 0.001f || hsv[2] > 0.001f) {
                hue = hsv[0]
                saturation = hsv[1]
                value = hsv[2]
            }
        }
        alpha = newAlpha
        updateColor(notify = false)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // Capped so the square stays thumb-sized on a tablet, where the dialog
        // would otherwise hand it the whole screen width.
        val width = minOf(MeasureSpec.getSize(widthMeasureSpec), context.dp(MAX_WIDTH_DP))
        val square = (width * SQUARE_RATIO).toInt()
        val height = square + (stripHeight + gutter).toInt() * 2
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        val square = height - (stripHeight + gutter) * 2
        svRect.set(0f, 0f, width.toFloat(), square)
        hueRect.set(0f, square + gutter, width.toFloat(), square + gutter + stripHeight)
        alphaRect.set(0f, square + gutter + stripHeight + gutter, width.toFloat(), height.toFloat())
        rebuildShaders()
    }

    private fun rebuildShaders() {
        if (svRect.isEmpty) return
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val saturationShader =
            LinearGradient(
                svRect.left,
                svRect.top,
                svRect.right,
                svRect.top,
                Color.WHITE,
                hueColor,
                Shader.TileMode.CLAMP,
            )
        // White at the top, black at the bottom: multiplying the two gives the
        // value axis for free.
        val valueShader =
            LinearGradient(
                svRect.left,
                svRect.top,
                svRect.left,
                svRect.bottom,
                Color.WHITE,
                Color.BLACK,
                Shader.TileMode.CLAMP,
            )
        svShader = ComposeShader(saturationShader, valueShader, PorterDuff.Mode.MULTIPLY)
        hueShader =
            LinearGradient(
                hueRect.left,
                hueRect.top,
                hueRect.right,
                hueRect.top,
                HUE_COLORS,
                null,
                Shader.TileMode.CLAMP,
            )
        alphaShader = null
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val radius = 2f * density

        svShader?.let {
            paint.shader = it
            canvas.drawRoundRect(svRect, radius, radius, paint)
        }

        paint.shader = hueShader
        if (paint.shader != null) canvas.drawRoundRect(hueRect, radius, radius, paint)

        // Checkerboard first, then the transparency ramp on top of it.
        paint.shader = checkerShader
        canvas.drawRoundRect(alphaRect, radius, radius, paint)
        paint.shader =
            alphaShader
                ?: LinearGradient(
                    alphaRect.left,
                    alphaRect.top,
                    alphaRect.right,
                    alphaRect.top,
                    Color.HSVToColor(0, floatArrayOf(hue, saturation, value)),
                    Color.HSVToColor(255, floatArrayOf(hue, saturation, value)),
                    Shader.TileMode.CLAMP,
                ).also { alphaShader = it }
        canvas.drawRoundRect(alphaRect, radius, radius, paint)
        paint.shader = null

        drawCursor(canvas, svRect.left + saturation * svRect.width(), svRect.bottom - value * svRect.height())
        drawCursor(canvas, hueRect.left + hue / 360f * hueRect.width(), hueRect.centerY())
        drawCursor(canvas, alphaRect.left + alpha / 255f * alphaRect.width(), alphaRect.centerY())
    }

    private fun drawCursor(
        canvas: Canvas,
        x: Float,
        y: Float,
    ) {
        // A handle at value 1 sits on the top edge; without clamping half of
        // it is painted outside the view and disappears.
        val cx = x.coerceIn(cursorRadius, width - cursorRadius)
        val cy = y.coerceIn(cursorRadius, height - cursorRadius)
        cursorPaint.strokeWidth = resources.displayMetrics.density * 2f
        cursorPaint.color = 0x66000000
        canvas.drawCircle(cx, cy, cursorRadius, cursorPaint)
        cursorPaint.strokeWidth = resources.displayMetrics.density * 1.5f
        cursorPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, cursorRadius, cursorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePiece = pieceAt(event.y)
                if (activePiece == Piece.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                apply(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePiece == Piece.NONE) return false
                apply(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePiece = Piece.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pieceAt(y: Float): Piece = when {
        y <= svRect.bottom -> Piece.SATURATION_VALUE
        hueRect.top <= y && y <= hueRect.bottom -> Piece.HUE
        alphaRect.top <= y && y <= alphaRect.bottom -> Piece.ALPHA
        else -> Piece.NONE
    }

    private fun apply(event: MotionEvent) {
        val x = event.x.coerceAtLeast(0f)
        when (activePiece) {
            Piece.SATURATION_VALUE -> {
                saturation = (x / svRect.width()).coerceIn(0f, 1f)
                value = ((svRect.bottom - event.y) / svRect.height()).coerceIn(0f, 1f)
                // The square is coloured by the hue, so its gradient follows.
                rebuildShaders()
            }

            Piece.HUE -> {
                hue = (x / hueRect.width()).coerceIn(0f, 1f) * 360f
                rebuildShaders()
            }

            Piece.ALPHA -> alpha = ((x / alphaRect.width()).coerceIn(0f, 1f) * 255f).toInt()

            Piece.NONE -> return
        }
        updateColor(notify = true)
        invalidate()
    }

    private fun updateColor(notify: Boolean) {
        color = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
        if (notify) onColorChanged?.invoke(color)
    }

    // internal 而不是 private：色相顺序有单测钉着（见 ColorPickerHueOrderTest）。
    internal companion object {
        /** Square height as a fraction of the width. */
        const val SQUARE_RATIO = 0.60f

        /** Widest the picker is allowed to get, in dp. */
        const val MAX_WIDTH_DP = 320

        /**
         * 色相条的颜色，必须与 `hue = x / width * 360` 这套读法同向。
         *
         * 左边是 0°（红），往右角度增大：黄 60°、绿 120°、青 180°、蓝 240°、品红 300°。
         * 这串原来写成了红→品红→蓝→青→绿→黄→红（反向），于是点在「看着是蓝」的
         * 位置上读出来是 120°（绿）——看到的颜色和拿到的颜色差半个色环。
         */
        @androidx.annotation.VisibleForTesting
        internal val HUE_COLORS =
            intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED,
            )
    }
}
