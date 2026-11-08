/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Pins the flow-map grammar against the three quoting styles that occur in the
 * shipped themes. A value read one character off becomes a wrong key binding,
 * so these cases are deliberately literal.
 */
class KeyboardYamlTest :
    BehaviorSpec({
        fun pairs(entries: List<KeyboardYaml.Entry>?) = entries?.map { it.key to it.value }

        Given("a flow map from a real theme line") {
            Then("bare, single-quoted and double-quoted values all decode") {
                val entries = KeyboardYaml.parseFlowMap("""{click: q, long_click: '_', label: "手"}""")
                pairs(entries) shouldContainExactly
                    listOf("click" to "q", "long_click" to "_", "label" to "手")
            }

            Then("a quoted comma is part of the value, not a separator") {
                val entries = KeyboardYaml.parseFlowMap("""{click: ',', long_click: .}""")
                pairs(entries) shouldContainExactly listOf("click" to ",", "long_click" to ".")
            }

            Then("braces and brackets inside quotes do not nest") {
                val entries = KeyboardYaml.parseFlowMap("""{click: '"', label: '“”{Left}'}""")
                pairs(entries) shouldContainExactly listOf("click" to "\"", "label" to "“”{Left}")
            }

            Then("a lone backslash survives the round trip") {
                KeyboardYaml.parseFlowMap("""{click: '\', long_click: '.', composing: 'ˋ'}""")
                    ?.first()?.value shouldBe "\\"
            }

            Then("a doubled single quote unwraps to one") {
                KeyboardYaml.parseFlowMap("""{label: 'it''s'}""")?.first()?.value shouldBe "it's"
            }

            Then("a double-quoted escape is decoded") {
                KeyboardYaml.parseFlowMap("""{long_click: "\\"}""")?.first()?.value shouldBe "\\"
            }

            Then("an empty value stays null instead of becoming an empty string") {
                pairs(KeyboardYaml.parseFlowMap("{click: }")) shouldContainExactly listOf("click" to null)
            }
        }

        Given("a line that is not a flow map") {
            Then("block style is refused rather than guessed") {
                KeyboardYaml.parseFlowMap("click: q").shouldBeNull()
            }

            Then("an unterminated map is refused") {
                KeyboardYaml.parseFlowMap("{click: q").shouldBeNull()
            }
        }

        Given("values that need quoting") {
            val cases =
                mapOf(
                    "" to "''",
                    "1" to "'1'",
                    "1.5" to "'1.5'",
                    "true" to "'true'",
                    "null" to "'null'",
                    "~" to "'~'",
                    "a,b" to "'a,b'",
                    "a#b" to "'a#b'",
                    "{Left}" to "'{Left}'",
                    "it's" to "'it''s'",
                    " x" to "' x'",
                    "q" to "q",
                    "Return" to "Return",
                    "Page_Up" to "Page_Up",
                    "手" to "手",
                )
            cases.forEach { (raw, expected) ->
                Then("$raw → $expected") {
                    KeyboardYaml.quote(raw) shouldBe expected
                }
            }
        }

        Given("formatting a key back to a line") {
            Then("only the entries that are set come out, in the given order") {
                KeyboardYaml.formatFlowMap(
                    listOf(
                        KeyboardYaml.Entry("click", "q"),
                        KeyboardYaml.Entry("width", "5"),
                    ),
                ) shouldBe "{click: q, width: '5'}"
            }

            Then("a nested value survives a round trip verbatim") {
                val line = """{click: {commit: '，', label: '逗号'}, width: 8}"""
                val entries = KeyboardYaml.parseFlowMap(line)
                KeyboardYaml.formatFlowMap(entries!!) shouldBe line
            }

            Then("a string that looks like a structure keeps its quotes") {
                val line = """{long_click: '{}{Left}'}"""
                val entries = KeyboardYaml.parseFlowMap(line)
                KeyboardYaml.formatFlowMap(entries!!) shouldBe line
            }

            Then("format then parse returns the original values") {
                val original =
                    listOf(
                        KeyboardYaml.Entry("click", ","),
                        KeyboardYaml.Entry("long_click", "!"),
                        KeyboardYaml.Entry("label", "手"),
                        KeyboardYaml.Entry("width", "12.5"),
                    )
                val line = KeyboardYaml.formatFlowMap(original)
                pairs(KeyboardYaml.parseFlowMap(line)) shouldBe
                    listOf("click" to ",", "long_click" to "!", "label" to "手", "width" to "12.5")
            }
        }
    })
