/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

/**
 * Undo and redo for key edits, by snapshot.
 *
 * A snapshot is the whole key list — 47 small data classes, which is cheaper to
 * reason about than inverse operations per field, and immune to the "undo went
 * to the wrong slot" class of bug. What matters is granularity: dragging a
 * width fires dozens of changes per second, so changes sharing a [coalesceKey]
 * within [COALESCE_MS] collapse into one entry.
 */
class KeyboardEditHistory<T>(
    private val limit: Int = 50,
) {
    private val undoStack = ArrayDeque<List<T>>()
    private val redoStack = ArrayDeque<List<T>>()
    private var lastKey: String? = null
    private var lastAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Records that [before] was replaced.
     *
     * @param coalesceKey groups rapid changes to the same thing; null means the
     *   change always gets its own entry.
     */
    fun record(
        before: List<T>,
        coalesceKey: String?,
        now: Long,
    ) {
        val merge = coalesceKey != null && coalesceKey == lastKey && now - lastAt < COALESCE_MS
        lastKey = coalesceKey
        lastAt = now
        if (merge) return
        undoStack.addLast(before)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /** @return the snapshot to restore, or null when there is nothing to undo. */
    fun undo(current: List<T>): List<T>? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastKey = null
        return previous
    }

    /** @return the snapshot to restore, or null when there is nothing to redo. */
    fun redo(current: List<T>): List<T>? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastKey = null
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastKey = null
    }

    companion object {
        const val COALESCE_MS = 600L
    }
}
