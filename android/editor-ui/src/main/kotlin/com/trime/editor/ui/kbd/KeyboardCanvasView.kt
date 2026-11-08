/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.kbd

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.osfans.trime.ime.keyboard.KeyColorBoardView
import com.trime.editor.ui.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The editable keyboard: the same drawing as the preview board, plus a
 * selection, a resize handle and a per-row weight hint.
 *
 * It draws a real [com.osfans.trime.ime.keyboard.Keyboard] built from the draft,
 * so the keys being dragged are the keys the IME will show — shapes, gaps,
 * labels and all. Only the overlay is drawn here.
 */
class KeyboardCanvasView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : KeyColorBoardView(context, attrs) {
    /** Per-key weights and row membership, recomputed by the screen on every change. */
    class State(
        val weights: FloatArray,
        val rowSums: FloatArray,
    )

    var state: State? = null
        set(value) {
            field = value
            invalidate()
        }

    var selectedIndex: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Draft index ↔ real key index.
     *
     * A key whose `click` has no action never becomes a `Key` — it keeps its
     * width but draws nothing. Filtering those out is what makes a tap land
     * on the key the user sees; -1 marks an index with no counterpart.
     */
    var draftToKey: IntArray = IntArray(0)
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 反方向：真实键序号 → 草稿序号。
     *
     * 点击命中拿到的是真实键序号（[keyboard] 里的第几个 Key），面板要的是草稿
     * 序号。两个方向都要带，用一个去顶另一个会在第一个占位键之后整体错位。
     */
    var keyToDraft: IntArray = IntArray(0)
        set(value) {
            field = value
            invalidate()
        }

    /** Where the action-less keys sit; they are the layout's indents. */
    class BlankMark(
        val draftIndex: Int,
        val rect: RectF,
    )

    var blanks: List<BlankMark> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onSelect: ((Int?) -> Unit)? = null

    /** Mid-drag weight, for the inspector to follow the finger. */
    var onWidthPreview: ((Int, Float) -> Unit)? = null

    /** Final weight, once the finger lifts. */
    var onWidthCommit: ((Int, Float) -> Unit)? = null

    private val accent = context.resolveColor(android.R.attr.colorAccent, 0xFF1E88E5.toInt())
    private val selection =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(2).toFloat()
            color = accent
        }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Blank keys get a dashed outline: the editor's way of admitting they exist. */
    private val blankOutline =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(1).toFloat()
            color = 0x61000000
            pathEffect = DashPathEffect(floatArrayOf(context.dp(4).toFloat(), context.dp(4).toFloat()), 0f)
        }
    private val rowLabel =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
            textSize = context.dp(10).toFloat()
        }
    private val box = RectF()

    /** Width of the grab strip on a key's right edge; blanks use the same. */
    private val edge: Float get() = context.dp(HANDLE_WIDTH_DP).toFloat()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var resizing = false
    private var resizeStartX = 0f
    private var resizeStartWeight = 0f
    private var lastPreview = 0f

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val keys = keyboard?.keys ?: return
        drawBlanks(canvas)
        drawRowHints(canvas)
        val index = selectedIndex ?: return
        val keyIndex = draftToKey.getOrNull(index) ?: -1
        if (keyIndex !in keys.indices) {
            // A blank has no key to outline; frame its gap instead.
            blanks.firstOrNull { it.draftIndex == index }?.let { blank ->
                val r = context.dp(6).toFloat()
                canvas.drawRoundRect(blank.rect, r, r, selection)
                val grip = handleRect(index)
                canvas.drawRoundRect(grip, grip.width() / 2f, grip.width() / 2f, handle)
            }
            return
        }
        val key = keys[keyIndex]
        box.set(
            key.x.toFloat(),
            key.y.toFloat(),
            (key.x + key.width).toFloat(),
            (key.y + key.height).toFloat(),
        )
        box.inset(INSET, INSET)
        val radius = context.dp(6).toFloat()
        canvas.drawRoundRect(box, radius, radius, selection)
        val grip = handleRect(index)
        canvas.drawRoundRect(grip, grip.width() / 2f, grip.width() / 2f, handle)
    }

    private fun drawBlanks(canvas: Canvas) {
        blanks.forEach { blank ->
            canvas.drawRoundRect(blank.rect, context.dp(6).toFloat(), context.dp(6).toFloat(), blankOutline)
        }
    }

    /**
     * Marks rows whose weights no longer add up to 100.
     *
     * A full-width rule read as a strikethrough over the keys; a small pill
     * at the row's right edge says the same thing without hiding them.
     */
    private fun drawRowHints(canvas: Canvas) {
        val current = state ?: return
        val keys = keyboard?.keys ?: return
        current.rowSums.forEachIndexed { row, sum ->
            val off = sum - MAX_TOTAL_WEIGHT
            if (abs(off) <= 0.5f) return@forEachIndexed
            val inRow = keys.filter { it.row == row }
            if (inRow.isEmpty()) return@forEachIndexed
            val bottom = inRow.maxOf { it.y + it.height }.toFloat()
            val text = formatWeight(sum)
            badge.color = if (off > 0f) DANGER else WARN
            val padding = context.dp(8).toFloat()
            val half = context.dp(9).toFloat()
            val w = rowLabel.measureText(text) + padding * 2
            // Keep the pill inside the canvas: the last row's bottom edge is
            // also the view's bottom, so half of it would be clipped.
            val center = bottom.coerceIn(half, height - half)
            box.set(width - w - context.dp(4), center - half, width - context.dp(4).toFloat(), center + half)
            canvas.drawRoundRect(box, half, half, badge)
            rowLabel.color = 0xFFFFFFFF.toInt()
            canvas.drawText(
                text,
                box.right - padding,
                box.centerY() - (rowLabel.fontMetrics.ascent + rowLabel.fontMetrics.descent) / 2f,
                rowLabel,
            )
        }
    }

    /** The grab area on the right edge of the selected key (a draft index). */
    fun handleRect(index: Int): RectF {
        val keys = keyboard?.keys ?: return RectF()
        val keyIndex = draftToKey.getOrNull(index) ?: -1
        if (keyIndex !in keys.indices) {
            val blank = blanks.firstOrNull { it.draftIndex == index } ?: return RectF()
            return RectF(blank.rect.right - edge, blank.rect.top, blank.rect.right, blank.rect.bottom)
        }
        val key = keys[keyIndex]
        val w = context.dp(HANDLE_WIDTH_DP).toFloat()
        val insetY = key.height * 0.22f
        return RectF(
            key.x + key.width - w,
            key.y + insetY,
            (key.x + key.width).toFloat(),
            key.y + key.height - insetY,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val keys = keyboard?.keys ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragged = false
                val index = selectedIndex
                if (index != null && handleRect(index).contains(event.x, event.y)) {
                    resizing = true
                    resizeStartX = event.x
                    resizeStartWeight = state?.weights?.getOrNull(index) ?: 0f
                    lastPreview = resizeStartWeight
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (resizing) {
                    val index = selectedIndex ?: return true
                    val perWeight = width / MAX_TOTAL_WEIGHT
                    if (perWeight <= 0f) return true
                    val next =
                        (resizeStartWeight + (event.x - resizeStartX) / perWeight)
                            .coerceIn(MIN_WEIGHT, MAX_WEIGHT)
                            .roundToHalf()
                    if (next != lastPreview) {
                        lastPreview = next
                        onWidthPreview?.invoke(index, next)
                    }
                    return true
                }
                if (!dragged && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                    dragged = true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (resizing) {
                    resizing = false
                    selectedIndex?.let { onWidthCommit?.invoke(it, lastPreview) }
                    return true
                }
                if (!dragged) {
                    val index = keyAt(event.x, event.y)
                    selectedIndex = index
                    onSelect?.invoke(index)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resizing = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Topmost key under the point, as a draft index; the last drawn key wins. */
    private fun keyAt(
        x: Float,
        y: Float,
    ): Int? {
        val keys = keyboard?.keys ?: return null
        // A blank covers a gap no key occupies, so it can be tested first.
        blanks
            .firstOrNull { x >= it.rect.left && x <= it.rect.right && y >= it.rect.top && y <= it.rect.bottom }
            ?.let { return it.draftIndex }
        for (i in keys.indices.reversed()) {
            val key = keys[i]
            if (x >= key.x && x <= key.x + key.width && y >= key.y && y <= key.y + key.height) {
                // i 是**真实键**的序号，要走 keyToDraft；draftToKey 是反方向的。
                // 用反了的话，第一个占位键之前碰巧对（两边都是 i），之后每过一个
                // 占位键就整体错位一格——点空格会选中它前面的键。
                return keyToDraft.getOrNull(i)?.takeIf { it >= 0 }
            }
        }
        return null
    }

    private fun formatWeight(value: Float): String = if (value == value.toInt().toFloat()) value.toInt().toString() else String.format("%.1f", value)

    private fun Float.roundToHalf(): Float = (this * 2f).roundToInt() / 2f

    private fun Context.resolveColor(
        attr: Int,
        fallback: Int,
    ): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return fallback
        return if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            value.data
        } else {
            fallback
        }
    }

    private companion object {
        const val HANDLE_WIDTH_DP = 14
        const val MIN_WEIGHT = 1f
        const val MAX_WEIGHT = 60f
        const val MAX_TOTAL_WEIGHT = 100f
        const val INSET = 1f
        const val WARN = 0xFFE8A33D.toInt()
        const val DANGER = 0xFFD9483B.toInt()
    }
}
