/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * 主题相关的偏好项，由宿主实现。
 *
 * 只收共享层真正读的几项：输入法的 `ThemePrefs` 里还有 `navbarBackground` 这种
 * 带枚举和 API 判断的条目，但那是界面自己的事，不进这张表——放进来只会逼着
 * 编辑器为一项它用不到的设置去实现一个枚举。
 */
interface ThemePreferenceStore {
    /** 当前主题的 config id。 */
    var selectedTheme: String

    /** 当前配色方案 id。 */
    var normalModeColor: String

    /** 配色是否跟随系统深色模式。 */
    var followSystemDayNight: Boolean

    /** 主题目录覆盖；空串表示用宿主自己的默认目录。 */
    var externalThemeDir: String
}
