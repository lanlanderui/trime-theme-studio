/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import com.osfans.trime.data.theme.kbd.KeyboardYaml

/**
 * One button of the toolbar, as the screen lists it.
 *
 * [visualIndex] counts from the left of the rendered bar, which is the order the
 * user sees and drags; [arrayIndex] is where the item actually sits in the
 * file's `buttons:` sequence. They run opposite ways (`ROW_REVERSE`), and every
 * reorder goes through [ToolBarSlot] rather than doing arithmetic here.
 *
 * [blockId] is null for an inline `{action: Hide}` item: that form is shown but
 * not editable, because re-rendering it would restyle a structure this editor
 * does not model.
 */
data class ToolBarButton(
    val visualIndex: Int,
    val arrayIndex: Int,
    val blockId: String?,
    /** `*hide_button` or the inline `{…}` text, exactly as written. */
    val source: String,
    val action: String,
    /** `long_press_action`, only the primary button usually has one. */
    val longPressAction: String,
    /** `foreground/style`, e.g. `ic@keyboard_close`; empty when not set. */
    val style: String,
    /** `foreground/option_styles`, unquoted; empty when the button has one look. */
    val optionStyles: List<String>,
    /** The `&anchor` name of the block, when it declares one. */
    val anchor: String?,
) {
    /**
     * A button swaps its look between two states only when it declares exactly
     * two styles. One style is decoration; three is a cycle, which the toolbar
     * cannot show on a two-state background.
     */
    val twoState: Boolean get() = optionStyles.size == 2

    val editable: Boolean get() = blockId != null
}

/**
 * The `tool_bar` block, arranged for the editor.
 *
 * The file is a set of named blocks plus a list of references to them; the
 * screen is a row of buttons. This is the translation, and it is also where the
 * two-state rule is decided — the runtime `ToolBar` has the aliases expanded and
 * cannot tell which block a style came from, which is exactly what the editor
 * has to show.
 */
data class ToolBarModel(
    val exists: Boolean,
    val editable: Boolean,
    val unsupported: String?,
    /** Section-level scalars: `button_spacing`, `button_font`, `back_style`. */
    val scalars: Map<String, String?>,
    /** Pinned at the left edge, outside the `buttons` list. */
    val primary: ToolBarButton?,
    /** Left to right, the order they are drawn in. */
    val buttons: List<ToolBarButton>,
    /** Block ids nothing points at: still in the file, invisible on the keyboard. */
    val unreferenced: List<String>,
) {
    val twoStateCount: Int get() = buttons.count { it.twoState }
}

object ToolBarCatalog {
    /** The scalars the screen edits, in display order. */
    val SCALARS = listOf("button_spacing", "button_font", "back_style")

    /** Fields of the primary button the screen edits, as file paths. */
    val PRIMARY_FIELDS =
        listOf(
            listOf("foreground", "style"),
            listOf("action"),
            listOf("long_press_action"),
        )

    /** Fields of an ordinary button the screen edits, as file paths. */
    val BUTTON_FIELDS =
        listOf(
            listOf("foreground", "style"),
            listOf("foreground", "option_styles"),
            listOf("action"),
        )

    fun build(raw: RawToolBar): ToolBarModel {
        if (!raw.exists) {
            return ToolBarModel(
                exists = false,
                editable = false,
                unsupported = null,
                scalars = emptyMap(),
                primary = null,
                buttons = emptyList(),
                unreferenced = emptyList(),
            )
        }
        val total = raw.buttons.size
        val buttons =
            raw.buttons.map { ref ->
                val block = ref.alias?.let { raw.blocks[it] }
                ToolBarButton(
                    visualIndex = ToolBarSlot.arrayToVisual(ref.index, total),
                    arrayIndex = ref.index,
                    blockId = ref.alias,
                    source = ref.source,
                    action = unquote(block?.value("action")),
                    longPressAction = unquote(block?.value("long_press_action")),
                    style = unquote(block?.value("foreground", "style")),
                    optionStyles = stylesOf(block),
                    anchor = block?.anchor,
                )
            }.sortedBy { it.visualIndex }

        val primary =
            raw.primary?.let { block ->
                ToolBarButton(
                    visualIndex = -1,
                    arrayIndex = -1,
                    blockId = RawToolBar.PRIMARY,
                    source = "*${RawToolBar.PRIMARY}",
                    action = unquote(block.value("action")),
                    longPressAction = unquote(block.value("long_press_action")),
                    style = unquote(block.value("foreground", "style")),
                    optionStyles = stylesOf(block),
                    anchor = block.anchor,
                )
            }

        return ToolBarModel(
            exists = true,
            editable = raw.editable,
            unsupported = raw.unsupported,
            scalars = SCALARS.associateWith { unquote(raw.scalars[it]?.value) },
            primary = primary,
            buttons = buttons,
            unreferenced = raw.unreferenced(),
        )
    }

    /**
     * The value as the user should see it.
     *
     * The toolbar reader keeps scalars verbatim (`"ic@arrow-left"`), which is what
     * the writer needs to compare against; the screen shows the value without the
     * quoting. Both sides go through here so a no-op save stays a no-op.
     */
    private fun unquote(raw: String?): String = KeyboardYaml.unquote(raw.orEmpty())

    /** `foreground/option_styles` as a list, unquoted; empty when unset. */
    private fun stylesOf(block: RawButtonBlock?): List<String> {
        val text = block?.value("foreground", "option_styles") ?: return emptyList()
        val items = KeyboardYaml.parseFlowSequence(text) ?: return emptyList()
        return items.map { unquote(it.trim()) }.filter { it.isNotEmpty() }
    }
}
