/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on disk, and hands it back on the next start.
 *
 * The default handler is *wrapped*, not replaced: the log is written first and
 * then the previous handler runs, so the platform still shows its own crash
 * dialog and the process still dies. Swallowing the throwable here would turn a
 * bug into a hang, which is harder to report and impossible to symbolicate.
 *
 * Written to the app's own `filesDir`: no permission, cleared on uninstall, and
 * one file — an editor that writes a log per crash grows a directory nobody
 * reads.
 */
object CrashLog {
    private const val FILE_NAME = "crash.log"

    /** Cap for the whole file; one report is a few KB. */
    internal const val MAX_BYTES = 256 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Whether the previous run ended in a crash the user has not seen yet. */
    fun hasReport(context: Context): Boolean = file(context).let { it.isFile && it.length() > 0 }

    fun read(context: Context): String? = runCatching { file(context).readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * Installs the handler. Safe to call twice: the previous handler is captured
     * once and the guard flag stops a chain of wrappers.
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // Let the platform do what it always does: show its dialog, kill us.
            previous?.uncaughtException(thread, error)
        }
    }

    private var installed = false

    private fun write(
        context: Context,
        thread: Thread,
        error: Throwable,
    ) {
        val target = file(context)
        // Rotation happens before the append, so a runaway log is dropped as a
        // whole instead of being trimmed: half a stack trace is worse than none.
        if (shouldRotate(target.length())) target.delete()
        target.appendText(format(stampNow(), thread.name, error))
    }

    private fun stampNow(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())

    /** Whether a file of [bytes] is already too big to append to. */
    internal fun shouldRotate(bytes: Long): Boolean = bytes > MAX_BYTES

    /**
     * One report: a header saying when and on which thread, then the stack.
     *
     * Split from the writing so its shape can be checked without a device — the
     * file path, the cap and the `Context` are the only parts that need one.
     * The trailing newline separates two reports that end up in the same file.
     */
    internal fun format(
        stamp: String,
        threadName: String,
        error: Throwable,
    ): String = buildString {
        append("==== ")
        append(stamp)
        append("  thread=")
        append(threadName)
        append('\n')
        append(StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString())
        append('\n')
    }
}
