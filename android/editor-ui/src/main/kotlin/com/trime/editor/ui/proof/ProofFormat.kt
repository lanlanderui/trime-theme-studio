/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import java.util.Locale

/**
 * 一行的文字怎么排：十六进制怎么写、「未设置」怎么认、项数怎么说。
 *
 * 故意不碰 Android：这些都是纯字符串规则，能放在 JVM 上单测。行视图只负责
 * 把结果贴到 TextView 上——两件事混在一起，颜色写法一改就得开模拟器验证。
 */
object ProofFormat {
    const val UNSET: String = "未设置"
    const val TRANSPARENT: String = "透明"

    /** 主题里一行可以没写这个键，也可以写了空值；两种都算「未设置」。 */
    fun isUnset(raw: String?): Boolean = raw.isNullOrBlank()

    /**
     * 把主题里的写法统一成 `#RRGGBB`（不透明）或 `#AARRGGBB`（带透明度）。
     *
     * 主题里有三种写法：`0xffe1e3e7`（多数）、`#E1E3E7`（少数手写）、`0x00`
     * （完全透明，只有两位）。认不出来就原样返回——把不认识的字符串硬转成
     * 一个颜色，比显示不出来更糟：用户会以为文件里写的就是那个色。
     */
    fun hex(raw: String?): String {
        val argb = colorOf(raw) ?: return raw?.trim().orEmpty()
        return hex(argb)
    }

    /** ARGB → `#RRGGBB` / `#AARRGGBB` / `透明`。解析出来的颜色走这一条。 */
    fun hex(argb: Int): String = when (val alpha = (argb ushr 24) and 0xFF) {
        0 -> TRANSPARENT
        0xFF -> String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
        else -> String.format(Locale.ROOT, "#%08X", argb)
    }

    /**
     * 颜色字符串 → ARGB。
     *
     * 三种写法各有各的意思，不能一律左补 0：
     * - 八位 `ffe1e3e7` → 本来就是 ARGB；
     * - 六位 `E1E3E7` → CSS 那种 RGB，透明度按不透明算，补 **FF**；
     * - 两位 `00` → 主题里就写了一个字节（全透明），补 0 才不会把它当白色。
     *
     * 其余情况（长度古怪、含非十六进制字符）返回 null，交给 [hex] 原样显示。
     */
    fun colorOf(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        val body =
            when {
                text.startsWith("0x", ignoreCase = true) -> text.substring(2)
                text.startsWith("#") -> text.substring(1)
                else -> return null
            }
        if (body.isEmpty() || body.length > 8) return null
        if (body.any { it.digitToIntOrNull(16) == null }) return null
        val value = body.toLong(16).toInt()
        return if (body.length == 6) 0xFF000000.toInt() or value else value
    }

    /** `*color2` 是引用锚点，不是颜色值；认出来才能在行里单独标色。 */
    fun anchor(raw: String?): String? = raw?.trim()?.takeIf { it.startsWith("*") && it.length > 1 }

    /**
     * 样张下面那行统计。两段都有才用「·」连；都为 0 就返回空串，由调用方整行隐藏。
     *
     * 不写「未设 0」：主题本身完整时这一行应该安静，而不是报一个 0 让人怀疑哪里错了。
     */
    fun caption(
        unset: Int,
        changed: Int,
    ): String = buildList {
        if (unset > 0) add("未设 $unset")
        if (changed > 0) add("本次已改 $changed")
    }.joinToString(" · ")

    /** 「11 项 / 已改 2」；没改过就不显示后半句，免得满屏「已改 0」。 */
    fun count(
        count: Int,
        changed: Int = 0,
    ): String = if (changed > 0) "$count 项 / 已改 $changed" else "$count 项"
}
