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
 * Reading `preedit` / `window`: a nested scalar tree whose defaults are often
 * written as comments.
 *
 * The commented lines are the reason this reader exists at all — a theme that
 * says `#  corner_radius: 0` has pinned the value, and an editor that only reads
 * live lines would show it as "never mentioned".
 */
class ThemeScalarReaderTest :
    BehaviorSpec({
        fun read(name: String): RawScalarSection {
            val file = File.createTempFile("theme", ".yaml")
            try {
                file.writeText(FIXTURE)
                return ThemeScalarReader.read(file, name)
            } finally {
                file.delete()
            }
        }

        Given("a live leaf") {
            Then("its path is the chain of groups above it") {
                val window = read("window")
                window.leaf(listOf("insets", "vertical"))?.value shouldBe "4"
                window.leaf(listOf("foreground", "text_font_size"))?.value shouldBe "20"
            }

            Then("the trailing comment stays with the leaf") {
                val comment = read("window").leaf(listOf("insets", "horizontal"))?.comment.orEmpty()
                // The whitespace before # is part of the comment, so it is checked
                // by shape rather than by counting spaces.
                comment.trimStart().startsWith("# 横向内边距") shouldBe true
            }
        }

        Given("a commented default") {
            Then("it is read, inside the group it lines up with") {
                val window = read("window")
                val radius = window.leaf(listOf("corner_radius"))
                radius?.commented shouldBe true
                radius?.value shouldBe "0"
            }

            Then("a commented line indented under a group stays in that group") {
                // `#    vertical: 0` sits under item_padding, not under window.
                val window = read("window")
                window.leaf(listOf("item_padding", "vertical"))?.commented shouldBe true
                window.leaf(listOf("item_padding", "vertical"))?.value shouldBe "0"
                window.leaf(listOf("vertical")) shouldBe null
            }
        }

        Given("a section the file does not have") {
            Then("the range is null and nothing is listed") {
                val missing = read("preedit_missing")
                missing.range shouldBe null
                missing.leaves shouldBe emptyList()
            }
        }

        Given("the next top-level section") {
            Then("a comment at column 0 does not end the section early") {
                // The fixture keeps `# 最好保留一项…` right before fallback_colors.
                read("window").leaves.size shouldBe 7
            }
        }
    })

private val FIXTURE =
    """
    style:
      key_text_size: 22

    preedit:                  # 预编辑文本视图参数
      horizontal_padding: 8     # 横向内边距（整数）
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
        text_font_size: 20    # 文字字体大小（浮点）

    # 最好保留一项，防止其他主题使用 __include 时无法加载
    fallback_colors:
      default: {key: "#FFFFFF"}
    """.trimIndent() + "\n"
