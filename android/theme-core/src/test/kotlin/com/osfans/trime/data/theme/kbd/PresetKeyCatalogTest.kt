/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.data.theme.model.PresetKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.io.File

@kotlinx.serialization.Serializable
private data class KeysHolder(
    val presetKeys: Map<String, PresetKey> = emptyMap(),
)

/**
 * What the action picker is allowed to claim about a key.
 *
 * The three editors (key bindings, toolbar actions, preset-key list) all offer
 * this dictionary, and the row a user taps has to say the same thing in all
 * three: the name the author gave it, what it does, and whether it is a switch.
 */
class PresetKeyCatalogTest :
    BehaviorSpec({
        fun decoded(): Map<String, PresetKey> {
            val bytes =
                checkNotNull(javaClass.getResourceAsStream("/tongwenfeng.trime.yaml")) {
                    "missing test fixture tongwenfeng.trime.yaml"
                }.use { it.readBytes() }
            val file = File.createTempFile("keys", ".yaml").apply {
                writeBytes(bytes)
                deleteOnExit()
            }
            val text = ThemeTextFile.read(file)!!.text
            return ThemeYaml.parser.decodeFromString(KeysHolder.serializer(), text).presetKeys
        }

        Given("the preset keys of a shipped theme") {
            Then("every entry becomes one row") {
                val keys = decoded()
                keys.size shouldBe 240
                PresetKeyCatalog.build(keys).size shouldBe 240
            }

            Then("rows are ordered by kind, letters first") {
                val entries = PresetKeyCatalog.build(decoded())
                val orders = entries.map { it.kind.order }
                orders shouldBe orders.sorted()
                entries.first().kind shouldBe PresetKeyEntry.Kind.SEND
            }
        }

        Given("how a row is named") {
            val keys = decoded()

            Then("the author's label wins") {
                val entry = PresetKeyCatalog.entryOf("BackSpace", keys.getValue("BackSpace"))
                entry.displayName shouldBe "退格"
                entry.kind shouldBe PresetKeyEntry.Kind.SEND
                entry.detail shouldBe "send: BackSpace"
                entry.twoState shouldBe false
            }

            Then("a key with no label is named by its first state") {
                val entry = PresetKeyCatalog.entryOf("Mode_switch", keys.getValue("Mode_switch"))
                entry.displayName shouldBe "中文"
                entry.stateCount shouldBe 2
                entry.twoState shouldBe true
                entry.kind shouldBe PresetKeyEntry.Kind.TOGGLE
            }

            Then("a key with neither is named by its id") {
                val keys = mapOf("Some_key" to PresetKey(send = "Some_key"))
                PresetKeyCatalog.entryOf("Some_key", keys.getValue("Some_key")).displayName shouldBe "Some_key"
            }

            Then("a selector says what it selects, not what it sends") {
                // `Keyboard_default` sends `Eisu_toggle` as well; the picker has
                // to describe the part that matters.
                val entry = PresetKeyCatalog.entryOf("Keyboard_default", keys.getValue("Keyboard_default"))
                entry.kind shouldBe PresetKeyEntry.Kind.SELECT
                entry.detail shouldBe "select: .default"
            }
        }

        Given("the two-state rule") {
            Then("only exactly two states count as a switch") {
                val entries = PresetKeyCatalog.build(decoded())
                val toggles = entries.filter { it.kind == PresetKeyEntry.Kind.TOGGLE }
                toggles.map { it.id } shouldContain "Mode_switch"
                toggles.filter { it.twoState }.size shouldBe 5
                toggles.forEach { if (it.stateCount != 2) it.twoState shouldBe false }
            }
        }
    })
