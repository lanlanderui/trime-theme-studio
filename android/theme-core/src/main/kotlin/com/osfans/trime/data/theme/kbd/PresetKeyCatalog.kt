/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.model.PresetKey

/**
 * One action a key can be bound to, as the three editors show it.
 *
 * The picker behind `click`, the toolbar's `action` and the preset-key list all
 * offer the same dictionary (`preset_keys`), so the naming and grouping live
 * here rather than in three screens that would drift.
 */
data class PresetKeyEntry(
    val id: String,
    /** What the row says: the author's label, else the first state, else the id. */
    val displayName: String,
    val kind: Kind,
    /** One line saying what the key does, e.g. `select: .next`. */
    val detail: String,
    /** How many states the key declares; 2 is the only value that can toggle. */
    val stateCount: Int,
) {
    /**
     * A key is two-state only when it declares exactly two states.
     *
     * `states: [ 中文, 西文 ]` is a switch; one state is decoration, three is a
     * cycle, and neither can be drawn as an on/off button.
     */
    val twoState: Boolean get() = stateCount == 2

    /** How a row is grouped in the picker. */
    enum class Kind(
        /** Sort order in the picker; the behaviours users look for come first. */
        val order: Int,
    ) {
        SEND(0),
        FUNCTION(1),
        SELECT(2),
        COMMAND(3),
        TEXT(4),
        TOGGLE(5),
        OTHER(6),
    }
}

/**
 * Turns the decoded `preset_keys` dictionary into pickable rows.
 *
 * Ordering is by [PresetKeyEntry.kind] and then by id: a picker that mixed a
 * `command` in with the letters would make the common case (bind a letter) cost
 * a search every time.
 */
object PresetKeyCatalog {
    fun build(keys: Map<String, PresetKey>): List<PresetKeyEntry> = keys.map { (id, key) -> entryOf(id, key) }
        .sortedWith(compareBy({ it.kind.order }, { it.id.lowercase() }))

    fun entryOf(
        id: String,
        key: PresetKey,
    ): PresetKeyEntry {
        val states = key.states.map { it.trim() }
        return PresetKeyEntry(
            id = id,
            displayName = key.label.trim().ifEmpty { states.firstOrNull().orEmpty() }.ifEmpty { id },
            kind = kindOf(key),
            detail = detailOf(key),
            stateCount = states.size,
        )
    }

    private fun kindOf(key: PresetKey): PresetKeyEntry.Kind = when {
        // `select` wins over `send`: a key that switches schema sends a keyboard
        // event too, but what it *is* is a selector.
        key.select.isNotBlank() -> PresetKeyEntry.Kind.SELECT

        key.command.isNotBlank() -> PresetKeyEntry.Kind.COMMAND

        key.toggle.isNotBlank() -> PresetKeyEntry.Kind.TOGGLE

        key.text.isNotBlank() -> PresetKeyEntry.Kind.TEXT

        key.functional -> PresetKeyEntry.Kind.FUNCTION

        key.send.isNotBlank() -> PresetKeyEntry.Kind.SEND

        else -> PresetKeyEntry.Kind.OTHER
    }

    private fun detailOf(key: PresetKey): String = when {
        key.select.isNotBlank() -> "select: ${key.select}"
        key.command.isNotBlank() -> "command: ${key.command}" + if (key.option.isNotBlank()) " · ${key.option}" else ""
        key.toggle.isNotBlank() -> "toggle: ${key.toggle}"
        key.text.isNotBlank() -> "text: ${key.text}"
        key.send.isNotBlank() -> "send: ${key.send}"
        else -> ""
    }
}
