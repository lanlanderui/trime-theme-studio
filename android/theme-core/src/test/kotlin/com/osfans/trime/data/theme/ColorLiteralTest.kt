/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Reading and writing colour literals.
 *
 * The writer is the part that shows up in the user's theme file, so what is
 * pinned here is the shape it leaves behind: the same prefix and the same
 * number of digits as the value it replaced, in lowercase.
 */
class ColorLiteralTest :
    BehaviorSpec({
        Given("a colour literal") {
            Then("reads the forms themes actually use") {
                ColorLiteral.parse("0xffffd27a") shouldBe 0xFFFFD27A.toInt()
                ColorLiteral.parse("#ffd27a") shouldBe 0xFFFFD27A.toInt()
                ColorLiteral.parse("0XFFD27A") shouldBe 0xFFFFD27A.toInt()
                ColorLiteral.parse("  #abc  ") shouldBe 0xFFAABBCC.toInt()
                ColorLiteral.parse("0x7f000000") shouldBe 0x7F000000
            }

            Then("refuses anything that is not one") {
                ColorLiteral.parse(null) shouldBe null
                ColorLiteral.parse("") shouldBe null
                ColorLiteral.parse("   ") shouldBe null
                ColorLiteral.parse("#") shouldBe null
                ColorLiteral.parse("0x") shouldBe null
                ColorLiteral.parse("transparent") shouldBe null
                ColorLiteral.parse("key_back.png") shouldBe null
                ColorLiteral.parse("0xffff") shouldBe null
                ColorLiteral.parse("0xzzzzzz") shouldBe null
            }

            Then("round-trips through format") {
                val argb = 0xFFFFD27A.toInt()
                ColorLiteral.parse(ColorLiteral.format(argb, "0xffffd27a")) shouldBe argb
                ColorLiteral.parse(ColorLiteral.format(argb, "#ffd27a")) shouldBe argb
            }
        }

        Given("writing a colour back") {
            Then("keeps the prefix the scheme used") {
                ColorLiteral.format(0xFFFFD27A.toInt(), "0xffffd27a") shouldBe "0xffffd27a"
                ColorLiteral.format(0xFFFFD27A.toInt(), "#ffd27a") shouldBe "#ffd27a"
                ColorLiteral.format(0xFFFFD27A.toInt(), "  #FFD27A") shouldBe "#ffd27a"
            }

            Then("drops an opaque alpha the scheme never wrote") {
                ColorLiteral.format(0xFFFFD27A.toInt(), "0xffd27a") shouldBe "0xffd27a"
                ColorLiteral.format(0xFFFFD27A.toInt(), null) shouldBe "0xffd27a"
                ColorLiteral.format(0xFFFFD27A.toInt(), "") shouldBe "0xffd27a"
            }

            Then("keeps eight digits when the scheme wrote them") {
                ColorLiteral.format(0xFFFFD27A.toInt(), "0xffffd27a") shouldBe "0xffffd27a"
            }

            Then("always keeps a translucent alpha") {
                ColorLiteral.format(0x7F000000, "0x000000") shouldBe "0x7f000000"
                ColorLiteral.format(0x00FFFFFF, null) shouldBe "0x00ffffff"
            }

            Then("does not inherit the prefix from a background image name") {
                ColorLiteral.format(0xFFFFD27A.toInt(), "key_back.png") shouldBe "0xffd27a"
            }
        }
    })
