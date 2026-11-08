/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.kbd

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Pins how a width edit is represented while the user drags.
 *
 * `width: 0` and "no width at all" mean the same thing at runtime (both fall
 * back to the keyboard's default) but not in the file. The draft has to keep
 * them apart, or every layout the user touches turns into a wall of explicit
 * numbers on the next save.
 */
class KeyboardDraftTest :
    BehaviorSpec({
        fun layout(
            defaultWidth: Float = 10f,
            widths: List<Float> = listOf(0f, 0f, 0f),
        ) = TextKeyboard(
            name = "测试布局",
            width = defaultWidth,
            columns = 30,
            keys = widths.map { TextKey(width = it, click = KeyActionToken.Plain("q")) },
        )

        Given("a layout whose keys do not set a width") {
            val draft = KeyboardDraft(layout())

            Then("every key reports the keyboard default") {
                (0 until draft.size).forEach { draft.effectiveWidth(it) shouldBe 10f }
            }

            Then("writing that same width back leaves the key unset") {
                draft.setWidth(1, 10f)
                draft.toConfig().keys[1].width shouldBe 0f
            }
        }

        Given("a key given a new width") {
            val draft = KeyboardDraft(layout())

            Then("the draft reports it and keeps the neighbours untouched") {
                draft.setWidth(0, 12.5f)
                draft.effectiveWidth(0) shouldBe 12.5f
                draft.effectiveWidth(1) shouldBe 10f
                draft.toConfig().keys[0].width shouldBe 12.5f
            }

            Then("changing it back to the default clears the field") {
                draft.setWidth(0, 12.5f)
                draft.setWidth(0, 10f)
                draft.toConfig().keys[0].width shouldBe 0f
            }
        }

        Given("converting back to a layout") {
            val source = layout(widths = listOf(0f, 5f, 0f))
            val draft = KeyboardDraft(source)
            draft.setWidth(2, 7f)

            Then("layout-level fields survive") {
                val config = draft.toConfig()
                config.name shouldBe "测试布局"
                config.width shouldBe 10f
                config.columns shouldBe 30
            }

            Then("keys the user did not touch keep their original values") {
                draft.toConfig().keys[1].width shouldBe 5f
            }

            Then("the source layout is not modified") {
                source.keys[2].width shouldBe 0f
            }
        }

        Given("a key with one action") {
            val draft = KeyboardDraft(layout())

            Then("a new token lands in the slot it was picked for") {
                draft.setAction(0, ActionSlot.CLICK, "BackSpace")
                draft.action(0, ActionSlot.CLICK) shouldBe "BackSpace"
                draft.toConfig().keys[0].click shouldBe KeyActionToken.Plain("BackSpace")
            }

            Then("the other slots stay empty") {
                draft.setAction(0, ActionSlot.SWIPE_LEFT, "a")
                draft.action(0, ActionSlot.SWIPE_LEFT) shouldBe "a"
                draft.action(0, ActionSlot.SWIPE_RIGHT) shouldBe null
                draft.action(0, ActionSlot.LONG_CLICK) shouldBe null
                draft.toConfig().keys[0].swipeRight shouldBe null
            }

            Then("clearing a slot leaves the rest alone") {
                draft.setAction(0, ActionSlot.CLICK, "Return")
                draft.setAction(0, ActionSlot.CLICK, null)
                draft.action(0, ActionSlot.CLICK) shouldBe null
                draft.action(0, ActionSlot.SWIPE_LEFT) shouldBe "a"
            }

            Then("an inline token shows its label until it is replaced") {
                val inline =
                    TextKeyboard(
                        keys = listOf(TextKey(click = KeyActionToken.Inline(commit = "，", label = "逗号"))),
                    )
                val inlineDraft = KeyboardDraft(inline)
                inlineDraft.action(0, ActionSlot.CLICK) shouldBe "逗号"
                inlineDraft.setAction(0, ActionSlot.CLICK, "BackSpace")
                inlineDraft.toConfig().keys[0].click shouldBe KeyActionToken.Plain("BackSpace")
            }

            Then("clearing the click pins the weight the key was occupying") {
                // A key without a click is a spacer, and a spacer that leaves its
                // width unset renders zero pixels wide: invisible and too narrow
                // to select, so the binding could never be restored.
                val fresh = KeyboardDraft(layout(widths = listOf(0f, 0f, 0f)))
                fresh.setAction(0, ActionSlot.CLICK, null)
                fresh.producesKey(0) shouldBe false
                fresh.toConfig().keys[0].width shouldBe 10f
            }

            Then("clearing any other slot does not touch the width") {
                val fresh = KeyboardDraft(layout(widths = listOf(0f, 0f, 0f)))
                fresh.setAction(0, ActionSlot.SWIPE_UP, null)
                fresh.toConfig().keys[0].width shouldBe 0f
                fresh.producesKey(0) shouldBe true
            }
        }

        Given("a draft loaded from a layout") {
            val draft = KeyboardDraft(layout(widths = listOf(0f, 0f, 0f)))

            Then("each key remembers the line it came from") {
                draft.origins() shouldBe listOf<Int?>(0, 1, 2)
            }

            Then("a copy sits right after its source and shares its origin") {
                val copy = draft.duplicate(1)
                copy shouldBe 2
                draft.size shouldBe 4
                draft.origins() shouldBe listOf<Int?>(0, 1, 1, 2)
                draft.key(2) shouldBe draft.key(1)
            }

            Then("deleting removes the key but never the last one") {
                val before = draft.size
                draft.delete(0) shouldBe true
                draft.size shouldBe before - 1
                draft.origins() shouldBe listOf<Int?>(1, 1, 2)
                while (draft.size > 1) draft.delete(0)
                draft.delete(0) shouldBe false
                draft.size shouldBe 1
            }
        }

        Given("a key being renamed") {
            val draft = KeyboardDraft(layout())

            Then("the label is kept and an empty one clears it") {
                draft.setLabel(0, "空格")
                draft.toConfig().keys[0].label shouldBe "空格"
                draft.setLabel(0, "")
                draft.toConfig().keys[0].label shouldBe ""
            }
        }

        Given("a key being moved") {
            val draft = KeyboardDraft(layout(widths = listOf(0f, 5f, 0f)))

            Then("it swaps with its neighbour and reports where it landed") {
                draft.setAction(0, ActionSlot.CLICK, "a")
                draft.setAction(1, ActionSlot.CLICK, "b")
                draft.move(0, 1) shouldBe 1
                draft.action(0, ActionSlot.CLICK) shouldBe "b"
                draft.action(1, ActionSlot.CLICK) shouldBe "a"
                // The origins moved with the keys, so writes still patch their own lines.
                draft.origins() shouldBe listOf<Int?>(1, 0, 2)
            }

            Then("moving past either end does nothing") {
                draft.move(0, -1) shouldBe null
                draft.move(2, 1) shouldBe null
            }
        }

        Given("a blank key inserted after another") {
            val draft = KeyboardDraft(layout(widths = listOf(0f, 5f, 0f)))

            Then("it has no origin and one default width") {
                val blank = draft.insertBlank(0)
                blank shouldBe 1
                draft.size shouldBe 4
                draft.origin(1) shouldBe null
                draft.effectiveWidth(1) shouldBe 10f
                draft.action(1, ActionSlot.CLICK) shouldBe null
            }
        }

        Given("a layout with a spacer between keys") {
            // Mirrors qwerty in the shipped theme: the spacer keeps its width but
            // becomes no key, so the keyboard's index and the draft's index drift.
            val withSpacer =
                TextKeyboard(
                    width = 10f,
                    keys =
                    listOf(
                        TextKey(click = KeyActionToken.Plain("q")),
                        TextKey(click = KeyActionToken.Plain("w")),
                        TextKey(width = 5f),
                        TextKey(click = KeyActionToken.Plain("a")),
                    ),
                )
            val draft = KeyboardDraft(withSpacer)

            Then("the spacer is reported as producing no key") {
                draft.producesKey(2) shouldBe false
                draft.producesKey(0) shouldBe true
                // A blank click is how a theme unsets an action; it is a spacer too.
                draft.setAction(0, ActionSlot.CLICK, "")
                draft.producesKey(0) shouldBe false
            }

            Then("the map skips it on both sides") {
                val pristine = KeyboardDraft(withSpacer)
                pristine.indexMap(3).draftToKey.toList() shouldBe listOf(0, 1, -1, 2)
                pristine.indexMap(3).keyToDraft.toList() shouldBe listOf(0, 1, 3)
                // If the keyboard somehow built fewer keys, the tail is unmapped
                // rather than silently pointing at the wrong key.
                pristine.indexMap(2).draftToKey.toList() shouldBe listOf(0, 1, -1, -1)
            }
        }

        Given("a key added to a layout") {
            val draft = KeyboardDraft(layout(widths = listOf(0f, 0f, 0f)))

            Then("it sits after the key it was added from and has no origin") {
                val created = draft.insert(1, TextKey(click = KeyActionToken.Plain("a")))
                created shouldBe 2
                draft.size shouldBe 4
                draft.origin(2) shouldBe null
                draft.action(2, ActionSlot.CLICK) shouldBe "a"
                draft.origins() shouldBe listOf<Int?>(0, 1, null, 2)
            }

            Then("an anchor outside the list still lands inside it") {
                // -1 is how the editor says "at the head", and a layout with no
                // keys has no other index to offer.
                val empty = KeyboardDraft(TextKeyboard(width = 10f))
                empty.insert(-1, TextKey(click = KeyActionToken.Plain("q"))) shouldBe 0
                empty.size shouldBe 1
                empty.origin(0) shouldBe null
                empty.effectiveWidth(0) shouldBe 10f
            }

            Then("adding past the end appends") {
                val fresh = KeyboardDraft(layout(widths = listOf(0f, 0f, 0f)))
                fresh.insert(fresh.size - 1, TextKey(click = KeyActionToken.Plain("b"))) shouldBe 3
                fresh.action(3, ActionSlot.CLICK) shouldBe "b"
            }
        }

        Given("a layout whose keys pin their captions") {
            val pinned =
                TextKeyboard(
                    width = 10f,
                    keys =
                    listOf(
                        TextKey(click = KeyActionToken.Plain("q"), label = "七"),
                        TextKey(click = KeyActionToken.Plain("w")),
                        TextKey(click = KeyActionToken.Plain("e"), label = "九"),
                    ),
                )
            val draft = KeyboardDraft(pinned)

            Then("the count is how many keys would lose something") {
                draft.labelCount() shouldBe 2
            }

            Then("clearing drops every caption and leaves the bindings") {
                draft.clearLabels() shouldBe 2
                draft.toConfig().keys.map { it.label } shouldBe listOf("", "", "")
                draft.action(0, ActionSlot.CLICK) shouldBe "q"
                draft.labelCount() shouldBe 0
                // Nothing left to clear, so a second pass is a no-op.
                draft.clearLabels() shouldBe 0
            }

            Then("the source layout keeps its own labels") {
                pinned.keys.map { it.label } shouldBe listOf("七", "", "九")
            }
        }

        Given("a swipe direction") {
            Then("the dominant axis decides the slot") {
                ActionSlot.swipe(80f, 10f) shouldBe ActionSlot.SWIPE_RIGHT
                ActionSlot.swipe(-80f, 10f) shouldBe ActionSlot.SWIPE_LEFT
                ActionSlot.swipe(5f, 90f) shouldBe ActionSlot.SWIPE_DOWN
                ActionSlot.swipe(5f, -90f) shouldBe ActionSlot.SWIPE_UP
            }
        }
    })
