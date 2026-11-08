/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import androidx.annotation.ColorInt
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeFilesManager
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.osfans.trime.util.ColorUtils
import java.io.File

/**
 * One row of the workbench: what a theme file is called, and what happened when
 * it was read.
 *
 * Both halves are kept because both are useful before anything is opened: a
 * theme that fails to load is still a file the user may want to fix, and the
 * reason it failed is more useful on the row than behind a tap.
 */
data class ThemeEntry(
    val id: String,
    /** The `name:` the theme declares, or its file name when it declares none. */
    val name: String,
    val result: ThemeLoadResult,
) {
    val theme: Theme? get() = (result as? ThemeLoadResult.Success)?.theme

    /** Why this theme could not be read, in the loader's own words. */
    val failure: String? get() = (result as? ThemeLoadResult.Failure)?.error?.message

    /**
     * A few of the theme's own colours, in the order the keyboard shows them.
     *
     * Read from the scheme map as text, not through `ColorManager`: that is a
     * process-wide singleton over *one* theme, so asking it for a band of
     * colours per row would paint every row with the last theme loaded.
     */
    val swatches: List<Int> get() = theme?.let(::swatchesOf).orEmpty()

    /**
     * 「50 布局 · 54 色键 · 10 方案」，全部来自已经读进内存的主题，不再多读一次文件。
     *
     * 不写行数：那要把 200 KB 的 YAML 再数一遍，而列表在滚动时只为了一行数字
     * 去读盘并不划算；行数在页脚和诊断页都能看到。
     */
    val counts: String
        get() {
            val loaded = theme ?: return ""
            val schemes = loaded.presetColorSchemes
            val keys = (schemes[DEFAULT_SCHEME] ?: schemes.values.firstOrNull())?.size ?: 0
            return "%d 布局 · %d 色键 · %d 方案".format(
                loaded.presetKeyboards.size,
                keys,
                schemes.size,
            )
        }

    private companion object {
        const val DEFAULT_SCHEME = "default"

        /**
         * 方案的前几个色，按文件里的顺序。
         *
         * 上限 6 个，是卡片化的代价：旧版把四十多个色全画成一条细带，那是「按顺序
         * 罗列」；放进 16dp 圆角的卡片里之后，那条带变成了彩虹，读不出任何东西，
         * 还盖过了主题名。取前 6 个至少能看出「是深色还是浅色、偏冷还是偏暖」。
         *
         * 显示不全不影响判断：真正的配色在配色页，四十多个色一个不少。
         */
        const val SWATCH_LIMIT = 6

        fun swatchesOf(theme: Theme): List<Int> {
            val schemes = theme.presetColorSchemes
            val scheme = schemes[DEFAULT_SCHEME] ?: schemes.values.firstOrNull() ?: return emptyList()
            return scheme.values.mapNotNull(::parseOrNull).take(SWATCH_LIMIT)
        }

        @ColorInt
        fun parseOrNull(value: String): Int? = runCatching { ColorUtils.parseColor(value) }.getOrNull()
    }
}

/**
 * Reads every theme in [dir], newest file first.
 *
 * Loading happens here rather than in the adapter because it is file I/O plus a
 * YAML parse per theme; the caller runs it off the main thread and hands the
 * finished list over in one go.
 */
fun readThemes(dir: File?): List<ThemeEntry> {
    if (dir == null) return emptyList()
    return ThemeFilesManager.listThemes(dir).map { item ->
        ThemeEntry(item.configId, item.name, ThemeLoader.loadTheme(item.configId))
    }
}
