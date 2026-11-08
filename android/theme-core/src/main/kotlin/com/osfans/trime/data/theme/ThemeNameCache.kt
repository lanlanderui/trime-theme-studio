/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File

/**
 * Keeps the display name read out of a theme file until the file changes.
 *
 * Reading a name is not cheap — the file is expanded through its `__include`
 * chain and patched — and listing the themes does it for every file, every time
 * the picker opens. A name changes only when the file does.
 *
 * A miss is not remembered: "no name" usually means the file did not expand, and
 * a later deploy may still provide one.
 */
class ThemeNameCache {
    private class Entry(
        val stamp: Long,
        val size: Long,
        val name: String,
    )

    private val entries = HashMap<String, Entry>()

    fun nameOf(
        file: File,
        read: (File) -> String?,
    ): String? {
        val path = file.absolutePath
        val stamp = file.lastModified()
        val size = file.length()
        synchronized(entries) {
            entries[path]?.takeIf { it.stamp == stamp && it.size == size }?.let { return it.name }
        }
        val name = read(file) ?: return null
        synchronized(entries) { entries[path] = Entry(stamp, size, name) }
        return name
    }
}
