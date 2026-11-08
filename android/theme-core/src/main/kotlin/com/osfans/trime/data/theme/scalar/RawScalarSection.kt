/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.scalar

/**
 * One `path: value` leaf of a scalar section, as the file wrote it.
 *
 * Sections like `preedit` and `window` are nested scalar trees rather than flow
 * maps, so a leaf is located by its path, not by a line number: `window →
 * insets → vertical`. The line number is kept for writing.
 */
data class ScalarLeaf(
    val path: List<String>,
    /** The value as written, or null when the line is commented out. */
    val value: String?,
    val line: Int,
    val indent: Int,
    /** Trailing comment, with the whitespace that separated it. */
    val comment: String,
    /**
     * True when the author wrote the line commented out.
     *
     * That is how a theme says "leave this at the default": the value is still
     * there to read, but the runtime never sees it. Editing such a leaf means
     * enabling it, which is a different write than changing a live one.
     */
    val commented: Boolean = false,
) {
    val key: String get() = path.last()
}

/**
 * One scalar section: its line range (null when the file has no such section)
 * and every leaf found inside, live and commented alike.
 */
data class RawScalarSection(
    val name: String,
    val range: IntRange?,
    val leaves: List<ScalarLeaf>,
) {
    fun leaf(path: List<String>): ScalarLeaf? = leaves.firstOrNull { it.path == path }

    /** Paths that the file does not mention at all. */
    fun missing(paths: List<List<String>>): List<List<String>> = paths.filter { leaf(it) == null }
}
