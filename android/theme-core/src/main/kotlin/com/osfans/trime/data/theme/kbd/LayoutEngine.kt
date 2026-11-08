/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.orAbsent

/**
 * Splits a key list into rows exactly the way `Keyboard` does.
 *
 * A layout has no stored row structure: `Keyboard` walks the keys once, adding
 * each one's width weight to a budget of [MAX_TOTAL_WEIGHT] and starting a new
 * row when the next key would not fit (or when `columns` is reached). The
 * editor has to show those rows, so it repeats the same integer arithmetic —
 * a different rounding here would draw a layout the user cannot get.
 *
 * The second pass of `Keyboard` (normalising each row to the full width) is
 * deliberately not repeated: the canvas draws a real `Keyboard`, and the
 * thumbnail only needs the row membership and the row's weight sum.
 */
object LayoutEngine {
    /** Every row's weights add up to this; `Keyboard.MAX_TOTAL_WEIGHT`. */
    const val MAX_TOTAL_WEIGHT = 100f

    /** One key as the layout sees it. */
    data class Key(
        /** Explicit `width:` weight, or null when the key did not set one. */
        val weight: Float?,
        /** Only clickable keys count toward `columns`. */
        val clickable: Boolean,
    )

    data class Row(
        /** Indices into the key list, in layout order. */
        val indices: List<Int>,
        /** Sum of the row's weights; 100 means the row is exactly full. */
        val weightSum: Float,
    )

    fun rows(
        keys: List<Key>,
        keyboardWidth: Float,
        columns: Int,
        allowedWidthPx: Int,
    ): List<Row> {
        if (keys.isEmpty() || allowedWidthPx <= 0) return emptyList()
        val maxColumns = if (columns == -1) Int.MAX_VALUE else columns
        val rows = mutableListOf<Row>()
        var current = mutableListOf<Int>()
        var totalWeight = 0f
        var x = 0
        var column = 0

        keys.forEachIndexed { index, key ->
            val weight = effectiveWeight(key, keyboardWidth)
            val widthPx = (weight * allowedWidthPx / MAX_TOTAL_WEIGHT).toInt()
            if (column >= maxColumns || x + widthPx > allowedWidthPx) {
                rows += Row(current, totalWeight)
                current = mutableListOf()
                totalWeight = 0f
                x = 0
                column = 0
            }
            current += index
            totalWeight += weight
            if (key.clickable) column++
            x += widthPx
        }
        rows += Row(current, totalWeight)
        return rows
    }

    /** Convenience for a decoded layout at a given pixel width. */
    fun rows(
        config: TextKeyboard,
        allowedWidthPx: Int,
    ): List<Row> = rows(
        keys = config.keys.map { key ->
            Key(
                weight = key.width.takeIf { it > 0f },
                clickable = key.click.orAbsent != null,
            )
        },
        keyboardWidth = config.width,
        columns = config.columns,
        allowedWidthPx = allowedWidthPx,
    )

    /**
     * A key without an explicit width falls back to the keyboard's own default
     * width — but only when it has a click action; a pure spacer stays at zero.
     */
    private fun effectiveWeight(
        key: Key,
        keyboardWidth: Float,
    ): Float = when {
        key.weight != null -> key.weight
        key.clickable -> keyboardWidth
        else -> 0f
    }
}
