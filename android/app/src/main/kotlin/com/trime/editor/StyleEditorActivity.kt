/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.trime.editor.ui.style.StyleEditorFragment
import com.trime.editor.ui.style.StyleEditorHost

/**
 * The style form with the keyboard it affects directly above it.
 *
 * Both on one screen on purpose: a style field is a number in a file until the
 * keyboard is rebuilt from it, so "saved" and "visible" have to be the same
 * moment.
 */
class StyleEditorActivity :
    ThemeEditorActivity(),
    StyleEditorHost {
    override val subtitleRes: Int get() = R.string.style_editor_title

    override val outlineChapter: String get() = "style"

    override fun createEditor(): Fragment = StyleEditorFragment.newInstance(themeId)

    /** A style change can move key sizes and labels, so the board is rebuilt. */
    override fun reloadBoard() = reloadTheme()

    override fun refreshCaption() = updateCaption()

    /** Colours are resolved while drawing, so the board only needs a repaint. */
    override fun applyScheme(schemeId: String) {
        board.refresh()
    }

    companion object {
        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = intentOf(context, StyleEditorActivity::class.java, themeId, themeName)
    }
}
