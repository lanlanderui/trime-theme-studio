/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import com.osfans.trime.data.theme.ColorKey
import com.osfans.trime.data.theme.ColorLiteral
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

/**
 * What a generated scheme promises the theme: every key the theme can paint.
 *
 * "Complete" is not a feeling here — the key list is compared with the sample
 * template's own scheme in `TemplateThemeTest`, and the pairs that end up on
 * screen together are checked for contrast, because a palette that looks lovely
 * in a swatch row and makes the candidates unreadable is the one failure mode
 * that matters.
 */
class MaterialSchemeTest :
    BehaviorSpec({
        Given("a palette grown from a seed") {
            val colors = MaterialScheme.colors(MaterialPalettes.fromSeed(0xFF6750A4.toInt()))

            Then("defines every key in both modes") {
                // Insertion order is the order the theme file gets written in.
                colors.light.keys.toList() shouldContainExactly MaterialScheme.keys
                colors.dark.keys.toList() shouldContainExactly MaterialScheme.keys
            }

            Then("covers the whole theme surface, not a subset") {
                // 52 typed keys plus the two the theme reads by name.
                MaterialScheme.keys.size shouldBe 54
                MaterialScheme.keys.toSet() shouldBe
                    (ColorKey.entries.map { it.key } + setOf("navbar_background", "normal_mode_color")).toSet()
            }

            Then("leaves the theme's own palette aliases to the theme") {
                MaterialScheme.keys.none { it in MaterialScheme.retiredKeys } shouldBe true
            }

            Then("spells every value the way the theme parses") {
                (colors.light.values + colors.dark.values).forEach { value ->
                    // Eight digits for the translucent shadow, six everywhere else.
                    value shouldMatch Regex("^0x(?:[0-9a-f]{6}|[0-9a-f]{8})$")
                    ColorLiteral.parse(value).shouldNotBeNull()
                }
            }

            Then("gives the shadow an alpha instead of an opaque black") {
                colors.light.getValue("shadow_color").length shouldBe 10
                colors.dark.getValue("shadow_color").length shouldBe 10
            }

            Then("keeps text readable on what it sits on") {
                val pairs =
                    listOf(
                        "key_text_color" to "key_back_color",
                        "key_symbol_color" to "key_back_color",
                        "candidate_text_color" to "back_color",
                        "comment_text_color" to "back_color",
                        "hilited_candidate_text_color" to "hilited_candidate_back_color",
                        "label_color" to "key_back_color",
                        "off_key_text_color" to "off_key_back_color",
                        "off_key_symbol_color" to "off_key_back_color",
                        "on_key_text_color" to "on_key_back_color",
                        "on_key_symbol_color" to "on_key_back_color",
                        "hilited_key_text_color" to "hilited_key_back_color",
                        "hilited_key_symbol_color" to "hilited_key_back_color",
                        "hilited_off_key_text_color" to "hilited_off_key_back_color",
                        "hilited_on_key_text_color" to "hilited_on_key_back_color",
                        "popup_text_color" to "popup_back_color",
                        "hilited_popup_text_color" to "hilited_popup_back_color",
                        "long_text_color" to "long_text_back_color",
                    )
                listOf("light" to colors.light, "dark" to colors.dark).forEach { (mode, map) ->
                    pairs.forEach { (text, background) ->
                        val ratio = contrast(map.getValue(text), map.getValue(background))
                        withClue("$mode: $text on $background = ${"%.2f".format(ratio)}") {
                            (ratio >= 4.5) shouldBe true
                        }
                    }
                }
            }

            Then("makes light light and dark dark") {
                ColorMath.lstarFromArgb(argb(colors.light.getValue("back_color"))) shouldBe (99.0 plusOrMinus 2.0)
                ColorMath.lstarFromArgb(argb(colors.dark.getValue("back_color"))) shouldBe (10.0 plusOrMinus 2.0)
                ColorMath.lstarFromArgb(argb(colors.light.getValue("key_back_color"))) shouldBe
                    (95.0 plusOrMinus 2.0)
                ColorMath.lstarFromArgb(argb(colors.dark.getValue("key_back_color"))) shouldBe (20.0 plusOrMinus 2.0)
            }

            Then("paints the accent on pressed keys, with a light caption") {
                val light = colors.light
                ColorMath.lstarFromArgb(argb(light.getValue("hilited_key_back_color"))) shouldBe
                    (40.0 plusOrMinus 2.0)
                ColorMath.lstarFromArgb(argb(light.getValue("hilited_key_text_color"))) shouldBe
                    (100.0 plusOrMinus 1.0)
            }

            Then("does not hand the same value to both modes") {
                val identical = MaterialScheme.keys.count { colors.light[it] == colors.dark[it] }
                (identical <= 2) shouldBe true
            }
        }

        Given("two different seeds") {
            Then("produce different accents") {
                val first = MaterialScheme.colors(MaterialPalettes.fromSeed(0xFF6750A4.toInt()))
                val second = MaterialScheme.colors(MaterialPalettes.fromSeed(0xFFB3261E.toInt()))
                first.light.getValue("hilited_key_back_color") shouldNotBe
                    second.light.getValue("hilited_key_back_color")
            }
        }

        Given("a neutral seed") {
            Then("still produces a readable scheme") {
                val colors = MaterialScheme.colors(MaterialPalettes.fromSeed(0xFF808080.toInt()))
                val ratio = contrast(colors.light.getValue("key_text_color"), colors.light.getValue("key_back_color"))
                (ratio >= 4.5) shouldBe true
            }
        }
    })

private fun argb(literal: String): Int = requireNotNull(ColorLiteral.parse(literal))

/** WCAG 2.1 contrast ratio; 4.5 is the body-text threshold. */
private fun contrast(
    foreground: String,
    background: String,
): Double {
    val a = ColorMath.relativeLuminance(argb(foreground)) / 100.0
    val b = ColorMath.relativeLuminance(argb(background)) / 100.0
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}
