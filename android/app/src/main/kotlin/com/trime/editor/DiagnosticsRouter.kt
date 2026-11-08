/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

/** The screens a diagnostics finding can lead to. */
enum class EditorScreen {
    STYLE,
    COLORS,
    KEYBOARD_LIST,
    KEYBOARD_EDITOR,
    PRESET_KEYS,
    TOOL_BAR,
    CANDIDATE_WINDOW,

    /** Nothing owns this path; the row still says what is wrong. */
    NONE,
}

/**
 * The screen that fixes one finding, and the entry to single out there.
 *
 * [id] is filled only for screens that open one entry: a keyboard id opens that
 * keyboard, a preset-key id becomes the list's search text. Colour schemes and
 * the two scalar sections have no such entry point, so their second segment is
 * dropped here rather than handed to a screen that would ignore it.
 */
data class DiagnosticsRoute(
    val screen: EditorScreen,
    val id: String = "",
)

/**
 * Maps a finding's path to the screen that can fix it.
 *
 * A path is `section[/id[/key...]]`, and the first segment is a top-level key
 * of the theme file — which is exactly what decides the screen. Keeping that
 * mapping in a pure object instead of a `when` over strings inside the activity
 * buys two things: it can be tested on the JVM, and the activity switches on
 * [EditorScreen], so a screen added to the enum fails to compile until it is
 * mapped here. With strings, a new section silently became a dead tap.
 *
 * Paths the checks emit are not always rooted at a section: an unknown
 * top-level key is reported as `/key` (see `ThemeDiagnostics`), the slash
 * standing for "document root". Empty segments are dropped for that reason, so
 * a leading slash can never hide the section that follows it.
 */
object DiagnosticsRouter {
    fun route(path: String?): DiagnosticsRoute {
        val segments = path.orEmpty().split('/').filter { it.isNotEmpty() }
        val section = segments.firstOrNull().orEmpty()
        val id = segments.getOrNull(1).orEmpty()
        return when (section) {
            "style" -> DiagnosticsRoute(EditorScreen.STYLE)

            "preset_color_schemes", "fallback_colors", "colors" -> DiagnosticsRoute(EditorScreen.COLORS)

            "preset_keyboards" ->
                if (id.isEmpty()) {
                    // The finding is about the section as a whole (`no 'default'
                    // keyboard`), so there is no keyboard to open.
                    DiagnosticsRoute(EditorScreen.KEYBOARD_LIST)
                } else {
                    DiagnosticsRoute(EditorScreen.KEYBOARD_EDITOR, id)
                }

            "preset_keys" -> DiagnosticsRoute(EditorScreen.PRESET_KEYS, id)

            "tool_bar" -> DiagnosticsRoute(EditorScreen.TOOL_BAR)

            "window", "preedit" -> DiagnosticsRoute(EditorScreen.CANDIDATE_WINDOW)

            else -> DiagnosticsRoute(EditorScreen.NONE)
        }
    }
}
