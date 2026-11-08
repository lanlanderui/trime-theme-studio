/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CAM16 colour appearance model, the space Material's tonal palettes are cut in.
 *
 * Only the default viewing conditions are modelled: the palettes are decided
 * once, off-screen, and then stored as plain ARGB — nothing at render time
 * depends on how the room is lit.
 *
 * The constants are the CAT16 matrices and the CIECAM16 surround factors from
 * the reference implementation, not values tuned here.
 */
class ViewingConditions private constructor(
    /** Background induction factor. */
    val n: Double,
    /** Absolute luminance of the white point. */
    val aw: Double,
    /** Luminance-level adaptation factor. */
    val nbb: Double,
    /** Achromatic response factor. */
    val ncb: Double,
    /** Surround impact on chroma. */
    val c: Double,
    /** Chromatic surround. */
    val nc: Double,
    /** Per-channel degree of adaptation. */
    val rgbD: DoubleArray,
    /** Luminance adaptation factor. */
    val fl: Double,
    /** Base for the lightness exponent. */
    val z: Double,
) {
    companion object {
        /** Mid-grey background under a D65 white: the conditions Material evaluates at. */
        val DEFAULT: ViewingConditions by lazy {
            make(
                whitePoint = doubleArrayOf(95.047, 100.0, 108.883),
                adaptingLuminance = 200.0 / PI * ColorMath.yFromLstar(50.0) / 100.0,
                backgroundLstar = 50.0,
                surround = 2.0,
                discounting = false,
            )
        }

        fun make(
            whitePoint: DoubleArray,
            adaptingLuminance: Double,
            backgroundLstar: Double,
            surround: Double,
            discounting: Boolean,
        ): ViewingConditions {
            val rW = whitePoint[0] * 0.401288 + whitePoint[1] * 0.650173 + whitePoint[2] * -0.051461
            val gW = whitePoint[0] * -0.250268 + whitePoint[1] * 1.204414 + whitePoint[2] * 0.045854
            val bW = whitePoint[0] * -0.002079 + whitePoint[1] * 0.048952 + whitePoint[2] * 0.953127

            val f = 0.8 + surround / 10.0
            val c = if (f >= 0.9) lerp(0.59, 0.69, (f - 0.9) * 10.0) else lerp(0.525, 0.59, (f - 0.8) * 10.0)
            val d =
                if (discounting) {
                    1.0
                } else {
                    (f * (1.0 - 1.0 / 3.6 * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)
                }
            val rgbD =
                doubleArrayOf(
                    d * whitePoint[1] / rW + 1.0 - d,
                    d * whitePoint[1] / gW + 1.0 - d,
                    d * whitePoint[1] / bW + 1.0 - d,
                )
            val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
            val k4 = k * k * k * k
            val k4F = 1.0 - k4
            val fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * (5.0 * adaptingLuminance).pow(1.0 / 3.0)
            val n = ColorMath.yFromLstar(backgroundLstar) / whitePoint[1]
            val nbb = 0.725 / n.pow(0.2)
            return ViewingConditions(
                n = n,
                aw = (2.0 * response(fl, rgbD[0] * rW) + response(fl, rgbD[1] * gW) + response(fl, rgbD[2] * bW) * 0.05) * nbb,
                nbb = nbb,
                ncb = nbb,
                c = c,
                nc = f,
                rgbD = rgbD,
                fl = fl,
                z = 1.48 + sqrt(n),
            )
        }

        /** The 400·x^0.42/(x^0.42+27.13) cone compression, applied signed. */
        private fun response(
            fl: Double,
            value: Double,
        ): Double {
            val af = (fl * abs(value) / 100.0).pow(0.42)
            return sign(value) * 400.0 * af / (af + 27.13)
        }

        private fun lerp(
            a: Double,
            b: Double,
            t: Double,
        ): Double = a + (b - a) * t
    }
}

/** One colour as CAM16 sees it: [hue] in degrees, [chroma] and [j] on 0..100. */
class Cam16(
    val hue: Double,
    val chroma: Double,
    val j: Double,
    private val xyz: DoubleArray,
) {
    /**
     * Linear-light sRGB, unclamped.
     *
     * Values outside 0..1 mean the colour cannot be shown, which is how the
     * palette generator knows to give up chroma rather than clip a channel.
     */
    fun toLinearRgb(): DoubleArray {
        val r = (3.2406 * xyz[0] - 1.5372 * xyz[1] - 0.4986 * xyz[2]) / 100.0
        val g = (-0.9689 * xyz[0] + 1.8758 * xyz[1] + 0.0415 * xyz[2]) / 100.0
        val b = (0.0557 * xyz[0] - 0.2040 * xyz[1] + 1.0570 * xyz[2]) / 100.0
        return doubleArrayOf(r, g, b)
    }

    /** Luminance on the 0..100 scale: what a tone is defined by. */
    fun y(): Double = xyz[1]

    val isInGamut: Boolean
        get() {
            val rgb = toLinearRgb()
            return rgb[0] >= -GAMUT_EPSILON && rgb[0] <= 1.0 + GAMUT_EPSILON &&
                rgb[1] >= -GAMUT_EPSILON && rgb[1] <= 1.0 + GAMUT_EPSILON &&
                rgb[2] >= -GAMUT_EPSILON && rgb[2] <= 1.0 + GAMUT_EPSILON
        }

    /** sRGB gamma-compressed channels, 0..1, clipped. */
    fun toSrgb(): DoubleArray = toLinearRgb().map { ColorMath.delinearize(it) }.toDoubleArray()

    companion object {
        private const val GAMUT_EPSILON = 1e-4

        /** The colour of [argb] under [vc]. */
        fun fromArgb(
            argb: Int,
            vc: ViewingConditions = ViewingConditions.DEFAULT,
        ): Cam16 {
            val r = ColorMath.linearize(ColorMath.red(argb) / 255.0)
            val g = ColorMath.linearize(ColorMath.green(argb) / 255.0)
            val b = ColorMath.linearize(ColorMath.blue(argb) / 255.0)
            val x = (r * 0.41233895 + g * 0.35762064 + b * 0.18051042) * 100.0
            val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100.0
            val z = (r * 0.01932141 + g * 0.11916382 + b * 0.95034478) * 100.0
            return fromXyz(x, y, z, vc)
        }

        /**
         * The XYZ of (J, C, h), on the 0..100 scale.
         *
         * CIECAM16's inverse: recover the achromatic response and the two
         * opponent axes, undo the cone compression per channel, then un-adapt
         * and rotate back to XYZ.
         */
        fun fromJch(
            j: Double,
            chroma: Double,
            hueDegrees: Double,
            vc: ViewingConditions = ViewingConditions.DEFAULT,
        ): Cam16 = Cam16(hueDegrees, chroma, j, xyzFor(j, chroma, hueDegrees, vc))

        private fun xyzFor(
            j: Double,
            chroma: Double,
            hueDegrees: Double,
            vc: ViewingConditions,
        ): DoubleArray {
            val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
            val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
            val hRad = Math.toRadians(hueDegrees)
            val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
            val ac = vc.aw * (j / 100.0).pow(1.0 / vc.c / vc.z)
            val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
            val p2 = ac / vc.nbb
            val hSin = sin(hRad)
            val hCos = cos(hRad)
            // 23·t·(p2 + 0.305) is the algebraic inverse of the forward
            // t = p1·|(a,b)| / (u + 0.305) — the 0.305 is that denominator's
            // constant, not a second p1 term.
            val gamma = 23.0 * t * (p2 + 0.305) / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
            val a = gamma * hCos
            val b = gamma * hSin
            val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
            val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
            val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
            val r = unAdapt(vc, rA, 0)
            val g = unAdapt(vc, gA, 1)
            val bb = unAdapt(vc, bA, 2)
            return doubleArrayOf(
                1.86206786 * r - 1.01125463 * g + 0.14918677 * bb,
                0.38752654 * r + 0.62144744 * g - 0.00897398 * bb,
                -0.01584150 * r - 0.03412294 * g + 1.04996444 * bb,
            )
        }

        private fun unAdapt(
            vc: ViewingConditions,
            response: Double,
            channel: Int,
        ): Double {
            val base = 27.13 * abs(response) / (400.0 - abs(response))
            return sign(response) * (100.0 / vc.fl) * base.pow(1.0 / 0.42) / vc.rgbD[channel]
        }

        private fun fromXyz(
            x: Double,
            y: Double,
            z: Double,
            vc: ViewingConditions,
        ): Cam16 {
            val rA = compress(vc, 0.401288 * x + 0.650173 * y - 0.051461 * z, 0)
            val gA = compress(vc, -0.250268 * x + 1.204414 * y + 0.045854 * z, 1)
            val bA = compress(vc, -0.002079 * x + 0.048952 * y + 0.953127 * z, 2)
            val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
            val b = (rA + gA - 2.0 * bA) / 9.0
            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
            val hue = ColorMath.sanitizeDegrees(Math.toDegrees(atan2(b, a)))
            val j = 100.0 * (p2 * vc.nbb / vc.aw).pow(vc.c * vc.z)
            val huePrime = if (hue < 20.14) hue + 360.0 else hue
            val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * hypot(a, b) / (u + 0.305)
            val alpha = t.pow(0.9) * (1.64 - 0.29.pow(vc.n)).pow(0.73)
            return Cam16(hue, alpha * sqrt(j / 100.0), j, doubleArrayOf(x, y, z))
        }

        private fun compress(
            vc: ViewingConditions,
            channel: Double,
            index: Int,
        ): Double {
            val adapted = vc.rgbD[index] * channel
            val af = (vc.fl * abs(adapted) / 100.0).pow(0.42)
            return sign(adapted) * 400.0 * af / (af + 27.13)
        }
    }
}
