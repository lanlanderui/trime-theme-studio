/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.color

import android.graphics.Color
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 色相条的颜色顺序必须和 `hue = x / width * 360` 这套读法同向。
 *
 * 这条曾经写反过：条上从左到右画的是红→品红→蓝→青→绿→黄，而读位置时按
 * 红→黄→绿→青→蓝→品红算角度。结果就是「点在一段看着是蓝色的位置上，拿到
 * 的是 120° 的绿」——方块和两套预览全绿，用户找不到哪里错了。
 *
 * 断言只用色相环上的六个基准色，不碰 `Color.colorToHSV`：那是 android.jar 的
 * 桩方法，JVM 单测里会直接抛 Stub。
 */
class ColorPickerHueOrderTest :
    BehaviorSpec({
        Given("六段色相条") {
            val colors = ColorPickerView.HUE_COLORS

            Then("首尾都是 0° 的红，中间分成六段") {
                colors.size shouldBe 7
                colors.first() shouldBe Color.RED
                colors.last() shouldBe Color.RED
            }

            Then("往右角度递增：黄 60、绿 120、青 180、蓝 240、品红 300") {
                colors[1] shouldBe Color.YELLOW
                colors[2] shouldBe Color.GREEN
                colors[3] shouldBe Color.CYAN
                colors[4] shouldBe Color.BLUE
                colors[5] shouldBe Color.MAGENTA
            }
        }
    })
