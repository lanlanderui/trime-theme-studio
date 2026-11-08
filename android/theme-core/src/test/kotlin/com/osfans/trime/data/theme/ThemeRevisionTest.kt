/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File

class ThemeRevisionTest :
    BehaviorSpec({
        fun tempDir(): File = File.createTempFile("revision", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        fun themeFile(dir: File): File = File(dir, "trime.yaml").apply { writeText("name: t\n") }

        Given("a directory that was never written to") {
            Then("it reads as zero, not as an error") {
                val dir = tempDir()
                ThemeRevision.read(dir) shouldBe 0
                ThemeRevision.of(themeFile(dir)) shouldBe 0
            }

            Then("stamping a file without a counter still works") {
                val dir = tempDir()
                ThemeFileStamp.of(themeFile(dir)).revision shouldBe 0
            }
        }

        Given("a bump after a save") {
            Then("the counter starts at one and counts up") {
                val dir = tempDir()
                val target = themeFile(dir)
                ThemeRevision.bump(target) shouldBe 1
                ThemeRevision.bump(target) shouldBe 2
                ThemeRevision.read(dir) shouldBe 2
            }

            Then("the file says what it is and what was written") {
                val dir = tempDir()
                ThemeRevision.bump(themeFile(dir))
                val text = ThemeRevision.fileOf(dir).readText()
                text shouldStartWith "#"
                text shouldContain "revision: 1"
                text shouldContain "file: trime.yaml"
                text shouldContain "at: "
            }
        }

        Given("a counter file a user has edited") {
            Then("a missing revision line reads as zero instead of throwing") {
                val dir = tempDir()
                ThemeRevision.fileOf(dir).writeText("# nothing useful here\n")
                ThemeRevision.read(dir) shouldBe 0
            }

            Then("a revision line with surrounding junk is still found") {
                val dir = tempDir()
                ThemeRevision.fileOf(dir).writeText("# counted by hand\nrevision: 41\nfile: trime.yaml\n")
                ThemeRevision.read(dir) shouldBe 41
            }

            Then("a corrupt counter is overwritten by the next bump rather than trusted") {
                val dir = tempDir()
                ThemeRevision.fileOf(dir).writeText("\u0000\u0000\u0000")
                ThemeRevision.read(dir) shouldBe 0
                ThemeRevision.bump(themeFile(dir)) shouldBe 1
            }
        }

        Given("a directory that cannot be written") {
            Then("the bump reports zero and does not blow up the save") {
                val dir = tempDir()
                val target = themeFile(dir)
                // A directory cannot be replaced by a file of the same name, so
                // this is a writable-dir-only failure the caller must survive.
                ThemeRevision.fileOf(dir).mkdirs() shouldBe true
                ThemeRevision.bump(target) shouldBe 0
            }
        }
    })
