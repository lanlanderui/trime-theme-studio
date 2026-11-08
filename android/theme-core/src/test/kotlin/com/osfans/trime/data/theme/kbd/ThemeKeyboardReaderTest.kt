/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins the file view of `preset_keyboards`.
 *
 * The reader is what tells the editor "this layout is an alias" or "this block
 * cannot be written back" — getting that wrong turns a browse screen into a
 * way to corrupt a theme, so the shipped theme is used as the corpus.
 */
class ThemeKeyboardReaderTest :
    BehaviorSpec({
        Given("a theme with three shapes of layout") {
            val layouts = ThemeKeyboardReader.read(SAMPLE.lines())

            Then("layouts come in file order with their display names") {
                layouts.map { it.id } shouldContainExactly listOf("alpha", "beta", "gamma")
                layouts[0].displayName shouldBe "甲"
                layouts[2].displayName shouldBe "gamma"
            }

            Then("a layout with keys reports its own indentation") {
                layouts[0].keys.size shouldBe 2
                layouts[0].indent shouldBe Indent(keys = 4, item = 6)
                layouts[0].isAlias shouldBe false
            }

            Then("a layout that only includes another one is an alias") {
                layouts[1].isAlias shouldBe true
                layouts[1].includeTarget shouldBe "alpha"
                layouts[1].keys.isEmpty() shouldBe true
            }

            Then("the four-space item style is recognised too") {
                layouts[2].keys.size shouldBe 2
                layouts[2].indent shouldBe Indent(keys = 4, item = 4)
            }

            Then("a key line keeps its trailing comment") {
                layouts[2].keys[0].comment shouldBe "  #一聲"
            }

            Then("a clean theme reports nothing unsupported") {
                layouts.forEach { it.unsupported.shouldBeNull() }
            }
        }

        Given("a layout whose keys are not flow maps") {
            val layouts =
                ThemeKeyboardReader.read(
                    """
                    preset_keyboards:
                      broken:
                        keys:
                          - click: q
                          - {click: w}
                    """.trimIndent().lines(),
                )

            Then("it is marked unsupported instead of half-read") {
                layouts.single().unsupported.shouldNotBeNull()
                layouts.single().keys.size shouldBe 1
            }
        }

        Given("the shipped theme") {
            val file = File("src/main/assets/shared/trime.yaml")

            Then("every layout resolves, with the expected key counts") {
                val layouts = ThemeKeyboardReader.read(file)
                layouts.size shouldBe 18
                val byId = layouts.associateBy { it.id }
                byId.getValue("default").keys.size shouldBe 47
                byId.getValue("mini").keys.size shouldBe 20
                byId.getValue("letter").includeTarget shouldBe "default"
                byId.getValue("scj6").includeTarget shouldBe "cangjie5"
            }

            Then("only two layouts are pure aliases") {
                val layouts = ThemeKeyboardReader.read(file)
                layouts.filter { it.keys.isEmpty() }.map { it.id } shouldContainExactly
                    listOf("letter", "scj6")
            }

            Then("the two item indentation styles both occur") {
                val layouts = ThemeKeyboardReader.read(file)
                layouts.mapNotNull { it.indent?.item }.toSet() shouldBe setOf(4, 6)
            }

            Then("no real layout is rejected") {
                ThemeKeyboardReader.read(file).forEach { raw ->
                    raw.unsupported shouldBe null
                }
            }

            Then("the key entries decode to the same clicks the file shows") {
                val default = ThemeKeyboardReader.read(file).first { it.id == "default" }
                default.keys[0]["click"] shouldBe "1"
                default.keys[0]["long_click"] shouldBe "!"
                default.keys[10]["click"] shouldBe "q"
            }
        }
    })

private val SAMPLE =
    """
    style:
      horizontal_gap: 1

    preset_keyboards:
      alpha:
        name: 甲
        width: 10
        keys:
          - {click: q}
          - {click: w, width: 5}
      beta:
        __include: /preset_keyboards/alpha
        ascii_mode: 1
      gamma:
        keys:
        - {click: a}  #一聲
        - {click: b}

    preset_keys:
      Return: {label: Enter, send: Return}
    """.trimIndent() + "\n"
