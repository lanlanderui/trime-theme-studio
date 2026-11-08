/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey

/**
 * One layout, as the list page needs it.
 *
 * Three things have to be visible before a card is opened: whether the layout
 * owns its keys or only points at another one, whether it contains spacers (a
 * key that takes width and builds nothing), and whether a `.custom.yaml` is
 * going to overrule whatever is edited here. Each of them is the difference
 * between "I changed it and it worked" and "I changed it and nothing happened".
 */
data class KeyboardEntry(
    val id: String,
    /** `name:` from the file, falling back to the id. */
    val displayName: String,
    /** Keys the keyboard will build — what the user counts on screen. */
    val keyCount: Int,
    /** Key lines this layout owns in the file; 0 for a pure alias. */
    val lineCount: Int,
    /** Layout this one inherits from (`__include`), or null. */
    val aliasTarget: String?,
    /** Whether some key takes width without an action. */
    val hasSpacer: Boolean,
    /** Whether `<theme>.custom.yaml` patches this layout. */
    val patched: Boolean,
    /** Why the block cannot be edited safely, or null when it can. */
    val unsupported: String?,
    /** The decoded layout, for a thumbnail; null when the theme has no such id. */
    val layout: TextKeyboard?,
) {
    /** A block with no keys of its own: it can be looked at, never edited. */
    val isAlias: Boolean get() = aliasTarget != null && lineCount == 0

    /** Inherits from another layout but still has key lines to patch. */
    val inherits: Boolean get() = aliasTarget != null && lineCount > 0

    val editable: Boolean get() = !isAlias && unsupported == null && lineCount > 0
}

/**
 * Builds the layout list out of the two views of a theme.
 *
 * The file view ([RawKeyboard]) decides what can be edited and where; the
 * decoded view ([TextKeyboard]) decides what the card draws. They are not
 * interchangeable: an alias has a full decoded layout and no key lines, and a
 * block with `keys:` written as multi-line maps has key lines the reader will
 * not touch. Keeping both means the list can say which is which instead of
 * showing 50 identical cards and failing on the eleventh.
 */
object KeyboardCatalog {
    fun build(
        raws: List<RawKeyboard>,
        layouts: Map<String, TextKeyboard>,
        /** Layout ids a `.custom.yaml` overrides; see `ThemePatchDetector`. */
        patched: Set<String> = emptySet(),
    ): List<KeyboardEntry> = raws.map { raw -> entryOf(raw, layouts[raw.id], raw.id in patched) }

    fun entryOf(
        raw: RawKeyboard,
        layout: TextKeyboard?,
        patched: Boolean = false,
    ): KeyboardEntry = KeyboardEntry(
        id = raw.id,
        displayName = raw.displayName,
        keyCount = layout?.keys?.size ?: 0,
        lineCount = raw.keys.size,
        aliasTarget = raw.includeTarget,
        hasSpacer = layout?.keys?.any(::isSpacer) ?: raw.keys.any { it["click"] == null },
        patched = patched,
        unsupported = raw.unsupported,
        layout = layout,
    )

    /**
     * A key that occupies width but builds nothing.
     *
     * `Keyboard` skips a key whose click action is missing or empty, so the
     * spacers are what make the runtime key list shorter than the file's. The
     * same rule decides which keys the canvas can select.
     */
    fun isSpacer(key: TextKey): Boolean = when (val token = key.click) {
        // The exact inverse of `KeyboardDraft.producesKey`, and of what
        // `Keyboard` does when it walks the key list.
        null -> true

        is KeyActionToken.Plain -> token.token.isEmpty()

        is KeyActionToken.Inline -> false
    }
}
