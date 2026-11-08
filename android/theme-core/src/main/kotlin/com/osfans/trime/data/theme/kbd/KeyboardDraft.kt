/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey

/**
 * The keys of one layout while they are being edited.
 *
 * Each key remembers the line it came from ([Entry.origin]); a duplicate points
 * at the same line, a key the editor invents has none. That is what lets the
 * writer patch lines by identity rather than by position — insert a key in the
 * middle and every following line keeps its comment and its exact spelling.
 *
 * Nothing here writes to the file: the draft only feeds the canvas and the
 * writer, and can be thrown away at any point.
 */
class KeyboardDraft(
    val config: TextKeyboard,
    /**
     * Each key's `popup` as read leniently from the file, in draft order.
     *
     * The decoded model only understands the list spelling of `popup`, so the
     * space-separated one (`'q w'`) arrives empty. Passing the reader's result
     * here is what lets the inspector show it — and what keeps an unrelated
     * edit from dropping the line.
     */
    popups: List<List<String>>? = null,
) {
    /** A key plus the source line it was read from, or null for a new key. */
    data class Entry(
        val key: TextKey,
        val origin: Int?,
    )

    private val entries: MutableList<Entry> =
        config.keys.mapIndexed { index, key ->
            val lenient = popups?.getOrNull(index).orEmpty()
            Entry(if (lenient.isEmpty()) key else key.copy(popup = lenient), index)
        }.toMutableList()

    val size: Int get() = entries.size

    fun key(index: Int): TextKey = entries[index].key

    fun origin(index: Int): Int? = entries[index].origin

    fun origins(): List<Int?> = entries.map { it.origin }

    /**
     * Whether this key becomes a real key on screen.
     *
     * [com.osfans.trime.ime.keyboard.Keyboard] skips keys whose `click` has no
     * action — they still take up width, but no `Key` object is created for
     * them. That is why the draft's index and the keyboard's index drift apart
     * after the first spacer, and why the editor needs a mapping at all.
     */
    fun producesKey(index: Int): Boolean = when (val token = entries[index].key.click) {
        null -> false
        is KeyActionToken.Plain -> token.token.isNotEmpty()
        is KeyActionToken.Inline -> true
    }

    /** The weight this key actually occupies: an unset width follows the keyboard's. */
    fun effectiveWidth(index: Int): Float = entries[index].key.width.takeIf { it > 0f } ?: config.width

    /**
     * Sets the key's weight, or clears it when it matches the keyboard default.
     *
     * `width: 0` and "no width" are the same in the model but different in the
     * file; keeping a key at the default width unset is what stops the editor
     * from turning every layout into a wall of explicit numbers.
     */
    fun setWidth(
        index: Int,
        weight: Float,
    ) {
        val normalized = if (weight == config.width) 0f else weight
        update(index) { it.copy(width = normalized) }
    }

    /** The token bound to [slot], as the theme writes it, or null when unset. */
    fun action(
        index: Int,
        slot: ActionSlot,
    ): String? = tokenOf(key(index), slot)?.let(::displayToken)

    /**
     * Binds [token] to [slot]; null clears it.
     *
     * Only plain tokens are produced. An inline token (`{commit: …, label: …}`)
     * survives untouched until someone edits that slot, at which point it
     * becomes a plain reference — the picker cannot express the other form.
     */
    fun setAction(
        index: Int,
        slot: ActionSlot,
        token: String?,
    ) {
        val action = token?.takeIf { it.isNotEmpty() }?.let { KeyActionToken.Plain(it) }
        update(index) { key ->
            val updated =
                when (slot) {
                    ActionSlot.CLICK -> key.copy(click = action)
                    ActionSlot.LONG_CLICK -> key.copy(longClick = action)
                    ActionSlot.SWIPE_UP -> key.copy(swipeUp = action)
                    ActionSlot.SWIPE_DOWN -> key.copy(swipeDown = action)
                    ActionSlot.SWIPE_LEFT -> key.copy(swipeLeft = action)
                    ActionSlot.SWIPE_RIGHT -> key.copy(swipeRight = action)
                    ActionSlot.DOUBLE_CLICK -> key.copy(doubleClick = action)
                    ActionSlot.LAZY_DOUBLE_CLICK -> key.copy(lazyDoubleClick = action)
                    ActionSlot.COMPOSING -> key.copy(composing = action)
                    ActionSlot.PAGING -> key.copy(paging = action)
                    ActionSlot.HAS_MENU -> key.copy(hasMenu = action)
                    ActionSlot.COMBO -> key.copy(combo = action)
                    ActionSlot.ASCII -> key.copy(ascii = action)
                }
            // A key with no click is a spacer, and a spacer that leaves its width
            // to the keyboard default is zero pixels wide — invisible, and too
            // narrow to select, so clearing a binding would quietly lose the key.
            // Pin the weight it had while it still had an action.
            if (slot == ActionSlot.CLICK && action == null && updated.width <= 0f) {
                updated.copy(width = effectiveWidth(index))
            } else {
                updated
            }
        }
    }

    /**
     * Sets the text drawn on the key.
     *
     * An empty label falls back to whatever the key's action resolves to, which
     * is what most layouts rely on; setting one pins the caption.
     */
    fun setLabel(
        index: Int,
        label: String,
    ) {
        update(index) { it.copy(label = label) }
    }

    /** What a long press on this key offers, left to right. */
    fun popup(index: Int): List<String> = entries[index].key.popup

    /**
     * Replaces the popup list.
     *
     * An empty list means "no popup": the writer drops the field rather than
     * writing `popup: []`, which would be a different thing to look at in the
     * file even though the keyboard behaves the same.
     */
    fun setPopup(
        index: Int,
        items: List<String>,
    ) {
        update(index) { it.copy(popup = items) }
    }

    /**
     * Swaps key [index] with its neighbour [delta] places away.
     *
     * The origin travels with the key, so a moved key keeps the line it came
     * from — comments stay attached to their key rather than to a position.
     *
     * @return the key's new index, or null when the move would leave the list.
     */
    fun move(
        index: Int,
        delta: Int,
    ): Int? {
        val target = index + delta
        if (target !in entries.indices) return null
        val moved = entries[index]
        entries[index] = entries[target]
        entries[target] = moved
        return target
    }

    /**
     * Inserts [key] right after [after]; `-1` puts it at the head of the layout.
     *
     * A new key has no [Entry.origin] — nothing in the file corresponds to it,
     * so the writer renders its line from the model. [after] is clamped rather
     * than rejected: adding a key must work with nothing selected, which is the
     * only way to fill a layout that has no keys yet.
     *
     * @return the index the key landed on.
     */
    fun insert(
        after: Int,
        key: TextKey,
    ): Int {
        val at = (after + 1).coerceIn(0, entries.size)
        entries.add(at, Entry(key, origin = null))
        return at
    }

    /**
     * Inserts a key with no action after [after].
     *
     * It renders as an empty key of one default width — the usual way to push a
     * row's contents around without inventing a caption for the gap.
     *
     * @return the index of the new key.
     */
    fun insertBlank(after: Int): Int = insert(after, TextKey(width = config.width))

    /** How many keys pin a caption of their own, for the bulk-clear prompt. */
    fun labelCount(): Int = entries.count { it.key.label.isNotEmpty() }

    /**
     * Drops the pinned caption from every key that has one.
     *
     * A key that spells out `label:` keeps saying that no matter what it is
     * bound to, so a layout written that way cannot be re-bound without the
     * captions lying. Clearing them makes each key show the name of its click
     * action again, and the ones worth naming can be re-pinned one by one.
     *
     * @return how many keys were cleared.
     */
    fun clearLabels(): Int {
        var cleared = 0
        entries.forEachIndexed { index, entry ->
            if (entry.key.label.isEmpty()) return@forEachIndexed
            entries[index] = entry.copy(key = entry.key.copy(label = ""))
            cleared++
        }
        return cleared
    }

    /**
     * Copies key [index] right after itself, sharing its source line.
     *
     * @return the index of the copy, so the caller can select it.
     */
    fun duplicate(index: Int): Int {
        entries.add(index + 1, entries[index])
        return index + 1
    }

    /**
     * Removes key [index]. The last key stays: a layout with no keys is not a
     * layout, and the writer would have nothing to write.
     *
     * @return whether the key was removed.
     */
    fun delete(index: Int): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(index)
        return true
    }

    /** A snapshot for the undo stack. */
    fun snapshot(): List<Entry> = entries.toList()

    /** Restores a snapshot taken by [snapshot]. */
    fun restore(snapshot: List<Entry>) {
        entries.clear()
        entries.addAll(snapshot)
    }

    /**
     * Draft index ↔ keyboard key index, for a keyboard that built [keyCount] keys.
     *
     * A theme's spacer (`{width: 5}` with no action) occupies width but yields no
     * key, so the two lists offset from that point on. Feeding the editor's
     * selection through this map is what keeps "the key I tapped" and "the key
     * the panel describes" the same key.
     */
    data class IndexMap(
        val draftToKey: IntArray,
        val keyToDraft: IntArray,
    )

    fun indexMap(keyCount: Int): IndexMap {
        val draftToKey = IntArray(entries.size) { -1 }
        val keyToDraft = IntArray(keyCount) { -1 }
        var next = 0
        entries.indices.forEach { index ->
            if (!producesKey(index) || next >= keyCount) return@forEach
            draftToKey[index] = next
            keyToDraft[next] = index
            next++
        }
        return IndexMap(draftToKey, keyToDraft)
    }

    fun toConfig(): TextKeyboard = config.copy(keys = entries.map { it.key })

    private fun update(
        index: Int,
        block: (TextKey) -> TextKey,
    ) {
        val entry = entries[index]
        entries[index] = entry.copy(key = block(entry.key))
    }

    private fun tokenOf(
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

    private fun displayToken(token: KeyActionToken): String = when (token) {
        is KeyActionToken.Plain -> token.token
        is KeyActionToken.Inline -> token.label ?: token.text ?: token.commit ?: ""
    }
}
