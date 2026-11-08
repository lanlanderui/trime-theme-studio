/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File
import com.osfans.trime.data.theme.ThemeLog as Timber

/**
 * Lists, renames and deletes the entries of `preset_color_schemes` directly in
 * a theme file.
 *
 * Like [ThemeStyleWriter] this edits the document in place: comments, key order
 * and blank lines are kept, so the theme stays a portable file. Only a scheme's
 * `name` (its display label) is renamed — the id is the key other settings
 * reference, so renaming it would silently break them.
 */
object ThemeSchemeWriter {
    data class Scheme(
        val id: String,
        val displayName: String,
    )

    /**
     * What a write did, and the text it left behind.
     *
     * [text] is handed back so the editor can make it the baseline for the next
     * save without reading a four-thousand-line theme again — and, more
     * importantly, so a run of edits never compares against a snapshot it has
     * already replaced.
     */
    data class SaveResult(
        val outcome: ThemeTextFile.WriteOutcome,
        val text: String? = null,
    )

    private const val SECTION_KEY = "preset_color_schemes"
    private val SCHEME_LINE = Regex("""^  ([A-Za-z0-9_.\-]+):\s*(?:#.*)?$""")
    private val NAME_LINE = Regex("""^(\s{4})name:(\s*)(.*)$""")
    private val PROPERTY_LINE = Regex("""^(\s{4})([A-Za-z0-9_]+):(\s*)(.*)$""")

    /** Entries that describe the scheme itself rather than a colour. */
    private val META_KEYS = setOf("name", "author", "light_scheme", "dark_scheme")

    /** Schemes in file order; [Scheme.displayName] falls back to the id. */
    fun listSchemes(file: File): List<Scheme> {
        val document = ThemeTextFile.read(file) ?: return emptyList()
        return listSchemes(document.lines)
    }

    /** [listSchemes] over lines that were already read; see [ThemeStyleWriter.readEntry]. */
    fun listSchemes(lines: List<String>): List<Scheme> {
        val range = sectionRange(lines, SECTION_KEY) ?: return emptyList()
        val result = mutableListOf<Scheme>()
        var currentId: String? = null
        for (i in range) {
            val line = lines[i]
            val scheme = SCHEME_LINE.find(line)
            if (scheme != null) {
                currentId = scheme.groupValues[1]
                result += Scheme(currentId, currentId)
                continue
            }
            val name = NAME_LINE.find(line) ?: continue
            val id = currentId ?: continue
            val value = unquote(ThemeStyleWriter.stripComment(name.groupValues[3]))
            if (value.isEmpty()) continue
            result[result.lastIndex] = Scheme(id, value)
        }
        return result
    }

    /**
     * Renames the display label of [id]. The `name:` line keeps its trailing
     * comment; a scheme without one gets the line inserted first.
     */
    fun renameScheme(
        file: File,
        id: String,
        newName: String,
    ): Boolean = renameSchemeChecked(file, id, newName).outcome == ThemeTextFile.WriteOutcome.WRITTEN

    /** [renameScheme] with the reason it did not happen, and a conflict baseline. */
    fun renameSchemeChecked(
        file: File,
        id: String,
        newName: String,
        expectedText: String? = null,
    ): SaveResult {
        val document = ThemeTextFile.read(file) ?: return failed()
        stale(file, document, expectedText)?.let { return it }
        val lines = document.lines
        val bounds = schemeBounds(lines, id) ?: return failed()
        val value = ThemeStyleWriter.formatScalar(newName)

        for (i in bounds) {
            val match = NAME_LINE.find(lines[i]) ?: continue
            val comment = ThemeStyleWriter.commentOf(match.groupValues[3])
            lines[i] = "    name: $value$comment"
            return write(file, document, lines)
        }
        // bounds.first is the scheme's own `id:` line; the body starts below it.
        lines.add(bounds.first + 1, "    name: $value")
        return write(file, document, lines)
    }

    /** Deletes a scheme together with its indented body. */
    fun deleteScheme(
        file: File,
        id: String,
    ): Boolean = deleteSchemeChecked(file, id).outcome == ThemeTextFile.WriteOutcome.WRITTEN

    /** [deleteScheme] with the reason it did not happen, and a conflict baseline. */
    fun deleteSchemeChecked(
        file: File,
        id: String,
        expectedText: String? = null,
    ): SaveResult {
        val document = ThemeTextFile.read(file) ?: return failed()
        stale(file, document, expectedText)?.let { return it }
        val lines = document.lines
        val range = sectionRange(lines, SECTION_KEY) ?: return failed()
        val schemes = listSchemes(lines)
        if (schemes.size <= 1) return failed()

        val start = boundsStart(lines, range, id) ?: return failed()
        var end = start + 1
        while (end <= range.last && !SCHEME_LINE.matches(lines[end])) end++
        // The section's closing banner is not part of the scheme: a scheme ends
        // where the next one starts, or where the trailing blanks and column-0
        // comments begin. Without this the last scheme took
        // `#----- 配色结束 -----#` with it, which is the author's comment.
        while (end > start + 1 && isSectionTrailer(lines[end - 1])) end--
        // Take the blank lines the block was followed by as well: leaving them
        // widens the gap by one line on every create/delete round trip.
        while (end <= range.last && lines[end].isBlank()) end++
        // A closing comment hugs the section it closes — that is how the shipped
        // themes are spaced — so the blank that separated this scheme from the
        // one above it goes too when the banner is what comes next.
        var from = start
        if (lines.getOrNull(end)?.startsWith("#") == true) {
            while (from > range.first && lines[from - 1].isBlank()) from--
        }
        lines.subList(from, end).clear()
        return write(file, document, lines)
    }

    /** All `key: value` pairs of one scheme, in file order (includes `name`/`author`). */
    fun readScheme(
        file: File,
        id: String,
    ): Map<String, String> {
        val lines = runCatching { file.readLines() }.getOrElse { return emptyMap() }
        val bounds = schemeBounds(lines, id) ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (i in bounds) {
            val match = PROPERTY_LINE.find(lines[i]) ?: continue
            result[match.groupValues[2]] = unquote(ThemeStyleWriter.stripComment(match.groupValues[4]))
        }
        return result
    }

    /**
     * Sets one color of [id]. An existing line keeps its indentation and
     * trailing comment; a missing key is appended to the scheme body.
     */
    fun setColor(
        file: File,
        id: String,
        key: String,
        value: String,
    ): Boolean = setColorChecked(file, id, key, value).outcome == ThemeTextFile.WriteOutcome.WRITTEN

    /** [setColor] with the reason it did not happen, and a conflict baseline. */
    fun setColorChecked(
        file: File,
        id: String,
        key: String,
        value: String,
        expectedText: String? = null,
    ): SaveResult {
        val document = ThemeTextFile.read(file) ?: return failed()
        stale(file, document, expectedText)?.let { return it }
        val lines = document.lines
        val bounds = schemeBounds(lines, id) ?: return failed()
        val pattern = Regex("""^(\s{4})${Regex.escape(key)}:(\s*)(.*)$""")
        val formatted = ThemeStyleWriter.formatScalar(value)

        for (i in bounds) {
            val match = pattern.find(lines[i]) ?: continue
            val comment = ThemeStyleWriter.commentOf(match.groupValues[3])
            lines[i] = "    $key: $formatted$comment"
            return write(file, document, lines)
        }

        var insertAt = bounds.last + 1
        while (insertAt > bounds.first && lines[insertAt - 1].isBlank()) insertAt--
        lines.add(insertAt, "    $key: $formatted")
        return write(file, document, lines)
    }

    /**
     * Writes many entries of one scheme in a single pass.
     *
     * Regenerating a whole palette means ~40 lines change at once; doing that
     * with [setColor] would read, render and write the file 40 times, and one
     * failure in the middle would leave a half-old scheme behind. Existing
     * lines keep their comments and indentation, missing ones are appended to
     * the scheme body, and when [create] is given a scheme that does not exist
     * yet is added at the end of the section.
     *
     * @return false when the file cannot be read, the scheme is missing and
     *   cannot be created, or the write itself was refused (file changed
     *   outside the app).
     */
    fun setEntries(
        file: File,
        id: String,
        entries: Map<String, String>,
        create: Boolean = false,
    ): Boolean = setSchemes(file, mapOf(id to entries), create)

    /**
     * [setEntries] for several schemes at once, still one read and one write.
     *
     * A day/night pair has to land together: writing the light scheme and then
     * failing on the dark one would leave the keyboard in a palette that has
     * no counterpart to switch to.
     *
     * [rewrite] makes the block a *replacement* rather than a patch: the colour
     * entries are dropped and written again in [schemes] order, while `name`,
     * `author` and the `light_scheme`/`dark_scheme` links stay. A generated
     * palette wants this — keys it no longer paints would otherwise survive as
     * stale rows, and new ones would pile up at the bottom of the block instead
     * of following the template's order.
     */
    fun setSchemes(
        file: File,
        schemes: Map<String, Map<String, String>>,
        create: Boolean = false,
        rewrite: Boolean = false,
    ): Boolean = setSchemesChecked(file, schemes, create, rewrite).outcome == ThemeTextFile.WriteOutcome.WRITTEN

    /** [setSchemes] with the reason it did not happen, and a conflict baseline. */
    fun setSchemesChecked(
        file: File,
        schemes: Map<String, Map<String, String>>,
        create: Boolean = false,
        rewrite: Boolean = false,
        expectedText: String? = null,
    ): SaveResult {
        if (schemes.isEmpty()) return failed()
        val document = ThemeTextFile.read(file) ?: return failed()
        stale(file, document, expectedText)?.let { return it }
        val lines = document.lines
        var applied = false
        for ((id, entries) in schemes) {
            if (entries.isEmpty()) continue
            if (!applyEntries(lines, id, entries, create, rewrite)) continue
            applied = true
        }
        if (!applied) return failed()
        return write(file, document, lines)
    }

    /** Mutates [lines] in place; false when the scheme is missing and [create] is off. */
    private fun applyEntries(
        lines: MutableList<String>,
        id: String,
        entries: Map<String, String>,
        create: Boolean,
        rewrite: Boolean,
    ): Boolean {
        val section = sectionRange(lines, SECTION_KEY)
        val existing = schemeBounds(lines, id)

        if (existing == null) {
            if (!create || section == null) return false
            // A section usually closes with a banner comment ("#--- 配色结束 ---#").
            // The new scheme belongs inside the section, so the banner has to
            // stay last: insert before the trailing blank/comment lines rather
            // than after them, where the next run would not find it.
            var insertAt = section.last + 1
            while (insertAt > section.first && isSectionTrailer(lines[insertAt - 1])) insertAt--
            val block = mutableListOf<String>()
            if (insertAt > 0 && lines[insertAt - 1].isNotBlank()) block += ""
            block += "  $id:"
            entries.forEach { (key, value) -> block += "    $key: ${ThemeStyleWriter.formatScalar(value)}" }
            lines.addAll(insertAt, block)
            // Keep one blank line between the new block and whatever follows it.
            val after = insertAt + block.size
            if (after < lines.size && lines[after].isNotBlank()) lines.add(after, "")
            return true
        }

        if (rewrite) {
            // Everything the block says about a colour goes, and so does the
            // blank-line grouping around it: the entries arrive in their own
            // order, so the old layout would leave gaps in the wrong places.
            // What stays is the metadata that makes it a usable scheme — its
            // label and its day/night partner.
            (existing.first + 1 until existing.last)
                .filter { i ->
                    val key = PROPERTY_LINE.find(lines[i])?.groupValues?.get(2)
                    lines[i].isBlank() || (key != null && key !in META_KEYS)
                }
                .asReversed()
                .forEach { lines.removeAt(it) }
        }
        val bounds = schemeBounds(lines, id) ?: existing

        // Append in a stable order, right after the last non-blank line of the
        // scheme, so a regenerated file does not drift a little every run.
        var appendAt = bounds.last + 1
        while (appendAt > bounds.first && lines[appendAt - 1].isBlank()) appendAt--
        val additions = mutableListOf<String>()
        for ((key, value) in entries) {
            val pattern = Regex("""^(\s{4})${Regex.escape(key)}:(\s*)(.*)$""")
            val index = (bounds.first..(bounds.last - 1)).firstOrNull { pattern.matches(lines[it]) }
            if (index == null) {
                additions += "    $key: ${ThemeStyleWriter.formatScalar(value)}"
            } else {
                val match = pattern.find(lines[index])!!
                val comment = ThemeStyleWriter.commentOf(match.groupValues[3])
                lines[index] = "    $key: ${ThemeStyleWriter.formatScalar(value)}$comment"
            }
        }
        if (additions.isNotEmpty()) lines.addAll(appendAt, additions)
        if (rewrite) {
            // The blanks inside the block are gone, so the one that separated it
            // from the next scheme has to be put back.
            val end = appendAt + additions.size
            if (end < lines.size && lines[end].isNotBlank()) lines.add(end, "")
        }
        return true
    }

    /** Other schemes pointing at [id] through `light_scheme` / `dark_scheme`. */
    fun referencingSchemes(
        file: File,
        id: String,
    ): List<String> {
        val lines = runCatching { file.readLines() }.getOrElse { return emptyList() }
        val range = sectionRange(lines, SECTION_KEY) ?: return emptyList()
        val result = mutableListOf<String>()
        var currentId: String? = null
        for (i in range) {
            val scheme = SCHEME_LINE.find(lines[i])
            if (scheme != null) {
                currentId = scheme.groupValues[1]
                continue
            }
            val trimmed = lines[i].trim()
            if ((trimmed.startsWith("light_scheme:") || trimmed.startsWith("dark_scheme:")) &&
                ThemeStyleWriter.stripComment(trimmed.substringAfter(':')) == id
            ) {
                currentId?.takeIf { it != id }?.let { if (it !in result) result += it }
            }
        }
        return result
    }

    /** Blank lines and column-0 comments close a section without being part of it. */
    private fun isSectionTrailer(line: String): Boolean = line.isBlank() || line.startsWith("#")

    private fun boundsStart(
        lines: List<String>,
        range: IntRange,
        id: String,
    ): Int? {
        for (i in range) {
            val scheme = SCHEME_LINE.find(lines[i]) ?: continue
            if (scheme.groupValues[1] == id) return i
        }
        return null
    }

    /** Line range of one scheme, from its id line to the line before the next scheme. */
    private fun schemeBounds(
        lines: List<String>,
        id: String,
    ): IntRange? {
        val range = sectionRange(lines, SECTION_KEY) ?: return null
        val start = boundsStart(lines, range, id) ?: return null
        var end = start + 1
        while (end <= range.last && !SCHEME_LINE.matches(lines[end])) end++
        return start until end
    }

    /** Index range of the lines inside a top-level [key] block. */
    private fun sectionRange(
        lines: List<String>,
        key: String,
    ): IntRange? {
        val start = lines.indexOfFirst { it.startsWith("$key:") }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (!line.startsWith(" ") && !line.startsWith("\t") && !line.startsWith("#")) {
                end = i
                break
            }
        }
        if (end <= start + 1) return null
        return (start + 1) until end
    }

    private fun write(
        file: File,
        document: ThemeTextFile.Document,
        lines: List<String>,
    ): SaveResult {
        val outcome = ThemeTextFile.writeChecked(file, document, lines)
        if (outcome != ThemeTextFile.WriteOutcome.WRITTEN) {
            Timber.w("Cannot write color schemes to %s: %s", file.name, outcome)
            return SaveResult(outcome)
        }
        return SaveResult(outcome, ThemeTextFile.render(document, lines))
    }

    private fun failed() = SaveResult(ThemeTextFile.WriteOutcome.FAILED)

    /**
     * The refusal for a file that no longer holds what the editor read.
     *
     * Same rule as the style writer: the snapshot the rows were built from is
     * the caller's, so only the caller can say whether it is still current.
     */
    private fun stale(
        file: File,
        document: ThemeTextFile.Document,
        expectedText: String?,
    ): SaveResult? {
        if (expectedText == null || document.text == expectedText) return null
        Timber.w("Color schemes changed outside the app: %s", file.name)
        return SaveResult(ThemeTextFile.WriteOutcome.CONFLICT)
    }

    /** Strips a matching pair of single or double quotes around a YAML scalar. */
    private fun unquote(raw: String): String {
        if (raw.length < 2) return raw
        val first = raw.first()
        val last = raw.last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
    }
}
