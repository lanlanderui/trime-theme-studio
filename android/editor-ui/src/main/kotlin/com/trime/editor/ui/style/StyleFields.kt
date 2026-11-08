/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.style

import androidx.annotation.StringRes
import com.trime.editor.ui.R

/**
 * The `style:` entries the editor offers, grouped the way the file groups them.
 *
 * Every entry here is a top-level scalar of the theme's `style:` block, so it
 * can be written back line-for-line without touching anything around it. The
 * table is data: the screen that renders it decides how a value is edited,
 * this only says which values exist and what they may hold.
 *
 * Field order inside a group follows the theme template, so reading the screen
 * and reading the YAML tells the same story.
 */

enum class StyleFieldType {
    INT,
    FLOAT,
    TEXT,
    BOOL,
    ENUM,
}

data class StyleField(
    val key: String,
    @StringRes val title: Int,
    val type: StyleFieldType,
    val min: Float? = null,
    val max: Float? = null,
    val options: List<String> = emptyList(),
    /** Display labels for [options]; empty means the values are readable as-is. */
    val optionLabels: List<Int> = emptyList(),
)

data class StyleGroup(
    @StringRes val title: Int,
    val fields: List<StyleField>,
)

val styleGroups: List<StyleGroup> =
    listOf(
        StyleGroup(
            R.string.style_group_candidate,
            listOf(
                StyleField("candidate_view_height", R.string.style_candidate_view_height, StyleFieldType.INT, 0f, 200f),
                StyleField("candidate_text_size", R.string.style_candidate_text_size, StyleFieldType.FLOAT, 1f, 100f),
                StyleField("candidate_padding", R.string.style_candidate_padding, StyleFieldType.INT, 0f, 100f),
                StyleField("candidate_spacing", R.string.style_candidate_spacing, StyleFieldType.FLOAT, 0f, 20f),
                StyleField("candidate_border", R.string.style_candidate_border, StyleFieldType.INT, 0f, 50f),
                StyleField("candidate_border_round", R.string.style_candidate_border_round, StyleFieldType.FLOAT, 0f, 50f),
                StyleField("candidate_corner_radius", R.string.style_candidate_corner_radius, StyleFieldType.FLOAT, 0f, 50f),
                StyleField("candidate_text_vertical_bias", R.string.style_candidate_text_vertical_bias, StyleFieldType.FLOAT, 0f, 1f),
            ),
        ),
        StyleGroup(
            R.string.style_group_comment,
            listOf(
                StyleField("comment_height", R.string.style_comment_height, StyleFieldType.INT, 0f, 100f),
                StyleField("comment_text_size", R.string.style_comment_text_size, StyleFieldType.FLOAT, 1f, 100f),
                StyleField("comment_vertical_bias", R.string.style_comment_vertical_bias, StyleFieldType.FLOAT, 0f, 1f),
                StyleField(
                    "comment_position",
                    R.string.style_comment_position,
                    StyleFieldType.ENUM,
                    options = listOf("right", "top", "overlay"),
                    optionLabels =
                    listOf(
                        R.string.style_comment_position_right,
                        R.string.style_comment_position_top,
                        R.string.style_comment_position_overlay,
                    ),
                ),
            ),
        ),
        StyleGroup(
            R.string.style_group_key,
            listOf(
                StyleField("key_height", R.string.style_key_height, StyleFieldType.FLOAT, 0f, 300f),
                StyleField("key_width", R.string.style_key_width, StyleFieldType.FLOAT, 0f, 100f),
                StyleField("key_text_size", R.string.style_key_text_size, StyleFieldType.FLOAT, 1f, 100f),
                StyleField("key_long_text_size", R.string.style_key_long_text_size, StyleFieldType.FLOAT, 1f, 100f),
                StyleField("key_border", R.string.style_key_border, StyleFieldType.INT, 0f, 50f),
                StyleField("round_corner", R.string.style_round_corner, StyleFieldType.FLOAT, 0f, 50f),
                StyleField("shadow_radius", R.string.style_shadow_radius, StyleFieldType.FLOAT, 0f, 50f),
                // The four offset pairs and the two text sizes below were
                // missing from the test-2 screen: a theme that moves a label
                // or sets a per-family size could not be adjusted there.
                StyleField("label_text_size", R.string.style_label_text_size, StyleFieldType.FLOAT, 0f, 100f),
                StyleField("symbol_text_size", R.string.style_symbol_text_size, StyleFieldType.FLOAT, 0f, 100f),
                StyleField("key_text_offset_x", R.string.style_key_text_offset_x, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_text_offset_y", R.string.style_key_text_offset_y, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_symbol_offset_x", R.string.style_key_symbol_offset_x, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_symbol_offset_y", R.string.style_key_symbol_offset_y, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_hint_offset_x", R.string.style_key_hint_offset_x, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_hint_offset_y", R.string.style_key_hint_offset_y, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_press_offset_x", R.string.style_key_press_offset_x, StyleFieldType.FLOAT, -100f, 100f),
                StyleField("key_press_offset_y", R.string.style_key_press_offset_y, StyleFieldType.FLOAT, -100f, 100f),
            ),
        ),
        StyleGroup(
            R.string.style_group_gap,
            listOf(
                StyleField("horizontal_gap", R.string.style_horizontal_gap, StyleFieldType.INT, 0f, 50f),
                StyleField("vertical_gap", R.string.style_vertical_gap, StyleFieldType.INT, 0f, 50f),
                StyleField("keyboard_padding", R.string.style_keyboard_padding, StyleFieldType.INT, 0f, 100f),
                StyleField("keyboard_padding_left", R.string.style_keyboard_padding_left, StyleFieldType.INT, 0f, 100f),
                StyleField("keyboard_padding_right", R.string.style_keyboard_padding_right, StyleFieldType.INT, 0f, 100f),
                StyleField("keyboard_padding_bottom", R.string.style_keyboard_padding_bottom, StyleFieldType.INT, 0f, 100f),
                StyleField("keyboard_padding_land", R.string.style_keyboard_padding_land, StyleFieldType.INT, 0f, 100f),
                StyleField("keyboard_padding_land_bottom", R.string.style_keyboard_padding_land_bottom, StyleFieldType.INT, 0f, 100f),
            ),
        ),
        StyleGroup(
            R.string.style_group_height,
            listOf(
                StyleField("keyboard_height", R.string.style_keyboard_height, StyleFieldType.INT, 0f, 2000f),
                StyleField("keyboard_height_land", R.string.style_keyboard_height_land, StyleFieldType.INT, 0f, 2000f),
            ),
        ),
        StyleGroup(
            R.string.style_group_popup,
            listOf(
                StyleField("popup_width", R.string.style_popup_width, StyleFieldType.INT, 0f, 300f),
                StyleField("popup_height", R.string.style_popup_height, StyleFieldType.INT, 0f, 300f),
                StyleField("popup_text_size", R.string.style_popup_text_size, StyleFieldType.FLOAT, 1f, 100f),
                StyleField("popup_bottom_margin", R.string.style_popup_bottom_margin, StyleFieldType.INT, -300f, 600f),
                StyleField("popup_key_height", R.string.style_popup_key_height, StyleFieldType.INT, 0f, 300f),
            ),
        ),
        StyleGroup(
            R.string.style_group_font,
            listOf(
                StyleField("candidate_font", R.string.style_candidate_font, StyleFieldType.TEXT),
                StyleField("comment_font", R.string.style_comment_font, StyleFieldType.TEXT),
                StyleField("key_font", R.string.style_key_font, StyleFieldType.TEXT),
                StyleField("label_font", R.string.style_label_font, StyleFieldType.TEXT),
                StyleField("latin_font", R.string.style_latin_font, StyleFieldType.TEXT),
                StyleField("symbol_font", R.string.style_symbol_font, StyleFieldType.TEXT),
                StyleField("text_font", R.string.style_text_font, StyleFieldType.TEXT),
                StyleField("hanb_font", R.string.style_hanb_font, StyleFieldType.TEXT),
                StyleField("popup_font", R.string.style_popup_font, StyleFieldType.TEXT),
            ),
        ),
        StyleGroup(
            R.string.style_group_behavior,
            listOf(
                StyleField("auto_caps", R.string.style_auto_caps, StyleFieldType.BOOL),
                StyleField("reset_ascii_mode_on_focus_change", R.string.style_reset_ascii_mode, StyleFieldType.BOOL),
                // The values are numbered in the model (0..3), so the list
                // needs labels of its own; the raw numbers mean nothing.
                StyleField(
                    "enter_label_mode",
                    R.string.style_enter_label_mode,
                    StyleFieldType.ENUM,
                    options = listOf("0", "1", "2", "3"),
                    optionLabels =
                    listOf(
                        R.string.style_enter_label_mode_0,
                        R.string.style_enter_label_mode_1,
                        R.string.style_enter_label_mode_2,
                        R.string.style_enter_label_mode_3,
                    ),
                ),
            ),
        ),
    )
