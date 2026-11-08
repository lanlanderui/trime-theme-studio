/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.skin

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.trime.editor.ui.R

/**
 * 皮肤 token 的唯一出口。
 *
 * 三套皮肤（校样 / 层叠 / 舞台）在资源里只是三张 ThemeOverlay，覆盖同一批
 * `ui_*` 属性；页面与自绘 View 通过这里取值，因此「换皮肤」不需要改页面。
 *
 * 为什么不用 `R.color.*`：颜色资源在编译期定死，运行时换不了。属性是运行时
 * 唯一能改的那一层。
 *
 * 为什么这些函数是 internal：只有 `editor-ui` 内部的组件该用它们；`:app` 需要的
 * 那几个（`color` / `dimen` / `radius`）在 `UiTokensApi` 里转公开，避免整个包被
 * 当成公共 API 卖掉。
 */
object UiTokens {
    /**
     * 取一个颜色 token。
     *
     * 属性缺失时返回洋红（调试色）而不是抛异常：一个色板漏填的时候，界面上
     * 出现一块洋红比整页崩掉更容易定位，也不会让用户丢掉正在编辑的内容。
     */
    @ColorInt
    fun color(
        context: Context,
        @AttrRes attr: Int,
    ): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return MISSING
        return if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            value.data
        } else {
            runCatching { ContextCompat.getColor(context, value.resourceId) }.getOrElse { MISSING }
        }
    }

    /** 取一个尺寸 token（圆角、字阶、间距都走它）。缺失时返回 0。 */
    fun dimen(
        context: Context,
        @AttrRes attr: Int,
    ): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return 0
        return when {
            value.type == TypedValue.TYPE_DIMENSION -> TypedValue.complexToDimensionPixelSize(value.data, context.resources.displayMetrics)
            value.type == TypedValue.TYPE_FLOAT -> value.float.toInt()
            value.resourceId != 0 -> context.resources.getDimensionPixelSize(value.resourceId)
            else -> 0
        }
    }

    fun textSize(
        context: Context,
        @AttrRes attr: Int,
    ): Float {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return 0f
        return when {
            value.type == TypedValue.TYPE_DIMENSION -> TypedValue.complexToFloat(value.data)
            value.resourceId != 0 -> context.resources.getDimension(value.resourceId)
            else -> 0f
        }
    }

    /** 字体族 token（`serif` / `sans-serif` / `monospace`）。 */
    fun font(
        context: Context,
        @AttrRes attr: Int,
    ): String {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return "sans-serif"
        return value.string?.toString() ?: "sans-serif"
    }

    /**
     * 卡片底：填 [R.attr.ui_card] + 圆角 [R.attr.ui_radius_card] + 一道 1dp 描边。
     *
     * 描边不是装饰：校样皮肤的卡片底色与纸几乎同色，没有描边就只剩一个
     * 「看不见的块」，而层叠皮肤靠它把卡片从底色上分出来。
     */
    fun card(
        context: Context,
        fillAttr: Int = R.attr.ui_card,
        strokeAttr: Int = R.attr.ui_rule,
    ): GradientDrawable = roundedShape(context, fillAttr, strokeAttr, R.attr.ui_radius_card)

    /** 字段底：outlined 风格，圆角比卡片小一档。 */
    fun field(context: Context): GradientDrawable = roundedShape(context, R.attr.ui_sheet, R.attr.ui_rule_strong, R.attr.ui_radius_field)

    /**
     * 卡片底，圆角档位由调用方给。
     *
     * B3 一屏有三档卡片（大卡 26 / 半卡 22 / 卡片 28），底色与描边都一样，
     * 只有圆角不同；给每档建一个函数会变成三个几乎一样的函数。
     */
    fun rounded(
        context: Context,
        @AttrRes radiusAttr: Int,
        fillAttr: Int = R.attr.ui_card,
        strokeAttr: Int = R.attr.ui_rule,
    ): GradientDrawable = roundedShape(context, fillAttr, strokeAttr, radiusAttr)

    /** 色块底：统一圆角与 1dp 描边，白块放在白纸上才不会消失。 */
    fun swatch(
        context: Context,
        @ColorInt argb: Int,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dimen(context, R.attr.ui_radius_swatch).toFloat()
        setColor(argb)
        setStroke(strokeWidth(context), color(context, R.attr.ui_swatch_stroke))
    }

    private fun roundedShape(
        context: Context,
        @AttrRes fillAttr: Int,
        @AttrRes strokeAttr: Int,
        @AttrRes radiusAttr: Int,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dimen(context, radiusAttr).toFloat()
        setColor(color(context, fillAttr))
        setStroke(strokeWidth(context), color(context, strokeAttr))
    }

    /**
     * 舞台头：上沿直角贴住窗口顶边，下沿两个角是圆角——这是「一块色带压在内容上」，
     * 不是「一张浮在中间的卡片」。
     *
     * 方向不能反：反了就变成「顶栏是一张卡」，而稿子里它是一整块背景墙。
     */
    fun stageHeader(context: Context): GradientDrawable {
        val r = dimen(context, R.attr.ui_radius_sheet).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            setColor(color(context, R.attr.ui_card_high))
        }
    }

    /** 悬浮工具条：四角全圆 + 主色容器，靠阴影从内容上浮起来。 */
    fun dock(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dimen(context, R.attr.ui_radius_sheet).toFloat()
        setColor(color(context, R.attr.ui_card_high))
    }

    /** 描边宽度：1dp 资源，随密度换算；不分皮肤。 */
    private fun strokeWidth(context: Context): Int = context.resources.getDimensionPixelSize(R.dimen.ui_swatch_stroke_w)

    /** 调试色：品红。看见它说明某个 skin 少填了一个 `ui_*`。 */
    private const val MISSING = 0xFFFF00FF.toInt()
}
