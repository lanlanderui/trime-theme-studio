/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.scalar

import com.osfans.trime.data.theme.ThemeTextFile
import java.io.File
import com.osfans.trime.data.theme.ThemeLog as Timber

/**
 * Writes one leaf of a nested scalar section such as `preedit` or `window`.
 *
 * The section is a tree, so a write can mean four different things, and they
 * are not interchangeable:
 *
 * | the file has | the write does |
 * |---|---|
 * | a live line | replace its value, keep indent and trailing comment |
 * | only a commented line (`#  corner_radius: 0`) | turn it into a live line |
 * | no line, but the parent group | insert it under that group |
 * | no line and no parent group, maybe not even the section | build the chain |
 *
 * The third row is where a naive writer breaks a theme: appending `vertical: 4`
 * at the wrong indent makes it a sibling of `item_padding` instead of a child,
 * which silently changes a different setting.
 */
object ThemeScalarWriter {
    /**
     * Sets [path] inside [section] to [value] (an already YAML-formatted
     * scalar).
     *
     * Nothing outside the edited place changes: the rest of the file keeps its
     * bytes, comments, blank lines and anchors, because this edits one line of
     * the document instead of regenerating it from a parsed model.
     *
     * @return whether the file was written.
     */
    fun set(
        file: File,
        section: String,
        path: List<String>,
        value: String,
    ): Boolean {
        if (path.isEmpty()) return false
        val document = ThemeTextFile.read(file) ?: return false
        val lines = document.lines
        val range = ThemeScalarReader.sectionRange(lines, section)

        if (range == null) {
            appendSection(lines, document.endsWithNewline, section, path, value)
        } else {
            val nodes = ThemeScalarReader.nodes(lines, range)
            val leaf = nodes.lastOrNull { !it.group && it.path == path }
            // Same reason as the toolbar: a value written back unchanged must
            // not re-quote the author's line.
            if (leaf != null && !leaf.commented && leaf.value == value) return true
            if (leaf != null) {
                // A commented line is a default the author pinned, so writing to
                // it means enabling it: same line, same indent, same comment,
                // minus the `#`.
                lines[leaf.index] = render(leaf.indent, path.last(), value, leaf.comment)
            } else {
                insertLeaf(lines, nodes, range.first, path, value)
            }
        }

        return ThemeTextFile.write(file, document, lines).also { written ->
            if (!written) Timber.w("Cannot write %s.%s to %s", section, path.joinToString("."), file.name)
        }
    }

    /**
     * Puts [path] back to "whatever the runtime defaults to" by commenting the
     * line out, the way the theme itself says it.
     *
     * Not by writing the default value: a pinned `corner_radius: 0` keeps
     * overriding the built-in default, so a later change to that default would
     * never reach this theme. Commenting leaves the runtime on its own default
     * **and** keeps the value that was there — tapping the row again shows it in
     * the box, so a reset can be undone by typing it back.
     *
     * The line stays where it is — only a `#` goes in front of it, which is
     * also how the reader measures indentation (spaces before `#` plus spaces
     * after it), so a commented line keeps belonging to the same group.
     *
     * @return false only when the file could not be written; a line that is
     * already commented, or that the file does not have, counts as done.
     */
    fun reset(
        file: File,
        section: String,
        path: List<String>,
    ): Boolean {
        if (path.isEmpty()) return false
        val document = ThemeTextFile.read(file) ?: return false
        val lines = document.lines
        val range = ThemeScalarReader.sectionRange(lines, section) ?: return true
        val leaf = ThemeScalarReader.nodes(lines, range).lastOrNull { !it.group && it.path == path } ?: return true
        if (leaf.commented) return true

        // `#` in front of the line: the spaces that indented the key become the
        // spaces after `#`, which is exactly how the reader expects to find a
        // commented entry.
        lines[leaf.index] = "#" + lines[leaf.index]
        return ThemeTextFile.write(file, document, lines).also { written ->
            if (!written) Timber.w("Cannot comment out %s.%s in %s", section, path.joinToString("."), file.name)
        }
    }

    /** One `key: value # comment` line, indented. */
    private fun render(
        indent: Int,
        key: String,
        value: String,
        comment: String,
    ): String = " ".repeat(indent) + "$key: $value" + comment

    /**
     * Inserts a leaf that the file does not mention, creating the groups above
     * it along the way.
     *
     * The anchor is the last line of the deepest group that does exist, so a new
     * key lands among its siblings rather than at the end of the section.
     */
    private fun insertLeaf(
        lines: MutableList<String>,
        nodes: List<ThemeScalarReader.Node>,
        sectionStart: Int,
        path: List<String>,
        value: String,
    ) {
        val step = stepOf(nodes)
        val groups = path.dropLast(1)
        // Longest prefix of the group chain that the file already has.
        var known = groups.size
        while (known > 0 && nodes.none { it.group && it.path == groups.take(known) }) known--

        val anchor = nodes.lastOrNull { known == 0 || it.path.take(known) == groups.take(known) }
        val indent =
            if (known == 0) {
                // Top level of the section: line up with whatever is already there.
                nodes.firstOrNull { it.path.size == 1 }?.indent ?: step
            } else {
                val parent = nodes.last { it.group && it.path == groups.take(known) }
                parent.indent + step
            }

        val created = mutableListOf<String>()
        var nested = indent
        for (key in groups.drop(known)) {
            created += " ".repeat(nested) + "$key:"
            nested += step
        }
        created += render(nested, path.last(), value, "")

        // After the anchor when there is one; otherwise right under the header.
        val at = (anchor?.index ?: (sectionStart - 1)) + 1
        lines.addAll(at, created)
    }

    /**
     * Builds a whole section for a file that has none.
     *
     * It goes at the end of the document: top-level order carries no meaning in
     * YAML, and guessing a "right" neighbourhood would put a new section in the
     * middle of another one the moment a theme reorders its keys.
     */
    private fun appendSection(
        lines: MutableList<String>,
        endsWithNewline: Boolean,
        section: String,
        path: List<String>,
        value: String,
    ) {
        // `split('\n')` leaves a trailing empty element for the final newline;
        // it is not a blank line and must not become one.
        if (endsWithNewline && lines.lastOrNull()?.isEmpty() == true) lines.removeAt(lines.lastIndex)
        if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""

        lines += "$section:"
        var indent = STEP
        for (key in path.dropLast(1)) {
            lines += " ".repeat(indent) + "$key:"
            indent += STEP
        }
        lines += render(indent, path.last(), value, "")
    }

    /**
     * How far children are indented below their group, as the file already
     * writes it; two when there is nothing to learn from.
     */
    private fun stepOf(nodes: List<ThemeScalarReader.Node>): Int {
        val group = nodes.firstOrNull { it.group } ?: return STEP
        val child = nodes.firstOrNull { it.path.size == group.path.size + 1 && it.indent > group.indent } ?: return STEP
        return (child.indent - group.indent).takeIf { it in 1..8 } ?: STEP
    }

    private const val STEP = 2
}
