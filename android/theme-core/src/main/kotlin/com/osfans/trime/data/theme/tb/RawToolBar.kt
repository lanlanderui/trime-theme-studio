/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

/**
 * One scalar line under `tool_bar`, located by its path inside the block it
 * belongs to.
 *
 * The path is relative, not absolute: `foreground/style` means "this button's
 * foreground style", whichever block that is. An absolute path would collide
 * across blocks, because `hide_button`, `emoji_button` and `primary_button` all
 * have a `foreground/style` of their own — which is exactly why
 * [com.osfans.trime.data.theme.scalar.ThemeScalarReader] cannot be reused here
 * (it treats `hide_button: &hide_button` as a leaf, so the children below it
 * lose their parent and land on the same path as `primary_button`'s).
 */
data class RawLeaf(
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
     * Themes keep their defaults as `#  corner_radius: 0`; such a line is a
     * value the runtime never sees, so writing to it means enabling it.
     */
    val commented: Boolean = false,
) {
    val key: String get() = path.last()
}

/**
 * A named button block under `tool_bar`, e.g. `hide_button: &hide_button`.
 *
 * These blocks are where a button's properties actually live: `buttons` only
 * lists aliases pointing at them. The same block may be referenced more than
 * once, in which case editing it changes every button that points here.
 */
data class RawButtonBlock(
    val id: String,
    /** Anchor name after `&`, when the block declares one. */
    val anchor: String?,
    val headerLine: Int,
    /** Line range of the whole block, header included. */
    val range: IntRange,
    val leaves: List<RawLeaf>,
) {
    fun leaf(vararg path: String): RawLeaf? = leaves.lastOrNull { it.path == path.toList() }

    fun value(vararg path: String): String? = leaf(*path)?.value
}

/**
 * One item of the `buttons` sequence.
 *
 * Two spellings occur: an alias to a block (`*hide_button`) and an inline
 * mapping (`{action: Hide}`). Only the alias form is editable — rewriting an
 * inline map would mean re-rendering a structure this editor does not model,
 * and turning one into the other would silently change what the author wrote.
 */
data class RawButtonRef(
    val index: Int,
    /** Block id when written as `*alias`, else null. */
    val alias: String?,
    /** Source text of an inline `{...}` item, else null. */
    val inlineText: String?,
) {
    val isInline: Boolean get() = inlineText != null

    /** The item as it goes back into the list. */
    val source: String get() = inlineText ?: "*${alias.orEmpty()}"
}

/**
 * The `tool_bar` block as the theme file writes it.
 *
 * A file view, deliberately separate from the runtime
 * [com.osfans.trime.data.theme.model.ToolBar]: the model has the aliases
 * already expanded and cannot tell which block a value came from, which is the
 * one thing an editor must know.
 */
data class RawToolBar(
    /** Line range of the block body, or null when the theme has no `tool_bar:`. */
    val range: IntRange?,
    /** One indent step inside the block, learned from the file (2 in the shipped themes). */
    val indentStep: Int,
    /** Indent of the block's own keys (`button_spacing`, `primary_button`, …). */
    val childIndent: Int,
    /** Section-level scalars: `button_spacing`, `button_font`, `back_style`. */
    val scalars: Map<String, RawLeaf>,
    /** Every named block, `primary_button` included. */
    val blocks: Map<String, RawButtonBlock>,
    val buttonsLine: Int?,
    val buttons: List<RawButtonRef>,
    /** Whether the list is written `[ *a ]` rather than `[*a]`; kept on save. */
    val buttonsSpaced: Boolean,
    /** Why this block cannot be edited, or null when it can. */
    val unsupported: String?,
) {
    val exists: Boolean get() = range != null

    val editable: Boolean get() = exists && unsupported == null

    val primary: RawButtonBlock? get() = blocks[PRIMARY]

    /** How many items of `buttons` point at [id]. */
    fun referenceCount(id: String): Int = buttons.count { it.alias == id }

    /** Block ids that no `buttons` item points at. */
    fun unreferenced(): List<String> = blocks.keys.filter { it != PRIMARY && referenceCount(it) == 0 }

    companion object {
        const val PRIMARY = "primary_button"

        val EMPTY =
            RawToolBar(
                range = null,
                indentStep = 2,
                childIndent = 2,
                scalars = emptyMap(),
                blocks = emptyMap(),
                buttonsLine = null,
                buttons = emptyList(),
                buttonsSpaced = true,
                unsupported = null,
            )
    }
}
