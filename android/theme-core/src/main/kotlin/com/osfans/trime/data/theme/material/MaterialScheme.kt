/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

/** One material scheme in both modes, as the theme file spells colours. */
data class MaterialYouColors(
    val light: Map<String, String>,
    val dark: Map<String, String>,
) {
    companion object {
        val EMPTY = MaterialYouColors(emptyMap(), emptyMap())
    }
}

/**
 * Which colour of the palette each theme key paints with.
 *
 * The key list is the *full* set a scheme can paint: the same 54 keys the
 * template's sample scheme defines — every [com.osfans.trime.data.theme.ColorKey]
 * plus the two the theme layer reads by name (`navbar_background`,
 * `normal_mode_color`). A scheme that covered only some of them would leave the
 * rest at whatever `fallback_colors` says while everything else moved with the
 * wallpaper, which reads as a rendering bug; a key the theme never reads would
 * be dead weight in every regenerated file. `TemplateThemeTest` holds this list
 * and the template together, so the two cannot drift.
 *
 * Theme-defined palette *aliases* (`bbs`, `tkg`, …) are deliberately absent:
 * they belong to the theme that declares them, and the sample template is
 * dropping them in favour of the standard keys. [retiredKeys] lists the ones
 * earlier builds wrote, so a regenerated block loses them instead of carrying
 * rows nothing resolves any more.
 *
 * Tones follow Material's own role mapping: surfaces are 99/10, containers
 * 90/30, accents 40/80, and anything that sits *on* a colour takes the far end
 * of the scale so the pair stays legible in both modes.
 */
object MaterialScheme {
    /** Fully opaque; only the shadow asks for anything else. */
    private const val OPAQUE = MaterialPalettes.OPAQUE

    data class Slot(
        val key: String,
        val role: PaletteRole,
        val lightTone: Int,
        val darkTone: Int,
        val lightAlpha: Int = OPAQUE,
        val darkAlpha: Int = OPAQUE,
    )

    private fun slot(
        key: String,
        role: PaletteRole,
        light: Int,
        dark: Int,
        lightAlpha: Int = OPAQUE,
        darkAlpha: Int = OPAQUE,
    ) = Slot(key, role, light, dark, lightAlpha, darkAlpha)

    /** The order the template lists them in, so a generated block reads the same. */
    val slots: List<Slot> =
        listOf(
            // 整体
            slot("back_color", PaletteRole.NEUTRAL, 99, 10),
            slot("border_color", PaletteRole.NEUTRAL_VARIANT, 80, 30),
            // tone 0 is black in every palette, so this is "black at N%".
            slot("shadow_color", PaletteRole.NEUTRAL, 0, 0, lightAlpha = 0x33, darkAlpha = 0x66),
            slot("root_background", PaletteRole.NEUTRAL, 99, 10),
            slot("keyboard_back_color", PaletteRole.NEUTRAL, 99, 10),
            slot("keyboard_background", PaletteRole.NEUTRAL, 99, 10),
            slot("liquid_keyboard_background", PaletteRole.NEUTRAL, 99, 10),
            // 候选栏
            slot("candidate_background", PaletteRole.NEUTRAL, 99, 10),
            slot("candidate_border_color", PaletteRole.NEUTRAL_VARIANT, 80, 30),
            slot("candidate_separator_color", PaletteRole.NEUTRAL_VARIANT, 80, 30),
            slot("candidate_text_color", PaletteRole.NEUTRAL, 10, 90),
            slot("comment_text_color", PaletteRole.NEUTRAL_VARIANT, 30, 80),
            slot("hilited_text_color", PaletteRole.PRIMARY, 40, 80),
            slot("hilited_back_color", PaletteRole.PRIMARY, 90, 30),
            slot("hilited_candidate_back_color", PaletteRole.PRIMARY, 90, 30),
            slot("hilited_candidate_text_color", PaletteRole.PRIMARY, 10, 90),
            slot("hilited_candidate_button_color", PaletteRole.PRIMARY, 40, 80),
            slot("hilited_label_color", PaletteRole.PRIMARY, 10, 90),
            slot("hilited_comment_text_color", PaletteRole.PRIMARY, 10, 90),
            // 编码区
            slot("text_color", PaletteRole.PRIMARY, 40, 80),
            slot("text_back_color", PaletteRole.NEUTRAL, 95, 20),
            // 普通键
            slot("key_back_color", PaletteRole.NEUTRAL, 95, 20),
            slot("key_text_color", PaletteRole.NEUTRAL, 10, 90),
            slot("key_symbol_color", PaletteRole.NEUTRAL_VARIANT, 30, 80),
            slot("key_border_color", PaletteRole.NEUTRAL_VARIANT, 80, 30),
            slot("label_color", PaletteRole.PRIMARY, 40, 80),
            // 功能键，未按下：次要色容器区分于普通键
            slot("off_key_back_color", PaletteRole.SECONDARY, 90, 30),
            slot("off_key_text_color", PaletteRole.SECONDARY, 10, 90),
            slot("off_key_symbol_color", PaletteRole.SECONDARY, 30, 80),
            slot("off_key_border_color", PaletteRole.NEUTRAL_VARIANT, 80, 30),
            // 功能键，开启态：实心 primary，文字/符号落到 on 色
            slot("on_key_back_color", PaletteRole.PRIMARY, 40, 80),
            slot("on_key_text_color", PaletteRole.PRIMARY, 100, 20),
            slot("on_key_symbol_color", PaletteRole.PRIMARY, 95, 25),
            slot("on_key_border_color", PaletteRole.NEUTRAL_VARIANT, 80, 30),
            // 按下：三种键都收到同一套「按下色」上，边框与填充同色即视觉上消失
            slot("hilited_key_back_color", PaletteRole.PRIMARY, 40, 80),
            slot("hilited_key_text_color", PaletteRole.PRIMARY, 100, 20),
            slot("hilited_key_symbol_color", PaletteRole.PRIMARY, 95, 25),
            slot("hilited_key_border_color", PaletteRole.PRIMARY, 40, 80),
            slot("hilited_off_key_back_color", PaletteRole.SECONDARY, 40, 80),
            slot("hilited_off_key_text_color", PaletteRole.SECONDARY, 100, 20),
            slot("hilited_off_key_symbol_color", PaletteRole.SECONDARY, 95, 25),
            slot("hilited_off_key_border_color", PaletteRole.SECONDARY, 40, 80),
            slot("hilited_on_key_back_color", PaletteRole.PRIMARY, 40, 80),
            slot("hilited_on_key_text_color", PaletteRole.PRIMARY, 100, 20),
            slot("hilited_on_key_symbol_color", PaletteRole.PRIMARY, 95, 25),
            slot("hilited_on_key_border_color", PaletteRole.PRIMARY, 40, 80),
            // 按键气泡
            slot("popup_back_color", PaletteRole.NEUTRAL, 95, 20),
            slot("popup_text_color", PaletteRole.NEUTRAL, 10, 90),
            slot("hilited_popup_back_color", PaletteRole.PRIMARY, 90, 30),
            slot("hilited_popup_text_color", PaletteRole.PRIMARY, 10, 90),
            // 长文本键
            slot("long_text_color", PaletteRole.NEUTRAL, 10, 90),
            slot("long_text_back_color", PaletteRole.NEUTRAL, 95, 20),
            // 界面
            slot("navbar_background", PaletteRole.NEUTRAL, 99, 10),
            slot("normal_mode_color", PaletteRole.PRIMARY, 40, 80),
        )

    /** Every key a generated scheme defines, in the order the table lists them. */
    val keys: List<String> = slots.map { it.key }

    /**
     * Palette aliases earlier builds wrote into `material_you`.
     *
     * They were the template's own shorthand for space, enter, 123 and shift,
     * and a generated block used to define them so those keys followed the
     * wallpaper too. The template now paints those keys with the standard
     * entries below, so the aliases are not ours to paint: [keys] no longer
     * contains them, and a rewritten block is sized exactly like the template's
     * sample scheme. The set is kept as the explicit "never again" list the
     * tests assert against — a regression here would be invisible on screen
     * until someone edited the theme and found rows nothing resolves.
     */
    val retiredKeys: Set<String> = setOf("bkg", "tkg", "benter", "tenter", "bgn", "tgn", "bbs", "tbs")

    /** `0xrrggbb`, the spelling the theme file uses. */
    fun literal(argb: Int): String = MaterialPalettes.literal(argb)

    fun colors(palettes: MaterialPalettes): MaterialYouColors = MaterialYouColors(
        light = slots.associate { it.key to palettes.literal(it.role, it.lightTone, it.lightAlpha) },
        dark = slots.associate { it.key to palettes.literal(it.role, it.darkTone, it.darkAlpha) },
    )
}
