/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme.core

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 门禁探针：证明 `:theme-core` 的 kotest + JUnit Platform 接线可用。
 *
 * 全工程统一用 kotest（与测试3 的 608 例同一套写法），不混 JUnit4 注解。
 */
class ThemeCoreTest :
    BehaviorSpec({
        Given("theme-core 模块") {
            Then("模块名稳定") {
                ThemeCore.MODULE shouldBe "theme-core"
            }
        }
    })
