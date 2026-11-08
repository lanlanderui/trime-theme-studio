/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.kbd.ThemeKeyboardWriter
import com.osfans.trime.data.theme.kbd.ThemePresetKeyReader
import com.osfans.trime.data.theme.kbd.ThemePresetKeyWriter
import com.osfans.trime.data.theme.scalar.ThemeScalarReader
import com.osfans.trime.data.theme.scalar.ThemeScalarWriter
import com.osfans.trime.data.theme.tb.ThemeToolBarReader
import com.osfans.trime.data.theme.tb.ThemeToolBarWriter
import com.osfans.trime.data.theme.tb.ToolBarCatalog
import com.osfans.trime.data.theme.tb.ToolBarSlot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

/**
 * The same gate as the layout editor, for the three sections D7 edits.
 *
 * `preset_keys`, `tool_bar` and the candidate/preedit scalar blocks are all
 * hand-written YAML in the same file as the layouts: a save that re-renders a
 * line the user did not touch shows up as noise in `git diff`, and after a few
 * of those the author stops trusting the editor. So every section is checked
 * the same way — write back what was read, and nothing may change.
 */
class ThemeSectionsRoundTripTest :
    BehaviorSpec({
        fun fixture(): File {
            val bytes =
                checkNotNull(javaClass.getResourceAsStream("/tongwenfeng.trime.yaml")) {
                    "missing test fixture tongwenfeng.trime.yaml"
                }.use { it.readBytes() }
            return File.createTempFile("sections", ".yaml").apply {
                writeBytes(bytes)
                deleteOnExit()
                File(parentFile, name + ".bak").deleteOnExit()
            }
        }

        Given("the preset_keys of a shipped theme") {
            Then("it is the dictionary the gate was written against") {
                val file = fixture()
                val section = ThemePresetKeyReader.read(file) shouldNotBe null
                section!!.keys.size shouldBe 240
                // Every entry is a single `id: {…}` line — no block-style entry
                // the reader would skip over.
                section.keys.map { it.id }.distinct().size shouldBe 240
            }

            Then("writing each entry back as it was leaves the file byte for byte") {
                val file = fixture()
                val before = file.readText()
                val section = ThemePresetKeyReader.read(file)!!
                var touched = 0
                section.keys.forEach { key ->
                    val label = key["label"] ?: return@forEach
                    ThemePresetKeyWriter.update(file, section, key.id, mapOf("label" to label))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    touched++
                }
                // 235 of the 240 carry a label; the other five are pure bindings.
                touched shouldBe 235
                file.readText() shouldBe before
            }

            Then("a one-field edit moves exactly one line") {
                val file = fixture()
                val before = file.readText().lines()
                val section = ThemePresetKeyReader.read(file)!!
                val target = section.keys.first { it.id == "BackSpace" }
                ThemePresetKeyWriter.update(file, section, "BackSpace", mapOf("label" to "退格键"))
                    .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                val after = file.readText().lines()
                after.size shouldBe before.size
                val changed = before.indices.filter { before[it] != after[it] }
                changed.size shouldBe 1
                changed.single() shouldBe target.line
            }

            Then("a boolean is written bare, not quoted") {
                // `functional: 'true'` is a string, not a flag; the theme wrote a
                // bare boolean and the editor must keep that shape.
                val file = fixture()
                ThemePresetKeyWriter.update(file, ThemePresetKeyReader.read(file)!!, "BackSpace", mapOf("functional" to "true"))
                    .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                val line = file.readText().lines().first { it.startsWith("  BackSpace: ") }
                line shouldContain "functional: true"
                line.contains("'true'") shouldBe false
            }

            Then("a state list stays a flow sequence") {
                val file = fixture()
                ThemePresetKeyWriter.update(file, ThemePresetKeyReader.read(file)!!, "Mode_switch", mapOf("states" to "[ 中文, 西文 ]"))
                    .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                file.readText().lines().first { it.startsWith("  Mode_switch: ") } shouldContain
                    "states: [ 中文, 西文 ]"
            }

            Then("clearing a field removes it instead of pinning an empty value") {
                val file = fixture()
                ThemePresetKeyWriter.update(file, ThemePresetKeyReader.read(file)!!, "BackSpace", mapOf("preview" to null))
                    .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                val line = file.readText().lines().first { it.startsWith("  BackSpace: ") }
                line.contains("preview") shouldBe false
                line shouldContain "label: 退格"
            }
        }

        Given("the tool_bar of a shipped theme") {
            Then("its scalars, blocks and buttons read back as written") {
                val raw = ThemeToolBarReader.read(fixture()) shouldNotBe null
                raw!!.editable shouldBe true
                raw.scalars.keys shouldBe setOf("button_spacing", "button_font", "back_style")
                raw.buttons.size shouldBe 6
                // Six aliases, no inline maps: the shape the editor can edit.
                raw.buttons.count { it.isInline } shouldBe 0
                raw.primary shouldNotBe null
                // The list is written right-to-left on screen; `buttons[0]` is
                // drawn at the right edge, which is why the editor reverses it.
                raw.buttons.first().alias shouldBe "hide_button"
                ToolBarSlot.arrayToVisual(0, raw.buttons.size) shouldBe 5
                ToolBarSlot.visualToArray(5, raw.buttons.size) shouldBe 0
            }

            Then("writing the same scalars, fields and list back changes nothing") {
                val file = fixture()
                val before = file.readText()
                val raw = ThemeToolBarReader.read(file)!!

                raw.scalars.forEach { (key, leaf) ->
                    leaf.value?.let { value ->
                        ThemeToolBarWriter.setScalar(file, key, value)
                            .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    }
                }
                raw.buttons.map { it.source }.let { sources ->
                    ThemeToolBarWriter.setButtons(file, sources)
                        .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                }
                // One field of one block, written with the value it already has.
                ThemeToolBarWriter.setBlockField(file, "hide_button", listOf("foreground", "style"), "\"ic@keyboard_close\"")
                    .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()

                file.readText() shouldBe before
            }

            Then("moving a button in the visual list only rewrites the buttons line") {
                val file = fixture()
                val before = file.readText().lines()
                val raw = ThemeToolBarReader.read(file)!!
                val line = raw.buttonsLine!!
                // Drag the left-most button (visual 0) to the far right. Visual 0
                // is the array's *last* item — the list is drawn ROW_REVERSE — so
                // the drag ends with it stored at index 0.
                val moved = ToolBarSlot.move(raw.buttons.map { it.source }, 0, raw.buttons.size - 1)
                moved.first() shouldBe "*full_shape_button"
                moved.last() shouldBe "*ascii_mode_button"
                ThemeToolBarWriter.setButtons(file, moved)
                    .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()

                val after = file.readText().lines()
                after.size shouldBe before.size
                val changed = before.indices.filter { before[it] != after[it] }
                changed.size shouldBe 1
                changed.single() shouldBe line
            }

            Then("editing an anchored block is what the buttons list then reads back") {
                // `hide_button: &hide_button` is referenced as `*hide_button`, so the
                // block is the single source for that button: what the editor shows
                // after the write is what the keyboard will draw.
                val file = fixture()
                val before = file.readText().lines()
                ThemeToolBarWriter.setBlockField(file, "hide_button", listOf("action"), "Menu")
                    .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()

                val after = file.readText().lines()
                val changed = before.indices.filter { before[it] != after[it] }
                changed.size shouldBe 1
                // The anchor itself is untouched.
                after[changed.single()] shouldBe "    action: Menu"

                val model = ToolBarCatalog.build(ThemeToolBarReader.read(file)!!)
                model.buttons.first { it.blockId == "hide_button" }.action shouldBe "Menu"
                model.buttons.first { it.blockId == "hide_button" }.anchor shouldBe "hide_button"
            }
        }

        Given("a theme without a candidate window block") {
            // This theme never writes one, so the runtime uses libime's defaults.
            // The reader reports "absent" rather than inventing values — but a
            // write is what *adds* the block, which is how the screen offers
            // "this theme does not set it; add it?".
            Then("the reader reports it absent rather than inventing one") {
                val section = ThemeScalarReader.read(fixture(), "candidate_window")
                section.range shouldBe null
                section.leaves shouldBe emptyList()
            }

            Then("writing one appends the block and leaves the rest of the file alone") {
                val file = fixture()
                val before = file.readText()
                ThemeScalarWriter.set(file, "candidate_window", listOf("height"), "30") shouldBe true
                val after = file.readText()
                (after.length > before.length) shouldBe true
                after.startsWith(before.trimEnd()) shouldBe true
                after shouldContain "candidate_window:"
                // Reading it back gives the leaf that was just written.
                val section = ThemeScalarReader.read(file, "candidate_window")
                section.leaf(listOf("height"))?.value shouldBe "30"
            }
        }
    })
