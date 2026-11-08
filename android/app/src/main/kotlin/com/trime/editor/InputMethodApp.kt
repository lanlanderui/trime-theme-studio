/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import timber.log.Timber

/**
 * The keyboard app this editor writes themes for.
 *
 * The editor and the keyboard are separate apps sharing one theme directory, so
 * every round trip — change a colour, look at the real keyboard, come back and
 * change another — means switching apps by hand. "回到输入法" does that switch,
 * which is the only way the loop can be short enough to be worth doing.
 *
 * The package is looked up from the system's own list of enabled input methods
 * rather than hardcoded, because several builds of the keyboard can be
 * installed side by side and the one that matters is the one the system would
 * actually show.
 */
object InputMethodApp {
    /**
     * 已知的输入法包名，**按优先级排列**：原版同文输入法在前。
     *
     * `com.osfans.trime03` 是一次外观分支试验的包名（已不再推进），但装着它的
     * 设备仍然存在，所以留作兜底——换回原版之前，「回到输入法」不至于点不动。
     * 同一台机器上装了两个时，[enabledPackage] 按这个顺序挑，除非系统默认输入法
     * 明确指向其中一个。
     */
    private val KNOWN_PACKAGES =
        listOf(
            "com.osfans.trime", // 原版同文输入法
            "com.osfans.trime.debug", // 原版调试包
            "com.osfans.trime03", // 之前的外观分支，保留兜底
            "com.osfans.trime03.debug",
        )

    /**
     * The keyboard package to open, or null when none of [KNOWN_PACKAGES] is enabled.
     *
     * **优先级说了算，不看系统默认**：03 那一支已经不再推进，即使它此刻还是
     * 默认输入法，这里也应该开原版——不然「回到输入法」会一直把人送回一个
     * 已经放弃的分支。默认输入法只在同一优先级内参与（见 [pick]）。
     *
     * Read through `InputMethodManager` rather than `Settings.Secure`: from
     * Android 15 on, `enabled_input_methods` is only readable by apps targeting
     * API 33 or lower, and reading it throws instead of returning null.
     */
    fun enabledPackage(context: Context): String? {
        val enabled =
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.enabledInputMethodList
                ?.map { it.packageName }
                ?.distinct()
                ?.filter { it in KNOWN_PACKAGES }
                // enabledInputMethodList 不保证顺序；按 KNOWN_PACKAGES 的优先级排，
                // 否则「装了两个」时打开哪个由系统心情决定。
                ?.sortedBy { KNOWN_PACKAGES.indexOf(it) }
                .orEmpty()
        // 默认输入法仍然读一下：同一优先级里有多个时（理论上只有同名的
        // release/debug 变体），它是唯一能区分「用户此刻在用哪个」的信息。
        val default =
            runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }
                .getOrNull()
                ?.substringBefore('/')
        return pick(enabled, default)
    }

    /**
     * 从已启用的输入法里挑一个打开：**优先级最高的那一档**，同档内再看系统默认。
     *
     * 原版（`com.osfans.trime`）永远压过 03，哪怕 03 还是默认输入法——用户已经
     * 放弃那一支，再把他送回去就是没修好。抽成纯函数是为了能单测：设备上只装了
     * 一个时，这个顺序根本验不出来。
     */
    @VisibleForTesting
    internal fun pick(
        enabled: List<String>,
        default: String?,
    ): String? {
        if (enabled.isEmpty()) return null
        val best = enabled.minOf { KNOWN_PACKAGES.indexOf(it) }
        val top = enabled.filter { KNOWN_PACKAGES.indexOf(it) == best }
        return top.firstOrNull { it == default } ?: top.first()
    }

    /**
     * Whether the keyboard app can be opened at all.
     *
     * Checked before the menu item is shown: an item that only ever reports
     * "not installed" is noise on a phone that does not have the keyboard.
     */
    fun isInstalled(context: Context): Boolean = packageToOpen(context) != null

    /** Opens the keyboard app; false when there is nothing to open. */
    fun launch(context: Context): Boolean {
        val pkg = packageToOpen(context)
        if (pkg == null) {
            Toast.makeText(context, R.string.back_to_ime_missing, Toast.LENGTH_LONG).show()
            return false
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Timber.w("Input method %s has no launcher activity", pkg)
            Toast.makeText(context, R.string.back_to_ime_missing, Toast.LENGTH_LONG).show()
            return false
        }
        // The editor stays in the back stack: the point of the trip is to come
        // straight back and change one more thing.
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse {
            Timber.w(it, "Cannot open input method %s", pkg)
            Toast.makeText(context, R.string.back_to_ime_missing, Toast.LENGTH_LONG).show()
            false
        }
    }

    /** The enabled keyboard if it can be opened, otherwise any installed one. */
    private fun packageToOpen(context: Context): String? {
        val enabled = enabledPackage(context)
        if (enabled != null && context.packageManager.getLaunchIntentForPackage(enabled) != null) return enabled
        return KNOWN_PACKAGES.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
    }
}
