/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.trime.editor.ui.R
import com.trime.editor.ui.color.checkerSwatch
import com.trime.editor.ui.skin.UiTokens

/**
 * 尺寸与色块这两件事的唯一出口。
 *
 * 尺寸本身在 `proof_dimens.xml`（平板的那几个值在 `values-sw600dp` 覆盖），
 * 这里只放「必须在代码里造」的那几个：色块、开关的两片颜色。它们都不是一张
 * 图，而是「这个东西在纸上长什么样」——半透明的要垫棋盘格，否则 `0x00` 会被
 * 画成纯黑，跟文件里写的完全相反。
 */
internal object ProofTokens {
    /**
     * 26dp 的方块：填色 + 1dp 描边。
     *
     * 描边不是装饰——白色键的背景是 `#FFFFFF`，纸也是近白，没有描边这一格
     * 就消失了，看起来像「没设置」。
     */
    fun swatch(
        context: Context,
        argb: Int,
    ): Drawable {
        val alpha = (argb ushr 24) and 0xFF
        if (alpha < 0xFF) return context.checkerSwatch(argb)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.resources.getDimension(R.dimen.ui_swatch_radius)
            setColor(argb)
            setStroke(
                context.resources.getDimensionPixelSize(R.dimen.ui_swatch_stroke_w),
                UiTokens.color(context, R.attr.ui_swatch_stroke),
            )
        }
    }

    /**
     * 开关的轨道。默认会取 AppCompat 的 `colorAccent`（编辑器品牌橙），
     * 在这张纸上是一块不属于任何一层的颜色；轨道改成朱红配 rule_strong。
     */
    fun switchTrack(context: Context): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(
            UiTokens.color(context, R.attr.ui_accent),
            UiTokens.color(context, R.attr.ui_rule_strong),
        ),
    )

    /** 滑块颜色：两套模式都取近白的 proof_knob——sheet 在暗色下太深，会跟纸糊在一起。 */
    fun switchThumb(context: Context): ColorStateList = ColorStateList.valueOf(UiTokens.color(context, R.attr.ui_knob))
}
