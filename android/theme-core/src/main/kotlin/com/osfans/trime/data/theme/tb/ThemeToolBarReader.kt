/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import com.osfans.trime.data.theme.kbd.KeyboardYaml
import java.io.File

/**
 * Reads the `tool_bar:` block of a theme file without decoding YAML.
 *
 * Only the shape the editor needs is recognised:
 *
 * ```yaml
 * tool_bar:
 *   button_spacing: 5                    ← section scalar
 *   primary_button:                      ← block (no value)
 *     foreground:                        ← nested group
 *       style: "ic@dots_horizontal"      ← leaf, path = [foreground, style]
 *   hide_button: &hide_button            ← block with an anchor
 *     action: Hide
 *   buttons: [ *hide_button ]            ← flow sequence of aliases
 * ```
 *
 * The anchor case is the reason this reader exists instead of reusing
 * [com.osfans.trime.data.theme.scalar.ThemeScalarReader]: that one decides
 * "group or leaf" from `value == null`, and `hide_button: &hide_button` has a
 * value, so the whole block would be read as a leaf and its children would lose
 * their parent — `hide_button`'s `foreground/style` would land on the same path
 * as `primary_button`'s.
 *
 * Everything else — a block-style `buttons:` list, an alias used as a value, a
 * dangling `*name` — is reported through [RawToolBar.unsupported] rather than
 * guessed at, because a wrong guess here becomes a corrupted theme on save.
 */
object ThemeToolBarReader {
    private const val SECTION = "tool_bar"
    private const val BUTTONS = "buttons"

    /** The block as written, or null when the theme declares no `tool_bar:`. */
    fun read(file: File): RawToolBar? = runCatching { read(file.readLines()) }.getOrNull()

    fun read(lines: List<String>): RawToolBar? {
        val range = sectionRange(lines) ?: return null
        if (range.isEmpty()) return RawToolBar.EMPTY.copy(range = range)

        val childIndent = childIndentOf(lines, range)
        if (childIndent < 0) return RawToolBar.EMPTY.copy(range = range)

        val scalars = linkedMapOf<String, RawLeaf>()
        val blocks = linkedMapOf<String, RawButtonBlock>()
        var buttonsLine: Int? = null
        var buttonsRaw: String? = null
        var unsupported: String? = null
        var step = 0
        var current: BlockBuilder? = null

        fun close(at: Int) {
            val builder = current ?: return
            current = null
            blocks[builder.id] = builder.build(at.coerceIn(builder.headerLine, range.last))
        }

        for (i in range) {
            val entry = parseEntry(lines[i]) ?: continue

            if (entry.commented) {
                // A commented line never opens, closes or widens a block: it is
                // a value the author pinned at the default, and only makes sense
                // inside the block it visually sits in.
                val builder = current ?: continue
                if (entry.indent <= childIndent) continue
                builder.leaves +=
                    RawLeaf(
                        path = builder.pathFor(entry.key, entry.indent),
                        value = null,
                        line = i,
                        indent = entry.indent,
                        comment = entry.comment,
                        commented = true,
                    )
                builder.lastLine = i
                continue
            }

            if (entry.indent <= childIndent) {
                close(i - 1)
                if (entry.key == BUTTONS) {
                    buttonsLine = i
                    buttonsRaw = entry.value.orEmpty()
                    continue
                }
                if (entry.value?.startsWith("*") == true) {
                    // `primary_button: *common` — legal YAML, but writing it back
                    // would mean naming the target block, which this editor does
                    // not model.
                    unsupported =
                        unsupported ?: "「${entry.key}」使用了别名引用，暂不支持编辑（第 ${i + 1} 行）"
                    continue
                }
                val anchor = entry.value?.takeIf { it.startsWith("&") }?.removePrefix("&")?.trim()
                if (entry.value == null || anchor != null) {
                    current = BlockBuilder(entry.key, anchor, i)
                    continue
                }
                scalars[entry.key] = RawLeaf(listOf(entry.key), entry.value, i, entry.indent, entry.comment)
                continue
            }

            // Deeper than the block's own keys: belongs to the open block.
            val builder = current
            if (builder == null) {
                unsupported = unsupported ?: "第 ${i + 1} 行不属于任何按钮块"
                continue
            }
            if (step == 0) step = (entry.indent - childIndent).takeIf { it in 1..8 } ?: 0

            if (entry.value == null || entry.value.startsWith("&")) {
                val path = builder.pathFor(entry.key, entry.indent)
                val nestedAnchor = entry.value?.startsWith("&") == true
                if (nestedAnchor) {
                    unsupported = unsupported ?: "按钮「${builder.id}」内部还有嵌套锚点，暂不支持编辑"
                }
                builder.stack += entry.indent to (path.lastOrNull() ?: entry.key)
            } else {
                builder.leaves +=
                    RawLeaf(
                        path = builder.pathFor(entry.key, entry.indent),
                        value = entry.value,
                        line = i,
                        indent = entry.indent,
                        comment = entry.comment,
                    )
            }
            builder.lastLine = i
        }
        close(range.last)

        val refs = mutableListOf<RawButtonRef>()
        val buttonsText = buttonsRaw
        if (buttonsLine != null) {
            val items = KeyboardYaml.parseFlowSequence(buttonsText.orEmpty())
            if (items == null) {
                unsupported = unsupported ?: "buttons 不是单行 flow 序列（第 ${buttonsLine + 1} 行）"
            } else {
                items.forEachIndexed { index, item ->
                    when {
                        item.startsWith("*") -> {
                            val name = item.removePrefix("*").trim()
                            if (name.isEmpty()) {
                                unsupported = unsupported ?: "buttons 第 ${index + 1} 项是空的别名"
                            } else {
                                refs += RawButtonRef(index, name, null)
                            }
                        }

                        item.startsWith("{") -> refs += RawButtonRef(index, null, item)

                        else -> unsupported = unsupported ?: "buttons 第 ${index + 1} 项无法识别：$item"
                    }
                }
            }
        }

        val dangling = refs.mapNotNull { it.alias }.filter { it !in blocks }
        if (dangling.isNotEmpty()) {
            unsupported = unsupported ?: "buttons 引用了不存在的按钮：${dangling.distinct().joinToString()}"
        }

        return RawToolBar(
            range = range,
            indentStep = step.takeIf { it > 0 } ?: DEFAULT_STEP,
            childIndent = childIndent,
            scalars = scalars,
            blocks = blocks,
            buttonsLine = buttonsLine,
            buttons = refs,
            buttonsSpaced = buttonsText?.let { it.startsWith("[ ") && it.endsWith(" ]") } ?: true,
            unsupported = unsupported,
        )
    }

    /** Line range of the `tool_bar:` body, or null when the file has no such section. */
    internal fun sectionRange(lines: List<String>): IntRange? {
        val start = lines.indexOfFirst { it.startsWith("$SECTION:") }
        if (start < 0) return null

        var end = lines.size
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (line.first().isWhitespace()) continue
            // A comment at column 0 still belongs to this section: themes put a
            // banner right above the next section's key.
            if (line.trimStart().startsWith("#")) continue
            end = i
            break
        }
        return (start + 1) until end
    }

    /** Indent of the block's own keys, or -1 when the block has no live content. */
    private fun childIndentOf(
        lines: List<String>,
        range: IntRange,
    ): Int = range.firstOrNull { i ->
        val line = lines[i]
        line.isNotBlank() && !line.trimStart().startsWith("#")
    }?.let { indentOf(lines[it]) } ?: -1

    private fun indentOf(line: String): Int = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }

    /**
     * One line as `key: value # comment`, live or commented.
     *
     * Mirrors [com.osfans.trime.data.theme.scalar.ThemeScalarReader]'s handling
     * of a commented line: the `#` itself takes a column the author did not
     * count, so `#  size:` lines up with a live `    size:` sibling.
     */
    private fun parseEntry(line: String): Entry? {
        if (line.isBlank()) return null
        val leading = indentOf(line)
        val body = line.substring(leading)
        if (body.startsWith("#")) {
            val afterHash = body.substring(1)
            val spaces = afterHash.indexOfFirst { !it.isWhitespace() }
            if (spaces < 0) return null
            val entry = parseKeyValue(afterHash.substring(spaces)) ?: return null
            return entry.copy(indent = leading + spaces, commented = true)
        }
        return parseKeyValue(body)?.copy(indent = leading)
    }

    private fun parseKeyValue(text: String): Entry? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val key = text.substring(0, colon).trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) return null
        val (valueText, comment) = KeyboardYaml.splitComment(text.substring(colon + 1))
        return Entry(key, valueText.trim().ifEmpty { null }, 0, comment, false)
    }

    private data class Entry(
        val key: String,
        val value: String?,
        val indent: Int,
        val comment: String,
        val commented: Boolean,
    )

    private const val DEFAULT_STEP = 2

    private class BlockBuilder(
        val id: String,
        val anchor: String?,
        val headerLine: Int,
    ) {
        val leaves = mutableListOf<RawLeaf>()

        /** (indent, key) of the groups this block is currently inside. */
        val stack = mutableListOf<Pair<Int, String>>()

        var lastLine: Int = headerLine

        /** Full path of [key] at [indent], popping the groups it left behind. */
        fun pathFor(
            key: String,
            indent: Int,
        ): List<String> {
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeAt(stack.lastIndex)
            return stack.map { it.second } + key
        }

        fun build(end: Int): RawButtonBlock = RawButtonBlock(
            id = id,
            anchor = anchor,
            headerLine = headerLine,
            range = headerLine..end,
            leaves = leaves.toList(),
        )
    }
}
