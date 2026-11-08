/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme.editor

/**
 * 编辑层：写盘（`ThemeTextFile` 与各 Writer）、诊断、生成（Material You / 模板）。
 *
 * 只有编辑器依赖这一层；输入法不带写盘代码。
 */
object ThemeEditor {
    const val MODULE = "theme-editor"
}
