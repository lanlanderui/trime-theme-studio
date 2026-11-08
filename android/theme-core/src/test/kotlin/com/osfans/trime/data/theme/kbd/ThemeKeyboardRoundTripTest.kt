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
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Serializable
import java.io.File

/** Just enough of a theme to decode its layouts the way `Theme` does. */
@Serializable
private data class PresetHolder(
    val presetKeyboards: Map<String, TextKeyboard> = emptyMap(),
)

/**
 * The gate the whole layout editor rests on: a real theme's key lines must
 * survive a round trip untouched.
 *
 * The fixture is `tongwenfeng.trime.yaml` itself (202,005 bytes / 4,234 lines),
 * the file the keyboard is drawn from, so its quirks are the ones that must
 * survive: `width: 12.5`, `'@'` and `"18"` quoting, `#comment` tails, blank
 * lines grouping rows, `height: *jpgd5` anchors defined in another section.
 *
 * A save that rewrites a line the user did not touch shows up as noise in
 * `git diff`, and after a few of those the author stops trusting the editor
 * with their file. So "no edit" has to mean *no byte changes*, not "parses back
 * to the same layout".
 *
 * Copy it out of the app's theme directory with `theme-core/tools/pull-theme.sh`
 * (it also prints the sha256 the fixture is expected to have).
 */
class ThemeKeyboardRoundTripTest :
    BehaviorSpec({
        fun fixtureBytes(): ByteArray = checkNotNull(javaClass.getResourceAsStream("/tongwenfeng.trime.yaml")) {
            "missing test fixture tongwenfeng.trime.yaml"
        }.use { it.readBytes() }

        val original = String(fixtureBytes(), Charsets.UTF_8)

        fun decoded(): Map<String, TextKeyboard> = ThemeYaml.parser.decodeFromString(PresetHolder.serializer(), original).presetKeyboards

        fun tempFile(): File = File.createTempFile("keyboards", ".yaml").apply {
            writeBytes(fixtureBytes())
            deleteOnExit()
            File(parentFile, name + ".bak").deleteOnExit()
        }

        /** Every layout the file declares, in file order. */
        fun raws(file: File): List<RawKeyboard> = ThemeKeyboardReader.read(ThemeTextFile.read(file)!!.lines)

        Given("a shipped theme's preset_keyboards block") {
            Then("the fixture is still the CRLF file it claims to be") {
                // `.gitattributes` must keep this file verbatim (`-text`): with
                // LF endings the round trip below would still pass, while no
                // longer testing that a save preserves `\r\n`.
                original.contains("\r\n") shouldBe true
                original.count { it == '\n' } shouldBe 4234
            }

            Then("it is the block the gate was written against") {
                val file = tempFile()
                val layouts = raws(file)
                layouts.size shouldBe 50
                layouts.sumOf { it.keys.size } shouldBe 2465
                // This theme writes every layout out in full — no `__include`
                // aliases, no block-style key lists. Either would be reported
                // through `unsupported` rather than silently half-edited.
                layouts.count { it.keys.isEmpty() } shouldBe 0
                layouts.mapNotNull { it.unsupported } shouldBe emptyList()
            }

            Then("every layout with keys survives read → write → read byte for byte") {
                val decoded = decoded()
                val file = tempFile()
                val withKeys = raws(file).filter { it.keys.isNotEmpty() }
                withKeys.size shouldBe 50
                withKeys.forEach { raw ->
                    val config = decoded[raw.id]
                    config shouldNotBe null

                    val before = file.readText()
                    val draft = KeyboardDraft(config!!)
                    val result =
                        ThemeKeyboardWriter.replaceKeys(
                            file = file,
                            raw = raw,
                            keys = draft.toConfig().keys,
                            origins = draft.origins(),
                        )
                    result.shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    file.readText() shouldBe before
                }
            }
        }

        Given("one key of a shipped layout") {
            Then("changing its width moves exactly that line") {
                val decoded = decoded()
                val file = tempFile()
                val before = file.readText().lines()
                val raw = raws(file).first { it.id == "default" }
                val draft = KeyboardDraft(decoded.getValue("default"))
                draft.setWidth(0, 7.5f)
                ThemeKeyboardWriter.replaceKeys(file, raw, draft.toConfig().keys, draft.origins())
                    .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()

                val after = file.readText().lines()
                after.size shouldBe before.size
                val changed = before.indices.filter { before[it] != after[it] }
                changed.size shouldBe 1
                // The new width is appended; every entry the line already had
                // keeps its spelling, its order and its value.
                val oldEntries = KeyboardYaml.parseFlowMap(before[changed.single()].substringAfter("- "))!!
                val newEntries = KeyboardYaml.parseFlowMap(after[changed.single()].substringAfter("- "))!!
                newEntries.filter { it.key != "width" } shouldBe oldEntries
                newEntries.first { it.key == "width" }.value shouldBe "7.5"
            }
        }

        Given("every layout padded with spacers") {
            // A key with no action takes width but builds no runtime key, so the
            // draft index and the keyboard index drift apart after the first
            // spacer. Selecting the key under the finger depends on this map, so
            // it is checked on all of them, not just the one that was reported.
            Then("the draft-to-key map skips exactly the spacers") {
                val decoded = decoded()
                val drafting = KeyboardDraft(decoded.getValue("default"))
                // Counted from the model, not from `producesKey` itself: the two
                // agreeing is the assertion.
                val clickable = (0 until drafting.size).count { clickables(drafting.key(it)) }
                drafting.size shouldNotBe clickable

                val map = drafting.indexMap(clickable)
                map.draftToKey.count { it >= 0 } shouldBe clickable
                // Indexed by draft position, holding runtime key numbers — so a
                // contiguous 0..n-1 once the spacers are dropped.
                map.draftToKey.filter { it >= 0 } shouldBe (0 until clickable).toList()
                // Indexed by runtime key number, holding draft positions: this is
                // the direction a tap travels, and the spacers must be missing.
                map.keyToDraft.filter { it >= 0 } shouldBe
                    (0 until drafting.size).filter { clickables(drafting.key(it)) }
                // Every runtime key is reachable from the draft, and back.
                map.keyToDraft.all { it >= 0 } shouldBe true
            }

            Then("none of them maps a runtime key onto a spacer") {
                val file = tempFile()
                val spaced = KeyboardCatalog.build(ThemeKeyboardReader.read(file), decoded())
                    .filter { it.hasSpacer }
                // Nine of the shipped theme's fifty layouts carry spacers.
                spaced.size shouldBe 9
                spaced.forEach { entry ->
                    val drafting = KeyboardDraft(entry.layout!!)
                    val clickable = (0 until drafting.size).count { clickables(drafting.key(it)) }
                    val map = drafting.indexMap(clickable)
                    map.keyToDraft.filter { it >= 0 }.forEach { draftIndex ->
                        clickables(drafting.key(draftIndex)) shouldBe true
                    }
                    map.draftToKey.filter { it >= 0 }.size shouldBe clickable
                }
            }
        }
    })

/** The click rule `Keyboard` uses, spelled out here so the draft cannot drift from it silently. */
private fun clickables(key: TextKey): Boolean = when (val token = key.click) {
    null -> false
    is KeyActionToken.Plain -> token.token.isNotEmpty()
    is KeyActionToken.Inline -> true
}
