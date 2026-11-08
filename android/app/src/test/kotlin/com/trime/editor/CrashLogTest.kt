/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith

/**
 * The one part of [CrashLog] that can be read without a device: the text of a
 * report. Everything else in there is a file path, a cap and a `Context`.
 */
class CrashLogTest :
    BehaviorSpec({
        fun report(
            stamp: String = "2026-11-03 12:00:00",
            threadName: String = "main",
        ): String {
            val error =
                IllegalStateException("boom").apply {
                    stackTrace = arrayOf(StackTraceElement("Foo", "bar", "Foo.kt", 42))
                }
            return CrashLog.format(stamp, threadName, error)
        }

        given("a report") {
            then("the header carries the time and the thread") {
                report().lineSequence().first() shouldBe "==== 2026-11-03 12:00:00  thread=main"
            }

            then("the exception and its frames follow the header") {
                val text = report()
                text shouldContain "java.lang.IllegalStateException: boom"
                text shouldContain "at Foo.bar(Foo.kt:42)"
            }

            then("it ends with a blank line, so two reports do not run together") {
                report() shouldEndWith "\n\n"
            }

            then("the thread name belongs to the header") {
                val header = report(threadName = "pool-1-thread-2").lineSequence().first()
                header shouldBe "==== 2026-11-03 12:00:00  thread=pool-1-thread-2"
            }
        }

        given("the size cap") {
            then("a file exactly at the cap is still appended to") {
                CrashLog.shouldRotate(CrashLog.MAX_BYTES.toLong()) shouldBe false
            }

            then("one byte over is dropped before the new report") {
                CrashLog.shouldRotate(CrashLog.MAX_BYTES.toLong() + 1) shouldBe true
            }

            then("an empty file is never dropped") {
                CrashLog.shouldRotate(0) shouldBe false
            }
        }
    })
