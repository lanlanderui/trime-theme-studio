/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.ThemeDirectory
import java.io.File

/**
 * Finds layouts a `<theme>.custom.yaml` is likely to override.
 *
 * A patch is a merge tree, and re-implementing its evaluation here would mean
 * two answers to "what does this theme actually say". Looking for the layout
 * id under `preset_keyboards/` is enough for the one job this has: telling the
 * user their edit may be shadowed before they spend time on it.
 */
object ThemePatchDetector {
    fun patchedLayouts(
        themeId: String,
        ids: Set<String>,
    ): Set<String> {
        val patch = patchText(themeId) ?: return emptySet()
        if (!patch.contains("preset_keyboards")) return emptySet()
        return ids.filterTo(mutableSetOf()) { patch.contains("preset_keyboards/$it") }
    }

    fun isPatched(
        themeId: String,
        id: String,
    ): Boolean = patchedLayouts(themeId, setOf(id)).isNotEmpty()

    private fun patchText(themeId: String): String? = ThemeDirectory.roots()
        .map { File(it, "$themeId.custom.yaml") }
        .firstOrNull { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
}
