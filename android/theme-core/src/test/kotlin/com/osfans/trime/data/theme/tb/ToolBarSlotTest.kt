/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The list shows the toolbar left to right; `buttons` is written right to left.
 *
 * Getting this wrong is invisible in the code and obvious on the phone, one
 * drag too late — hence a test that only asserts the mapping, with no file or
 * runtime involved.
 */
class ToolBarSlotTest :
    BehaviorSpec({
        Given("the visual position of a button") {
            Then("counting from the left is the reverse of the array index") {
                // `buttons = [a, b, c]` puts c leftmost: ButtonsBarUi lays the
                // middle segment out ROW_REVERSE and pins buttons[0] at the end.
                ToolBarSlot.visualToArray(0, 3) shouldBe 2
                ToolBarSlot.visualToArray(1, 3) shouldBe 1
                ToolBarSlot.visualToArray(2, 3) shouldBe 0
            }

            Then("both directions are inverses for every size the editor may meet") {
                for (total in 0..8) {
                    for (index in 0 until total) {
                        val visual = ToolBarSlot.arrayToVisual(index, total)
                        ToolBarSlot.visualToArray(visual, total) shouldBe index
                    }
                }
            }
        }

        Given("moving a button one place left") {
            Then("it swaps with its left neighbour in the rendered order") {
                // Rendered order is c, b, a; moving b one place left gives
                // b, c, a, which is written as the array [a, c, b].
                ToolBarSlot.move(listOf("*a", "*b", "*c"), 1, 0) shouldBe listOf("*a", "*c", "*b")
            }
        }

        Given("moving the right-most button to the left edge") {
            Then("the array is reversed, reordered, and reversed back") {
                val moved = ToolBarSlot.move(listOf("*a", "*b", "*c"), 0, 2)
                moved shouldBe listOf("*c", "*a", "*b")
                // Rendering the result reverses it again: b, a, c.
                moved.reversed() shouldBe listOf("*b", "*a", "*c")
            }
        }

        Given("a move that goes nowhere") {
            Then("the list comes back untouched, including when the index is out of range") {
                val sources = listOf("*a", "*b")
                ToolBarSlot.move(sources, 1, 1) shouldBe sources
                ToolBarSlot.move(sources, 5, 0) shouldBe sources
            }
        }

        Given("removing a button") {
            Then("the item at that visual position goes, whatever the array order") {
                ToolBarSlot.remove(listOf("*a", "*b", "*c"), 0) shouldBe listOf("*a", "*b")
                ToolBarSlot.remove(listOf("*a", "*b", "*c"), 2) shouldBe listOf("*b", "*c")
            }

            Then("an inline item is removed without being rewritten") {
                val sources = listOf("*a", "{action: Hide}", "*c")
                ToolBarSlot.remove(sources, 1) shouldBe listOf("*a", "*c")
            }
        }
    })
