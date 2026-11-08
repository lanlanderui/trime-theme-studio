/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import com.osfans.trime.data.theme.ThemeSchemeWriter
import java.io.File

/**
 * Writes a Material You light/dark pair into a theme file.
 *
 * The two ids are fixed on purpose. A theme can declare a link
 * (`dark_scheme: material_you_dark`), and a key can run
 * `set_color_scheme: material_you`; both break silently if regeneration starts
 * inventing new ids.
 *
 * Both blocks land in one write: writing the light one and then failing on the
 * dark one would leave the keyboard in a palette that has no counterpart to
 * switch to at night.
 */
object MaterialYouWriter {
    const val LIGHT_ID = "material_you"
    const val DARK_ID = "material_you_dark"

    /** Marks the blocks as generated, so a hand-written scheme stays distinguishable. */
    const val AUTHOR = "Material You"

    /** The palettes the wallpaper publishes; null below Android 12. */
    fun fromPalettes(palettes: MaterialPalettes): MaterialYouColors = MaterialScheme.colors(palettes)

    /** Palettes grown from one accent; works on every supported version. */
    fun fromSeed(seed: Int): MaterialYouColors = MaterialScheme.colors(MaterialPalettes.fromSeed(seed))

    /**
     * The two blocks, in the order the table lists the keys.
     *
     * `name`, `author` and the day/night link come first: they are what makes
     * the block a scheme rather than a bag of colours, and putting them at the
     * top keeps a regenerated block readable next to the template's own.
     */
    fun entries(
        colors: MaterialYouColors,
        lightName: String,
        darkName: String,
    ): Map<String, Map<String, String>> = linkedMapOf(
        LIGHT_ID to
            linkedMapOf(
                "name" to lightName,
                "author" to AUTHOR,
                "dark_scheme" to DARK_ID,
            ).apply { putAll(colors.light) },
        DARK_ID to
            linkedMapOf(
                "name" to darkName,
                "author" to AUTHOR,
                "light_scheme" to LIGHT_ID,
            ).apply { putAll(colors.dark) },
    )

    /**
     * Replaces both blocks in [file], creating them when the theme has none.
     *
     * [rewrite] rather than a patch: the blocks are ours from top to bottom, so
     * keys an older build wrote go away instead of surviving as rows nothing
     * resolves, and the order matches the table's — which is the template's.
     */
    fun write(
        file: File,
        colors: MaterialYouColors,
        lightName: String,
        darkName: String,
        expectedText: String? = null,
    ): ThemeSchemeWriter.SaveResult = ThemeSchemeWriter.setSchemesChecked(
        file = file,
        schemes = entries(colors, lightName, darkName),
        create = true,
        rewrite = true,
        expectedText = expectedText,
    )

    /**
     * What the theme currently holds, or null when it has no complete pair.
     *
     * A half-written pair — one block present, the other missing — is reported
     * as absent so regenerating replaces it instead of editing around it.
     */
    fun readCurrent(file: File): MaterialYouColors? {
        val light = ThemeSchemeWriter.readScheme(file, LIGHT_ID).filterKeys { it in MaterialScheme.keys }
        val dark = ThemeSchemeWriter.readScheme(file, DARK_ID).filterKeys { it in MaterialScheme.keys }
        if (light.size < MaterialScheme.keys.size || dark.size < MaterialScheme.keys.size) return null
        return MaterialYouColors(light, dark)
    }
}
