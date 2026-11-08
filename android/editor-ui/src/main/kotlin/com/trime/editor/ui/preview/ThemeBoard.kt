/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.preview

import android.view.ViewGroup
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.osfans.trime.ime.keyboard.KeyColorBoardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A preview board and the theme that is currently loaded into it.
 *
 * Every screen that shows a keyboard — the read-only preview and the style
 * editor — needs the same three steps in the same order: load the theme off the
 * main thread, make it the process's active theme (so `Key` resolves colours
 * through it), then build the keyboard at the width the board was given. Doing
 * that here is what keeps the two screens from drifting apart: there is one
 * place that knows the order, and one place that remembers the measured width.
 *
 * Call [load] and [show] from the main thread; only the file read is moved off.
 */
class ThemeBoard private constructor(
    private val container: ViewGroup,
    val view: KeyColorBoardView,
) {
    private var width = 0

    var theme: Theme? = null
        private set

    /** 候选栏与底部内边距的示意带子，包着 [view]。 */
    private val chrome = PreviewChrome(container.context)

    init {
        chrome.wrap(view)
        container.addView(
            chrome,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // The keyboard is built from the width the board actually got. That width
        // can change after the first layout — the editor grows a directory rail
        // when the theme loads, rotation re-lays everything out, split screen
        // resizes the page — and a board built for the old width draws past its
        // right edge. So this listens for every layout change, not just the first.
        //
        // The build is posted rather than run inline: setting the keyboard calls
        // requestLayout(), and a request made during a layout pass is dropped —
        // the board then keeps its fallback height and clips its bottom row.
        view.addOnLayoutChangeListener { laid, _, _, right, _, _, _, _, _ ->
            val measured = right - laid.left
            if (measured > 0 && measured != width) laid.post { build(measured) }
        }
    }

    /** Draws [theme] at the width the board has, or will have once it is measured. */
    fun show(theme: Theme) {
        this.theme = theme
        // 候选栏和底部内边距都按这份主题摆：换主题时它们也要跟着变。
        chrome.applyStyle(theme)
        if (width > 0) build(width)
    }

    /**
     * Loads [themeId] and draws it.
     *
     * Loading and activating are one call on purpose: a theme that is read but
     * not attached has no colour scheme, so a board built from it would resolve
     * every colour against whatever theme was active before.
     */
    suspend fun load(themeId: String): ThemeLoadResult {
        val result = withContext(Dispatchers.IO) { ThemeLoader.loadTheme(themeId) }
        if (result is ThemeLoadResult.Success) {
            ColorManager.attachTheme(result.theme)
            show(result.theme)
        }
        return result
    }

    /** Repaints without rebuilding; enough after a colour scheme switch. */
    fun refresh() {
        view.invalidate()
        // 候选栏的底色和边框是 applyStyle 时解析好存下的，只重画板子不会带上它。
        chrome.refreshColors()
    }

    private fun build(targetWidth: Int) {
        width = targetWidth
        val current = theme ?: return
        view.showTheme(current, targetWidth)
    }

    companion object {
        /** Wraps [container]'s content in a board; the container must be empty. */
        fun attach(container: ViewGroup): ThemeBoard = ThemeBoard(container, KeyColorBoardView(container.context))
    }
}
