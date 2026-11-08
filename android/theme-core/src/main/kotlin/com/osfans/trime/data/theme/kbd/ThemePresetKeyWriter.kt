/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.ThemeTextFile
import java.io.File

/**
 * Writes one `preset_keys` entry back into the theme file.
 *
 * Same discipline as the layout editor: a line is patched, never regenerated.
 * Only the fields that changed are touched — everything else keeps the author's
 * spelling, order and trailing comment, including fields this app has no UI for.
 */
object ThemePresetKeyWriter {
    /**
     * Fields whose value is YAML rather than a string, written verbatim.
     *
     * `functional: true` must stay a bare boolean and `states: [ 中文, 西文 ]` a
     * flow sequence; quoting either would turn a flag into the string "true"
     * and a list into one long string.
     */
    private val RAW_FIELDS = setOf("functional", "repeatable", "states", "sticky", "slide_cursor", "slide_delete")

    /**
     * Applies [changes] to entry [id].
     *
     * A null value removes the field, a value sets it. Passing the current value
     * is not a change: the file is left untouched so a "save" that changed
     * nothing does not churn the file.
     *
     * @param rawFields fields the caller already formatted as YAML, overriding
     *   [RAW_FIELDS] when it knows better (a value the theme wrote as a string).
     */
    fun update(
        file: File,
        section: RawPresetSection,
        id: String,
        changes: Map<String, String?>,
        rawFields: Set<String> = RAW_FIELDS,
    ): ThemeKeyboardWriter.Result {
        val raw = section.keys.firstOrNull { it.id == id }
            ?: return ThemeKeyboardWriter.Result.Failure("找不到条目「$id」")

        val entries = raw.entries.toMutableList()
        var changed = false
        changes.forEach { (field, value) ->
            val index = entries.indexOfFirst { it.key == field }
            when {
                value == null -> if (index >= 0) {
                    entries.removeAt(index)
                    changed = true
                }

                index >= 0 -> if (entries[index].value != value) {
                    entries[index] = KeyboardYaml.Entry(field, value, value.takeIf { field in rawFields })
                    changed = true
                }

                else -> {
                    entries += KeyboardYaml.Entry(field, value, value.takeIf { field in rawFields })
                    changed = true
                }
            }
        }
        if (!changed) return ThemeKeyboardWriter.Result.Success

        val indent = " ".repeat(section.itemIndent)
        val line = "$indent$id: ${KeyboardYaml.formatFlowMap(entries)}${raw.comment}"
        // Validate the flow map alone: the trailing comment is not part of it.
        val body = KeyboardYaml.splitComment(line.trimStart().substringAfter(':').trim()).first
        if (KeyboardYaml.parseFlowMap(body) == null) {
            return ThemeKeyboardWriter.Result.Failure("生成的条目无法解析：$line")
        }

        val document = ThemeTextFile.read(file) ?: return ThemeKeyboardWriter.Result.Failure("读取主题失败")
        val lines = document.lines
        if (raw.line !in lines.indices) return ThemeKeyboardWriter.Result.Failure("文件行号越界")
        if (!lines[raw.line].trim().startsWith("$id:")) {
            return ThemeKeyboardWriter.Result.Failure("文件已变化，第 ${raw.line + 1} 行不再是「$id」")
        }
        lines[raw.line] = line
        return ThemeKeyboardWriter.write(file, document, lines)
    }

    /**
     * Appends a new entry at the end of the section.
     *
     * Appended, not sorted: authors group their presets by purpose, and
     * alphabetizing would scatter those groups on every save.
     */
    fun add(
        file: File,
        section: RawPresetSection,
        id: String,
        fields: Map<String, String>,
    ): ThemeKeyboardWriter.Result {
        idProblem(id)?.let { return ThemeKeyboardWriter.Result.Failure(it) }
        if (section.keys.any { it.id == id }) return ThemeKeyboardWriter.Result.Failure("已存在同名的预设按键「$id」")

        val entries = fields.filterValues { it.isNotEmpty() }.map { (k, v) -> KeyboardYaml.Entry(k, v) }
        val indent = " ".repeat(section.itemIndent)
        val line = "$indent$id: ${KeyboardYaml.formatFlowMap(entries)}"
        val body = line.trimStart().substringAfter(':').trim()
        if (KeyboardYaml.parseFlowMap(body) == null) {
            return ThemeKeyboardWriter.Result.Failure("生成的条目无法解析：$line")
        }

        val document = ThemeTextFile.read(file) ?: return ThemeKeyboardWriter.Result.Failure("读取主题失败")
        val lines = document.lines
        val after = section.keys.last().line
        if (after !in lines.indices) return ThemeKeyboardWriter.Result.Failure("文件行号越界")
        if (!lines[after].trim().startsWith(section.keys.last().id + ":")) {
            return ThemeKeyboardWriter.Result.Failure("文件已变化，第 ${after + 1} 行不再是「${section.keys.last().id}」")
        }
        lines.add(after + 1, line)
        return ThemeKeyboardWriter.write(file, document, lines)
    }

    /**
     * Removes entry [id].
     *
     * The keys that referenced it keep their token but lose the behaviour the
     * token used to stand for, so the caller is expected to say how many those
     * are before getting here.
     */
    fun delete(
        file: File,
        section: RawPresetSection,
        id: String,
    ): ThemeKeyboardWriter.Result {
        val raw = section.keys.firstOrNull { it.id == id }
            ?: return ThemeKeyboardWriter.Result.Failure("找不到条目「$id」")

        val document = ThemeTextFile.read(file) ?: return ThemeKeyboardWriter.Result.Failure("读取主题失败")
        val lines = document.lines
        if (raw.line !in lines.indices) return ThemeKeyboardWriter.Result.Failure("文件行号越界")
        if (!lines[raw.line].trim().startsWith("$id:")) {
            return ThemeKeyboardWriter.Result.Failure("文件已变化，第 ${raw.line + 1} 行不再是「$id」")
        }
        lines.removeAt(raw.line)
        return ThemeKeyboardWriter.write(file, document, lines)
    }

    /** Why [id] cannot be used as an entry name, or null when it can. */
    fun idProblem(id: String): String? = when {
        id.isBlank() -> "名字不能为空"
        id.any { it.isWhitespace() } -> "名字不能包含空格"
        id.contains(':') -> "名字不能包含冒号"
        else -> null
    }
}
