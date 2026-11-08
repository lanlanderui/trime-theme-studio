/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

@kotlinx.serialization.Serializable
private data class CatalogHolder(
    val presetKeyboards: Map<String, TextKeyboard> = emptyMap(),
)

/**
 * What the layout list is allowed to claim about a card.
 *
 * The card is the only place that can warn "this is an alias" or "a custom file
 * overrides this" before the user spends time on it, so the flags have to come
 * from the file view, not from a guess.
 */
class KeyboardCatalogTest :
    BehaviorSpec({
        fun fixture(): File {
            val bytes =
                checkNotNull(javaClass.getResourceAsStream("/tongwenfeng.trime.yaml")) {
                    "missing test fixture tongwenfeng.trime.yaml"
                }.use { it.readBytes() }
            return File.createTempFile("catalog", ".yaml").apply {
                writeBytes(bytes)
                deleteOnExit()
                File(parentFile, name + ".bak").deleteOnExit()
            }
        }

        /**
         * The decoded layouts of [text], straight from YAML.
         *
         * Deliberately *not* run through `ThemeDslExpander`: an alias card's keys
         * come from its target after expansion, and the alias tests stand in for
         * that by handing the target's layout to `entryOf`.
         */
        fun decode(text: String): Map<String, TextKeyboard> = ThemeYaml.parser.decodeFromString(CatalogHolder.serializer(), text).presetKeyboards

        fun decoded(file: File): Map<String, TextKeyboard> = decode(ThemeTextFile.read(file)!!.text)

        val sample =
            """
            preset_keyboards:
              full:
                name: 完整布局
                width: 10
                keys:
                  - {click: q}
                  - {width: 5}
                  - {click: w}
              base:
                name: 底座
                width: 10
                keys:
                  - {click: a}
              alias:
                __include: base
              extended:
                __include: base
                name: 扩写
                keys:
                  - {click: z}
              blocked:
                name: 读不了
                keys: [ {click: x} ]
            """.trimIndent()

        Given("a layout that owns its keys") {
            Then("it is editable, and spacers are counted from the model") {
                val layouts = decode(sample)
                val raw = ThemeKeyboardReader.read(sample.lines()).first { it.id == "full" }
                val entry = KeyboardCatalog.entryOf(raw, layouts["full"])

                entry.displayName shouldBe "完整布局"
                entry.keyCount shouldBe 3
                entry.lineCount shouldBe 3
                entry.hasSpacer shouldBe true
                entry.isAlias shouldBe false
                entry.inherits shouldBe false
                entry.editable shouldBe true
            }

            Then("a layout without spacers says so") {
                val layouts = decode(sample)
                val raw = ThemeKeyboardReader.read(sample.lines()).first { it.id == "base" }
                KeyboardCatalog.entryOf(raw, layouts["base"]).hasSpacer shouldBe false
            }
        }

        Given("a layout that only includes another one") {
            Then("it is an alias: readable, not editable, and it names its target") {
                val layouts = decode(sample)
                val raw = ThemeKeyboardReader.read(sample.lines()).first { it.id == "alias" }
                // After the loader expands it, this card carries the target's keys.
                val entry = KeyboardCatalog.entryOf(raw, layouts.getValue("base"))

                entry.aliasTarget shouldBe "base"
                entry.lineCount shouldBe 0
                entry.isAlias shouldBe true
                entry.editable shouldBe false
                // The card still draws the target's keys.
                entry.keyCount shouldBe 1
            }
        }

        Given("a layout that includes another one and adds keys") {
            Then("it is editable and still says where it came from") {
                val layouts = decode(sample)
                val raw = ThemeKeyboardReader.read(sample.lines()).first { it.id == "extended" }
                val entry = KeyboardCatalog.entryOf(raw, layouts["extended"])

                entry.aliasTarget shouldBe "base"
                entry.isAlias shouldBe false
                entry.inherits shouldBe true
                entry.editable shouldBe true
            }
        }

        Given("a block the reader cannot edit") {
            Then("it is reported, not offered") {
                val layouts = decode(sample)
                val raw = ThemeKeyboardReader.read(sample.lines()).first { it.id == "blocked" }
                val entry = KeyboardCatalog.entryOf(raw, layouts["blocked"], patched = true)

                entry.unsupported shouldNotBe null
                entry.editable shouldBe false
                entry.patched shouldBe true
            }
        }

        Given("the shipped theme") {
            Then("every layout is editable and four of them carry spacers") {
                val file = fixture()
                val layouts = decoded(file)
                val entries = KeyboardCatalog.build(ThemeKeyboardReader.read(file), layouts)

                entries.size shouldBe 50
                entries.count { it.editable } shouldBe 50
                entries.count { it.hasSpacer } shouldBe 9
                entries.none { it.isAlias } shouldBe true
                // The id the theme itself uses for the main keyboard.
                entries.first { it.id == "default" }.displayName shouldBe "26键默认布局"
                entries.sumOf { it.lineCount } shouldBe 2465
            }

            Then("a patch on one layout shows up on that card only") {
                val file = fixture()
                val entries =
                    KeyboardCatalog.build(
                        raws = ThemeKeyboardReader.read(file),
                        layouts = decoded(file),
                        patched = setOf("default"),
                    )
                entries.count { it.patched } shouldBe 1
                entries.first { it.patched }.id shouldBe "default"
            }
        }

        Given("the spacer rule") {
            Then("it is the inverse of producing a runtime key") {
                KeyboardCatalog.isSpacer(TextKey()) shouldBe true
                KeyboardCatalog.isSpacer(TextKey(click = KeyActionToken.Plain("q"))) shouldBe false
                KeyboardCatalog.isSpacer(TextKey(click = KeyActionToken.Plain(""))) shouldBe true
                KeyboardCatalog.isSpacer(
                    TextKey(click = KeyActionToken.Inline(commit = "，", label = "，")),
                ) shouldBe false
            }
        }
    })
