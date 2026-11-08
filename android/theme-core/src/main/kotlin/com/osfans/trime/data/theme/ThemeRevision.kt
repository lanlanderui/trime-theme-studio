/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File
import com.osfans.trime.data.theme.ThemeLog as Timber

/**
 * The cross-process "this directory changed" counter.
 *
 * [ThemeTextFile.writeGeneration] only counts the saves *this* process made, so
 * it cannot tell the editor's save from no save at all when the keyboard reads
 * the same file from another app. Timestamps are no better: on a FUSE mount
 * (`/storage/emulated/0`) a replaced file keeps the old mtime, which is exactly
 * the "I edited it and nothing happened" report this file exists to prevent.
 *
 * So every successful write bumps a counter stored next to the themes, and a
 * reader that cached a decoded theme compares the number it saw against the
 * number on disk. The file is deliberately tiny and human-readable: a user who
 * wonders what it is can read it, and a reader costs one small `readText`.
 *
 * The counter is not required to be strictly monotonic — it only has to *change*
 * when a write happens. Two apps bumping at the same time may both write the
 * same number, and that is fine: the per-file size/mtime/sha256 check in
 * [ThemeSourceSnapshot] catches the content that the shared number missed.
 */
object ThemeRevision {
    const val FILE_NAME = ".revision"

    private val COUNTER = Regex("""(?m)^revision:\s*(\d+)\s*$""")

    fun fileOf(dir: File): File = File(dir, FILE_NAME)

    /** The counter next to [themeFile]; 0 when it is absent or unreadable. */
    fun of(themeFile: File): Long = themeFile.parentFile?.let { read(it) } ?: 0

    /** The counter in [dir]; 0 when there is none, which is also what a fresh directory reports. */
    fun read(dir: File): Long = readText(fileOf(dir))

    private fun readText(file: File): Long {
        val text = runCatching { file.readText() }.getOrNull() ?: return 0
        return COUNTER.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    }

    /**
     * Records that [target] was just written, and returns the new counter.
     *
     * Called from [ThemeTextFile.replaceTextChecked] rather than from the
     * hosts, so a writer cannot forget it and a new writer gets it for free.
     * A failure here never fails the save: the theme is already on disk, and
     * the worst case is a reader that has to fall back on the content check.
     */
    fun bump(target: File): Long {
        val dir = target.parentFile ?: return 0
        val file = fileOf(dir)
        val next = readText(file) + 1
        val text =
            buildString {
                appendLine("# 同文主题修改计数：编辑器与输入法据此判断主题是否被另一个进程改过。")
                appendLine("# 由 ThemeTextFile 在每次成功写盘后更新，可安全删除（删除后重新从 1 计）。")
                appendLine("revision: $next")
                appendLine("file: ${target.name}")
                append("at: ${System.currentTimeMillis()}")
            }
        return runCatching {
            // One small write, not temp+rename: a torn read can only lower the
            // number, and a reader treats any change as "reload", so the cost
            // of the rare tear is one extra read rather than a stale theme.
            file.writeText(text)
            next
        }.getOrElse { error ->
            Timber.w(error, "Cannot update %s", FILE_NAME)
            0L
        }
    }
}
