/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent

/**
 * 章 id → 页面。
 *
 * 九个 Activity 各有各的 `intent(...)` 工厂，这里只是把它们收成一张表：目录栏
 * 只知道章 id，不该知道哪个类、带什么 extra。加一个页面时改这张表，栏本身不用动。
 *
 * 工作台例外：它不带主题（还没选主题），所以直接 `Intent` 过去。
 */
object PageRouter {
    fun intent(
        context: Context,
        chapter: String,
        themeId: String,
        themeName: String,
    ): Intent? = when (chapter) {
        "workbench" -> Intent(context, MainActivity::class.java)
        "preview" -> ThemePreviewActivity.intent(context, themeId, themeName)
        "style" -> StyleEditorActivity.intent(context, themeId, themeName)
        "colors" -> ColorEditorActivity.intent(context, themeId, themeName)
        "layout" -> KeyboardListActivity.intent(context, themeId, themeName)
        "keys" -> PresetKeyListActivity.intent(context, themeId, themeName)
        "toolbar" -> ToolBarActivity.intent(context, themeId, themeName)
        "window" -> CandidateWindowActivity.intent(context, themeId, themeName)
        "diagnostics" -> DiagnosticsActivity.intent(context, themeId, themeName)
        else -> null
    }
}
