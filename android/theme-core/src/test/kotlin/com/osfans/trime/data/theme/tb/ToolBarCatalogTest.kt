/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * What the toolbar screen is allowed to claim about a button.
 *
 * Two things it must get right, because both are invisible until the keyboard is
 * open: the order (the file writes `buttons` right-to-left) and whether a button
 * has two looks (only exactly two `option_styles` can be drawn as a switch).
 */
class ToolBarCatalogTest :
    BehaviorSpec({
        fun raw(): RawToolBar {
            val bytes =
                checkNotNull(javaClass.getResourceAsStream("/tongwenfeng.trime.yaml")) {
                    "missing test fixture tongwenfeng.trime.yaml"
                }.use { it.readBytes() }
            val file = File.createTempFile("toolbar", ".yaml").apply {
                writeBytes(bytes)
                deleteOnExit()
            }
            return ThemeToolBarReader.read(file)!!
        }

        fun rawOf(text: String): RawToolBar = ThemeToolBarReader.read(text.lines())!!

        Given("the tool bar of a shipped theme") {
            Then("the scalars and the primary button are read as written") {
                val model = ToolBarCatalog.build(raw())
                model.exists shouldBe true
                model.editable shouldBe true
                model.scalars["button_spacing"] shouldBe "5"
                model.scalars["button_font"] shouldBe "iconfont.ttf"
                model.scalars["back_style"] shouldBe "ic@arrow-left"

                val primary = model.primary!!
                primary.action shouldBe "menu_keyboard"
                primary.longPressAction shouldBe "Settings"
                primary.style shouldBe "ic@dots_horizontal"
                primary.anchor shouldBe null
                // Pinned at the left edge, outside the list it is drawn with.
                primary.visualIndex shouldBe -1
            }

            Then("the six buttons come back in the order they are drawn") {
                val model = ToolBarCatalog.build(raw())
                model.buttons.size shouldBe 6
                // `buttons:` is written right-to-left, so the last array item is
                // the left-most button on screen.
                model.buttons.map { it.blockId } shouldContainExactly
                    listOf(
                        "full_shape_button",
                        "ascii_mode_button",
                        "edit_button",
                        "clipboard_button",
                        "emoji_button",
                        "hide_button",
                    )
                model.buttons.map { it.visualIndex } shouldContainExactly (0..5).toList()
                model.buttons.map { it.arrayIndex } shouldContainExactly listOf(5, 4, 3, 2, 1, 0)
                // `hide_button` sits at the right edge, which is array index 0.
                model.buttons.first().action shouldBe "Zenkaku_Hankaku"
                model.buttons.last().action shouldBe "Hide"
                model.buttons.last().anchor shouldBe "hide_button"
            }

            Then("only the two buttons with exactly two styles are switches") {
                val model = ToolBarCatalog.build(raw())
                model.twoStateCount shouldBe 2
                model.buttons.filter { it.twoState }.map { it.blockId } shouldContainExactly
                    listOf("full_shape_button", "ascii_mode_button")
                model.buttons.filter { it.twoState }.first().optionStyles shouldContainExactly
                    listOf("ic@moon_full", "ic@moon_new")
                // One style is decoration, not a switch.
                model.buttons.first { it.blockId == "hide_button" }.optionStyles shouldBe emptyList()
                model.buttons.first { it.blockId == "edit_button" }.twoState shouldBe false
            }

            Then("nothing is unreferenced in this theme") {
                ToolBarCatalog.build(raw()).unreferenced shouldBe emptyList()
            }
        }

        Given("a button with one, two or three styles") {
            Then("only exactly two count as a switch") {
                val text =
                    """
                    tool_bar:
                      one: &one
                        foreground:
                          option_styles: ["单"]
                        action: A
                      two: &two
                        foreground:
                          option_styles: ["关", "开"]
                        action: B
                      three: &three
                        foreground:
                          option_styles: ["一", "二", "三"]
                        action: C
                      buttons: [ *one, *two, *three ]
                    """.trimIndent()
                val model = ToolBarCatalog.build(rawOf(text))
                model.buttons.map { it.blockId to it.twoState } shouldContainExactly
                    listOf("three" to false, "two" to true, "one" to false)
            }
        }

        Given("an inline button item") {
            Then("it is listed but not editable, because it has no block to patch") {
                val text =
                    """
                    tool_bar:
                      a: &a
                        action: Hide
                      buttons: [ *a, {action: Menu} ]
                    """.trimIndent()
                val model = ToolBarCatalog.build(rawOf(text))
                model.buttons.size shouldBe 2
                val inline = model.buttons.first { it.blockId == null }
                inline.source shouldBe "{action: Menu}"
                inline.editable shouldBe false
                model.buttons.first { it.blockId == "a" }.editable shouldBe true
            }
        }

        Given("a theme without a tool bar") {
            Then("the model says so instead of pretending an empty bar") {
                val model = ToolBarCatalog.build(RawToolBar.EMPTY)
                model.exists shouldBe false
                model.editable shouldBe false
                model.buttons shouldBe emptyList()
            }
        }
    })
