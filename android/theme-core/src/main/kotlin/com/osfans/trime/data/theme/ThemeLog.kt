/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * 主题层的日志出口。
 *
 * `theme-core` 是纯 JVM 模块，不能依赖 Timber（它是 AAR）。
 * 这里提供 Timber 的一个最小兼容子集，搬过来的文件只改一行 import
 * （`import ...ThemeLog as Timber`），调用点原样保留——与测试3 的差异因此只有一行，
 * 将来合并时不会产生噪声 diff。
 *
 * 不注入 [sink] 时静默，与「未 plant 的 Timber」行为一致。
 */
object ThemeLog {
    /** 由宿主注入（编辑器 / 输入法把它接到 Timber）。 */
    var sink: (priority: Char, message: String, throwable: Throwable?) -> Unit = { _, _, _ -> }

    fun d(
        message: String,
        vararg args: Any?,
    ): Int = log('D', null, message, args)

    fun d(
        t: Throwable?,
        message: String,
        vararg args: Any?,
    ): Int = log('D', t, message, args)

    fun w(
        message: String,
        vararg args: Any?,
    ): Int = log('W', null, message, args)

    fun w(
        t: Throwable?,
        message: String,
        vararg args: Any?,
    ): Int = log('W', t, message, args)

    fun e(
        message: String,
        vararg args: Any?,
    ): Int = log('E', null, message, args)

    fun e(
        t: Throwable?,
        message: String,
        vararg args: Any?,
    ): Int = log('E', t, message, args)

    private fun log(
        priority: Char,
        t: Throwable?,
        message: String,
        args: Array<out Any?>,
    ): Int {
        val text =
            if (args.isEmpty()) {
                message
            } else {
                runCatching { String.format(message, *args) }.getOrDefault(message)
            }
        sink(priority, text, t)
        return 0
    }
}
