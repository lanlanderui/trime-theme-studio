/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

/**
 * One entry of the `preset_keys:` section, kept as the file wrote it.
 *
 * Entries are single-line flow maps in every theme seen so far
 * (`BackSpace: {label: 退格, send: BackSpace}`), which is what makes editing
 * them a line patch rather than a re-serialization.
 */
data class RawPresetKey(
    val id: String,
    val entries: List<KeyboardYaml.Entry>,
    /** The trailing comment with its leading whitespace, or empty. */
    val comment: String,
    val sourceLine: String,
    /** Zero-based line index, so the writer can patch this line alone. */
    val line: Int,
) {
    operator fun get(name: String): String? = entries.firstOrNull { it.key == name }?.value
}

/** The whole `preset_keys:` section, in file order. */
data class RawPresetSection(
    val range: IntRange,
    /** Indent of one entry's `id:` — two spaces in the shipped theme. */
    val itemIndent: Int,
    val keys: List<RawPresetKey>,
)
