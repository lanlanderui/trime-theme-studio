/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui

/**
 * 界面层：工作台与六个编辑页（样式/配色/布局/动作/工具条/候选窗）。
 *
 * 沿用 View 体系（`Preference` + 自绘 View），不引入 Compose：
 * 预览板要复用输入法的 `KeyFaceRenderer`，换成 Compose 就再也做不到像素一致。
 */
object EditorUi {
    const val MODULE = "editor-ui"
}
