/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context

/**
 * 键盘布局要读的几项输入法设置。
 *
 * 都只影响「键盘长什么样、怎么排」：分体间隙、按下区域外扩、Shift 数字/符号是否上屏、
 * 以及横屏判定。输入法的 `AppPrefs.keyboard` 带着 `R.string` 标题和 `LandscapeMode`
 * 枚举，属设置界面，所以留一张这样的表给共享层用。
 */
interface KeyboardPreferenceStore {
    /** 横屏分体键盘的中缝占比（0..100 的整数百分比）。 */
    val splitSpacePercent: Int

    /** 把键的触摸区域外扩到中缝和边缘。 */
    val expandKeypressArea: Boolean

    /** Shift + 数字键是否按符号上屏（关掉则保持数字）。 */
    val hookShiftNum: Boolean

    /** Shift + 符号键是否切换符号（关掉则保持原符号）。 */
    val hookShiftSymbol: Boolean

    /**
     * 是否按横屏布局排键盘。
     *
     * 判定规则归宿主：输入法读设置里的三种模式（跟随宽屏 / 跟随方向 / 始终），
     * 编辑器则按屏幕实际方向。这里只留一个入口，省得把那个枚举也搬进来。
     */
    fun isLandscapeMode(context: Context): Boolean
}

/**
 * [KeyboardPreferenceStore] 的取用入口。
 *
 * `isLandscapeMode` 保留成 `Context` 的扩展函数，是因为它原来是
 * `KeyboardPrefs.isLandscapeMode`，调用点分布在输入栏、键盘窗口等处，这样 import 不用改。
 */
object KeyboardPrefs {
    @Volatile
    var store: KeyboardPreferenceStore? = null

    private fun require(): KeyboardPreferenceStore = store ?: error("KeyboardPrefs.store has not been installed by the host")

    val splitSpacePercent: Int get() = require().splitSpacePercent

    val expandKeypressArea: Boolean get() = require().expandKeypressArea

    val hookShiftNum: Boolean get() = require().hookShiftNum

    val hookShiftSymbol: Boolean get() = require().hookShiftSymbol

    fun Context.isLandscapeMode(): Boolean = require().isLandscapeMode(this)
}
