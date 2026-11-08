/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * 目录栏的定位规则。这一组用例存在的唯一理由：错位是「看起来有反应」的 bug。
 */
class OutlineTest :
    BehaviorSpec({
        Given("九个章") {
            Then("汉字钮不重复，标题不重复") {
                Outline.chapters.size shouldBe 9
                Outline.chapters.map { it.glyph }.toSet().size shouldBe 9
                Outline.chapters.map { it.title }.toSet().size shouldBe 9
            }
            Then("配色这章有八组，和 ColorKeyGroups 对上") {
                Outline.sectionsOf("colors").size shouldBe 8
            }
            Then("布局按 D9 不展开节") {
                Outline.sectionsOf("layout").shouldBeEmpty()
            }
            Then("没定义的章也返回空，不抛") {
                Outline.sectionsOf("nope").shouldBeEmpty()
            }
        }

        Given("位置映射") {
            Then("章头自己占一个位置") {
                val rows =
                    listOf(
                        Outline.Row(chapter = "候选栏与编码区"),
                        Outline.Row(),
                        Outline.Row(),
                        Outline.Row(chapter = "键盘背景"),
                        Outline.Row(),
                    )
                val positions = Outline.chapterPositions(rows)
                positions["候选栏与编码区"] shouldBe 0
                positions["键盘背景"] shouldBe 3
            }

            Then("隐藏行不计数——否则后面每一节都错一格") {
                val rows =
                    listOf(
                        Outline.Row(chapter = "候选栏与编码区"),
                        Outline.Row(visible = false),
                        Outline.Row(),
                        Outline.Row(chapter = "键盘背景"),
                    )
                val positions = Outline.chapterPositions(rows)
                positions["候选栏与编码区"] shouldBe 0
                positions["键盘背景"] shouldBe 2
            }

            Then("同名章只认第一次") {
                val rows =
                    listOf(
                        Outline.Row(chapter = "按键"),
                        Outline.Row(),
                        Outline.Row(chapter = "按键"),
                    )
                Outline.chapterPositions(rows)["按键"] shouldBe 0
            }

            Then("顺序与出现顺序一致") {
                val rows =
                    listOf(
                        Outline.Row(chapter = "a"),
                        Outline.Row(chapter = "b"),
                        Outline.Row(chapter = "c"),
                    )
                Outline.chapterPositions(rows).keys.toList() shouldContainExactly listOf("a", "b", "c")
            }
        }

        Given("找不到的章") {
            Then("回到顶部而不是乱跳") {
                Outline.positionOf(listOf(Outline.Row(chapter = "a")), "zzz") shouldBe 0
                Outline.positionOf(emptyList(), "a") shouldBe 0
            }
        }
    })
