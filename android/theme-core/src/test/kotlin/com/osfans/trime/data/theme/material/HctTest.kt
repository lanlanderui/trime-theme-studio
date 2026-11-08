/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * The colour maths, pinned on what can be checked without a Material reference
 * implementation at hand: a colour must survive a trip through HCT, and a
 * palette must keep the lightness steps it promises.
 *
 * A wrong constant in the CAM16 matrices shifts hues subtly — invisible in a
 * screenshot, obvious here.
 */
class HctTest :
    BehaviorSpec({
        Given("any sRGB colour") {
            Then("survives HCT and back, channel by channel") {
                val samples =
                    listOf(
                        0xFF6750A4.toInt(), // Material's baseline purple
                        0xFFB3261E.toInt(), // red
                        0xFF2E7D32.toInt(), // green
                        0xFF0061A4.toInt(), // blue
                        0xFFFFD54F.toInt(), // yellow
                        0xFF7D5260.toInt(), // dusty rose
                        0xFF808080.toInt(), // mid grey
                    )
                samples.forEach { argb ->
                    val cam = Cam16.fromArgb(argb)
                    val roundTrip = Hct.solve(cam.hue, cam.chroma, ColorMath.lstarFromArgb(argb))
                    channelDistance(argb, roundTrip) shouldBe (0.0 plusOrMinus 2.0)
                }
            }

            Then("reports the tone Material reports") {
                // Tone is CIELAB L*: black is 0, white is 100, mid grey ~53.6.
                ColorMath.lstarFromArgb(0xFF000000.toInt()) shouldBe (0.0 plusOrMinus 0.01)
                ColorMath.lstarFromArgb(0xFFFFFFFF.toInt()) shouldBe (100.0 plusOrMinus 0.01)
                ColorMath.lstarFromArgb(0xFF808080.toInt()) shouldBe (53.6 plusOrMinus 0.5)
            }
        }

        Given("a tonal palette") {
            val palette = TonalPalette.fromSeed(0xFF6750A4.toInt())

            Then("hits the ends of the scale exactly") {
                palette.tone(0) shouldBe 0xFF000000.toInt()
                palette.tone(100) shouldBe 0xFFFFFFFF.toInt()
            }

            Then("keeps the seed's hue at every stop") {
                val expected = Cam16.fromArgb(0xFF6750A4.toInt()).hue
                listOf(10, 30, 50, 70, 90).forEach { tone ->
                    ColorMath.hueDistance(Cam16.fromArgb(palette.tone(tone)).hue, expected) shouldBe
                        (0.0 plusOrMinus 6.0)
                }
            }

            Then("gets lighter at every step") {
                val tones = TONE_STOPS.map { ColorMath.lstarFromArgb(palette.tone(it)) }
                tones.zipWithNext().forEach { (lower, higher) ->
                    (higher - lower) shouldBe (10.0 plusOrMinus 1.0)
                }
            }

            Then("reproduces the seed at its own tone") {
                // Material's primary palette is the seed when the seed already
                // carries the chroma the scheme asks for, which is why changing
                // a wallpaper's accent shows up as that very colour.
                val seed = 0xFF6750A4.toInt()
                val cam = Cam16.fromArgb(seed)
                val rebuilt = TonalPalette.of(cam.hue, cam.chroma).tone(ColorMath.lstarFromArgb(seed).toInt())
                channelDistance(seed, rebuilt) shouldBe (0.0 plusOrMinus 3.0)
            }
        }

        Given("a hue that sRGB cannot hold at that chroma") {
            Then("gives up chroma, not tone") {
                // A vivid cyan at tone 90 does not fit; the result must still be
                // a 90, which is what keeps a palette readable top to bottom.
                val argb = Hct.solve(200.0, 120.0, 90.0)
                ColorMath.lstarFromArgb(argb) shouldBe (90.0 plusOrMinus 1.5)
            }
        }
    })

/** Mean absolute channel difference, 0..255. */
private fun channelDistance(
    a: Int,
    b: Int,
): Double {
    val dr = abs(ColorMath.red(a) - ColorMath.red(b))
    val dg = abs(ColorMath.green(a) - ColorMath.green(b))
    val db = abs(ColorMath.blue(a) - ColorMath.blue(b))
    return (dr + dg + db) / 3.0
}
