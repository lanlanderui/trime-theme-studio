/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.scalar

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Writing one leaf of `preedit` / `window`.
 *
 * A write means four different things depending on what the file already says,
 * and the two insert branches are where a theme gets broken: an inserted key at
 * the wrong indent becomes a sibling of a group instead of a child, which
 * changes a setting nobody asked about.
 */
class ThemeScalarWriterTest :
    BehaviorSpec({
        /** Runs one write on a temp copy of [fixture] and gives back the lines it left. */
        fun withTemp(
            fixture: String,
            write: (File) -> Unit,
        ): List<String> {
            val file = File.createTempFile("theme", ".yaml")
            try {
                file.writeText(fixture)
                write(file)
                return file.readText().lines()
            } finally {
                file.delete()
            }
        }

        /** Writes [lines] out and reads [section] back through the reader. */
        fun read(
            section: String,
            lines: List<String>,
        ): RawScalarSection {
            val file = File.createTempFile("theme", ".yaml")
            try {
                file.writeText(lines.joinToString("\n"))
                return ThemeScalarReader.read(file, section)
            } finally {
                file.delete()
            }
        }

        // Lines keep their trailing comment, so a line is found by what it starts
        // with rather than by equality.
        fun line(lines: List<String>, text: String): Int = lines.indexOfFirst { it.trimStart().startsWith(text) }

        fun edit(
            section: String,
            path: List<String>,
            value: String,
            fixture: String = FIXTURE,
        ): List<String> = withTemp(fixture) { file -> ThemeScalarWriter.set(file, section, path, value) shouldBe true }

        fun reset(
            section: String,
            path: List<String>,
            fixture: String = FIXTURE,
        ): List<String> = withTemp(fixture) { file -> ThemeScalarWriter.reset(file, section, path) shouldBe true }

        Given("a live line") {
            Then("only its value changes, indent and comment stay") {
                val after = edit("window", listOf("insets", "horizontal"), "6")
                after[line(after, "horizontal: 6")] shouldBe "    horizontal: 6         # 横向内边距（整数）"
                // The sibling on the same group is untouched.
                after.none { it.contains("vertical: 6") } shouldBe true
            }

            Then("the rest of the file is byte-identical") {
                val after = edit("window", listOf("insets", "vertical"), "7")
                after.filterIndexed { i, _ -> i != line(after, "vertical: 7") } shouldBe
                    FIXTURE.lines().filterIndexed { i, _ -> i != line(FIXTURE.lines(), "vertical: 4") }
            }
        }

        Given("a commented line") {
            Then("it becomes live, keeping its column and its comment") {
                val after = edit("window", listOf("corner_radius"), "8")
                val written = after[line(after, "corner_radius: 8")]
                written.startsWith("  corner_radius: 8") shouldBe true
                written.contains("# 圆角半径（浮点）") shouldBe true
                after.none { it.trimStart().startsWith("#  corner_radius") } shouldBe true
            }

            Then("a commented line indented under a group keeps that group") {
                // `#    vertical: 0` belongs to item_padding, not to window.
                val after = edit("window", listOf("item_padding", "vertical"), "1")
                after[line(after, "vertical: 1")] shouldBe "    vertical: 1           # 纵向内边距（整数）"
                // Reading it back must find it in the same place.
                read("window", after).leaf(listOf("item_padding", "vertical"))?.value shouldBe "1"
            }
        }

        Given("a value that is in effect") {
            Then("reset comments the line out where it stands") {
                val after = reset("window", listOf("insets", "horizontal"))
                after[line(after, "#    horizontal")] shouldBe "#    horizontal: 4         # 横向内边距（整数）"
                // Nothing else moved.
                after.filterIndexed { i, _ -> i != line(after, "#    horizontal") } shouldBe
                    FIXTURE.lines().filterIndexed { i, _ -> i != line(FIXTURE.lines(), "horizontal: 4") }
            }

            Then("the reader still files it under the same group") {
                // `#` eats the indent as seen by a human but not as measured by
                // the reader: spaces before `#` plus spaces after it.
                val window = read("window", reset("window", listOf("item_padding", "horizontal")))
                val leaf = window.leaf(listOf("item_padding", "horizontal"))
                leaf?.commented shouldBe true
                leaf?.value shouldBe "4"
                window.leaf(listOf("horizontal")) shouldBe null
            }

            Then("typing the value back undoes it exactly") {
                val after = reset("window", listOf("insets", "vertical"))
                val restored = edit("window", listOf("insets", "vertical"), "4", after.joinToString("\n"))
                restored shouldBe FIXTURE.lines()
            }
        }

        Given("a value that is already commented out") {
            Then("reset leaves the file alone") {
                reset("window", listOf("corner_radius")).joinToString("\n") shouldBe FIXTURE
            }
        }

        Given("a key the file does not have") {
            Then("reset leaves the file alone") {
                reset("window", listOf("foreground", "text_font_size")).joinToString("\n") shouldBe FIXTURE
            }
        }

        Given("a key the file does not mention") {
            Then("it is inserted under its group, at the sibling indent") {
                val after = edit("window", listOf("foreground", "text_font_size"), "18")
                val at = line(after, "text_font_size: 18")
                after[at] shouldBe "    text_font_size: 18"
                // Right below the line it now follows in the group.
                after[at - 1].trim() shouldBe "label_font_size: 20   # 序号字体大小（浮点）"
            }

            Then("a missing group is created with it") {
                val withoutInsets =
                    FIXTURE.lines()
                        .filterNot { it.startsWith("  insets:") || it.startsWith("    vertical: 4") || it.startsWith("    horizontal: 4") }
                        .joinToString("\n")
                val after = edit("window", listOf("insets", "vertical"), "6", withoutInsets)
                val at = line(after, "insets:")
                after[at] shouldBe "  insets:"
                after[at + 1] shouldBe "    vertical: 6"
            }
        }

        Given("a section the file does not have") {
            Then("the section and its chain are built at the end") {
                val after = edit("preedit_missing", listOf("foreground", "font_size"), "15")
                val at = line(after, "preedit_missing:")
                after[at] shouldBe "preedit_missing:"
                after[at + 1] shouldBe "  foreground:"
                after[at + 2] shouldBe "    font_size: 15"
                // The document still ends with a single newline, not a blank line.
                after.last() shouldBe ""
                after[after.lastIndex - 1] shouldBe "    font_size: 15"
            }

            Then("nothing above it changes") {
                val after = edit("preedit_missing", listOf("alpha"), "0.5")
                val original = FIXTURE.lines()
                after.take(original.size - 1) shouldBe original.take(original.size - 1)
            }
        }

        Given("a path with no key") {
            Then("nothing is written") {
                val file = File.createTempFile("theme", ".yaml")
                try {
                    file.writeText(FIXTURE)
                    ThemeScalarWriter.set(file, "window", emptyList(), "1") shouldBe false
                    file.readText() shouldBe FIXTURE
                } finally {
                    file.delete()
                }
            }
        }
    })

/** The shape the shipped themes actually use: one commented line per group. */
private val FIXTURE =
    """
    style:
      key_text_size: 22

    preedit:                  # 预编辑文本视图参数
      horizontal_padding: 8     # 横向内边距（整数）
    #  alpha: 0.8                 # 透明度（0-1，浮点）
      foreground:               # 前景样式
        font_size: 16             # 预编辑字体大小（浮点）

    window:               # 候选窗口（悬浮窗）参数
      insets:               # 窗口内边距
        vertical: 4           # 纵向内边距（整数）
        horizontal: 4         # 横向内边距（整数）
      item_padding:         # 候选项内边距
    #    vertical: 0           # 纵向内边距（整数）
        horizontal: 4         # 横向内边距（整数）
    #  corner_radius: 0      # 圆角半径（浮点）
      foreground:           # 前景样式
        label_font_size: 20   # 序号字体大小（浮点）

    fallback_colors:
      default: {key: "#FFFFFF"}
    """.trimIndent() + "\n"
