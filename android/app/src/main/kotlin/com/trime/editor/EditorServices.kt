/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import com.osfans.trime.data.theme.ThemeDirectory
import com.osfans.trime.data.theme.ThemeLog
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.keyboard.KeyboardPreferenceStore
import com.osfans.trime.ime.keyboard.KeyboardPrefs
import com.osfans.trime.ime.keyboard.RimeStatus
import com.osfans.trime.ime.keyboard.RimeStatusProvider
import timber.log.Timber

/**
 * 编辑器侧的接缝实现。
 *
 * 共享库（`:theme-runtime`）里凡是「只有宿主知道」的东西都做成注入点，这里把它们
 * 逐个填上。和输入法的区别只有一条：**没有 librime**。所以：
 *
 * - `RimeStatus` 全部取静态值——预览板画的是一张不随输入法状态变化的键盘；
 * - `KeyboardPreferenceStore` 的开关取输入法的出厂默认值（`hookShiftNum`/`hookShiftSymbol`
 *   都是 false），这样编辑器看到的与用户装好后第一次看到的一致；
 * - `BuiltinPresets` **不装**：`dynamic_color_*` 是 Material You 的两个动作，属于输入法，
 *   编辑器里凭空多出两条会让 `preset_keys` 的列表对不上文件。
 */
object EditorServices {
    fun install() {
        ThemeLog.sink = { priority, message, throwable ->
            when (priority) {
                'D' -> Timber.d(throwable, "%s", message)
                'I' -> Timber.i(throwable, "%s", message)
                'W' -> Timber.w(throwable, "%s", message)
                'E' -> Timber.e(throwable, "%s", message)
                else -> Timber.v(throwable, "%s", message)
            }
        }
        // 主题目录由用户选：取值函数而不是值，切换目录后下一次读取就换了地方。
        ThemeDirectory.themeDirFrom { EditorPrefs.themeDir }
        // 编辑器只认用户目录：没有一份「出厂 shared 目录」可以让主题悄悄回退过去，
        // 否则用户改的是自己的文件、看到的却是另一份。
        ThemeDirectory.sharedDir = null
        ThemeManager.preferenceStore = EditorPrefs
        RimeStatusProvider.status = HeadlessRimeStatus
        KeyboardPrefs.store = EditorKeyboardPreferences
    }
}

/** 没有输入法在跑：永远不是 ASCII、没有组合、没有候选。 */
private object HeadlessRimeStatus : RimeStatus {
    override val isAsciiMode: Boolean get() = false

    override val isAsciiPunct: Boolean get() = false

    override val isComposing: Boolean get() = false

    override val hasMenu: Boolean get() = false

    override val paging: Boolean get() = false

    override val schemaName: String get() = ""

    override val schemaId: String get() = ""

    override fun getRuntimeOption(option: String): Boolean = false
}

/**
 * 键盘布局的设置项，取输入法出厂默认值。
 *
 * `splitSpacePercent = 0`（不分体）、`expandKeypressArea = false`、两个 `hook*` 都是 false。
 *
 * 横屏判定同样是**出厂默认** `LandscapeMode.NEVER`，也就是恒为 false。这一条不能想当然地
 * 按屏幕方向来：`NEVER` 时输入法永远用 `keyboardHeight`（竖屏值），若这里按方向返回 true，
 * 预览就会取 `keyboardHeightLand`，同一个主题画出来的键盘比真机矮/高一截。真机实测：
 * 按方向判定得 500px，按 `NEVER` 得 475px，与输入法一致。
 */
private object EditorKeyboardPreferences : KeyboardPreferenceStore {
    override val splitSpacePercent: Int get() = 0

    override val expandKeypressArea: Boolean get() = false

    override val hookShiftNum: Boolean get() = false

    override val hookShiftSymbol: Boolean get() = false

    override fun isLandscapeMode(context: Context): Boolean = false
}
