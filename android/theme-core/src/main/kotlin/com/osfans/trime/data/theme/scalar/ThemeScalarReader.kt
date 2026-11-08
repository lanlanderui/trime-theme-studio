/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.scalar

import com.osfans.trime.data.theme.kbd.KeyboardYaml
import java.io.File

/**
 * Reads a nested scalar section such as `preedit` or `window`.
 *
 * Two things make this different from reading a keyboard layout:
 *
 * - A leaf is addressed by its **path**, because the section is a tree
 *   (`window → insets → vertical`), not a list of one-line maps.
 * - **Commented-out lines are read too.** Themes keep their defaults as
 *   `#  corner_radius: 0`; ignoring those lines would make the editor think the
 *   field was never mentioned, when in fact the author pinned it to a default.
 *
 * Writing needs the same judgement of "is this line a group, a leaf, or a
 * commented leaf", so the line walk is exposed to [ThemeScalarWriter] rather
 * than written twice: an editor that inserts a key where the reader cannot see
 * it would look like it silently did nothing.
 */
object ThemeScalarReader {
    fun read(
        file: File,
        name: String,
    ): RawScalarSection = runCatching { read(file.readLines(), name) }
        .getOrElse { RawScalarSection(name, null, emptyList()) }

    fun read(
        lines: List<String>,
        name: String,
    ): RawScalarSection {
        val range = sectionRange(lines, name) ?: return RawScalarSection(name, null, emptyList())
        val leaves =
            nodes(lines, range)
                .filterNot { it.group }
                .map {
                    ScalarLeaf(
                        path = it.path,
                        value = it.value,
                        line = it.index,
                        indent = it.indent,
                        comment = it.comment,
                        commented = it.commented,
                    )
                }
        return RawScalarSection(name, range, leaves)
    }

    /**
     * One parsed line of a section, with the path that leads to it.
     *
     * [group] is true for a line that only opens a nested mapping
     * (`foreground:`), which is what makes a line either a place to write a
     * value or a place to hang children under.
     */
    internal data class Node(
        val index: Int,
        val path: List<String>,
        val indent: Int,
        val value: String?,
        val comment: String,
        val commented: Boolean,
        val group: Boolean,
    )

    /** Index range of the lines inside the top-level [name] block. */
    internal fun sectionRange(
        lines: List<String>,
        name: String,
    ): IntRange? {
        val start = lines.indexOfFirst { it.startsWith("$name:") }
        if (start < 0) return null

        var end = lines.size
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (line.first().isWhitespace()) continue
            // A comment at column 0 still belongs to the section: themes put
            // commented defaults anywhere, including at the outer indent.
            if (line.trimStart().startsWith("#")) continue
            end = i
            break
        }
        return start + 1..(end - 1)
    }

    /** Every line of [range], parsed and located by path. */
    internal fun nodes(
        lines: List<String>,
        range: IntRange,
    ): List<Node> {
        val nodes = mutableListOf<Node>()
        // (indent, key) of the groups we are currently inside.
        val stack = mutableListOf<Pair<Int, String>>()

        for (i in range) {
            val parsed = parseEntry(lines[i]) ?: continue
            // removeAt, not removeLast: the latter is a List method only from
            // API 35, and this has to run on minSdk 21.
            while (stack.isNotEmpty() && stack.last().first >= parsed.indent) stack.removeAt(stack.lastIndex)
            val path = stack.map { it.second } + parsed.key

            val group = parsed.value == null && !parsed.commented
            if (group) stack += parsed.indent to parsed.key
            nodes +=
                Node(
                    index = i,
                    path = path,
                    indent = parsed.indent,
                    value = parsed.value,
                    comment = parsed.comment,
                    commented = parsed.commented,
                    group = group,
                )
        }
        return nodes
    }

    private data class Entry(
        val key: String,
        val value: String?,
        val indent: Int,
        val comment: String,
        val commented: Boolean,
    )

    /**
     * One line as `key: value # comment`, live or commented.
     *
     * For a commented line the effective indent is `spaces before # + spaces
     * after #`: the `#` itself takes a column that the author did not count, so
     * `#    vertical:` lines up with a live `    horizontal:` sibling.
     */
    private fun parseEntry(line: String): Entry? {
        if (line.isBlank()) return null
        val leading = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        val body = line.substring(leading)
        if (body.startsWith("#")) {
            val afterHash = body.substring(1)
            val spaces = afterHash.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) return null else it }
            val entry = parseKeyValue(afterHash.substring(spaces))
            return entry?.copy(indent = leading + spaces, commented = true)
        }
        val entry = parseKeyValue(body) ?: return null
        return entry.copy(indent = leading)
    }

    private fun parseKeyValue(text: String): Entry? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val key = text.substring(0, colon).trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) return null
        val rest = text.substring(colon + 1).trimEnd()
        val split = KeyboardYaml.splitComment(rest)
        val value = split.first.trim()
        return Entry(
            key = key,
            value = value.ifEmpty { null },
            indent = 0,
            comment = split.second,
            commented = false,
        )
    }
}
