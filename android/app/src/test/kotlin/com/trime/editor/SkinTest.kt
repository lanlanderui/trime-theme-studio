/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import com.trime.editor.ui.skin.Skin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * 皮肤表本身的行为。
 *
 * 这些用例不碰 Android 框架：皮肤表是「id → overlay + 能力位」的纯数据，能用
 * JVM 单测钉住的部分就都在这里钉住。真机才验得到的东西（换肤后整页重建、
 * 快捷键入口不闪白屏）不写成单测，写成验收清单。
 *
 * 0.4.0 起校样下架，只保留层叠与舞台；`proof` 变成「未知 id」的一种，
 * 必须和所有未知串一样回退到默认皮肤，不能让老用户卡在下架的皮肤上。
 */
class SkinTest :
    BehaviorSpec({
        Given("皮肤表") {
            Then("默认皮肤是层叠（打底方案）") {
                Skin.DEFAULT shouldBe Skin.TONAL
            }

            Then("只剩层叠与舞台，id 互不相同且非空") {
                val ids = Skin.entries.map { it.id }
                ids shouldBe listOf("m3-tonal", "m3-expressive")
                ids.toSet().size shouldBe ids.size
                ids.forEach { it.isNotBlank() shouldBe true }
            }

            Then("每套皮肤都指向一张真实存在的 overlay") {
                // R.style.* 是编译期常量；为 0 说明忘了写 <style>，运行时会静默不换肤。
                Skin.entries.forEach { (it.overlay == 0) shouldBe false }
            }
        }

        Given("按 id 找皮肤") {
            Then("空值回退默认") {
                Skin.of(null) shouldBe Skin.DEFAULT
            }

            Then("未知 id 回退默认，不抛异常") {
                Skin.of("m2-purple-from-the-future") shouldBe Skin.DEFAULT
            }

            Then("下架的校样 id 也回退层叠") {
                Skin.of("proof") shouldBe Skin.TONAL
            }

            Then("两个合法 id 原样返回") {
                Skin.of("m3-tonal") shouldBe Skin.TONAL
                Skin.of("m3-expressive") shouldBe Skin.EXPRESSIVE
            }
        }

        Given("能力位") {
            Then("层叠不改结构") {
                Skin.TONAL.caps.stageHeader shouldBe false
                Skin.TONAL.caps.floatingDock shouldBe false
                Skin.TONAL.caps.segmented shouldBe false
            }

            Then("舞台同时开三处结构差异") {
                Skin.EXPRESSIVE.caps.stageHeader shouldBe true
                Skin.EXPRESSIVE.caps.floatingDock shouldBe true
                Skin.EXPRESSIVE.caps.segmented shouldBe true
            }
        }

        Given("同一套皮肤取两次") {
            Then("拿到的是同一个枚举实例（缓存/比较都靠它）") {
                Skin.of("m3-tonal").shouldBeSameInstanceAs(Skin.TONAL)
            }
        }
    })
