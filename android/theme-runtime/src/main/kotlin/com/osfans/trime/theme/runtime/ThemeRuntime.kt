/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme.runtime

/**
 * 运行时层：主题装载、取色、以及预览用的 [Keyboard]/[Key] 渲染子集。
 *
 * 这一层与输入法同源——测试3 也依赖它，避免预览随输入法迭代漂移。
 */
object ThemeRuntime {
    const val MODULE = "theme-runtime"
}
