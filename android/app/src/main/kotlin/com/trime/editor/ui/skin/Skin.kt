/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.skin

import android.app.Activity
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.trime.editor.EditorPrefs
import com.trime.editor.R

/**
 * 两套界面皮肤（0.4.0 起下架「校样」）。
 *
 * 「皮肤」在这里是一张 ThemeOverlay（覆盖 `ui_*` token）+ 一组能力位（允许改结构
 * 的那几个开关）。它不含任何行为，所以：
 * - 换皮肤 = 换一层资源，页面代码不动；
 * - 皮肤之间不共享改动风险，第三张 overlay 写错只影响它自己。
 *
 * 旧版本存下的 `proof` 不再是合法 id：[of] 会按未知值回退到 [DEFAULT]（层叠），
 * 所以老用户升级后不会卡在已经下架的皮肤上。
 */
enum class Skin(
    val id: String,
    @param:StringRes val label: Int,
    @param:StringRes val tagline: Int,
    @param:StyleRes val overlay: Int,
    /** 预览用的三个色点：主色 / 卡片 / 墨。外观页拿它画「这套皮肤长什么样」。 */
    @param:ColorRes val dotPrimary: Int,
    @param:ColorRes val dotCard: Int,
    @param:ColorRes val dotInk: Int,
    val caps: SkinCaps,
) {
    TONAL(
        id = "m3-tonal",
        label = R.string.skin_tonal,
        tagline = R.string.skin_tonal_tagline,
        overlay = R.style.Skin_Tonal,
        dotPrimary = R.color.tonal_primary,
        dotCard = R.color.tonal_card,
        dotInk = R.color.tonal_ink,
        caps = SkinCaps(),
    ),
    EXPRESSIVE(
        id = "m3-expressive",
        label = R.string.skin_expressive,
        tagline = R.string.skin_expressive_tagline,
        overlay = R.style.Skin_Expressive,
        dotPrimary = R.color.expressive_primary,
        dotCard = R.color.expressive_card,
        dotInk = R.color.expressive_ink,
        caps = SkinCaps(stageHeader = true, floatingDock = true, segmented = true),
    ),
    ;

    /** 把这张皮肤盖到 `activity` 现有的主题上。 */
    fun applyTo(activity: Activity) {
        activity.theme.applyStyle(overlay, true)
        UiMode.caps = caps
    }

    companion object {
        val DEFAULT: Skin = TONAL

        /** 按 id 找皮肤；未知 id 回退默认，不抛异常（旧版本存下来的值可能是任何串）。 */
        fun of(id: String?): Skin = entries.firstOrNull { it.id == id } ?: DEFAULT

        fun current(): Skin = of(EditorPrefs.skinId)
    }
}
