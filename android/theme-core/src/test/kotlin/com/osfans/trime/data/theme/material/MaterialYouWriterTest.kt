/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import com.osfans.trime.data.theme.ThemeSchemeWriter
import com.osfans.trime.data.theme.ThemeTextFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins what reaches the file when Material You is generated.
 *
 * The palette itself is [MaterialSchemeTest]'s subject; here the block has to
 * be a *usable scheme*: cross-linked to its twin, holding exactly the keys the
 * table paints, and replaceable without growing a row per run.
 */
class MaterialYouWriterTest :
    BehaviorSpec({
        Given("a theme without generated blocks") {
            Then("both blocks are created, cross-linked, with every key") {
                withTheme { file ->
                    val colors = MaterialYouWriter.fromSeed(SEED)
                    MaterialYouWriter.write(file, colors, "浅色", "深色").outcome shouldBe
                        ThemeTextFile.WriteOutcome.WRITTEN

                    val light = ThemeSchemeWriter.readScheme(file, MaterialYouWriter.LIGHT_ID)
                    val dark = ThemeSchemeWriter.readScheme(file, MaterialYouWriter.DARK_ID)
                    light["name"] shouldBe "浅色"
                    light["dark_scheme"] shouldBe MaterialYouWriter.DARK_ID
                    dark["light_scheme"] shouldBe MaterialYouWriter.LIGHT_ID
                    light.keys.count { it in MaterialScheme.keys } shouldBe MaterialScheme.keys.size
                    dark.keys.count { it in MaterialScheme.keys } shouldBe MaterialScheme.keys.size
                }
            }

            Then("the day/night link makes them a pair the theme can follow") {
                withTheme { file ->
                    MaterialYouWriter.write(file, MaterialYouWriter.fromSeed(SEED), "浅色", "深色")
                    ThemeSchemeWriter.referencingSchemes(file, MaterialYouWriter.DARK_ID) shouldBe
                        listOf(MaterialYouWriter.LIGHT_ID)
                }
            }
        }

        Given("a block written by an older build") {
            Then("regenerating drops the keys the table no longer paints") {
                val source =
                    "preset_color_schemes:\n" +
                        "  default:\n    back_color: 0xffffff\n" +
                        "  ${MaterialYouWriter.LIGHT_ID}:\n" +
                        "    name: 旧\n" +
                        "    bbs: 0xff000000\n" +
                        "    back_color: 0x111111\n"
                withTheme(source) { file ->
                    MaterialYouWriter.write(file, MaterialYouWriter.fromSeed(SEED), "浅色", "深色")
                    val light = ThemeSchemeWriter.readScheme(file, MaterialYouWriter.LIGHT_ID)
                    // `bbs` is a template alias this table retired. Left behind
                    // it would be a row nothing resolves, and a row per run.
                    light.containsKey("bbs") shouldBe false
                    light.containsKey("back_color") shouldBe true
                    light.size shouldBe MaterialScheme.keys.size + 3
                }
            }

            Then("writing the same colours again is a no-op") {
                withTheme { file ->
                    val colors = MaterialYouWriter.fromSeed(SEED)
                    MaterialYouWriter.write(file, colors, "浅色", "深色")
                    val once = file.readText()
                    MaterialYouWriter.write(file, colors, "浅色", "深色")
                    // A rewrite that is not stable reorders the block every run
                    // and shows up as a spurious diff in the user's repository.
                    file.readText() shouldBe once
                }
            }
        }

        Given("a half-written pair") {
            Then("readCurrent reports nothing so a regeneration replaces it") {
                val source =
                    "preset_color_schemes:\n" +
                        "  default:\n    back_color: 0xffffff\n" +
                        "  ${MaterialYouWriter.LIGHT_ID}:\n" +
                        "    name: 只剩浅色\n" +
                        "    back_color: 0x111111\n"
                withTheme(source) { file ->
                    MaterialYouWriter.readCurrent(file) shouldBe null
                }
            }
        }
    })

private const val SEED = 0xFF4285F4.toInt()

private fun withTheme(
    content: String = "preset_color_schemes:\n  default:\n    name: 預設\n    back_color: 0xffffff\n",
    block: (File) -> Unit,
) {
    val file = File.createTempFile("trime-material-", ".yaml")
    try {
        file.writeText(content)
        block(file)
    } finally {
        listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
    }
}
