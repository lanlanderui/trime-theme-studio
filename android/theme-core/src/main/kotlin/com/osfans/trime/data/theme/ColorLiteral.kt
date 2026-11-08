/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.util.Locale

/**
 * The colour literals a theme file is written with.
 *
 * Themes mix `0xRRGGBB`, `0xAARRGGBB` and `#RRGGBB`, and a few keys hold a
 * background image name instead of a colour at all. Both directions live here
 * rather than in the editor: they are small, they are easy to get subtly wrong,
 * and neither needs a device to check.
 */
object ColorLiteral {
    private const val DEFAULT_PREFIX = "0x"

    /**
     * `#rgb`, `#rrggbb` and `#aarrggbb`, with either prefix.
     *
     * Null for anything else — a blank field, a half-typed value, a background
     * image name — so callers can tell "not a colour yet" from "black".
     */
    fun parse(raw: String?): Int? {
        val value = digitsOf(raw)
        if (value.isEmpty()) return null
        val hex =
            when (value.length) {
                3 -> "ff" + value.map { "$it$it" }.joinToString("")
                6 -> "ff$value"
                8 -> value
                else -> return null
            }
        return hex.toLongOrNull(16)?.toInt()
    }

    /**
     * [argb] as the scheme would write it, following [like] where it can.
     *
     * The prefix is kept from the value that was there, since rewriting
     * `#RRGGBB` into `0xRRGGBB` on every edit churns the file for no reason.
     * Hex digits come out lowercase, which is how most of the shipped theme
     * writes them. The alpha bytes are only dropped when the colour is opaque
     * *and* the original did not carry them, so editing one channel of
     * `0xffffd27a` keeps the shape of the line.
     */
    fun format(
        argb: Int,
        like: String?,
    ): String {
        val prefix = if (like.orEmpty().trimStart().startsWith("#")) "#" else DEFAULT_PREFIX
        val opaque = ((argb ushr 24) and 0xFF) == 0xFF
        val keepAlpha = !opaque || digitsOf(like).length == 8
        return if (keepAlpha) {
            String.format(Locale.ROOT, "%s%08x", prefix, argb)
        } else {
            String.format(Locale.ROOT, "%s%06x", prefix, argb and 0xFFFFFF)
        }
    }

    private fun digitsOf(raw: String?): String = raw
        ?.trim()
        .orEmpty()
        .removePrefix("0x")
        .removePrefix("0X")
        .removePrefix("#")
}
