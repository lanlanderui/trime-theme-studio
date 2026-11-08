/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/**
 * What this app is for, said once.
 *
 * The editor is a second app standing next to a keyboard most people already
 * have installed, and nothing about the workbench explains why changing a line
 * here changes a keyboard there. The three steps are the whole mental model:
 * pick a theme, change one thing, go look at it. Shown once on first launch and
 * reachable afterwards from the menu, so it is an explanation rather than a
 * tutorial nobody can get back to.
 */
object FirstRunGuide {
    /** Shows the guide the first time this device opens the app. */
    fun showIfFirstRun(activity: Activity) {
        if (EditorPrefs.guideSeen) return
        show(activity)
    }

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.guide_title)
            .setMessage(R.string.guide_body)
            .setPositiveButton(R.string.guide_ok) { _, _ -> EditorPrefs.guideSeen = true }
            // Backing out counts as read: a dialog that returns on every launch
            // is a bug report, not a reminder.
            .setOnCancelListener { EditorPrefs.guideSeen = true }
            .setOnDismissListener { EditorPrefs.guideSeen = true }
            .show()
    }
}
