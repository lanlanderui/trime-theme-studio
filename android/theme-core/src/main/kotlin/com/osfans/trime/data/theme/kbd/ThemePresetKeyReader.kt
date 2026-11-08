/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import java.io.File

/**
 * Reads the `preset_keys:` section as written, keeping every entry on its line.
 *
 * The keyboard reader answers "what does this layout contain"; this one answers
 * "what does this dictionary contain". They share the flow-map parsing, not the
 * shape: a preset key is a single `id: {…}` line with no nesting to walk.
 */
object ThemePresetKeyReader {
    private const val SECTION = "preset_keys"

    fun read(file: File): RawPresetSection? = runCatching { read(file.readLines()) }.getOrNull()

    fun read(lines: List<String>): RawPresetSection? {
        val range = sectionRange(lines) ?: return null
        var itemIndent = -1
        val keys = mutableListOf<RawPresetKey>()

        for (i in range) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            val content = line.substring(indent).trimEnd()
            if (content.startsWith("#")) continue

            val colon = content.indexOf(':')
            if (colon <= 0) continue
            val id = content.substring(0, colon).trim()
            val rest = content.substring(colon + 1).trim()
            // A block-style entry (`id:` then indented fields) is not a line we
            // can patch; the shipped themes never use it, so it is skipped
            // rather than reported as unsupported.
            if (!rest.startsWith("{")) continue

            val split = KeyboardYaml.splitComment(rest)
            val entries = KeyboardYaml.parseFlowMap(split.first.trim()) ?: continue
            if (itemIndent < 0) itemIndent = indent
            keys += RawPresetKey(id, entries, split.second, content, i)
        }

        if (keys.isEmpty()) return null
        return RawPresetSection(range, if (itemIndent < 0) 2 else itemIndent, keys)
    }

    /** The `preset_keys:` block, or null when the file has none. */
    private fun sectionRange(lines: List<String>): IntRange? {
        val start = lines.indexOfFirst { it.startsWith("$SECTION:") }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (line.first().isWhitespace()) continue
            if (line.startsWith("#")) continue
            end = i
            break
        }
        if (end <= start + 1) return null
        return (start + 1) until end
    }
}
