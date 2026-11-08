/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

/**
 * What a save is allowed to touch inside `preset_keys`.
 *
 * The rule is the same as for layouts: patch the line, keep everything the
 * editor did not ask about — unknown fields, field order, the trailing comment.
 */
class ThemePresetKeyWriterTest :
    BehaviorSpec({
        fun withSection(block: (File, RawPresetSection) -> Unit) {
            val file = File.createTempFile("theme", ".yaml")
            try {
                file.writeText(FIXTURE)
                block(file, ThemePresetKeyReader.read(file)!!)
            } finally {
                listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
            }
        }

        Given("a label edit") {
            Then("only that field changes; order, unknown fields and the comment stay") {
                withSection { file, section ->
                    ThemePresetKeyWriter.update(file, section, "BackSpace", mapOf("label" to "退"))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    file.readText() shouldContain
                        "  BackSpace: {label: 退, send: BackSpace, repeatable: true} # 退格"
                }
            }

            Then("a change that changes nothing leaves the file alone") {
                withSection { file, section ->
                    ThemePresetKeyWriter.update(file, section, "BackSpace", mapOf("label" to "退格"))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    file.readText() shouldBe FIXTURE
                }
            }
        }

        Given("a field the editor cannot render") {
            Then("it is kept as written when another field is edited") {
                withSection { file, section ->
                    ThemePresetKeyWriter.update(file, section, "CALCULATOR", mapOf("label" to "计算器"))
                    val text = file.readText()
                    text shouldContain
                        "  CALCULATOR: {label: 计算器, command: run, option: \"https://example.com/?a=1\", sticky: true}"
                }
            }
        }

        Given("clearing and adding fields") {
            Then("null removes the field, a new key appends it") {
                withSection { file, section ->
                    ThemePresetKeyWriter.update(
                        file,
                        section,
                        "Clear",
                        mapOf("text" to null, "label" to "清空"),
                    )
                    // The trailing comment stays: it belongs to the entry, not the fields.
                    val line = file.readLines().first { it.contains("Clear:") }
                    line.trim() shouldBe "Clear: {label: 清空} # 全選並刪除"
                }
            }
        }

        Given("a new entry") {
            Then("it is appended after the last one, not sorted into place") {
                withSection { file, section ->
                    ThemePresetKeyWriter.add(file, section, "my_key", mapOf("label" to "我的键", "send" to "a"))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    val lines = file.readLines()
                    val added = lines.indexOfFirst { it.startsWith("  my_key:") }
                    val last = lines.indexOfFirst { it.startsWith("  Clear:") }
                    added shouldBe last + 1
                    lines[added].trim() shouldBe "my_key: {label: 我的键, send: a}"
                }
            }

            Then("a duplicate id is refused") {
                withSection { file, section ->
                    ThemePresetKeyWriter.add(file, section, "BackSpace", mapOf("label" to "x"))
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldBe FIXTURE
                }
            }

            Then("an id with a space or colon is refused before touching the file") {
                withSection { file, section ->
                    ThemePresetKeyWriter.idProblem("my key") shouldBe "名字不能包含空格"
                    ThemePresetKeyWriter.idProblem("a:b") shouldBe "名字不能包含冒号"
                    ThemePresetKeyWriter.idProblem("  ") shouldBe "名字不能为空"
                    ThemePresetKeyWriter.idProblem("ok_key") shouldBe null
                }
            }
        }

        Given("a deleted entry") {
            Then("only its line disappears") {
                withSection { file, section ->
                    ThemePresetKeyWriter.delete(file, section, "CALCULATOR")
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Success>()
                    val text = file.readText()
                    text.contains("CALCULATOR") shouldBe false
                    text shouldContain "  BackSpace: {label: 退格, send: BackSpace, repeatable: true} # 退格"
                    text shouldContain "  Clear: {label: 清除"
                }
            }

            Then("an unknown id is refused") {
                withSection { file, section ->
                    ThemePresetKeyWriter.delete(file, section, "nope")
                        .shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                    file.readText() shouldBe FIXTURE
                }
            }
        }

        Given("a file that changed underneath") {
            Then("the write is refused instead of editing the wrong line") {
                withSection { file, section ->
                    // Someone renamed the entry under us: the line still exists,
                    // but it is no longer this preset key.
                    file.writeText(FIXTURE.replace("  BackSpace: {label: 退格", "  Something: {label: 退格"))
                    val result = ThemePresetKeyWriter.update(file, section, "BackSpace", mapOf("label" to "退"))
                    result.shouldBeInstanceOf<ThemeKeyboardWriter.Result.Failure>()
                }
            }
        }
    })

private val FIXTURE =
    """
    preset_keys:
      BackSpace: {label: 退格, send: BackSpace, repeatable: true} # 退格
      CALCULATOR: {label: 計算機, command: run, option: "https://example.com/?a=1", sticky: true}
      Clear: {label: 清除, text: "{Control+a}{BackSpace}"} # 全選並刪除

    preset_keyboards:
      alpha:
        keys:
          - {click: a}
    """.trimIndent() + "\n"
