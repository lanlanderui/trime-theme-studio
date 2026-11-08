/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.PresetKey

/**
 * 宿主自带的键动作。
 *
 * 两个 Material You 动作（`dynamic_color_update` / `dynamic_color_save`）故意不写进
 * `preset_keys`：主题文件是用户手动编辑的，凭空多出两条，下次编辑就会被写回去。
 * 所以它们由宿主提供给 `KeyAction`，主题里直接写 `{click: dynamic_color_update}` 也能用。
 */
fun interface BuiltinPresetKeys {
    /** 这个 token 对应的内置动作，主题自己的 token 返回 null。 */
    fun presetOf(token: String): PresetKey?
}

/** 当前装着的内置动作；不装就等于没有内置动作（编辑器暂时用不到）。 */
object BuiltinPresets {
    @Volatile
    var keys: BuiltinPresetKeys? = null

    fun presetOf(token: String): PresetKey? = keys?.presetOf(token)
}
