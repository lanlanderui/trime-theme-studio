/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps content out from under the status bar and the navigation bar.
 *
 * From Android 15 the window is edge-to-edge whether the app asks for it or
 * not, so a layout that used to sit below the status bar now draws behind it —
 * the title was overlapping the clock. Padding the root by the bar insets is
 * enough here: nothing on these screens is meant to paint under a bar.
 */
fun View.applySystemBarInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
