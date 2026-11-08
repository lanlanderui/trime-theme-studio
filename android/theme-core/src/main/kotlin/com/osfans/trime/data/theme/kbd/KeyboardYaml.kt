/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

/**
 * Reads and writes the single-line flow maps a theme uses for one key:
 *
 * ```yaml
 * - {click: '1', long_click: '!', width: 5}
 * ```
 *
 * Themes are hand-written, so the reader accepts the three quoting styles that
 * actually occur in the shipped themes — bare (`q`, `Return`, `1`), single
 * quoted (`'!'`, `'\'`) and double quoted (`"!"`, `"\\"`) — while the writer
 * only ever emits bare or single-quoted scalars, which need no escaping beyond
 * doubling an inner quote.
 */
object KeyboardYaml {
    /**
     * One `key: value` pair.
     *
     * [source] is the value exactly as the file wrote it. Re-rendering is only
     * safe for entries this app produced — an untouched one may be a nested
     * structure (`{commit: '，'}`) or a string that merely looks like one
     * (`'{}{Left}'`), and quoting either again would change its meaning.
     */
    data class Entry(
        val key: String,
        val value: String?,
        val source: String? = null,
    )

    /**
     * Parses the body of a flow map, `{...}`, into ordered entries.
     *
     * Returns null when [text] is not a flow map at all — a block-style key
     * (`- click: q`) ends up here, and the caller treats that block as
     * unsupported rather than guessing.
     */
    fun parseFlowMap(text: String): List<Entry>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val body = trimmed.substring(1, trimmed.length - 1)
        val entries = mutableListOf<Entry>()
        for (chunk in splitTopLevel(body)) {
            if (chunk.isBlank()) continue
            val colon = indexOfTopLevelColon(chunk) ?: return null
            val key = chunk.substring(0, colon).trim()
            if (key.isEmpty()) return null
            val raw = chunk.substring(colon + 1).trim()
            entries += Entry(key, if (raw.isEmpty()) null else unquote(raw), raw)
        }
        return entries
    }

    /** Renders entries as a flow map, quoting only what YAML needs. */
    fun formatFlowMap(entries: List<Entry>): String {
        val body = entries.joinToString(", ") { entry ->
            val value = entry.value
            when {
                value == null -> entry.key
                entry.source != null -> "${entry.key}: ${entry.source}"
                else -> "${entry.key}: ${quote(value)}"
            }
        }
        return "{$body}"
    }

    /**
     * Parses a flow sequence, `[ *a, *b ]`, into the raw text of each item.
     *
     * Items come back exactly as written — an alias keeps its `*`, an inline
     * mapping keeps its braces. `tool_bar.buttons` is a list of aliases to
     * button blocks, and a button the user only reordered must be put back
     * untouched instead of being re-rendered from a parsed model.
     *
     * Returns null when [text] is not a flow sequence: a block-style list
     * (`buttons:` followed by `- *a` lines) lands here, and the caller reports
     * the toolbar as unsupported rather than guessing where the list ends.
     */
    fun parseFlowSequence(text: String): List<String>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
        val body = trimmed.substring(1, trimmed.length - 1)
        if (body.isBlank()) return emptyList()
        return splitTopLevel(body).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Renders items as a flow sequence.
     *
     * [spaced] reproduces the theme's own `[ *a, *b ]` habit; both spellings
     * parse the same, but re-flowing a list nobody meant to restyle shows up as
     * a diff on every save.
     */
    fun formatFlowSequence(
        items: List<String>,
        spaced: Boolean = true,
    ): String = when {
        items.isEmpty() -> "[]"
        spaced -> "[ ${items.joinToString(", ")} ]"
        else -> "[${items.joinToString(", ")}]"
    }

    /**
     * Splits `value # comment` into the value and the comment with the
     * whitespace that separated them.
     *
     * A `#` inside quotes belongs to the value, so this cannot be a
     * `substringBefore(" #")`.
     */
    fun splitComment(raw: String): Pair<String, String> {
        var quote: Char? = null
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                quote == '\'' && c == '\'' && i + 1 < raw.length && raw[i + 1] == '\'' -> i++

                quote != null && c == quote -> quote = null

                quote == null && (c == '\'' || c == '"') -> quote = c

                quote == null && c == '#' && i > 0 && raw[i - 1].isWhitespace() -> {
                    var from = i
                    while (from > 0 && raw[from - 1].isWhitespace()) from--
                    return raw.substring(0, from) to raw.substring(from)
                }
            }
            i++
        }
        return raw to ""
    }

    /**
     * Quotes [raw] for a flow context.
     *
     * Flow maps are stricter than block scalars: `,` `{` `}` `[` `]` end a
     * value, and a bare `1` or `true` would be read back as a number or a
     * boolean instead of the string the keyboard expects.
     */
    fun quote(raw: String): String {
        val needsQuote =
            raw.isEmpty() ||
                raw.first().isWhitespace() ||
                raw.last().isWhitespace() ||
                raw.any { it in FLOW_SPECIALS } ||
                raw.toDoubleOrNull() != null ||
                raw.equals("true", ignoreCase = true) ||
                raw.equals("false", ignoreCase = true) ||
                raw == "null" ||
                raw == "~"
        if (!needsQuote) return raw
        return "'" + raw.replace("'", "''") + "'"
    }

    /** Characters that cannot appear bare inside a flow map. */
    private const val FLOW_SPECIALS = ",:{}[]#&*!|>'\"%@`\\"

    /** Splits on commas that are neither quoted nor nested. */
    private fun splitTopLevel(body: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var quote: Char? = null
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                quote == '\'' && c == '\'' && i + 1 < body.length && body[i + 1] == '\'' -> i++

                quote != null && c == quote -> quote = null

                quote == null && (c == '\'' || c == '"') -> quote = c

                quote == null && (c == '{' || c == '[') -> depth++

                quote == null && (c == '}' || c == ']') -> depth--

                quote == null && depth == 0 && c == ',' -> {
                    parts += body.substring(start, i)
                    start = i + 1
                }
            }
            i++
        }
        parts += body.substring(start)
        return parts
    }

    /** Index of the first `:` outside quotes, or null when the chunk is not `k: v`. */
    private fun indexOfTopLevelColon(chunk: String): Int? {
        var quote: Char? = null
        var i = 0
        while (i < chunk.length) {
            val c = chunk[i]
            when {
                quote == '\'' && c == '\'' && i + 1 < chunk.length && chunk[i + 1] == '\'' -> i++
                quote != null && c == quote -> quote = null
                quote == null && (c == '\'' || c == '"') -> quote = c
                quote == null && c == ':' -> return i
            }
            i++
        }
        return null
    }

    /**
     * Strips one layer of YAML quoting from a flow-context scalar.
     *
     * Kept public because flow sequences are read as raw item text (an alias must
     * keep its `*`, an inline map its braces), so callers that want the values
     * rather than the spelling — the toolbar's `option_styles` — need the same
     * unquoting the flow-map parser does.
     */
    fun unquote(raw: String): String {
        if (raw.length < 2) return raw
        return when {
            raw.first() == '\'' && raw.last() == '\'' ->
                raw.substring(1, raw.length - 1).replace("''", "'")

            raw.first() == '"' && raw.last() == '"' -> unescapeDouble(raw.substring(1, raw.length - 1))

            else -> raw
        }
    }

    private fun unescapeDouble(body: String): String {
        if (!body.contains('\\')) return body
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i + 1 >= body.length) {
                out.append(c)
                i++
                continue
            }
            when (val esc = body[i + 1]) {
                '\\' -> out.append('\\')
                '"' -> out.append('"')
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                '0' -> out.append('\u0000')
                else -> out.append('\\').append(esc)
            }
            i += 2
        }
        return out.toString()
    }
}
