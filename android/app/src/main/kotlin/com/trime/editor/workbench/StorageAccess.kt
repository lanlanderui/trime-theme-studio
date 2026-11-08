/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Whether the app can actually open the theme directory the user picks.
 *
 * The editor edits files by path — every writer in `:theme-core` does — so a
 * per-file `Uri` grant from a document picker is not enough: the app needs to
 * see a directory on the filesystem. That means all-files access on Android 11
 * and a plain read permission before it.
 */
object StorageAccess {
    fun hasAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

        else -> true
    }

    /**
     * The screen where all-files access is granted, or null when this build
     * predates it — the caller then asks for the runtime permission.
     *
     * Which of the two settings screens exist is left to the launch: asking
     * `resolveActivity` here needs a `<queries>` declaration for a system
     * action and answers "no" on Android 11+ even when the screen is right
     * there. The caller catches [android.content.ActivityNotFoundException]
     * and tries [fallbackSettingsIntent] instead.
     */
    fun settingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }

    /** The global list, for devices that only ship that one. */
    fun fallbackSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    /** Whether a runtime read permission is what this build asks for. */
    fun asksRuntimePermission(): Boolean = Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.R
}
