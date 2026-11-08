/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.data.theme.kbd.KeyboardYaml
import java.io.File
import com.osfans.trime.data.theme.ThemeLog as Timber

/**
 * Writes the `tool_bar:` block back to the theme file, one line at a time.
 *
 * Three operations cover the whole editor:
 *
 * - [setScalar] — a key directly under `tool_bar` (`button_spacing`,
 *   `back_style`, `button_font`).
 * - [setBlockField] — one leaf inside a button block, addressed by its path
 *   *within that block* (`foreground/style`). This is why the writer cannot
 *   delegate to [com.osfans.trime.data.theme.scalar.ThemeScalarWriter]: that one
 *   indexes leaves by absolute path, and every button block has its own
 *   `foreground/style`, so they collide.
 * - [setButtons] — the `buttons:` sequence, for reordering and removal.
 *
 * Everything else in the file is left byte-for-byte alone: comments, blank
 * lines, anchors, indentation and line endings all survive, because the theme
 * is meant to stay a readable, portable document rather than becoming a
 * serialiser's output. Nothing is inserted outside the edited block, and no
 * line is ever deleted — a field that is missing gets a line added, a field
 * that is present gets its value swapped.
 */
object ThemeToolBarWriter {
    sealed interface Result {
        data object Success : Result

        /** [reason] is shown to the user as-is; it names the file or the line. */
        data class Failure(
            val reason: String,
        ) : Result
    }

    /**
     * Writes a key that sits directly under `tool_bar:`.
     *
     * [value] must already be a YAML scalar ([ThemeStyleWriter.formatScalar]).
     */
    fun setScalar(
        file: File,
        key: String,
        value: String,
    ): Result {
        val document = ThemeTextFile.read(file) ?: return noFile(file)
        val lines = document.lines
        val raw = ThemeToolBarReader.read(lines) ?: return noSection(file)
        val leaf = raw.scalars[key]

        // Setting a value to what it already is is not an edit: rewriting the
        // line would re-quote it (`"ic@arrow-left"` → `ic@arrow-left`) and turn
        // a no-op into a diff. Compared unquoted, because the screen shows the
        // value without the file's quoting.
        if (leaf != null && !leaf.commented && KeyboardYaml.unquote(leaf.value.orEmpty()) == value) {
            return Result.Success
        }

        if (leaf != null && !leaf.commented) {
            lines[leaf.line] = line(leaf.indent, key, value, leaf.comment)
        } else if (leaf != null) {
            // A commented key is a value the runtime never sees; replacing the
            // line turns it on without leaving a duplicate behind.
            lines[leaf.line] = line(raw.childIndent, key, value, leaf.comment)
        } else {
            // An empty `tool_bar:` body still has a place to put the key: right
            // after the header, which is what `range.first` points at.
            val end = raw.range?.last ?: return noSection(file)
            val at = insertPoint(lines, end, raw.childIndent)
            lines.add(at, line(raw.childIndent, key, value, ""))
        }
        return write(file, document, lines, key)
    }

    /**
     * Writes one leaf inside the button block [blockId].
     *
     * [path] is relative to the block, e.g. `["foreground", "style"]`. Missing
     * parents are created so the line lands at a legal depth; the leaf itself
     * goes at the indent its live siblings use, not at an indent derived from
     * the file's `#`-prefixed defaults.
     */
    fun setBlockField(
        file: File,
        blockId: String,
        path: List<String>,
        value: String,
    ): Result {
        if (path.isEmpty()) return Result.Failure("字段路径为空")
        val document = ThemeTextFile.read(file) ?: return noFile(file)
        val lines = document.lines
        val raw = ThemeToolBarReader.read(lines) ?: return noSection(file)
        val block = raw.blocks[blockId]
            ?: return Result.Failure("主题里找不到按钮「$blockId」的定义")

        val indent = indentOf(raw, block, path)
        val live = block.leaves.lastOrNull { it.path == path && !it.commented }
        val commented = block.leaves.lastOrNull { it.path == path }

        if (live != null && KeyboardYaml.unquote(live.value.orEmpty()) == value) return Result.Success

        when {
            live != null -> lines[live.line] = line(live.indent, path.last(), value, live.comment)

            commented != null -> lines[commented.line] = line(indent, path.last(), value, commented.comment)

            else -> {
                // Append at the end of the block body: a dedent back to the new
                // leaf's column closes whatever group the last line opened, so
                // position inside the mapping does not matter.
                val at = insertPoint(lines, block.range.last, block.headerLine + 1)
                val created = mutableListOf<String>()
                missingGroups(block, path).forEach { depth ->
                    // A group at depth d holds keys one level deeper than the
                    // block's own keys, so `background` (d = 0) sits at
                    // childIndent + step × 1, not at childIndent.
                    val groupIndent = raw.childIndent + raw.indentStep * (depth + 1)
                    created += line(groupIndent, path[depth], "", "")
                }
                created += line(indent, path.last(), value, "")
                lines.addAll(at, created)
            }
        }
        return write(file, document, lines, "$blockId/${path.joinToString("/")}")
    }

    /**
     * Rewrites the `buttons:` sequence.
     *
     * [sources] holds each item as it should appear: `*hide_button` for an
     * alias, or the original text for an inline `{...}` map. Inline maps are
     * passed through untouched — re-rendering one would restyle a structure this
     * editor does not model.
     *
     * The new line is parsed back before anything is written: a list that does
     * not read back as the same number of items is refused rather than saved.
     */
    fun setButtons(
        file: File,
        sources: List<String>,
    ): Result {
        val document = ThemeTextFile.read(file) ?: return noFile(file)
        val lines = document.lines
        val raw = ThemeToolBarReader.read(lines) ?: return noSection(file)
        val at = raw.buttonsLine ?: return Result.Failure("主题里没有 buttons 列表")

        val text = KeyboardYaml.formatFlowSequence(sources, raw.buttonsSpaced)
        val parsed = KeyboardYaml.parseFlowSequence(text)
            ?: return Result.Failure("生成的 buttons 行无法解析，已放弃保存")
        if (parsed.size != sources.size) {
            return Result.Failure("生成的 buttons 行有 ${parsed.size} 项，应为 ${sources.size} 项")
        }
        if (parsed.map { it.trim() } != sources.map { it.trim() }) {
            return Result.Failure("生成的 buttons 行回读不一致，已放弃保存")
        }

        val (_, comment) = KeyboardYaml.splitComment(lines[at].substringAfter(':'))
        lines[at] = line(indentAt(lines[at]), "buttons", text, comment)
        return write(file, document, lines, "buttons")
    }

    /**
     * Removes a whole button block, `primary_button` included.
     *
     * Only offered for a block nothing points at. Refusing a referenced block
     * matters more than the convenience: the resulting theme would decode with
     * fewer buttons than its own `buttons` list claims, and `ToolBar.buttons`
     * would silently lose the last one rather than reporting an error.
     *
     * The trailing blank lines that separate blocks are kept; the header's own
     * comment above it is kept too — it is the file's, not the editor's.
     */
    fun removeBlock(
        file: File,
        blockId: String,
    ): Result {
        val document = ThemeTextFile.read(file) ?: return noFile(file)
        val lines = document.lines
        val raw = ThemeToolBarReader.read(lines) ?: return noSection(file)
        val block = raw.blocks[blockId] ?: return Result.Failure("主题里找不到按钮「$blockId」的定义")

        val references = raw.referenceCount(blockId)
        if (references > 0) {
            return Result.Failure("「$blockId」仍被 buttons 引用 $references 处，先把它从列表里删掉")
        }
        // Anchors are file-global, so an anchor that is aliased from another
        // section would break there too.
        val anchor = block.anchor
        if (anchor != null) {
            val usedElsewhere = lines.indices.any { it !in block.range && lines[it].contains("*$anchor") }
            if (usedElsewhere) return Result.Failure("锚点「&$anchor」在文件别处仍被引用，不能删除")
        }

        var end = block.range.last
        while (end > block.headerLine && lines[end].let { it.isBlank() || it.trimStart().startsWith("#") }) end--
        for (i in end downTo block.headerLine) lines.removeAt(i)

        val after = ThemeToolBarReader.read(lines)
            ?: return Result.Failure("删除后 tool_bar 段无法解析，已放弃保存")
        if (blockId in after.blocks) return Result.Failure("删除没有生效，已放弃保存")

        return write(file, document, lines, blockId)
    }

    /**
     * The indent a leaf at [path] should get inside [block].
     *
     * Taken from a live sibling in the same group when there is one, because
     * themes do not all use one indent step (`default` uses 2, some keyboard
     * blocks use 4). Falling back to `child_indent + step × depth` only matters
     * for a group that has no live children yet.
     */
    private fun indentOf(
        raw: RawToolBar,
        block: RawButtonBlock,
        path: List<String>,
    ): Int {
        val siblings = block.leaves.filter { !it.commented && it.path.dropLast(1) == path.dropLast(1) }
        return siblings.lastOrNull()?.indent ?: (raw.childIndent + raw.indentStep * path.size)
    }

    /** Depths of the parent groups that [path] needs but [block] does not have yet. */
    private fun missingGroups(
        block: RawButtonBlock,
        path: List<String>,
    ): List<Int> {
        val groups = path.dropLast(1)
        if (groups.isEmpty()) return emptyList()
        // A group exists when any leaf — even a commented one — sits below it:
        // a live `foreground:` header with only commented children is still a
        // header, and inserting a second one would create a duplicate key.
        var deepest = 0
        for (depth in 1..groups.size) {
            if (block.leaves.any { it.path.size > depth && it.path.take(depth) == groups.take(depth) }) deepest = depth
        }
        return (deepest until groups.size).toList()
    }

    /** Where a line added at [fallback] goes: after the last non-blank line. */
    private fun insertPoint(
        lines: List<String>,
        end: Int,
        floor: Int,
    ): Int {
        var at = end + 1
        while (at > floor && at <= lines.size && lines[at - 1].isBlank()) at--
        return at.coerceIn(floor, lines.size)
    }

    private fun line(
        indent: Int,
        key: String,
        value: String,
        comment: String,
    ): String {
        val body = if (value.isEmpty()) "$key:" else "$key: $value"
        return " ".repeat(indent.coerceAtLeast(0)) + body + comment
    }

    private fun indentAt(line: String): Int = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }

    private fun write(
        file: File,
        document: ThemeTextFile.Document,
        lines: List<String>,
        what: String,
    ): Result = if (ThemeTextFile.write(file, document, lines)) {
        Result.Success
    } else {
        Timber.w("Cannot write tool_bar %s to %s", what, file.name)
        Result.Failure("写入失败：${file.name} 可能已被其它程序修改，请退出后重进再试")
    }

    private fun noFile(file: File): Result = Result.Failure("无法读取主题文件：${file.name}")

    private fun noSection(file: File): Result = Result.Failure("主题文件里没有 tool_bar 段：${file.name}")
}
