/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.util.get
import com.osfans.trime.util.string
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    /** See [ThemeNameCache]: a name is reused until its file moves. */
    private val names = ThemeNameCache()

    /** @param dir a theme directory, or null when the host has not installed one. */
    fun listThemes(dir: File?): MutableList<ThemeItem> {
        val files = dir?.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val configId = file.nameWithoutExtension
                val name = nameOf(configId, file) ?: configId.removeSuffix(".trime")
                ThemeItem(configId, name)
            }.toMutableList()
    }

    private fun nameOf(
        configId: String,
        file: File,
    ): String? = names.nameOf(file) { readSourceName(configId, it) ?: readDeployedName(configId) }

    /**
     * Reads the theme name from its source file, expanding the supported DSL
     * subset so a name provided by an `__include`d node is resolved too.
     */
    private fun readSourceName(configId: String, file: File): String? {
        // Fast path first: a listing wants one string per file, and expanding the
        // whole DSL to get it costs a full read + decode per theme.
        fastNameOf(file)?.let { return it }
        return runCatching { ThemeLoader.loadSourceNode(configId, file)?.get("name")?.string }
            .getOrElse { e ->
                Timber.w("Failed to decode theme file ${file.absolutePath}: ${e.message}")
                null
            }
    }

    /** `name:` from the first lines of [file], without expanding the theme. */
    private fun fastNameOf(file: File): String? = runCatching {
        file.bufferedReader().useLines { lines ->
            ThemeNameReader.fastName(lines.take(ThemeNameReader.SCAN_LINES).toList())
        }
    }.getOrNull()

    /** Reads the theme name from the deployed artifact, when it exists. */
    private fun readDeployedName(configId: String): String? {
        val file = ThemeDirectory.deployedFile(configId) ?: return null
        if (!file.isFile) return null
        return runCatching { ThemeYaml.parser.parseToYamlNode(file.readText())["name"]?.string }
            .getOrElse { e ->
                Timber.w("Failed to read deployed theme ${file.absolutePath}: ${e.message}")
                null
            }
    }
}
