/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

/**
 * The behaviours a key can carry, in the order the editor lists them.
 *
 * `TextKey` stores these as thirteen separate fields; this enum is what lets
 * the picker, the inspector and (in K3) the writer talk about "a slot" without
 * a `when` in every caller. The order is the one users meet them: press first,
 * then long press, then the four swipes; the rest are advanced.
 */
enum class ActionSlot(
    val yaml: String,
) {
    CLICK("click"),
    LONG_CLICK("long_click"),
    SWIPE_UP("swipe_up"),
    SWIPE_DOWN("swipe_down"),
    SWIPE_LEFT("swipe_left"),
    SWIPE_RIGHT("swipe_right"),
    DOUBLE_CLICK("double_click"),
    LAZY_DOUBLE_CLICK("lazy_double_click"),
    COMPOSING("composing"),
    PAGING("paging"),
    HAS_MENU("has_menu"),
    COMBO("combo"),
    ASCII("ascii"),
    ;

    /** Slots the editor puts on the main panel; the rest hide under "advanced". */
    val isPrimary: Boolean
        get() = this == CLICK || this == LONG_CLICK || this in SWIPES

    /** The token bound to [slot] on [key], or null when the slot is empty. */
    fun of(key: com.osfans.trime.data.theme.model.TextKeyboard.TextKey): com.osfans.trime.data.theme.model.KeyActionToken? = when (this) {
        CLICK -> key.click
        LONG_CLICK -> key.longClick
        SWIPE_UP -> key.swipeUp
        SWIPE_DOWN -> key.swipeDown
        SWIPE_LEFT -> key.swipeLeft
        SWIPE_RIGHT -> key.swipeRight
        DOUBLE_CLICK -> key.doubleClick
        LAZY_DOUBLE_CLICK -> key.lazyDoubleClick
        COMPOSING -> key.composing
        PAGING -> key.paging
        HAS_MENU -> key.hasMenu
        COMBO -> key.combo
        ASCII -> key.ascii
    }

    companion object {
        val SWIPES = setOf(SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT)

        /** The slot a swipe in [direction] writes. */
        fun swipe(
            dx: Float,
            dy: Float,
        ): ActionSlot = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0) SWIPE_RIGHT else SWIPE_LEFT
        } else {
            if (dy > 0) SWIPE_DOWN else SWIPE_UP
        }
    }
}
