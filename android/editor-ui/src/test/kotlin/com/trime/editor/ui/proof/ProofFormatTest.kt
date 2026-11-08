/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 十六进制的写法在真主题里有三种，行里只显示一种。
 *
 * 这几条用例钉的是「看不出来等于没改」的那部分：认不出的字符串必须原样返回，
 * 透明必须显示成透明而不是黑色。
 */
class ProofFormatTest :
    BehaviorSpec({
        Given("主题里三种写法") {
            Then("0xffe1e3e7 去掉 0x 转大写") {
                ProofFormat.hex("0xffe1e3e7") shouldBe "#E1E3E7"
            }
            Then("手写的 # 也认") {
                ProofFormat.hex("#e1e3e7") shouldBe "#E1E3E7"
            }
            Then("0x00 是透明，不是黑色") {
                ProofFormat.hex("0x00") shouldBe ProofFormat.TRANSPARENT
            }
        }

        Given("带透明度的颜色") {
            Then("保留 alpha，写成八位") {
                ProofFormat.hex("0x80ffffff") shouldBe "#80FFFFFF"
            }
            Then("0xccd3d7da 也是八位") {
                ProofFormat.hex("0xccd3d7da") shouldBe "#CCD3D7DA"
            }
        }

        Given("认不出来的写法") {
            Then("原样返回，不猜") {
                ProofFormat.hex("transparent") shouldBe "transparent"
                ProofFormat.hex("0xzzzz") shouldBe "0xzzzz"
            }
            Then("空值返回空串") {
                ProofFormat.hex(null) shouldBe ""
                ProofFormat.hex("   ") shouldBe ""
            }
        }

        Given("颜色解析") {
            Then("不透明的黑") {
                ProofFormat.colorOf("0xff000000") shouldBe 0xFF000000.toInt()
            }
            Then("半透明白") {
                ProofFormat.colorOf("#80FFFFFF") shouldBe 0x80FFFFFF.toInt()
            }
            Then("认不出就是 null") {
                ProofFormat.colorOf("color2") shouldBe null
                ProofFormat.colorOf(null) shouldBe null
            }
        }

        Given("引用锚点") {
            Then("星号开头才算") {
                ProofFormat.anchor("*color2") shouldBe "*color2"
                ProofFormat.anchor("0xff000000") shouldBe null
                ProofFormat.anchor("*") shouldBe null
                ProofFormat.anchor(null) shouldBe null
            }
        }

        Given("未设置") {
            Then("空与空白都算") {
                ProofFormat.isUnset(null) shouldBe true
                ProofFormat.isUnset("") shouldBe true
                ProofFormat.isUnset("  ") shouldBe true
            }
            Then("有值就不算") {
                ProofFormat.isUnset("#FFFFFF") shouldBe false
                ProofFormat.isUnset("bottom") shouldBe false
            }
        }

        Given("项数") {
            Then("改过才说已改") {
                ProofFormat.count(11, 2) shouldBe "11 项 / 已改 2"
                ProofFormat.count(4) shouldBe "4 项"
                ProofFormat.count(4, 0) shouldBe "4 项"
            }
        }
    })
