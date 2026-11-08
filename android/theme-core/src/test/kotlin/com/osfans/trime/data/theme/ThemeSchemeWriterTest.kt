/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Pins the text-level editing of `preset_color_schemes`: the writer must touch
 * exactly the edited line and leave comments, ordering and every other scheme
 * alone, because the theme file is what the user carries between devices.
 */
class ThemeSchemeWriterTest :
    BehaviorSpec({
        Given("a theme with two schemes") {
            Then("listSchemes reports ids in file order with their display names") {
                withTheme { file ->
                    ThemeSchemeWriter.listSchemes(file) shouldContainExactly
                        listOf(
                            ThemeSchemeWriter.Scheme("default", "預設／default"),
                            ThemeSchemeWriter.Scheme("ink", "墨池／Ink"),
                        )
                }
            }

            Then("a scheme without a name falls back to its id") {
                withTheme("preset_color_schemes:\n  plain:\n    back_color: 0xffffff\n") { file ->
                    ThemeSchemeWriter.listSchemes(file) shouldContainExactly
                        listOf(ThemeSchemeWriter.Scheme("plain", "plain"))
                }
            }

            Then("readScheme returns every property without its trailing comment") {
                withTheme { file ->
                    val values = ThemeSchemeWriter.readScheme(file, "default")
                    values["name"] shouldBe "預設／default"
                    values["back_color"] shouldBe "0xe4e7e9"
                    values["border_color"] shouldBe "0xc1c7ca"
                }
            }

            Then("a tab-aligned comment is not read as part of the value") {
                val source =
                    "preset_color_schemes:\n  tabbed:\n    name: 制表符\t#名稱\n    back_color: 0xffffff\t#背景\n"
                withTheme(source) { file ->
                    val values = ThemeSchemeWriter.readScheme(file, "tabbed")
                    values["name"] shouldBe "制表符"
                    values["back_color"] shouldBe "0xffffff"
                }
            }
        }

        Given("a scheme being renamed") {
            Then("only the name line changes and its trailing comment survives") {
                withTheme { file ->
                    ThemeSchemeWriter.renameScheme(file, "default", "我的配色") shouldBe true
                    file.readText() shouldBe
                        SAMPLE.replace(
                            "    name: 預設／default #方案名稱",
                            "    name: 我的配色 #方案名稱",
                        )
                }
            }

            Then("a scheme without a name line gets one inserted first") {
                val source = "preset_color_schemes:\n  plain:\n    back_color: 0xffffff\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.renameScheme(file, "plain", "朴素") shouldBe true
                    file.readText() shouldBe
                        "preset_color_schemes:\n  plain:\n    name: 朴素\n    back_color: 0xffffff\n"
                }
            }

            Then("an unknown id is reported instead of silently doing nothing") {
                withTheme { file ->
                    ThemeSchemeWriter.renameScheme(file, "no_such", "x") shouldBe false
                    file.readText() shouldBe SAMPLE
                }
            }

            Then("the spacing between the name and its comment is kept") {
                // Themes are hand-written: some align comments with two spaces
                // or a tab, and an edit must not re-flow the document.
                val source = "preset_color_schemes:\n  wide:\n    name: 旧  #方案名稱\n    back_color: 0xffffff\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.renameScheme(file, "wide", "新") shouldBe true
                    file.readText() shouldBe
                        "preset_color_schemes:\n  wide:\n    name: 新  #方案名稱\n    back_color: 0xffffff\n"
                }
            }
        }

        Given("a scheme being deleted") {
            Then("its whole block goes and the blank separator with it") {
                withTheme { file ->
                    ThemeSchemeWriter.deleteScheme(file, "default") shouldBe true
                    val text = file.readText()
                    text.contains("default") shouldBe false
                    text.contains("墨池／Ink") shouldBe true
                    text.contains("\n\n\n") shouldBe false
                }
            }

            Then("the last remaining scheme is protected") {
                val source = "preset_color_schemes:\n  only:\n    back_color: 0xffffff\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.deleteScheme(file, "only") shouldBe false
                    file.readText() shouldBe source
                }
            }

            Then("the section's closing banner is left where the author put it") {
                val source =
                    "preset_color_schemes:\n" +
                        "  day:\n    back_color: 0xffffff\n" +
                        "  night:\n    back_color: 0x000000\n" +
                        "#----- 配色结束 -----#\n" +
                        "foo:\n  bar: 1\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.deleteScheme(file, "night") shouldBe true
                    // The banner is the author's comment, not the scheme's body:
                    // taking it would silently delete a line nobody edited.
                    file.readText() shouldBe
                        "preset_color_schemes:\n" +
                        "  day:\n    back_color: 0xffffff\n" +
                        "#----- 配色结束 -----#\n" +
                        "foo:\n  bar: 1\n"
                }
            }
        }

        Given("a scheme being created") {
            Then("it lands inside the section, before the closing banner") {
                val source =
                    "preset_color_schemes:\n" +
                        "  day:\n    back_color: 0xffffff\n" +
                        "#----- 配色结束 -----#\n" +
                        "foo:\n  bar: 1\n"
                withTheme(source) { file ->
                    val entries = linkedMapOf("name" to "夜间", "back_color" to "0x000000")
                    ThemeSchemeWriter.setSchemes(file, mapOf("night" to entries), create = true) shouldBe true
                    // Appending after the banner would put the block outside the
                    // group a reader sees, and the next edit would not find it.
                    file.readText() shouldBe
                        "preset_color_schemes:\n" +
                        "  day:\n    back_color: 0xffffff\n" +
                        "\n  night:\n    name: 夜间\n    back_color: 0x000000\n" +
                        "\n#----- 配色结束 -----#\n" +
                        "foo:\n  bar: 1\n"
                }
            }

            Then("an existing id is patched rather than duplicated") {
                withTheme { file ->
                    ThemeSchemeWriter.setSchemes(file, mapOf("ink" to mapOf("back_color" to "0x123456")), create = true) shouldBe true
                    val text = file.readText()
                    text.split("  ink:").size shouldBe 2
                    text.contains("back_color: 0x123456") shouldBe true
                }
            }

            Then("creating and deleting it leaves the file exactly as it was") {
                withTheme { file ->
                    val before = file.readText()
                    val entries = linkedMapOf("name" to "副本", "back_color" to "0x111111")
                    ThemeSchemeWriter.setSchemes(file, mapOf("copy" to entries), create = true) shouldBe true
                    ThemeSchemeWriter.deleteScheme(file, "copy") shouldBe true
                    // A round trip that is not a no-op grows a blank line every
                    // time, and the file the user carries around drifts.
                    file.readText() shouldBe before
                }
            }

            Then("the round trip is a no-op in front of the closing banner too") {
                val source =
                    "preset_color_schemes:\n" +
                        "  day:\n    back_color: 0xffffff\n" +
                        "#----- 配色结束 -----#\n" +
                        "foo:\n  bar: 1\n"
                withTheme(source) { file ->
                    val entries = linkedMapOf("name" to "副本", "back_color" to "0x111111")
                    ThemeSchemeWriter.setSchemes(file, mapOf("copy" to entries), create = true) shouldBe true
                    ThemeSchemeWriter.deleteScheme(file, "copy") shouldBe true
                    file.readText() shouldBe source
                }
            }
        }

        Given("a single colour being edited") {
            Then("the value changes and the trailing comment is kept") {
                withTheme { file ->
                    ThemeSchemeWriter.setColor(file, "default", "back_color", "0xff4444") shouldBe true
                    file.readText() shouldBe
                        SAMPLE.replace(
                            "    back_color: 0xe4e7e9 #候選區背景",
                            "    back_color: 0xff4444 #候選區背景",
                        )
                }
            }

            Then("a missing key is appended to the scheme body") {
                withTheme { file ->
                    ThemeSchemeWriter.setColor(file, "ink", "shadow_color", "0x000000") shouldBe true
                    val text = file.readText()
                    text.contains("    border_color: 0x000000\n    hilited_candidate_back_color: 0x000000\n    shadow_color: 0x000000\n")
                        .shouldBe(true)
                }
            }

            Then("a colour value keeps the spacing before its comment") {
                val source = "preset_color_schemes:\n  src:\n    back_color: 0xffffff\t#背景\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.setColor(file, "src", "back_color", "0x000000") shouldBe true
                    file.readText() shouldBe
                        "preset_color_schemes:\n  src:\n    back_color: 0x000000\t#背景\n"
                }
            }

            Then("the version before the edit is kept as a backup") {
                withTheme { file ->
                    ThemeSchemeWriter.setColor(file, "default", "back_color", "0xff4444") shouldBe true
                    ThemeTextFile.backupOf(file).readText() shouldBe SAMPLE
                }
            }
        }

        Given("schemes linked through light_scheme / dark_scheme") {
            Then("referencingSchemes names every scheme pointing at the id") {
                withTheme(LINKED) { file ->
                    ThemeSchemeWriter.referencingSchemes(file, "day") shouldContainExactly listOf("auto")
                    ThemeSchemeWriter.referencingSchemes(file, "night") shouldContainExactly listOf("auto")
                }
            }

            Then("an unreferenced scheme reports nothing") {
                withTheme(LINKED) { file ->
                    ThemeSchemeWriter.referencingSchemes(file, "auto").shouldBeEmpty()
                }
            }
        }

        Given("a save that carries the editor's baseline") {
            Then("a file edited elsewhere is a conflict, not a failure") {
                withTheme { file ->
                    val shown = file.readText()
                    file.writeText(shown.replace("0xffffff", "0xeeeeee"))

                    ThemeSchemeWriter.setColorChecked(file, "ink", "back_color", "0xff0000", shown).outcome shouldBe
                        ThemeTextFile.WriteOutcome.CONFLICT
                    // Nothing was touched, not even a backup written.
                    file.readText() shouldBe shown.replace("0xffffff", "0xeeeeee")
                    ThemeTextFile.hasBackup(file) shouldBe false
                }
            }

            Then("the same save without a baseline goes through") {
                withTheme { file ->
                    ThemeSchemeWriter.setColorChecked(file, "ink", "back_color", "0xff0000").outcome shouldBe
                        ThemeTextFile.WriteOutcome.WRITTEN
                }
            }

            Then("a missing scheme is a failure, not a conflict") {
                withTheme { file ->
                    ThemeSchemeWriter.setColorChecked(file, "nope", "back_color", "0xff0000").outcome shouldBe
                        ThemeTextFile.WriteOutcome.FAILED
                }
            }

            Then("every save hands back the text the next one compares against") {
                withTheme { file ->
                    var baseline = file.readText()
                    // A run of edits is what the editor does; a baseline that is
                    // not advanced after each one would report a conflict on
                    // the second save even though nothing outside changed.
                    repeat(20) { i ->
                        val result =
                            ThemeSchemeWriter.setColorChecked(file, "ink", "back_color", "0x0000%02x".format(i), baseline)
                        result.outcome shouldBe ThemeTextFile.WriteOutcome.WRITTEN
                        baseline = result.text.orEmpty()
                    }
                    file.readText() shouldBe baseline
                    file.readText() shouldContain "    back_color: 0x000013"
                }
            }
        }
    })

/** Writes [content] to a scratch file (the sample theme when omitted) and cleans up after. */
private fun withTheme(
    content: String = SAMPLE,
    block: (File) -> Unit,
) {
    val file = File.createTempFile("trime-scheme-", ".yaml")
    try {
        file.writeText(content)
        block(file)
    } finally {
        listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
    }
}

/** Mirrors the shape of the shipped theme: 2-space ids, 4-space properties. */
private val SAMPLE =
    """
    style:
      horizontal_gap: 1 #鍵水平間距

    preset_color_schemes:
      default:
        name: 預設／default #方案名稱
        author: osfans #作者資訊
        back_color: 0xe4e7e9 #候選區背景
        border_color: 0xc1c7ca #邊框

      ink:
        name: 墨池／Ink
        back_color: 0xffffff
        border_color: 0x000000
        hilited_candidate_back_color: 0x000000

    preset_keys:
      Return: {label: enter_labels, send: Return}
    """.trimIndent() + "\n"

private val LINKED =
    """
    preset_color_schemes:
      day:
        name: 日间
        back_color: 0xffffff
      night:
        name: 夜间
        back_color: 0x000000
      auto:
        name: 自动
        light_scheme: day
        dark_scheme: night
    """.trimIndent() + "\n"
