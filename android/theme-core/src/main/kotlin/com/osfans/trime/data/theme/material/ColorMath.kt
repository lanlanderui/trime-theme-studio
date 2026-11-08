/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * The colour-space arithmetic the Material palettes need.
 *
 * Everything here is pure (no `android.graphics`), so the whole generator can
 * be checked on the JVM — a wrong constant in these matrices shifts every hue
 * in the keyboard and is invisible in a screenshot.
 */
object ColorMath {
    /** Alpha, red, green, blue of an ARGB int, 0..255. */
    fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF

    fun red(argb: Int): Int = (argb ushr 16) and 0xFF

    fun green(argb: Int): Int = (argb ushr 8) and 0xFF

    fun blue(argb: Int): Int = argb and 0xFF

    fun argb(
        a: Int,
        r: Int,
        g: Int,
        b: Int,
    ): Int = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** CIE L* of a luminance on the 0..100 scale (tone, in Material's terms). */
    fun lstarFromY(y: Double): Double = labF(y / 100.0) * 116.0 - 16.0

    /** Luminance on the 0..100 scale that a tone (L*) stands for. */
    fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

    fun lstarFromArgb(argb: Int): Double = lstarFromY(relativeLuminance(argb))

    private fun labF(t: Double): Double = if (t > E) cbrt(t) else (KAPPA * t + 16.0) / 116.0

    private fun labInvf(ft: Double): Double {
        val ft3 = ft * ft * ft
        return if (ft3 > E) ft3 else (116.0 * ft - 16.0) / KAPPA
    }

    /** Rec. 709 luminance on the 0..100 scale, used for tone and for contrast. */
    fun relativeLuminance(argb: Int): Double {
        val r = linearize(red(argb) / 255.0)
        val g = linearize(green(argb) / 255.0)
        val b = linearize(blue(argb) / 255.0)
        return (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100.0
    }

    /** Gamma expansion: an sRGB channel 0..1 to a linear 0..1 channel. */
    fun linearize(channel: Double): Double = if (channel <= 0.040449936) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    /** Gamma compression: a linear 0..1 channel back to sRGB 0..1, clamped. */
    fun delinearize(channel: Double): Double {
        val clamped = channel.coerceIn(0.0, 1.0)
        return if (clamped <= 0.0031308) clamped * 12.92 else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
    }

    /** Wraps an angle into 0 until 360. */
    fun sanitizeDegrees(degrees: Double): Double {
        var value = degrees % 360.0
        if (value < 0) value += 360.0
        return value
    }

    /** The shorter of the two arcs between two hues, in degrees. */
    fun hueDistance(
        a: Double,
        b: Double,
    ): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private const val E = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0
}
