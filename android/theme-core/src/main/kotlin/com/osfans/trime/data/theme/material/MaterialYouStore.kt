/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import com.osfans.trime.data.theme.ThemeDirectory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import com.osfans.trime.data.theme.ThemeLog as Timber

/** One palette the user decided to keep, with the colours frozen as generated. */
@Serializable
data class SavedPalette(
    val id: String,
    val name: String,
    val createdAt: Long,
    /**
     * Where the colours came from: `system` or `seed`, or [SOURCE_CURRENT] when
     * they were saved off the keyboard rather than generated on demand. Only
     * used to label the row.
     */
    val source: String,
    /** A colour to draw the row with — the accent the palette came from. */
    val accent: String,
    val light: Map<String, String>,
    val dark: Map<String, String>,
) {
    companion object {
        /** [source] of a palette saved from what the theme was painting. */
        const val SOURCE_CURRENT = "current"
    }
}

/**
 * The favourites file: `dynamic_colors.json` next to the theme files.
 *
 * It sits in the theme directory rather than in app storage so that it travels
 * with the theme folder — a user who points the editor at `/sdcard/rime` finds
 * their saved palettes there too, and can back the file up like any other.
 *
 * The colours themselves are stored, not the seed: if the generator is ever
 * tuned, an entry the user saved a year ago must still apply as it looked then.
 */
object MaterialYouStore {
    const val FILE_NAME = "dynamic_colors.json"

    @Serializable
    private data class Contents(
        val version: Int = 1,
        val items: List<SavedPalette> = emptyList(),
    )

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** The file in the directory the host pointed the editor at, if there is one. */
    fun defaultFile(): File? = ThemeDirectory.themeDir?.let { File(it, FILE_NAME) }

    fun load(file: File): List<SavedPalette> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString<Contents>(file.readText()).items }
            .onFailure {
                // Never delete what might still be readable by hand: park it and
                // start clean, so the next save does not fail forever.
                Timber.w(it, "Cannot read %s; keeping it as .bad", file.name)
                runCatching { file.renameTo(File(file.parentFile, "${file.name}.bad")) }
            }.getOrDefault(emptyList())
    }

    /** Whether [candidate] is already saved, colours and all. */
    fun contains(
        items: List<SavedPalette>,
        candidate: SavedPalette,
    ): Boolean = items.any { it.light == candidate.light && it.dark == candidate.dark }

    fun add(
        palette: SavedPalette,
        file: File,
    ): Boolean {
        val items = load(file)
        if (contains(items, palette)) return false
        return save(items + palette, file)
    }

    fun remove(
        id: String,
        file: File,
    ): Boolean {
        val items = load(file)
        val remaining = items.filterNot { it.id == id }
        if (remaining.size == items.size) return false
        return save(remaining, file)
    }

    fun save(
        items: List<SavedPalette>,
        file: File,
    ): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val text = json.encodeToString(Contents(items = items))
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(text)
        // Replace through a temp file: a crash mid-write must not cost the
        // palettes the user collected over months.
        if (file.exists() && !file.delete()) return@runCatching false
        tmp.renameTo(file)
    }.getOrElse {
        Timber.w(it, "Cannot write %s", file.name)
        false
    }
}
