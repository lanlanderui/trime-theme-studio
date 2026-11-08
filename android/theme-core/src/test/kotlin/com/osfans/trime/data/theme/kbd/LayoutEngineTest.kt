/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins the row splitting that `Keyboard` performs implicitly.
 *
 * The editor shows rows and lets the user change widths, so the row it draws
 * has to be the row the IME will produce — same weights, same integer rounding.
 * The shipped theme is used as the corpus because its last row is 7 keys wide
 * with a 30-weight space bar, which is exactly where naive float math drifts.
 */
class LayoutEngineTest :
    BehaviorSpec({
        fun key(
            weight: Float? = null,
            clickable: Boolean = true,
        ) = LayoutEngine.Key(weight, clickable)

        Given("a full row of ten equal keys") {
            Then("it stays one row and adds up to 100") {
                val rows = LayoutEngine.rows(List(10) { key() }, keyboardWidth = 10f, columns = 30, allowedWidthPx = 3000)
                rows.size shouldBe 1
                rows[0].indices.size shouldBe 10
                rows[0].weightSum shouldBe 100f
            }

            Then("the eleventh key starts a second row") {
                val rows = LayoutEngine.rows(List(11) { key() }, keyboardWidth = 10f, columns = 30, allowedWidthPx = 3000)
                rows.map { it.indices.size } shouldContainExactly listOf(10, 1)
                rows[1].weightSum shouldBe 10f
            }
        }

        Given("a wide key") {
            Then("a 30-weight space bar completes its row exactly") {
                val keys = List(7) { key() } + key(weight = 30f) + key()
                val rows = LayoutEngine.rows(keys, keyboardWidth = 10f, columns = 30, allowedWidthPx = 3000)
                rows.map { it.indices.size } shouldContainExactly listOf(8, 1)
                rows[0].weightSum shouldBe 100f
                rows[1].weightSum shouldBe 10f
            }
        }

        Given("a column limit") {
            Then("rows break on the limit even when there is width left") {
                val rows = LayoutEngine.rows(List(10) { key(weight = 5f) }, keyboardWidth = 10f, columns = 5, allowedWidthPx = 3000)
                rows.map { it.indices.size } shouldContainExactly listOf(5, 5)
            }

            Then("-1 means no limit") {
                val rows =
                    LayoutEngine.rows(
                        List(40) { key(weight = 1f) },
                        keyboardWidth = 10f,
                        columns = -1,
                        allowedWidthPx = 3000,
                    )
                rows.size shouldBe 1
                rows[0].indices.size shouldBe 40
            }
        }

        Given("a spacer with no click action") {
            Then("it takes no width and does not count as a column") {
                val keys = List(5) { key() } + key(weight = null, clickable = false) + List(5) { key() }
                val rows = LayoutEngine.rows(keys, keyboardWidth = 10f, columns = 10, allowedWidthPx = 3000)
                rows.size shouldBe 1
                rows[0].indices.size shouldBe 11
                rows[0].weightSum shouldBe 100f
            }
        }

        Given("the shipped theme") {
            val file = File("src/main/assets/shared/trime.yaml")

            Then("default splits into 10/10/10/10/7 with every row full") {
                val raw = ThemeKeyboardReader.read(file).first { it.id == "default" }
                val rows = LayoutEngine.rows(raw.layoutKeys(), keyboardWidth = 10f, columns = 30, allowedWidthPx = 3000)
                rows.map { it.indices.size } shouldContainExactly listOf(10, 10, 10, 10, 7)
                rows.map { it.weightSum } shouldContainExactly List(5) { 100f }
            }

            Then("the wide space bar stays on the last row") {
                val raw = ThemeKeyboardReader.read(file).first { it.id == "default" }
                val rows = LayoutEngine.rows(raw.layoutKeys(), keyboardWidth = 10f, columns = 30, allowedWidthPx = 3000)
                val lastRow = rows.last()
                lastRow.indices.map { raw.keys[it]["click"] } shouldContainExactly
                    listOf("Shift_L", "Keyboard_symbols", "Mode_switch", "space", "'", "BackSpace", "Return")
            }

            Then("mini splits into two rows") {
                val raw = ThemeKeyboardReader.read(file).first { it.id == "mini" }
                val rows = LayoutEngine.rows(raw.layoutKeys(), keyboardWidth = 10f, columns = 30, allowedWidthPx = 3000)
                rows.map { it.indices.size } shouldContainExactly listOf(10, 10)
            }
        }
    })

/** Raw keys as the layout engine sees them: explicit weight plus clickability. */
private fun RawKeyboard.layoutKeys(): List<LayoutEngine.Key> = keys.map { raw ->
    LayoutEngine.Key(
        weight = raw["width"]?.toFloatOrNull()?.takeIf { it > 0f },
        clickable = raw["click"] != null,
    )
}
