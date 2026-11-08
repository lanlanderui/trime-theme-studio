/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The fast path a theme listing takes to print a name.
 *
 * It exists to avoid decoding fifty themes to draw fifty cards, so its job is to
 * answer for the themes that answer easily and to *decline* the rest — a wrong
 * name here is worse than a slow one, because the card is the only thing telling
 * two themes apart.
 */
class ThemeNameReaderTest :
    BehaviorSpec({
        Given("a theme whose name is near the top") {
            Then("the name is read without expanding anything") {
                ThemeNameReader.fastName(
                    listOf("# 主题", "config_version: 3.0", "name: 标准          #方案名称", "style:"),
                ) shouldBe "标准"
            }

            Then("a quoted name loses its quotes") {
                ThemeNameReader.fastName(listOf("name: \"My theme\"")) shouldBe "My theme"
                ThemeNameReader.fastName(listOf("name: 'It''s mine'")) shouldBe "It's mine"
            }
        }

        Given("a theme with nested names") {
            Then("only a column-0 name counts") {
                // A colour scheme or a layout may declare `name:` too; printing one
                // of those on the theme card would be a lie.
                ThemeNameReader.fastName(
                    listOf(
                        "style:",
                        "  name: 这不是主题名",
                        "preset_color_schemes:",
                        "  default:",
                        "    name: 默认配色",
                    ),
                ) shouldBe null
            }
        }

        Given("a theme whose name comes from an include, or is missing") {
            Then("the fast path declines so the caller expands it") {
                ThemeNameReader.fastName(listOf("config_version: 3.0", "__include: base.trime")) shouldBe null
                ThemeNameReader.fastName(emptyList()) shouldBe null
            }
        }

        Given("a name beyond the scan window") {
            Then("it declines rather than scanning the whole file") {
                val late = List(ThemeNameReader.SCAN_LINES) { "key$it: 1" } + "name: 太靠后"
                ThemeNameReader.fastName(late) shouldBe null
            }
        }

        Given("the shipped theme fixture") {
            Then("the fast path and the full decode agree") {
                val bytes =
                    checkNotNull(javaClass.getResourceAsStream("/tongwenfeng.trime.yaml")) {
                        "missing fixture"
                    }.use { it.readBytes() }
                val text = String(bytes, Charsets.UTF_8)
                ThemeNameReader.fastName(text.lines()) shouldBe "标准"
            }
        }
    })
