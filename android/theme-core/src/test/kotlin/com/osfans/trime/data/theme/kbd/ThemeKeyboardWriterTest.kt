/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

/**
 * Pins what a save is allowed to touch.
 *
 * A theme is a hand-written document: a key line may carry fields this app
 * knows nothing about, a comment after it, and an indentation the author chose.
 * The writer patches a line in place, and these cases say exactly what that means.
 */
class ThemeKeyboardWriterTest :
    BehaviorSpec({
        fun fileWith(
            text: String,
            block: (File, RawKeyboard) -> Unit,
        ) {
            val file = File.createTempFile("theme", ".yaml")
            try {
                file.writeText(text)
                val raw = ThemeKeyboardReader.read(file).first { it.id == "alpha" }
                block(file, raw)
            } finally {
                listOf("", ".bak", ".tmp").forEach { suffix ->
                    File(file.parentFile, file.name + suffix).delete()
                }
            }
        }

        // Mirrors what the theme loader hands the editor: every field the file
        // carries, not just the ones a test happens to care about.
        fun keysOf(raw: RawKeyboard): MutableList<TextKey> = raw.keys.map {
            TextKey(
                width = it["width"]?.toFloatOrNull() ?: 0f,
                click = it["click"]?.let { token -> KeyActionToken.Plain(token) },
                longClick = it["long_click"]?.let { token -> KeyActionToken.Plain(token) },
                // popup 的两种写法（列表 / 空格分隔的字符串）都归一到列表，
                // 编辑器拿到的模型就是这个形状。
                popup = ThemeKeyboardReader.popupOf(it).orEmpty(),
            )
        }.toMutableList()

        Given("a width edit") {
            Then("only that line changes, and unknown fields plus the comment survive") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(width = 7f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    file.readText() shouldBe
                        SAMPLE.replace(
                            "- {click: q, long_click: '_', popup: 'q w', width: 5}  # 字母",
                            "- {click: q, long_click: '_', popup: 'q w', width: 7}  # 字母",
                        )
                }
            }

            Then("clearing the width drops the field instead of writing zero") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[2] = keys[2].copy(width = 0f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "- {click: e}"
                }
            }

            Then("a value equal to the author's spelling is not reformatted") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    // 10.0 stays 10.0: same weight, so the line must not be touched.
                    keys[2] = keys[2].copy(width = 10f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "- {click: e, width: 10.0}"
                }
            }
        }

        Given("an action edit") {
            Then("a new slot is appended to the flow map") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[1] = keys[1].copy(longClick = KeyActionToken.Plain("!"))
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "- {click: w, long_click: '!'}"
                }
            }

            Then("clearing a slot removes it") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(longClick = null)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "- {click: q, popup: 'q w', width: 5}"
                }
            }

            Then("an inline token is left exactly as written") {
                fileWith(INLINE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(
                        click = KeyActionToken.Inline(commit = "，", label = "逗号"),
                        width = 8f,
                    )
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "{click: {commit: '，', label: '逗号'}, width: 8}"
                }
            }
        }

        Given("a popup edit") {
            Then("a key that had none gets one, in the field order the file uses") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[1] = keys[1].copy(popup = listOf("1", "2"))
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    // 数字形态的 token 加引号：popup 里它们是字符串，不是数字。
                    file.readText() shouldContain "- {click: w, popup: ['1', '2']}"
                }
            }

            Then("the old space-separated spelling is replaced in place, not appended to") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(popup = keys[0].popup + "1")
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "long_click: '_', popup: [q, w, '1'], width: 5"
                }
            }

            Then("clearing it drops the field instead of writing an empty list") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(popup = emptyList())
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "- {click: q, long_click: '_', width: 5}"
                }
            }

            Then("tokens that YAML needs quoted get quoted") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[1] = keys[1].copy(popup = listOf(":", "1"))
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "popup: [':', '1']"
                }
            }

            Then("an untouched popup keeps the author's spelling") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(width = 7f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    // 'q w' 原样留着：模型里它已经是 ["q","w"]，语义没变就不重排。
                    file.readText() shouldContain "popup: 'q w', width: 7}"
                }
            }
        }

        Given("the two spellings of popup") {
            Then("both read as the same list") {
                ThemeKeyboardReader.popupOf("""["TrimeWiki", "Settings"]""") shouldBe listOf("TrimeWiki", "Settings")
                ThemeKeyboardReader.popupOf("'q w'") shouldBe listOf("q", "w")
            }

            Then("absent and empty both read as no popup") {
                ThemeKeyboardReader.popupOf(null as String?) shouldBe emptyList()
                ThemeKeyboardReader.popupOf("[]") shouldBe emptyList()
            }
        }

        Given("the two indentation styles in the shipped theme") {
            Then("four-space items stay at four spaces") {
                fileWith(FOUR_SPACE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(width = 6f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    file.readText() shouldContain "    - {click: a, width: 6}"
                }
            }

            Then("a CRLF file stays CRLF") {
                fileWith(SAMPLE.replace("\n", "\r\n")) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(width = 7f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    val text = file.readText()
                    text.count { it == '\r' } shouldBe text.count { it == '\n' }
                }
            }
        }

        Given("a key nobody touched") {
            Then("its line is left byte for byte, quirks included") {
                fileWith(QUIRK) { file, raw ->
                    val keys = keysOf(raw)
                    keys[1] = keys[1].copy(width = 7f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    // The odd space before `}` on the first line stays as written.
                    file.readText() shouldContain "- {click: a, long_click: select_all }"
                }
            }
        }

        Given("a deleted key") {
            Then("its line disappears and the neighbours stay as written") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    // Key 1 (`w`) removed: origins 0 and 2 survive.
                    val kept = listOf(keys[0], keys[2])
                    ThemeKeyboardWriter.replaceKeys(file, raw, kept, listOf(0, 2))
                    val text = file.readText()
                    text shouldContain "      - {click: q, long_click: '_', popup: 'q w', width: 5}  # 字母"
                    text shouldContain "      - {click: e, width: 10.0}"
                    text.contains("{click: w}") shouldBe false
                }
            }
        }

        Given("a duplicated key") {
            Then("the copy carries the same line, once more") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    val doubled = listOf(keys[0], keys[1], keys[1], keys[2])
                    ThemeKeyboardWriter.replaceKeys(file, raw, doubled, listOf<Int?>(0, 1, 1, 2))
                    val text = file.readText()
                    text.split("{click: w}").size - 1 shouldBe 2
                }
            }
        }

        Given("a key with no source line") {
            Then("it is rendered from the model, not patched") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    val withBlank = listOf(keys[0], TextKey(width = 10f), keys[2])
                    ThemeKeyboardWriter.replaceKeys(file, raw, withBlank, listOf<Int?>(0, null, 2))
                    file.readText() shouldContain "      - {width: 10}"
                }
            }
        }

        Given("a key line changed outside the editor") {
            Then("the stale line range is refused instead of replacing that line") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(width = 7f)
                    file.writeText(SAMPLE.replace("- {click: w}", "- {click: x}"))

                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldContain "- {click: x}"
                    file.readText().contains("- {click: w}") shouldBe false
                }
            }
        }

        Given("a deleted layout") {
            Then("its block goes and the next one is untouched") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    val alpha = ThemeKeyboardReader.read(file).first { it.id == "alpha" }
                    ThemeKeyboardWriter.deleteLayout(file, alpha)
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    val text = file.readText()
                    text.contains("alpha:") shouldBe false
                    text shouldContain "  beta:"
                    text shouldContain "- {click: b}"
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("a moved range is refused instead of deleting the wrong block") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    val alpha = ThemeKeyboardReader.read(file).first { it.id == "alpha" }
                    // Someone else edited the file under us: the header moved.
                    file.writeText("preset_keyboards://n  gamma://n    keys://n      - {click: c}\n")
                    val result = ThemeKeyboardWriter.deleteLayout(file, alpha)
                    result.shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldContain "gamma:"
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }
        }

        Given("a layout created from scratch") {
            Then("it lands at the end of the section, spaced off, and reads back") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    ThemeKeyboardWriter.createLayout(file, "gamma", "丙")
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    val text = file.readText()
                    // A blank line on both sides: the new block must not glue
                    // itself to `preset_keys:` that follows the section.
                    text shouldContain "- {click: b}\n\n  gamma:"
                    text shouldContain "      - {width: 10}\n\npreset_keys:"
                    text shouldContain "    name: 丙"
                    // The layouts that were there keep their exact spelling.
                    text shouldContain "  alpha:\n    name: 甲"

                    val created = ThemeKeyboardReader.read(file).first { it.id == "gamma" }
                    created.scalars["name"] shouldBe "丙"
                    created.scalars["width"] shouldBe "10"
                    created.keys.size shouldBe 1
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("keys can be written into the new block straight away") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    ThemeKeyboardWriter.createLayout(file, "gamma", "丙")
                    val raw = ThemeKeyboardReader.read(file).first { it.id == "gamma" }
                    // The spacer is the one line an empty layout patches; without
                    // it there would be nothing for the editor to write against.
                    ThemeKeyboardWriter.replaceKeys(file, raw, listOf(TextKey(click = KeyActionToken.Plain("q"))), listOf(null))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    val text = file.readText()
                    text shouldContain "      - {click: q}"
                    text.contains("{width: 10}") shouldBe false
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("an id already in the file is refused and nothing is written") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    ThemeKeyboardWriter.createLayout(file, "alpha", "又一份")
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldBe TWO_LAYOUTS
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("an id that is not a layout id is refused") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    // A space would break the two-space block header the reader
                    // keys on, and a colon would end the key outright.
                    ThemeKeyboardWriter.createLayout(file, "我的 布局", "丙")
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldBe TWO_LAYOUTS
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("a name that would be read as syntax is quoted") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(TWO_LAYOUTS)
                    ThemeKeyboardWriter.createLayout(file, "gamma", "甲: 乙")
                    file.readText() shouldContain "    name: '甲: 乙'"
                    ThemeKeyboardReader.read(file).first { it.id == "gamma" }.scalars["name"] shouldBe "甲: 乙"
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }
        }

        Given("a layout whose rows are spaced out") {
            Then("an edit keeps the blank and comment lines between the keys") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(SPACED)
                    val raw = ThemeKeyboardReader.read(file).first { it.id == "alpha" }
                    val keys = keysOf(raw)
                    keys[1] = keys[1].copy(width = 7f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    file.readText() shouldBe SPACED.replace("- {click: b}", "- {click: b, width: 7}")
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("deleting a key does not take them with it") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(SPACED)
                    val raw = ThemeKeyboardReader.read(file).first { it.id == "alpha" }
                    val keys = keysOf(raw)
                    ThemeKeyboardWriter.replaceKeys(file, raw, listOf(keys[0], keys[2], keys[3]), listOf(0, 2, 3))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    val text = file.readText()
                    text.contains("{click: b}") shouldBe false
                    text shouldContain "# 下一行"
                    text.lines().count { it.isEmpty() } shouldBe SPACED.lines().count { it.isEmpty() }
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }
        }

        Given("a file with a backup") {
            Then("restoring swaps the two and can be undone by repeating it") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(SAMPLE)
                    val raw = ThemeKeyboardReader.read(file).first { it.id == "alpha" }
                    val edited = keysOf(raw).also { it[0] = it[0].copy(width = 7f) }
                    ThemeKeyboardWriter.replaceKeys(file, raw, edited)
                    val afterEdit = file.readText()

                    ThemeKeyboardWriter.hasBackup(file) shouldBe true
                    ThemeKeyboardWriter.restoreBackup(file)
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    file.readText() shouldBe SAMPLE

                    // Same action again returns to where we started.
                    ThemeKeyboardWriter.restoreBackup(file)
                    file.readText() shouldBe afterEdit
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }

            Then("restoring without a backup is refused") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(SAMPLE)
                    ThemeKeyboardWriter.hasBackup(file) shouldBe false
                    ThemeKeyboardWriter.restoreBackup(file)
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldBe SAMPLE
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }
        }

        Given("keys and origins that disagree") {
            Then("the write is refused rather than guessed at") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    val result = ThemeKeyboardWriter.replaceKeys(file, raw, keys, listOf<Int?>(0, 1))
                    result.shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldBe SAMPLE
                }
            }
        }

        Given("a successful write") {
            Then("the previous file is kept next to it") {
                fileWith(SAMPLE) { file, raw ->
                    val keys = keysOf(raw)
                    keys[0] = keys[0].copy(width = 7f)
                    ThemeKeyboardWriter.replaceKeys(file, raw, keys)
                    File(file.parentFile, file.name + ".bak").readText() shouldBe SAMPLE
                }
            }
        }
    })

private val SAMPLE =
    """
    preset_keyboards:
      alpha:
        name: 甲
        keys:
          - {click: q, long_click: '_', popup: 'q w', width: 5}  # 字母
          - {click: w}
          - {click: e, width: 10.0}

    preset_keys:
      Return: {send: Return}
    """.trimIndent() + "\n"

private val TWO_LAYOUTS =
    """
    preset_keyboards:
      alpha:
        name: 甲
        keys:
          - {click: a}
      beta:
        name: 乙
        keys:
          - {click: b}

    preset_keys:
      Return: {send: Return}
    """.trimIndent() + "\n"

private val SPACED =
    """
    preset_keyboards:
      alpha:
        name: 甲
        keys:
          - {click: a}
          - {click: b}
          # 下一行
          - {click: c}

          - {click: d}

    preset_keys:
      Return: {send: Return}
    """.trimIndent() + "\n"

private val QUIRK =
    """
    preset_keyboards:
      alpha:
        keys:
          - {click: a, long_click: select_all }
          - {click: b}

    preset_keys:
      Return: {send: Return}
    """.trimIndent() + "\n"

private val INLINE =
    """
    preset_keyboards:
      alpha:
        keys:
          - {click: {commit: '，', label: '逗号'}}

    preset_keys:
      Return: {send: Return}
    """.trimIndent() + "\n"

private val FOUR_SPACE =
    """
    preset_keyboards:
      alpha:
        name: 乙
        keys:
        - {click: a}
        - {click: b}

    preset_keys:
      Return: {send: Return}
    """.trimIndent() + "\n"
