/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

/**
 * One `preset_keyboards` entry as it is written in the theme file.
 *
 * This is the file view, not the runtime one: an entry that only declares
 * `__include` has no keys here even though [com.osfans.trime.data.theme.Theme]
 * resolves it to a full layout. Keeping both views apart is what lets the
 * editor say "this is an alias" instead of silently editing the wrong block.
 */
data class RawKeyboard(
    val id: String,
    /** Target of `__include`, already reduced to the layout id (`default`). */
    val includeTarget: String?,
    /** Layout-level scalars (`name`, `width`, `height`, …), comment-stripped. */
    val scalars: Map<String, String>,
    val keys: List<RawKey>,
    /** Indentation of this block's `keys:` and of its `- ` items, when it has any. */
    val indent: Indent?,
    /** Line range of the block inside the file, `[start, end]` inclusive. */
    val range: IntRange,
    /** Why this block cannot be edited safely, or null when it can. */
    val unsupported: String?,
) {
    val isAlias: Boolean get() = includeTarget != null && keys.isEmpty()

    val displayName: String get() = scalars["name"]?.takeIf { it.isNotBlank() } ?: id
}

/** One key line: the parsed flow map plus the trailing comment, kept verbatim. */
data class RawKey(
    val entries: List<KeyboardYaml.Entry>,
    /** The part from the first ` #` on, including its leading whitespace. */
    val comment: String,
    val sourceLine: String,
    /** Zero-based line index in the file, so the writer can splice by line. */
    val line: Int = 0,
) {
    operator fun get(name: String): String? = entries.firstOrNull { it.key == name }?.value
}

/** Column offsets used by one block; both are zero-based space counts. */
data class Indent(
    val keys: Int,
    val item: Int,
)
