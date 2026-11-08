/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import java.io.File

/**
 * Writes edited keys back into a theme file, touching only those lines.
 *
 * The file is the author's, not ours, so a key line is patched rather than
 * regenerated: fields the editor does not understand stay, field order stays,
 * trailing comments and indentation stay, and a value that did not change keeps
 * the author's exact spelling (`10.0` does not become `10`).
 */
object ThemeKeyboardWriter {
    /** Why a write was refused, or success. */
    sealed interface Result {
        data object Success : Result

        data class Failure(
            val reason: String,
        ) : Result
    }

    /**
     * Replaces the key list of [raw] with [keys].
     *
     * [origins] maps each key to the file line it came from; a key with a null
     * origin (a copy inserted by the editor keeps its source's origin, a key
     * from nowhere has none) is rendered from the model instead of patched.
     * Without [origins] the lists must line up one to one.
     */
    fun replaceKeys(
        file: File,
        raw: RawKeyboard,
        keys: List<TextKey>,
        origins: List<Int?> = keys.indices.toList(),
    ): Result {
        if (raw.keys.isEmpty()) return Result.Failure("布局没有实体键列表")
        if (keys.size != origins.size) {
            return Result.Failure("键与来源数量不一致（${keys.size} / ${origins.size}）")
        }
        val indent = raw.indent ?: return Result.Failure("找不到 keys 的缩进")
        val indentText = " ".repeat(indent.item)

        val newLines =
            keys.mapIndexed { index, key ->
                val line = renderKey(origins[index]?.let { raw.keys.getOrNull(it) }, key, indentText)
                val body = line.trimStart().removePrefix("- ").substringBefore(" #")
                if (KeyboardYaml.parseFlowMap(body) == null) {
                    return Result.Failure("生成的键行无法解析：$line")
                }
                line
            }
        return spliceAndWrite(file, raw, newLines)
    }

    /**
     * Removes the whole layout [raw] from the file.
     *
     * The range the reader reported is removed as-is, after checking it still
     * starts with this layout's header: if the file changed underneath the
     * editor, that check is what stops the wrong block from disappearing.
     */
    fun deleteLayout(
        file: File,
        raw: RawKeyboard,
    ): Result {
        val document = ThemeTextFile.read(file) ?: return Result.Failure("读取主题失败")
        val lines = document.lines

        val first = raw.range.first
        val last = raw.range.last
        if (first !in lines.indices || last !in lines.indices || last < first) {
            return Result.Failure("块范围越界")
        }
        if (!lines[first].trim().startsWith("${raw.id}:")) {
            return Result.Failure("文件已变化，第 ${first + 1} 行不再是「${raw.id}」")
        }
        changedKeyLine(lines, raw)?.let { return Result.Failure(it) }

        repeat(last - first + 1) { lines.removeAt(first) }
        return write(file, document, lines)
    }

    /**
     * Appends a layout named [id] to the theme file.
     *
     * Built from scratch rather than copied: the point of the feature is a
     * layout with nothing in it, and a copy would carry the source's keys,
     * colours and comments into a place they do not belong.
     *
     * The one spacer is not decoration. A `keys:` with no items under it is a
     * null value, which the theme decoder refuses — the whole theme would stop
     * loading — and an empty list cannot be spelled either, since the reader
     * treats `keys: [...]` as a form it does not edit. One `{width: 10}` line
     * keeps the block valid until the user adds real keys.
     */
    fun createLayout(
        file: File,
        id: String,
        name: String,
    ): Result {
        val trimmed = id.trim()
        if (!ThemeKeyboardReader.ID.matches(trimmed)) {
            return Result.Failure("布局 id 只能使用字母、数字、下划线、点和连字符")
        }
        val document = ThemeTextFile.read(file) ?: return Result.Failure("读取主题失败")
        val lines = document.lines
        val section = ThemeKeyboardReader.sectionRange(lines) ?: return Result.Failure("主题里没有 preset_keyboards 段")
        val taken = (section.first..section.last).mapNotNull { ThemeKeyboardReader.layoutId(lines[it]) }
        if (trimmed in taken) return Result.Failure("已经有一个叫「$trimmed」的布局")

        // Land after the last layout's own lines, not after the blank lines that
        // separate the section from whatever follows — otherwise the new block
        // swallows that gap and ends up glued to the next section.
        var insertAt = section.last + 1
        while (insertAt > section.first && lines[insertAt - 1].isBlank()) insertAt--
        val block = blockOf(trimmed, name).toMutableList()
        if (insertAt > section.first && lines[insertAt - 1].isNotBlank()) block.add(0, "")
        if (insertAt < lines.size && lines[insertAt].isNotBlank()) block.add("")
        lines.addAll(insertAt, block)
        return write(file, document, lines)
    }

    /** The lines of a fresh layout, indented the way the shipped themes are. */
    private fun blockOf(
        id: String,
        name: String,
    ): List<String> = listOf(
        "  $id:",
        "    name: ${KeyboardYaml.quote(name.trim().ifBlank { id })}",
        "    width: 10",
        "    ascii_mode: 0",
        "    keys:",
        "      - {width: 10}",
    )

    /** The previous version kept next to [file], if a write has happened. */
    fun backupOf(file: File): File = ThemeTextFile.backupOf(file)

    fun hasBackup(file: File): Boolean = backupOf(file).isFile

    /**
     * Puts the previous version back, and keeps the current one as the backup.
     *
     * It is a swap rather than a one-way restore: the same action again returns
     * to where you started, which is what "undo the last write" should feel
     * like. Nothing else on disk changes.
     */
    fun restoreBackup(file: File): Result {
        val backup = backupOf(file)
        if (!backup.isFile) return Result.Failure("没有可恢复的备份")
        val previous = runCatching { backup.readText() }.getOrElse { return Result.Failure("读取备份失败") }
        val current = runCatching { file.readText() }.getOrElse { return Result.Failure("读取主题失败") }
        return if (ThemeTextFile.replaceText(file, current, previous)) {
            Result.Success
        } else {
            Result.Failure("恢复失败")
        }
    }

    /**
     * One `- {…}` line: the original entries patched with what the editor changed.
     *
     * When nothing changed, the original line is returned verbatim — including
     * spacing quirks like `select_all }` that re-rendering would tidy away. A
     * save should show up in `git diff` as the edit, nothing else. A key with no
     * [original] is built from the model.
     */
    private fun renderKey(
        original: RawKey?,
        key: TextKey,
        indent: String,
    ): String {
        val entries = original?.entries?.toMutableList() ?: mutableListOf()
        var changed = original == null

        // `raw` marks a value that is already in the form the file wants (a
        // bare weight); everything else goes through the flow-map quoter.
        fun put(
            name: String,
            value: String?,
            raw: String? = null,
        ) {
            val index = entries.indexOfFirst { it.key == name }
            val old = if (index >= 0) entries[index].value else null
            if (value == null) {
                if (index >= 0) {
                    entries.removeAt(index)
                    changed = true
                }
                return
            }
            if (sameValue(old, value)) return
            val entry = KeyboardYaml.Entry(name, value, raw)
            if (index >= 0) entries[index] = entry else entries += entry
            changed = true
        }

        put("label", key.label.takeIf { it.isNotEmpty() })
        val width = key.width.takeIf { it > 0f }?.let(::formatNumber)
        put("width", width, raw = width)
        // `popup` 是列表。模型里空既可能是「本来就没有」，也可能是文件写的是
        // `'q w'` 这种解不出来的形式，所以只在两种情况下动它：用户给了内容，
        // 或者文件里那行确实读得懂、而用户把它清空了。
        val rawPopup = original?.get("popup")
        val popup = key.popup
        if (popup.isNotEmpty()) {
            if (!samePopup(rawPopup, popup)) {
                val text = formatPopup(popup, rawPopup)
                put("popup", text, raw = text)
            }
        } else if (ThemeKeyboardReader.popupOf(rawPopup)?.isNotEmpty() == true) {
            put("popup", null)
        }
        ActionSlot.entries.forEach { slot ->
            when (val token = actionOf(key, slot)) {
                null -> put(slot.yaml, null)

                is KeyActionToken.Plain -> put(slot.yaml, token.token.ifEmpty { null })

                // The picker cannot express an inline token, so an untouched one
                // is left exactly as the theme wrote it.
                is KeyActionToken.Inline -> Unit
            }
        }

        if (!changed && original != null) return indent + original.sourceLine
        return "$indent- ${KeyboardYaml.formatFlowMap(entries)}${original?.comment.orEmpty()}"
    }

    private fun actionOf(
        key: TextKey,
        slot: ActionSlot,
    ): KeyActionToken? = when (slot) {
        ActionSlot.CLICK -> key.click
        ActionSlot.LONG_CLICK -> key.longClick
        ActionSlot.SWIPE_UP -> key.swipeUp
        ActionSlot.SWIPE_DOWN -> key.swipeDown
        ActionSlot.SWIPE_LEFT -> key.swipeLeft
        ActionSlot.SWIPE_RIGHT -> key.swipeRight
        ActionSlot.DOUBLE_CLICK -> key.doubleClick
        ActionSlot.LAZY_DOUBLE_CLICK -> key.lazyDoubleClick
        ActionSlot.COMPOSING -> key.composing
        ActionSlot.PAGING -> key.paging
        ActionSlot.HAS_MENU -> key.hasMenu
        ActionSlot.COMBO -> key.combo
        ActionSlot.ASCII -> key.ascii
    }

    private fun spliceAndWrite(
        file: File,
        raw: RawKeyboard,
        newKeyLines: List<String>,
    ): Result {
        val document = ThemeTextFile.read(file) ?: return Result.Failure("读取主题失败")
        val lines = document.lines
        val header = raw.range.first
        if (header !in lines.indices || !lines[header].trim().startsWith("${raw.id}:")) {
            return Result.Failure("文件已变化，第 ${header + 1} 行不再是「${raw.id}」")
        }
        val first = raw.keys.first().line
        val last = raw.keys.last().line
        if (first !in lines.indices || last !in lines.indices || last < first) {
            return Result.Failure("文件行号越界")
        }
        changedKeyLine(lines, raw)?.let { return Result.Failure(it) }

        // Blank and comment lines between keys are the author's row grouping —
        // `tongwenfeng.trime.yaml` spaces its rows out that way. The key list is
        // what gets rewritten; those lines are not ours to drop, so each one is
        // put back in the same gap, after the same number of keys.
        val gaps = gapsOf(lines, raw, first, last)
        repeat(last - first + 1) { lines.removeAt(first) }
        lines.addAll(first, withGaps(newKeyLines, gaps))
        return write(file, document, lines)
    }

    /** The non-key lines inside a key block, keyed by how many keys precede them. */
    private fun gapsOf(
        lines: List<String>,
        raw: RawKeyboard,
        first: Int,
        last: Int,
    ): Map<Int, List<String>> {
        val keyLines = raw.keys.mapTo(HashSet()) { it.line }
        val gaps = sortedMapOf<Int, MutableList<String>>()
        var seen = 0
        for (i in first..last) {
            if (i in keyLines) seen++ else gaps.getOrPut(seen) { mutableListOf() } += lines[i]
        }
        return gaps
    }

    /** Interleaves [gaps] back into [keyLines] at the positions they came from. */
    private fun withGaps(
        keyLines: List<String>,
        gaps: Map<Int, List<String>>,
    ): List<String> {
        val out = mutableListOf<String>()
        keyLines.forEachIndexed { index, line ->
            out += line
            gaps[index + 1]?.let { out += it }
        }
        // A gap whose key has been deleted goes to the end instead of vanishing.
        gaps.filterKeys { it > keyLines.size }.forEach { (_, kept) -> out += kept }
        return out
    }

    /**
     * Refuses a stale line range instead of deleting whatever currently sits
     * there. [RawKeyboard] remembers every original key line; if any of them is
     * no longer at its recorded position, another writer changed the file while
     * the editor was open.
     */
    private fun changedKeyLine(
        lines: List<String>,
        raw: RawKeyboard,
    ): String? {
        for (key in raw.keys) {
            if (key.line !in lines.indices) return "文件行号越界"
            val current = lines[key.line].trimStart().trimEnd()
            if (current != key.sourceLine.trimEnd()) {
                return "文件已变化，第 ${key.line + 1} 行不再是原来的按键"
            }
        }
        return null
    }

    /** Shared with the preset-key writer, with backup and atomic replacement. */
    internal fun write(
        file: File,
        document: ThemeTextFile.Document,
        lines: List<String>,
    ): Result = if (ThemeTextFile.write(file, document, lines)) {
        Result.Success
    } else {
        Result.Failure("写入失败")
    }

    /** Same items in the same order, whichever of the two spellings the file used. */
    private fun samePopup(
        raw: String?,
        items: List<String>,
    ): Boolean = ThemeKeyboardReader.popupOf(raw) == items

    /**
     * `[a, b]`, quoting items only where YAML needs it, and keeping the inner
     * spacing the author used (`[ a, b ]` stays spaced).
     */
    private fun formatPopup(
        items: List<String>,
        raw: String?,
    ): String = KeyboardYaml.formatFlowSequence(
        items.map(KeyboardYaml::quote),
        spaced = raw?.trim()?.startsWith("[ ") == true,
    )

    /** `10.0` and `10` are the same weight; keep the author's spelling in that case. */
    private fun sameValue(
        old: String?,
        new: String,
    ): Boolean {
        if (old == null) return false
        if (old == new) return true
        val a = old.toDoubleOrNull() ?: return false
        val b = new.toDoubleOrNull() ?: return false
        return a == b
    }

    private fun formatNumber(value: Float): String = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
