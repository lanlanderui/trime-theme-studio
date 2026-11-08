/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins the file view of `tool_bar`.
 *
 * The reader is what tells the editor which block a button's properties live
 * in. Getting that wrong does not show up as a crash — it shows up as a save
 * that rewrites the wrong block, so the shipped themes are used as the corpus
 * and every expectation below was read off the files by hand.
 */
class ThemeToolBarReaderTest :
    BehaviorSpec({
        Given("the 同文主题's toolbar") {
            val raw = ThemeToolBarReader.read(File(TONGWEN)).shouldNotBeNull()

            Then("it is editable and lists six buttons, all aliases") {
                raw.editable shouldBe true
                raw.unsupported.shouldBeNull()
                raw.buttons.size shouldBe 6
                raw.buttons.map { it.alias } shouldContainExactly
                    listOf(
                        "hide_button",
                        "emoji_button",
                        "clipboard_button",
                        "edit_button",
                        "ascii_mode_button",
                        "full_shape_button",
                    )
                raw.buttons.forEach { it.isInline shouldBe false }
            }

            Then("primary and the six blocks are all known") {
                raw.blocks.keys.toList() shouldContainExactly
                    listOf(
                        "primary_button",
                        "hide_button",
                        "emoji_button",
                        "clipboard_button",
                        "edit_button",
                        "ascii_mode_button",
                        "full_shape_button",
                    )
                val primary = raw.primary.shouldNotBeNull()
                primary.anchor shouldBe null
            }

            Then("a block's anchor is kept apart from its id") {
                raw.blocks.getValue("hide_button").anchor shouldBe "hide_button"
                raw.blocks.getValue("ascii_mode_button").anchor shouldBe "ascii_mode_button"
            }

            Then("nested leaves are addressed by path, not by line") {
                val primary = raw.primary.shouldNotBeNull()
                primary.value("action") shouldBe "menu_keyboard"
                primary.value("long_press_action") shouldBe "Settings"
                primary.value("background", "type") shouldBe "circle"
                primary.value("background", "corner_radius") shouldBe "10"
                primary.value("foreground", "style") shouldBe "\"ic@dots_horizontal\""
                primary.value("foreground", "padding") shouldBe "10"
            }

            Then("a block's own leaves do not collide with primary's") {
                val ascii = raw.blocks.getValue("ascii_mode_button")
                ascii.value("action") shouldBe "Mode_switch"
                ascii.value("foreground", "option_styles") shouldBe "[\"中\", \"En\"]"
                ascii.value("foreground", "font_size") shouldBe "18"
                // primary has no option_styles; the paths must stay per-block.
                raw.primary.shouldNotBeNull().leaf("foreground", "option_styles").shouldBeNull()
            }

            Then("section scalars are read from the block's own indent") {
                raw.scalars.getValue("button_spacing").value shouldBe "5"
                raw.scalars.getValue("button_font").value shouldBe "iconfont.ttf"
                raw.scalars.getValue("back_style").value shouldBe "\"ic@arrow-left\""
            }

            Then("a commented line is kept as a value the author pinned") {
                // `# size: [120, 24]` under primary_button
                val size = raw.primary.shouldNotBeNull().leaf("size").shouldNotBeNull()
                size.commented shouldBe true
                size.value shouldBe null
            }

            Then("every block is referenced exactly once") {
                raw.referenceCount("hide_button") shouldBe 1
                raw.referenceCount("primary_button") shouldBe 0
                raw.unreferenced().shouldBeEmpty()
            }

            Then("the list keeps the theme's spaced spelling") {
                raw.buttonsSpaced shouldBe true
            }
        }

        Given("the bundled template's toolbar") {
            val raw = ThemeToolBarReader.read(File(TEMPLATE)).shouldNotBeNull()

            Then("it lists three buttons but defines seven blocks") {
                raw.buttons.map { it.alias } shouldContainExactly
                    listOf("hide_button", "clipboard_button", "ascii_mode_button")
                raw.blocks.size shouldBe 7
            }

            Then("the three unused blocks are reported, so the UI can hint at them") {
                raw.unreferenced() shouldContainExactly
                    listOf("emoji_button", "edit_button", "full_shape_button")
            }

            Then("a commented-out size is still visible to the editor") {
                raw.primary.shouldNotBeNull().leaf("size").shouldNotBeNull().commented shouldBe true
            }
        }

        Given("a theme without a toolbar") {
            Then("the reader says so instead of inventing an empty block") {
                ThemeToolBarReader.read(File(PLAIN)).shouldBeNull()
            }
        }

        Given("a toolbar whose list is written as a block sequence") {
            val raw =
                ThemeToolBarReader.read(
                    """
                    tool_bar:
                      a: &a
                        action: Hide
                      buttons:
                        - *a
                    """.trimIndent().lines(),
                ).shouldNotBeNull()

            Then("it is marked unsupported rather than half-read") {
                raw.unsupported.shouldNotBeNull()
                raw.editable shouldBe false
            }
        }

        Given("a list that points at a block which does not exist") {
            val raw =
                ThemeToolBarReader.read(
                    """
                    tool_bar:
                      a: &a
                        action: Hide
                      buttons: [ *missing ]
                    """.trimIndent().lines(),
                ).shouldNotBeNull()

            Then("the dangling alias is reported with its name") {
                raw.unsupported.shouldNotBeNull()
                raw.unsupported.contains("missing") shouldBe true
            }
        }

        Given("a toolbar that borrows another block by alias") {
            val raw =
                ThemeToolBarReader.read(
                    """
                    tool_bar:
                      primary_button: *hide_button
                      hide_button: &hide_button
                        action: Hide
                      buttons: [ *hide_button ]
                    """.trimIndent().lines(),
                ).shouldNotBeNull()

            Then("it is refused instead of writing into the wrong block") {
                raw.unsupported.shouldNotBeNull()
            }
        }

        Given("an inline mapping inside the list") {
            val raw =
                ThemeToolBarReader.read(
                    """
                    tool_bar:
                      a: &a
                        action: Hide
                      buttons: [ *a, {action: Settings} ]
                    """.trimIndent().lines(),
                ).shouldNotBeNull()

            Then("it is read as an inline item, not as an alias") {
                raw.buttons.size shouldBe 2
                raw.buttons[0].alias shouldBe "a"
                raw.buttons[1].isInline shouldBe true
                raw.buttons[1].inlineText shouldBe "{action: Settings}"
                raw.unsupported.shouldBeNull()
            }
        }
    })

private const val TONGWEN = "src/main/assets/shared/tongwenfeng.trime.yaml"
private const val TEMPLATE = "src/main/assets/templates/template.trime.yaml"
private const val PLAIN = "src/main/assets/shared/trime.yaml"
