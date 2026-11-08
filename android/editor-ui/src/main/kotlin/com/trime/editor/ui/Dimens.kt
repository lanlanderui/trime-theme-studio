/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui

import android.content.Context

/**
 * Density-independent pixels; a local copy so the UI module needs no helper library.
 *
 * Shared by every screen that builds views in code — the swatches, the layout
 * thumbnail, the canvas — because two copies of this would eventually disagree
 * about rounding and only one of them would be right.
 */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
