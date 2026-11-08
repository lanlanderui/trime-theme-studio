/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

/**
 * Converts between the order a toolbar is *seen* in and the order `buttons` is
 * *written* in.
 *
 * The two run opposite ways. `ButtonsBarUi` lays the middle segment out in a
 * `FlexboxLayout` with `ROW_REVERSE`, so `buttons[0]` is pinned at the right
 * edge and each following index steps left; `primary_button` is pinned at the
 * left edge outside that segment. An editor list that reads left to right is
 * therefore showing the array backwards, and getting the conversion wrong is
 * invisible until someone drags a button and watches it jump to the far side.
 *
 * Kept apart from the UI so the round trip can be asserted without a device.
 */
object ToolBarSlot {
    /**
     * Array index of the button shown at [visual], counting from the left.
     *
     * [visual] 0 is `buttons.last()`; [visual] `total - 1` is `buttons[0]`.
     */
    fun visualToArray(
        visual: Int,
        total: Int,
    ): Int = total - 1 - visual

    /** Visual position of the button stored at [array]; the inverse of [visualToArray]. */
    fun arrayToVisual(
        array: Int,
        total: Int,
    ): Int = total - 1 - array

    /**
     * Reorders [sources] to match a new visual order.
     *
     * [from] and [to] are visual positions. Moving an item in the rendered list
     * means moving it in the array, so the list is reversed, mutated, and
     * reversed back — which also keeps inline `{...}` items in place instead of
     * rebuilding them from a model.
     */
    fun move(
        sources: List<String>,
        from: Int,
        to: Int,
    ): List<String> {
        if (from == to) return sources
        if (from !in sources.indices || to !in sources.indices) return sources
        val visual = sources.asReversed().toMutableList()
        val item = visual.removeAt(from)
        visual.add(to, item)
        return visual.asReversed()
    }

    /** Drops the item shown at [visual]; the block it pointed at stays in the file. */
    fun remove(
        sources: List<String>,
        visual: Int,
    ): List<String> {
        val array = visualToArray(visual, sources.size)
        if (array !in sources.indices) return sources
        return sources.filterIndexed { index, _ -> index != array }
    }
}
