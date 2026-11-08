// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import java.util.Collections
import java.util.WeakHashMap

/**
 * A set of listeners that does not keep them alive.
 *
 * Registration happens on the main thread while the notifying side may run on
 * a background one, so the set itself is synchronized. Iteration is not: a
 * caller that walks it has to hold the set's monitor, or a concurrent
 * registration invalidates the iterator.
 */
@Suppress("FunctionName")
fun <T> WeakHashSet(): MutableSet<T> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<T, Boolean>()))

/**
 * Walks a set built by [WeakHashSet] with its monitor held.
 *
 * Registration takes the same monitor, so a listener added from another thread
 * cannot invalidate the iterator mid-walk. Listeners run under that monitor:
 * keep them short and do not block on another thread that registers.
 */
fun <T> MutableSet<T>.forEachListener(action: (T) -> Unit) {
    synchronized(this) { forEach(action) }
}
