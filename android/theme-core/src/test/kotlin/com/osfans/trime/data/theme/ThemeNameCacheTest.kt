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
 * The picker names every theme by expanding its file, which is the same work as
 * loading the theme; the answer is kept per file. These tests pin when it is
 * reused and when it is thrown away.
 */
class ThemeNameCacheTest :
    StringSpec({
        fun withFile(
            text: String,
            block: (File) -> Unit,
        ) {
            val dir = File.createTempFile("trime-name-", "").let { file ->
                file.delete()
                file.mkdir()
                file
            }
            val theme = File(dir, "alpha.trime.yaml")
            try {
                theme.writeText(text)
                block(theme)
            } finally {
                dir.deleteRecursively()
            }
        }

        "an unchanged file is read once" {
            withFile("name: Alpha\n") { file ->
                val cache = ThemeNameCache()
                var reads = 0
                val read = { _: File ->
                    reads++
                    "Alpha"
                }
                cache.nameOf(file, read) shouldBe "Alpha"
                cache.nameOf(file, read) shouldBe "Alpha"
                reads shouldBe 1
            }
        }

        "a file that moved is read again" {
            withFile("name: Alpha\n") { file ->
                val cache = ThemeNameCache()
                cache.nameOf(file) { "Alpha" } shouldBe "Alpha"

                // Same length, same timestamp: a re-read would return the
                // marker below instead of the cached answer.
                val stamp = file.lastModified()
                file.writeText("name: Omega\n")
                file.setLastModified(stamp)
                cache.nameOf(file) { "Read again" } shouldBe "Alpha"

                file.writeText("name: Beta\n")
                file.setLastModified(stamp + 1_000)
                cache.nameOf(file) { "Beta" } shouldBe "Beta"
                cache.nameOf(file) { "Gamma" } shouldBe "Beta"
            }
        }

        "a miss is not remembered" {
            withFile("name: [\n") { file ->
                val cache = ThemeNameCache()
                cache.nameOf(file) { null } shouldBe null
                // The next attempt must reach the source again: a failed
                // expansion may be fixed by a deploy that just landed.
                cache.nameOf(file) { "Deployed" } shouldBe "Deployed"
            }
        }
    })
