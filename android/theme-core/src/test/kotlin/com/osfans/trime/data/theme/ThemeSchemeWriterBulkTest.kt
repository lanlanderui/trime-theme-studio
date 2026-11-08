/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Bulk scheme writes: a regenerated palette touches ~40 lines at once, so what
 * matters is that everything *else* in the file survives — and that writing the
 * same palette twice leaves the bytes alone.
 */
class ThemeSchemeWriterBulkTest :
    BehaviorSpec({
        fun themeFile(): File = File.createTempFile("bulk-scheme", ".trime.yaml").also { file ->
            file.writeText(
                """
                    |preset_color_schemes:
                    |  default:
                    |    name: 默认
                    |    back_color: 0xffffff   # 底色
                    |    key_text_color: 0x000000
                    |
                    |  other:
                    |    name: Other
                    |    back_color: 0x101010
                    |
                    |fallback_colors:
                    |  border_color: back_color
                    |
                """.trimMargin(),
            )
        }

        Given("a scheme that already exists") {
            Then("updates only the given keys and keeps the comment") {
                val file = themeFile()
                val before = file.readText()
                ThemeSchemeWriter.setEntries(
                    file,
                    "default",
                    mapOf("back_color" to "0xfef7ff", "key_back_color" to "0xf3edf4"),
                ) shouldBe true
                val after = file.readText()
                after shouldContain "    back_color: 0xfef7ff   # 底色"
                after shouldContain "    key_back_color: 0xf3edf4"
                // The other scheme is untouched, byte for byte.
                after shouldContain before.substringAfter("  other:\n").substringBefore("\nfallback_colors:")
            }

            Then("is idempotent: the second write changes nothing") {
                val file = themeFile()
                val entries = mapOf("back_color" to "0xfef7ff", "key_back_color" to "0xf3edf4")
                ThemeSchemeWriter.setEntries(file, "default", entries) shouldBe true
                val once = file.readText()
                ThemeSchemeWriter.setEntries(file, "default", entries) shouldBe true
                file.readText() shouldBe once
            }
        }

        Given("a scheme that does not exist") {
            Then("is not created unless asked") {
                val file = themeFile()
                val before = file.readText()
                ThemeSchemeWriter.setEntries(file, "material_you", mapOf("back_color" to "0xffffff")) shouldBe false
                file.readText() shouldBe before
            }

            Then("is appended with its entries when asked") {
                val file = themeFile()
                ThemeSchemeWriter.setEntries(
                    file,
                    "material_you",
                    mapOf("name" to "Material You", "dark_scheme" to "material_you_dark", "back_color" to "0xfffbff"),
                    create = true,
                ) shouldBe true
                val text = file.readText()
                text shouldContain "  material_you:"
                text shouldContain "    dark_scheme: material_you_dark"
                text shouldContain "    back_color: 0xfffbff"
                // Parsing still works afterwards: the new block reads back.
                ThemeSchemeWriter.readScheme(file, "material_you").getValue("name") shouldBe "Material You"
                ThemeSchemeWriter.listSchemes(file).map { it.id } shouldContain "material_you"
                // And the block before the section end is still intact.
                text shouldContain "fallback_colors:"
            }
        }

        Given("a block a generator wants to replace") {
            fun generatedFile(): File = File.createTempFile("rewrite-scheme", ".trime.yaml").also { file ->
                file.writeText(
                    """
                    |preset_color_schemes:
                    |  material_you:
                    |    name: Material You
                    |    author: Material You
                    |    dark_scheme: material_you_dark
                    |    back_color: 0xffffff
                    |    bbs: 0x123456
                    |    key_text_color: 0x000000
                    |
                    """.trimMargin(),
                )
            }

            Then("keeps the metadata, drops the rest and follows the given order") {
                val file = generatedFile()
                ThemeSchemeWriter.setSchemes(
                    file,
                    mapOf(
                        "material_you" to
                            linkedMapOf(
                                "name" to "Material You",
                                "author" to "Material You",
                                "dark_scheme" to "material_you_dark",
                                "key_text_color" to "0x101010",
                                "back_color" to "0xfef7ff",
                            ),
                    ),
                    rewrite = true,
                ) shouldBe true
                val text = file.readText()
                text shouldNotContain "bbs:"
                text shouldContain "    name: Material You"
                text shouldContain "    dark_scheme: material_you_dark"
                // The order the caller gave is the order in the file, so a
                // regenerated block reads like the template it mirrors.
                ThemeSchemeWriter.readScheme(file, "material_you").keys.toList() shouldBe
                    listOf("name", "author", "dark_scheme", "key_text_color", "back_color")
            }

            Then("patches in place when no rewrite is asked for") {
                val file = generatedFile()
                ThemeSchemeWriter.setSchemes(
                    file,
                    mapOf("material_you" to mapOf("back_color" to "0xfef7ff")),
                ) shouldBe true
                file.readText() shouldContain "bbs: 0x123456"
            }
        }

        Given("a day and a night scheme together") {
            Then("both land in one write") {
                val file = themeFile()
                ThemeSchemeWriter.setSchemes(
                    file,
                    mapOf(
                        "material_you" to mapOf("name" to "浅色", "dark_scheme" to "material_you_dark", "back_color" to "0xfffbff"),
                        "material_you_dark" to mapOf("name" to "深色", "light_scheme" to "material_you", "back_color" to "0x1d1b20"),
                    ),
                    create = true,
                ) shouldBe true
                val ids = ThemeSchemeWriter.listSchemes(file).map { it.id }
                ids shouldContain "material_you"
                ids shouldContain "material_you_dark"
                ThemeSchemeWriter.readScheme(file, "material_you_dark").getValue("back_color") shouldBe "0x1d1b20"
            }

            Then("does not touch the schemes that were already there") {
                val file = themeFile()
                ThemeSchemeWriter.setSchemes(
                    file,
                    mapOf(
                        "material_you" to mapOf("name" to "浅色"),
                        "material_you_dark" to mapOf("name" to "深色"),
                    ),
                    create = true,
                )
                val text = file.readText()
                text shouldContain "    name: 默认"
                text shouldContain "    name: Other"
                text shouldNotContain "name: undefined"
            }
        }
    })
