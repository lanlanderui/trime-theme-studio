/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import java.io.File

/**
 * Reads the `preset_keyboards:` block of a theme file without decoding YAML.
 *
 * Only the shape the editor needs is recognised: a layout id at two spaces,
 * its scalars at four, and a `keys:` list whose items are one-line flow maps.
 * Everything else — `__patch`, block-style keys, inline sequences — is reported
 * through [RawKeyboard.unsupported] instead of being guessed at, because a
 * wrong guess here becomes a corrupted theme on the next save.
 */
object ThemeKeyboardReader {
    private const val SECTION = "preset_keyboards"

    /** All layouts of [file], in file order. */
    fun read(file: File): List<RawKeyboard> = runCatching { read(file.readLines()) }.getOrDefault(emptyList())

    /** All layouts of [lines], in file order. */
    fun read(lines: List<String>): List<RawKeyboard> {
        val section = sectionRange(lines) ?: return emptyList()
        val blocks = mutableListOf<RawKeyboard>()
        var i = section.first
        while (i <= section.last) {
            val id = layoutId(lines[i])
            if (id == null) {
                i++
                continue
            }
            var end = i + 1
            while (end <= section.last && layoutId(lines[end]) == null) end++
            blocks += parseBlock(lines, i, end, id)
            i = end
        }
        return blocks
    }

    /** One layout by id, or null when the file does not declare it. */
    fun read(
        lines: List<String>,
        id: String,
    ): RawKeyboard? = read(lines).firstOrNull { it.id == id }

    private fun parseBlock(
        lines: List<String>,
        start: Int,
        end: Int,
        id: String,
    ): RawKeyboard {
        var include: String? = null
        var keysIndent: Int? = null
        var itemIndent: Int? = null
        var inKeys = false
        var unsupported: String? = null
        val scalars = linkedMapOf<String, String>()
        val keys = mutableListOf<RawKey>()

        for (i in start + 1 until end) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            val content = line.substring(indent).trimEnd()
            if (content.startsWith("#")) continue

            if (inKeys) {
                if (content.startsWith("- ")) {
                    if (itemIndent == null) itemIndent = indent
                    if (indent != itemIndent) {
                        unsupported = unsupported ?: "键的缩进不一致（第 ${i + 1} 行）"
                        continue
                    }
                    val parsed = parseKeyLine(content, i)
                    if (parsed == null) {
                        unsupported = unsupported ?: "键不是单行 flow 写法（第 ${i + 1} 行）"
                    } else {
                        keys += parsed
                    }
                    continue
                }
                // Anything that is not an item ends the list; it can only be a
                // field of this layout, so fall through and parse it as one.
                if (indent > (keysIndent ?: 0)) {
                    unsupported = unsupported ?: "keys 之后出现了无法识别的嵌套内容（第 ${i + 1} 行）"
                    continue
                }
                inKeys = false
            }

            val colon = content.indexOf(':')
            if (colon <= 0) {
                unsupported = unsupported ?: "无法识别的行（第 ${i + 1} 行）"
                continue
            }
            val name = content.substring(0, colon).trim()
            val rawValue = content.substring(colon + 1)
            if (name == "keys") {
                keysIndent = indent
                inKeys = true
                val inline = stripComment(rawValue).trim()
                if (inline.isNotEmpty()) {
                    unsupported = "keys 使用了行内写法（第 ${i + 1} 行）"
                    inKeys = false
                }
                continue
            }
            if (name == "__include") {
                include = stripComment(rawValue).trim().trimQuotes().substringAfterLast('/')
                continue
            }
            if (name == "__patch") {
                unsupported = unsupported ?: "该布局带有 __patch"
                continue
            }
            scalars[name] = stripComment(rawValue).trim().trimQuotes()
        }

        val indentPair = keysIndent?.let { Indent(it, itemIndent ?: (it + 2)) }
        return RawKeyboard(
            id = id,
            includeTarget = include,
            scalars = scalars,
            keys = keys,
            indent = indentPair,
            range = start..(end - 1),
            unsupported = unsupported,
        )
    }

    /** Parses `- {click: q, width: 5}  # note` into entries plus the comment. */
    private fun parseKeyLine(
        content: String,
        line: Int,
    ): RawKey? {
        val body = content.removePrefix("- ")
        val split = splitComment(body)
        val entries = KeyboardYaml.parseFlowMap(split.first) ?: return null
        return RawKey(entries, split.second, content, line)
    }

    /** `- ` items of one layout id at two-space indent, or null. */
    internal fun layoutId(line: String): String? {
        if (!line.startsWith("  ") || line.startsWith("   ")) return null
        val content = line.substring(2).trimEnd()
        if (content.isEmpty() || content.startsWith("#") || !content.endsWith(":")) return null
        val id = content.dropLast(1)
        return id.takeIf { ID.matches(it) }
    }

    /**
     * Line range of the `preset_keyboards:` body, for the writer as well.
     *
     * Shaping a new block means appending it here, and the range has to be the
     * one the reader will look at afterwards — otherwise a layout could be
     * written into a place this reader never sees.
     */
    internal fun sectionRange(lines: List<String>): IntRange? {
        val start = lines.indexOfFirst { it.trimEnd() == "$SECTION:" }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.startsWith("#")) continue
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                end = i
                break
            }
        }
        if (end <= start + 1) return null
        return (start + 1) until end
    }

    /** Splits `value # comment` into value and the comment with its whitespace. */
    private fun splitComment(raw: String): Pair<String, String> = KeyboardYaml.splitComment(raw)

    private fun stripComment(raw: String): String = splitComment(raw).first

    /**
     * One key's `popup`, read leniently.
     *
     * Two spellings exist in the wild: the list `["a", "b"]` and the older
     * space-separated string `'q w'`. The theme decoder only understands the
     * list one — a string decodes to an empty list (see
     * `LenientStringListSerializer`) — so an editor that trusted the decoded
     * model would show "no popup" for the second spelling and delete the line
     * on the next unrelated edit.
     *
     * @return the items, empty when the key has no `popup`; null when the line
     *   carries one in a shape neither spelling covers, which callers must
     *   leave alone rather than overwrite.
     */
    fun popupOf(raw: String?): List<String>? {
        if (raw == null) return emptyList()
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        if (text.startsWith("[")) {
            return KeyboardYaml.parseFlowSequence(text)?.map(KeyboardYaml::unquote)
        }
        val value = KeyboardYaml.unquote(text)
        if (value.isEmpty()) return emptyList()
        return value.split(' ', '\t').filter { it.isNotEmpty() }
    }

    /** [popupOf] for a whole key line. */
    fun popupOf(rawKey: RawKey?): List<String>? = popupOf(rawKey?.get("popup"))

    private fun String.trimQuotes(): String = when {
        length >= 2 && first() == '\'' && last() == '\'' -> substring(1, length - 1).replace("''", "'")
        length >= 2 && first() == '"' && last() == '"' -> substring(1, length - 1)
        else -> this
    }

    /** What a layout id may look like; the writer holds new ids to the same rule. */
    internal val ID = Regex("""[A-Za-z0-9_.\-]+""")
}
