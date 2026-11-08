/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.kbd.KeyboardYaml

/**
 * Reads a theme's display name without expanding it.
 *
 * Listing a directory of themes needs one string per file, and the honest way to
 * get it — expand the DSL, then decode — reads and re-serialises every theme just
 * to print a label. Hand-written themes put `name:` near the top, so scanning the
 * first lines answers the question for all of them and the expensive path stays
 * for the exceptions (`name` supplied by an `__include`, or no name at all).
 *
 * Only a **column-0** `name:` counts: nested ones belong to a colour scheme or a
 * layout, and returning one of those would put a scheme's name on the theme card.
 */
object ThemeNameReader {
    /** How many lines the fast path looks at; both shipped themes answer by 13. */
    const val SCAN_LINES = 40

    /**
     * The theme's name from [lines], or null when the first [SCAN_LINES] lines do
     * not declare one — the caller then has to expand the theme.
     */
    fun fastName(lines: List<String>): String? {
        for (line in lines.take(SCAN_LINES)) {
            if (!line.startsWith("name:")) continue
            val raw = line.substringAfter(':')
            val value = KeyboardYaml.splitComment(raw).first.trim()
            val name = unquote(value)
            if (name.isNotEmpty()) return name
        }
        return null
    }

    /** Strips one layer of quoting and the trailing whitespace a theme may keep. */
    private fun unquote(raw: String): String {
        if (raw.length < 2) return raw.trim()
        return when {
            raw.first() == '\'' && raw.last() == '\'' -> raw.substring(1, raw.length - 1).replace("''", "'")
            raw.first() == '"' && raw.last() == '"' -> raw.substring(1, raw.length - 1)
            else -> raw.trim()
        }
    }
}
