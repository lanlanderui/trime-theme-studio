/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The colour editor's grouping: every key the runtime knows about has a home,
 * the specific `hilited_*` families win over the plain prefixes they share, and
 * grouping never drops or duplicates a key.
 */
class ColorKeyGroupsTest :
    BehaviorSpec({
        Given("every canonical ColorKey") {
            Then("lands in a section, none fall through to OTHER") {
                val orphans = ColorKey.entries.map { it.key }.filter { ColorKeyGroups.groupOf(it) == ColorGroup.OTHER }
                orphans shouldBe emptyList()
            }
        }

        Given("the hilited families") {
            Then("pressed keys beat the plain key_ prefix") {
                ColorKeyGroups.groupOf("hilited_key_back_color") shouldBe ColorGroup.PRESSED_KEY
                ColorKeyGroups.groupOf("hilited_on_key_symbol_color") shouldBe ColorGroup.PRESSED_KEY
                ColorKeyGroups.groupOf("hilited_back_color") shouldBe ColorGroup.CANDIDATE_BAR
                ColorKeyGroups.groupOf("hilited_candidate_text_color") shouldBe ColorGroup.CANDIDATE_BAR
                ColorKeyGroups.groupOf("hilited_popup_text_color") shouldBe ColorGroup.POPUP
            }
        }

        Given("the three key states") {
            Then("each family stays in its own section") {
                ColorKeyGroups.groupOf("key_text_color") shouldBe ColorGroup.KEY
                ColorKeyGroups.groupOf("off_key_text_color") shouldBe ColorGroup.FUNCTION_KEY
                ColorKeyGroups.groupOf("on_key_border_color") shouldBe ColorGroup.FUNCTION_KEY
                ColorKeyGroups.groupOf("label_color") shouldBe ColorGroup.KEY
            }
        }

        Given("a theme-only alias") {
            Then("it is kept, under OTHER") {
                ColorKeyGroups.groupOf("bbs") shouldBe ColorGroup.OTHER
                ColorKeyGroups.groupOf("normal_mode_color") shouldBe ColorGroup.APP_CHROME
            }
        }

        Given("a scheme with keys interleaved by family") {
            val keys =
                listOf(
                    "name",
                    "text_color",
                    "key_back_color",
                    "off_key_back_color",
                    "hilited_key_back_color",
                    "back_color",
                    "popup_text_color",
                )

            When("grouped") {
                val grouped = ColorKeyGroups.group(keys)

                Then("the order is the section order, and empty sections are dropped") {
                    grouped.map { it.first } shouldContainExactly
                        listOf(
                            ColorGroup.CANDIDATE_BAR,
                            ColorGroup.KEY,
                            ColorGroup.FUNCTION_KEY,
                            ColorGroup.PRESSED_KEY,
                            ColorGroup.POPUP,
                            ColorGroup.OTHER,
                        )
                }

                Then("the file order inside a section survives") {
                    grouped.first { it.first == ColorGroup.CANDIDATE_BAR }.second shouldContainExactly
                        listOf("text_color", "back_color")
                }

                Then("nothing is lost or duplicated") {
                    // Grouping reorders, so compare as sets.
                    grouped.flatMap { it.second }.sorted() shouldContainExactly keys.sorted()
                }
            }
        }
    })
