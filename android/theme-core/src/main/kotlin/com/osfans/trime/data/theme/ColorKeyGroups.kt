/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * Where a colour key belongs in the colour editor.
 *
 * The declaration order is the order the editor renders them in: it follows the
 * way a keyboard is read from top to bottom — the candidate bar, then the
 * keyboard surface, then the keys, then what a key looks like while held.
 */
enum class ColorGroup {
    CANDIDATE_BAR,
    KEYBOARD,
    KEY,
    FUNCTION_KEY,
    PRESSED_KEY,
    POPUP,
    APP_CHROME,
    OTHER,
}

/**
 * Files colour keys under a semantic group.
 *
 * Membership comes from the key name rather than from a hand-maintained list,
 * so a key a theme invents lands somewhere sensible without anyone remembering
 * to add it. Anything the rules do not recognise ends up in
 * [ColorGroup.OTHER] instead of disappearing — some themes define their own
 * aliases (`bbs`, `tkg`, …) that keys then reference, and the editor has to
 * keep showing them.
 */
object ColorKeyGroups {
    /**
     * Groups [keys] in rendering order, keeping the order the keys were given
     * with inside each group. Groups with no keys are dropped.
     */
    fun group(keys: List<String>): List<Pair<ColorGroup, List<String>>> {
        val buckets = LinkedHashMap<ColorGroup, MutableList<String>>()
        for (group in ColorGroup.entries) buckets[group] = mutableListOf()
        for (key in keys) buckets.getValue(groupOf(key)) += key
        return buckets.entries.map { it.key to it.value.toList() }.filter { it.second.isNotEmpty() }
    }

    /**
     * The group for one key. The order of the checks matters: the `hilited_`
     * families are more specific than the plain ones they share a prefix with.
     */
    fun groupOf(key: String): ColorGroup = when {
        key.isAnyOf("hilited_key_", "hilited_off_key_", "hilited_on_key_") -> ColorGroup.PRESSED_KEY

        key.isAnyOf("hilited_popup_") -> ColorGroup.POPUP

        key.isAnyOf("hilited_candidate_", "hilited_comment_", "hilited_text_", "hilited_back_") ->
            ColorGroup.CANDIDATE_BAR

        key == "hilited_label_color" -> ColorGroup.CANDIDATE_BAR

        key.isAnyOf("off_key_", "on_key_") -> ColorGroup.FUNCTION_KEY

        key.isAnyOf("popup_", "long_text_") -> ColorGroup.POPUP

        key.isAnyOf("key_") || key == "label_color" -> ColorGroup.KEY

        key.isAnyOf("candidate_", "comment_", "text_") || key == "back_color" -> ColorGroup.CANDIDATE_BAR

        key.isAnyOf("keyboard_", "liquid_keyboard_") ||
            key == "root_background" ||
            key == "shadow_color" ||
            key == "border_color" -> ColorGroup.KEYBOARD

        key == "navbar_background" || key == "normal_mode_color" -> ColorGroup.APP_CHROME

        else -> ColorGroup.OTHER
    }

    private fun String.isAnyOf(vararg prefixes: String): Boolean = prefixes.any { startsWith(it) }
}
