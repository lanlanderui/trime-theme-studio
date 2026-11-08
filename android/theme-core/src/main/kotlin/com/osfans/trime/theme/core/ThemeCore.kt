/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme.core

/**
 * 纯 JVM 层：主题模型、YAML 文本层、布局/工具条/标量读写、配色表与 Material You 数学。
 *
 * 这一层不允许出现 `android.*`：它是整个工程唯一能脱离设备跑单测的部分。
 */
object ThemeCore {
    const val MODULE = "theme-core"
}
