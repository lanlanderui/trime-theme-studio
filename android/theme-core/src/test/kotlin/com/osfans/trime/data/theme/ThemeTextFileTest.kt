/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins the safety net shared by all theme editors: the previous version is
 * kept, the author's line endings survive, and a stale editor cannot overwrite
 * a file that somebody else changed.
 */
class ThemeTextFileTest :
    StringSpec({
        fun tempFile(
            text: String,
            block: (File) -> Unit,
        ) {
            val file = File.createTempFile("trime-theme-", ".yaml")
            try {
                file.writeText(text)
                block(file)
            } finally {
                listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
            }
        }

        "a successful write keeps an exact backup and preserves CRLF" {
            tempFile("style:\r\n  horizontal_gap: 1\r\n") { file ->
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"

                ThemeTextFile.write(file, document, document.lines) shouldBe true

                file.readText() shouldBe "style:\r\n  horizontal_gap: 2\r\n"
                ThemeTextFile.backupOf(file).readText() shouldBe "style:\r\n  horizontal_gap: 1\r\n"
            }
        }

        "a file changed outside the app is not overwritten" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"
                file.writeText("style:\n  horizontal_gap: 9\n")

                ThemeTextFile.write(file, document, document.lines) shouldBe false
                file.readText() shouldBe "style:\n  horizontal_gap: 9\n"
            }
        }

        "a save reports a conflict separately from a failure" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"
                file.writeText("style:\n  horizontal_gap: 9\n")

                ThemeTextFile.writeChecked(file, document, document.lines) shouldBe
                    ThemeTextFile.WriteOutcome.CONFLICT
                // Nothing was written, so nothing was backed up either.
                ThemeTextFile.backupOf(file).exists() shouldBe false
            }
        }

        "a missing file is a failure, not a conflict" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"
                file.delete()

                ThemeTextFile.writeChecked(file, document, document.lines) shouldBe
                    ThemeTextFile.WriteOutcome.FAILED
            }
        }

        "the style writer only skips a save when the caller's copy is stale" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val shown = file.readText()
                file.writeText("style:\n  horizontal_gap: 9\n")

                ThemeStyleWriter.writeEntryChecked(file, "horizontal_gap", "2", shown).outcome shouldBe
                    ThemeTextFile.WriteOutcome.CONFLICT
                file.readText() shouldBe "style:\n  horizontal_gap: 9\n"

                // Without a baseline the writer trusts the file it just read.
                ThemeStyleWriter.writeEntryChecked(file, "horizontal_gap", "2").outcome shouldBe
                    ThemeTextFile.WriteOutcome.WRITTEN
                file.readText() shouldBe "style:\n  horizontal_gap: 2\n"
            }
        }

        "a save hands back the text the next check compares against" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val first = ThemeStyleWriter.writeEntryChecked(file, "horizontal_gap", "2", file.readText())
                first.outcome shouldBe ThemeTextFile.WriteOutcome.WRITTEN
                first.text shouldBe "style:\n  horizontal_gap: 2\n"

                // Twenty edits in a row, each using the previous result as the
                // baseline, must not produce a single false "changed outside".
                var baseline = first.text
                repeat(20) { index ->
                    val result =
                        ThemeStyleWriter.writeEntryChecked(file, "horizontal_gap", "${index + 3}", baseline)
                    result.outcome shouldBe ThemeTextFile.WriteOutcome.WRITTEN
                    baseline = result.text
                }
                file.readText() shouldBe "style:\n  horizontal_gap: 22\n"
            }
        }

        "the previous save can be put back from its backup" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val original = file.readText()
                ThemeTextFile.hasBackup(file) shouldBe false

                ThemeStyleWriter.writeEntry(file, "horizontal_gap", "2") shouldBe true
                ThemeTextFile.hasBackup(file) shouldBe true

                ThemeTextFile.restoreBackup(file) shouldBe ThemeTextFile.WriteOutcome.WRITTEN
                file.readText() shouldBe original
                // The backup now holds what was just replaced, so the action is
                // reversible instead of one-way.
                ThemeTextFile.backupOf(file).readText() shouldBe "style:\n  horizontal_gap: 2\n"
            }
        }

        "a failed temp write leaves the original and its backup intact" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val original = file.readText()
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"
                // Make the temp path unwritable by turning it into a directory.
                File(file.parentFile, file.name + ".tmp").mkdirs()

                ThemeTextFile.write(file, document, document.lines) shouldBe false
                file.readText() shouldBe original
                ThemeTextFile.backupOf(file).readText() shouldBe original
            }
        }

        "the style writer uses the same backup and CRLF rules" {
            tempFile("style:\r\n  horizontal_gap: 1 # gap\r\n") { file ->
                ThemeStyleWriter.writeEntry(file, "horizontal_gap", "2") shouldBe true

                file.readText() shouldBe "style:\r\n  horizontal_gap: 2 # gap\r\n"
                ThemeTextFile.backupOf(file).readText() shouldBe "style:\r\n  horizontal_gap: 1 # gap\r\n"
            }
        }

        "an anchor entry is shown by its value, not by the anchor's name" {
            // `key_height: *jpgd4` has no number of its own; an editor that
            // offered the anchor name for editing would write `key_height:
            // jpgd4` on OK, which the theme cannot parse.
            val lines =
                listOf(
                    "style:",
                    "  key_height: *jpgd4 #键高",
                    "  horizontal_gap: 2",
                    "preset_keyboards:",
                    "  4: &jpgd4 48 #主键盘",
                    "  5: &jpgd40 99",
                )
            val entry = ThemeStyleWriter.readEntry(lines, "key_height")
            entry.source shouldBe ThemeStyleWriter.Source.ANCHOR
            entry.value shouldBe "jpgd4"

            ThemeStyleWriter.resolveAnchor(lines, "jpgd4") shouldBe "48"
            // `&jpgd40` is a different anchor and must not answer for this one.
            ThemeStyleWriter.resolveAnchor(lines, "jpgd40") shouldBe "99"
            ThemeStyleWriter.resolveAnchor(lines, "missing") shouldBe null
        }

        "an anchor holding a block cannot be replaced by one value" {
            val lines = listOf("style:", "  key_height: *blk", "x: &blk", "  a: 1")
            ThemeStyleWriter.resolveAnchor(lines, "blk") shouldBe null
        }

        "a save bumps the generation a theme cache watches, a failed one does not" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                fun save(value: String) {
                    val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                    document.lines[1] = "  horizontal_gap: $value"
                    ThemeTextFile.write(file, document, document.lines) shouldBe true
                }

                val before = ThemeTextFile.writeGeneration
                save("2")
                val afterSave = ThemeTextFile.writeGeneration
                (afterSave > before) shouldBe true

                // Same byte count as the previous save: a cache watching only
                // length and timestamp could keep serving the old theme.
                save("1")
                val afterSecondSave = ThemeTextFile.writeGeneration
                (afterSecondSave > afterSave) shouldBe true

                val stale = ThemeTextFile.read(file) ?: error("cannot read fixture")
                file.writeText("style:\n  horizontal_gap: 9\n")
                ThemeTextFile.write(file, stale, stale.lines) shouldBe false
                ThemeTextFile.writeGeneration shouldBe afterSecondSave
            }
        }
    })
