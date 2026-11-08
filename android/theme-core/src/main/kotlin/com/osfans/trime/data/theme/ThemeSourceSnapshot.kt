/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File
import java.security.MessageDigest

/**
 * A file a theme load read, in the state the load found it.
 *
 * [resourceId] is the id it was resolved for, or null when the load was handed
 * an explicit file. Keeping the id lets the snapshot notice that a later user
 * copy now shadows the shared file the load actually read.
 *
 * The four fields are the four things that can be checked without trusting the
 * one that lies. See [isCurrent].
 */
data class ThemeFileStamp(
    val resourceId: String?,
    val path: String,
    val stamp: Long,
    val size: Long,
    /** [ThemeRevision] of the directory when the load ran; 0 when there is none. */
    val revision: Long = 0,
    /** SHA-256 of the whole file when the load ran; empty for a stamp made by hand. */
    val digest: String = "",
) {
    /**
     * Whether the file on disk is still the one this stamp describes.
     *
     * The stat-based checks come first because they are cheap and catch almost
     * everything. They are not enough on their own: on a FUSE mount
     * (`/storage/emulated/0`, where both apps keep their themes) replacing a
     * file neither advances the mtime nor, for a same-length edit, changes the
     * size. The `.revision` counter covers writes made by this project's own
     * writer; the digest covers everything else, including another app editing
     * the file directly.
     *
     * The digest is taken over the *whole* file, not a prefix. Theme edits land
     * in `preset_color_schemes` at the end of a 4000-line file, so a prefix
     * would report "unchanged" for exactly the edit this is meant to catch.
     */
    fun isCurrent(resolve: (String) -> File?): Boolean {
        val file = File(path)
        if (file.length() != size) return false
        if (file.lastModified() != stamp) return false
        if (ThemeRevision.of(file) != revision) return false
        if (digest.isNotEmpty() && ThemeDigest.of(file) != digest) return false
        if (resourceId == null) return true
        return resolve(resourceId)?.absolutePath == path
    }

    companion object {
        /** Stamps [file] as it is right now; call before reading it. */
        fun of(
            file: File,
            resourceId: String? = null,
        ) = ThemeFileStamp(
            resourceId = resourceId,
            path = file.absolutePath,
            stamp = file.lastModified(),
            size = file.length(),
            revision = ThemeRevision.of(file),
            digest = ThemeDigest.of(file),
        )
    }
}

/**
 * SHA-256 of a theme file, as hex.
 *
 * A theme is 100–200 KB, so hashing it costs well under a millisecond — the
 * cache that asks for it is entered when a theme is switched to or an editor
 * reloads, not per keystroke. Returns an empty string when the file cannot be
 * read, which never equals a stored digest and therefore counts as changed.
 */
internal object ThemeDigest {
    fun of(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}

/**
 * Everything a theme load depended on, so a cached theme can be told apart
 * from a still-valid one.
 *
 * A theme is not a single file: `__include` pulls in other resources and
 * `<id>.custom.yaml` patches the result. Keying the cache on the root file's
 * timestamp alone serves a stale theme as soon as an included or custom file
 * changes — including the common case of fixing an include from outside.
 */
data class ThemeSourceSnapshot(
    /** Files read, stamped before each read. */
    val files: List<ThemeFileStamp> = emptyList(),
    /** Resource ids looked for and not found; a file appearing later changes the theme. */
    val absent: List<String> = emptyList(),
) {
    fun isCurrent(resolve: (String) -> File?): Boolean {
        if (!files.all { it.isCurrent(resolve) }) return false
        return absent.none { resolve(it) != null }
    }

    /**
     * Combines two snapshots of the same load — the source files it tried and
     * the deployed artifact it ended up using. Both can change independently,
     * and either change means the cached theme is stale.
     */
    fun merge(other: ThemeSourceSnapshot): ThemeSourceSnapshot {
        val paths = files.mapTo(HashSet()) { it.path }
        return ThemeSourceSnapshot(
            files = files + other.files.filterNot { it.path in paths },
            absent = (absent + other.absent).distinct(),
        )
    }
}
