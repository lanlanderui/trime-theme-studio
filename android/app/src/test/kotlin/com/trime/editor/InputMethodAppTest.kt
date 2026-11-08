/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 「回到输入法」打开哪一个。
 *
 * 原版 `com.osfans.trime` 在前、`com.osfans.trime03` 只作兜底——这一条在只装了
 * 03 的设备上根本察觉不到，所以钉成用例。
 */
class InputMethodAppTest :
    BehaviorSpec({
        given("原版和 03 都启用") {
            val both = listOf("com.osfans.trime03.debug", "com.osfans.trime")

            then("没有系统默认项时，选原版") {
                InputMethodApp.pick(both, null) shouldBe "com.osfans.trime"
            }

            then("即使系统默认是 03，也开原版（那一支已经放弃）") {
                InputMethodApp.pick(both, "com.osfans.trime03.debug") shouldBe "com.osfans.trime"
            }
        }

        given("只装了 03") {
            then("兜底仍然打得开") {
                InputMethodApp.pick(listOf("com.osfans.trime03.debug"), null) shouldBe "com.osfans.trime03.debug"
            }
        }

        given("一个都没有") {
            then("返回 null，菜单项据此隐藏") {
                InputMethodApp.pick(emptyList(), null) shouldBe null
            }
        }

        given("默认项不在已启用列表里（刚被停用）") {
            then("退回按优先级挑") {
                InputMethodApp.pick(listOf("com.osfans.trime.debug"), "com.osfans.trime") shouldBe "com.osfans.trime.debug"
            }
        }
    })
