/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import kotlin.math.roundToInt

/**
 * HCT: CAM16's hue and chroma with CIELAB's tone.
 *
 * Material fixes palette *tone* in L\* — the reason a palette has even
 * lightness steps — while keeping hue and chroma in CAM16, which predicts how
 * saturated a colour looks far better than Lab does.
 */
object Hct {
    fun hue(argb: Int): Double = Cam16.fromArgb(argb).hue

    fun chroma(argb: Int): Double = Cam16.fromArgb(argb).chroma

    fun tone(argb: Int): Double = ColorMath.lstarFromArgb(argb)

    /** Whether [argb] is inside the sRGB gamut by definition; used by tests. */
    fun of(argb: Int): Triple<Double, Double, Double> {
        val cam = Cam16.fromArgb(argb)
        return Triple(cam.hue, cam.chroma, ColorMath.lstarFromArgb(argb))
    }

    /**
     * The colour with this hue, chroma and tone, as close as sRGB allows.
     *
     * Chroma is given up before tone: a palette whose 90 is lighter than its 80
     * looks broken, while a slightly less vivid 90 is invisible. The search
     * walks tone first (J until the luminance matches), then reduces chroma
     * until every channel fits.
     */
    fun solve(
        hue: Double,
        chroma: Double,
        tone: Double,
    ): Int {
        if (tone <= 0.01) return ARGB_BLACK
        if (tone >= 99.99) return ARGB_WHITE
        val hueNormalized = ColorMath.sanitizeDegrees(hue)
        val wantedChroma = chroma.coerceAtLeast(0.0)
        if (wantedChroma < 0.001) return greyOfTone(tone)

        val y = ColorMath.yFromLstar(tone)
        argbFor(hueNormalized, wantedChroma, y)?.let { return it }

        // Out of gamut: bisect the chroma down to the edge. Chroma 0 is a grey
        // of exactly this tone, so the low end is always representable.
        var low = 0.0
        var high = wantedChroma
        var fallback = greyOfTone(tone)
        repeat(12) {
            val mid = (low + high) / 2.0
            val candidate = argbFor(hueNormalized, mid, y)
            if (candidate == null) {
                high = mid
            } else {
                fallback = candidate
                low = mid
            }
        }
        return fallback
    }

    /**
     * [hue]/[chroma] at the luminance [y], or null when outside sRGB.
     *
     * J is solved by bisection: for fixed hue and chroma, luminance rises
     * monotonically with J, so 18 halvings land inside a rounding step.
     */
    private fun argbFor(
        hue: Double,
        chroma: Double,
        y: Double,
    ): Int? {
        var low = 0.0
        var high = 100.0
        var cam = Cam16.fromJch(high, chroma, hue)
        repeat(18) {
            val mid = (low + high) / 2.0
            cam = Cam16.fromJch(mid, chroma, hue)
            if (cam.y() < y) low = mid else high = mid
        }
        if (!cam.isInGamut) return null
        return argbOf(cam)
    }

    /** sRGB grey of [tone], used for black/white and the chroma-free fallback. */
    fun greyOfTone(tone: Double): Int {
        val y = ColorMath.yFromLstar(tone)
        val channel = (ColorMath.delinearize(y / 100.0) * 255.0).roundToInt().coerceIn(0, 255)
        return ColorMath.argb(255, channel, channel, channel)
    }

    private fun argbOf(cam: Cam16): Int {
        val rgb = cam.toSrgb()
        return ColorMath.argb(
            255,
            (rgb[0] * 255.0).roundToInt().coerceIn(0, 255),
            (rgb[1] * 255.0).roundToInt().coerceIn(0, 255),
            (rgb[2] * 255.0).roundToInt().coerceIn(0, 255),
        )
    }

    const val ARGB_BLACK = 0xFF000000.toInt()
    const val ARGB_WHITE = 0xFFFFFFFF.toInt()
}

/**
 * One tonal palette: a hue and a chroma, sampled at Material's tone stops.
 *
 * The stops are the ones the schemes reference (10, 20, … 100); asking for a
 * tone in between is allowed and just costs another solve.
 */
class TonalPalette private constructor(
    val hue: Double,
    val chroma: Double,
) {
    private val cache = HashMap<Int, Int>()

    fun tone(tone: Int): Int = cache.getOrPut(tone) { Hct.solve(hue, chroma, tone.toDouble()) }

    companion object {
        fun of(
            hue: Double,
            chroma: Double,
        ): TonalPalette = TonalPalette(ColorMath.sanitizeDegrees(hue), chroma.coerceAtLeast(0.0))

        /** The hue and chroma of [seed], at the chroma Material keeps for a seed. */
        fun fromSeed(seed: Int): TonalPalette = Cam16.fromArgb(seed).let { of(it.hue, it.chroma) }
    }
}
