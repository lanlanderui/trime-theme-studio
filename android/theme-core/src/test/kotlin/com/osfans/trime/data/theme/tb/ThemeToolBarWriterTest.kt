/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.tb

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

/**
 * What a save may touch inside `tool_bar`.
 *
 * The fixture follows the real block in `tongwenfeng.trime.yaml`, comment
 * alignment included: the point of these tests is that an edit changes one line
 * and leaves the author's formatting intact, including the `#`-prefixed
 * defaults that sit next to the live key they document.
 */
class ThemeToolBarWriterTest :
    BehaviorSpec({
        fun withFile(block: (File) -> Unit) {
            val file = File.createTempFile("theme", ".yaml")
            try {
                file.writeText(FIXTURE)
                block(file)
            } finally {
                listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
            }
        }

        /** The fixture with one line swapped, so only that line can differ. */
        fun fixtureWith(
            from: String,
            to: String,
        ): String {
            check(FIXTURE.contains(from)) { "fixture has no line: $from" }
            return FIXTURE.replace(from, to)
        }

        Given("a section scalar") {
            Then("button_spacing changes and its aligned comment stays") {
                withFile { file ->
                    ThemeToolBarWriter.setScalar(file, "button_spacing", "8")
                        .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldBe
                        fixtureWith("  button_spacing: 5 # 按钮间距", "  button_spacing: 8 # 按钮间距")
                }
            }
        }

        Given("a leaf that is already written") {
            Then("only its value changes; indentation and trailing comment stay") {
                withFile { file ->
                    ThemeToolBarWriter.setBlockField(
                        file,
                        "primary_button",
                        listOf("background", "type"),
                        "rectangle",
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldContain
                        "      type: rectangle           # 高亮形状 (可选值: circle|rectangle)"
                }
            }

            Then("a nested leaf is addressed relative to its own block") {
                withFile { file ->
                    ThemeToolBarWriter.setBlockField(
                        file,
                        "hide_button",
                        listOf("foreground", "style"),
                        "'ic@keyboard_off'",
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    val text = file.readText()
                    text shouldContain "      style: 'ic@keyboard_off'"
                    // The sibling block keeps its own `style` — the whole reason
                    // the writer addresses leaves per block instead of by
                    // absolute path.
                    text shouldContain "      style: \"ic@dots_horizontal\""
                }
            }
        }

        Given("a key the theme pinned as a comment") {
            Then("the commented line is replaced in place, at the live sibling indent") {
                withFile { file ->
                    ThemeToolBarWriter.setBlockField(
                        file,
                        "primary_button",
                        listOf("size"),
                        "[120, 24]",
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    // The source line is `    # size: ...`; its `#` puts the key
                    // one column right of where a live key belongs, so writing it
                    // back at 5 would be a YAML error next to `action:` at 4.
                    file.readText() shouldContain "    size: [120, 24]      # 视图组件整体宽高"
                }
            }

            Then("a commented leaf inside a group keeps the group's indent") {
                withFile { file ->
                    ThemeToolBarWriter.setBlockField(
                        file,
                        "primary_button",
                        listOf("foreground", "highlight"),
                        "hilited_candidate_text_color",
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldContain "      highlight: hilited_candidate_text_color"
                }
            }
        }

        Given("a key the block does not have") {
            Then("a line is appended at the block's child indent") {
                withFile { file ->
                    ThemeToolBarWriter.setBlockField(
                        file,
                        "hide_button",
                        listOf("long_press_action"),
                        "Settings",
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    val text = file.readText()
                    text shouldContain "    action: Hide\n    long_press_action: Settings\n"
                    // Still inside hide_button: the next block is untouched.
                    text shouldContain "\n  clipboard_button: &clipboard_button\n"
                }
            }

            Then("a missing parent group is created first, at a legal depth") {
                withFile { file ->
                    ThemeToolBarWriter.setBlockField(
                        file,
                        "hide_button",
                        listOf("background", "type"),
                        "circle",
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldContain
                        "    action: Hide\n    background:\n      type: circle\n"
                }
            }
        }

        Given("the buttons list") {
            Then("reordering rewrites only that line and keeps the spacing habit") {
                withFile { file ->
                    ThemeToolBarWriter.setButtons(file, listOf("*hide_button", "*emoji_button"))
                        .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldContain
                        "  buttons: [ *hide_button, *emoji_button ] # 从右向左排列"
                }
            }

            Then("writing the same list changes nothing") {
                withFile { file ->
                    ThemeToolBarWriter.setButtons(file, listOf("*hide_button"))
                        .shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldBe FIXTURE
                }
            }

            Then("an inline map item is carried through untouched") {
                withFile { file ->
                    ThemeToolBarWriter.setButtons(
                        file,
                        listOf("*hide_button", "{action: Hide, size: [40, 40]}"),
                    ).shouldBeInstanceOf<ThemeToolBarWriter.Result.Success>()
                    file.readText() shouldContain
                        "  buttons: [ *hide_button, {action: Hide, size: [40, 40]} ]"
                }
            }
        }

        Given("a request that cannot be honoured") {
            Then("an unknown block is refused without touching the file") {
                withFile { file ->
                    ThemeToolBarWriter
                        .setBlockField(file, "nope_button", listOf("action"), "Hide")
                        .shouldBeInstanceOf<ThemeToolBarWriter.Result.Failure>()
                    file.readText() shouldBe FIXTURE
                }
            }

            Then("a theme without tool_bar is refused, not invented") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText("style:\n  key_height: 50\n")
                    ThemeToolBarWriter.setScalar(file, "button_spacing", "5")
                        .shouldBeInstanceOf<ThemeToolBarWriter.Result.Failure>()
                    file.readText() shouldBe "style:\n  key_height: 50\n"
                } finally {
                    listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
                }
            }
        }
    })

private val FIXTURE =
    """
    style:
      key_height: 50

    tool_bar:
      button_spacing: 5 # 按钮间距
      button_font: iconfont.ttf # 按钮字体
      back_style: "ic@arrow-left" # 左侧返回按钮样式

      # 左侧固定主按钮配置
      primary_button:
        # size: [120, 24]      # 视图组件整体宽高
        # 背景样式设置
        background:
          type: circle           # 高亮形状 (可选值: circle|rectangle)
          corner_radius: 10      # 圆角半径
          normal: 0x00        # 正常状态背景颜色
          highlight: hilited_candidate_button_color  # 高亮状态背景色
          vertical_inset: 4      # 垂直方向内缩量
          horizontal_inset: 4    # 水平方向内缩量
        # 前景内容设置
        foreground:
          style: "ic@dots_horizontal"         # 内置质感设计图标样式
          normal: candidate_text_color  # 正常状态前景色
          # highlight:        # 高亮状态前景色
          padding: 10            # 前景内容与背景边界的内边距
        action: menu_keyboard
        long_press_action: Settings

      hide_button: &hide_button
        foreground:
          style: "ic@keyboard_close"
          normal: candidate_text_color
        action: Hide

      clipboard_button: &clipboard_button
        foreground:
          style: "ic@clipboard"
        action: clipboard_window

      buttons: [ *hide_button ] # 从右向左排列

    preset_keyboards:
      default: {}
    """.trimIndent() + "\n"
