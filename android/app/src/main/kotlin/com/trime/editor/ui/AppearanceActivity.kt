/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.trime.editor.EditorPrefs
import com.trime.editor.R
import com.trime.editor.applySystemBarInsets
import com.trime.editor.ui.skin.Skin
import com.trime.editor.ui.skin.UiTokens
import com.trime.editor.ui.R as UiR

/**
 * 外观：两套皮肤里挑一套。
 *
 * 为什么单独一页而不是塞进「样式」偏好里：皮肤影响的是**当前这一屏**，改完
 * 要重建界面才有意义；放在设置列表里会让人以为「勾了就生效」，而实际上半屏
 * 还是旧皮肤。这里一键切换、整页重建，因果最短。
 *
 * 切换入口只在 this 一处：编辑页里不提供换肤入口，避免用户在改主题改到一半时
 * 触发重建、把草稿丢掉。
 */
class AppearanceActivity : EditorActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(uiColor(UiR.attr.ui_paper))
                // 平板上一行铺满 848dp 会把三张卡拉成三条横杠；限宽 560dp 居中，
                // 手机上仍然是 20dp 边距。
                setPadding(sidePadding(), dp(24), sidePadding(), dp(32))
            }

        column.addView(
            text(getString(R.string.appearance_title), uiColor(UiR.attr.ui_ink), 26f, title = true),
        )
        column.addView(
            text(getString(R.string.appearance_subtitle), uiColor(UiR.attr.ui_muted), 11f, mono = true)
                .apply { setPadding(0, dp(4), 0, dp(16)) },
        )
        column.addView(
            text(getString(R.string.appearance_hint), uiColor(UiR.attr.ui_muted), 12f)
                .apply {
                    setLineSpacing(0f, 1.45f)
                    setPadding(0, 0, 0, dp(18))
                },
        )

        val current = Skin.current()
        Skin.entries.forEach { skin ->
            column.addView(option(skin, skin == current))
        }

        setContentView(ScrollView(this).apply { addView(column) }.also { it.applySystemBarInsets() })
    }

    private fun option(
        skin: Skin,
        selected: Boolean,
    ): View {
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTokens.card(this@AppearanceActivity)
                setPadding(dp(16), dp(14), dp(16), dp(16))
                isClickable = true
                isFocusable = true
                setOnClickListener { choose(skin) }
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(10) }
            }

        // 三个色点：主色 / 卡片 / 墨。一眼能分出三套皮肤，不必读字。
        val dots =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        dots.addView(dot(skin.dotPrimary))
        dots.addView(dot(skin.dotCard))
        dots.addView(dot(skin.dotInk))

        val head =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        head.addView(
            text(getString(skin.label), uiColor(UiR.attr.ui_ink), 15f, title = !selected).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        head.addView(dots)

        card.addView(head)
        card.addView(
            text(getString(skin.tagline), uiColor(UiR.attr.ui_muted), 11.5f).apply {
                setPadding(0, dp(6), 0, 0)
            },
        )
        if (selected) {
            card.addView(
                text(getString(R.string.skin_selected), uiColor(UiR.attr.ui_accent), 10.5f, mono = true)
                    .apply { setPadding(0, dp(8), 0, 0) },
            )
        }
        return card
    }

    /**
     * 一个色点。
     *
     * 颜色直接取「那张皮肤」的色板资源，不按当前主题取：选项上画的应该是它
     * 长什么样，而当前主题只有一套。为三个色点临时造一个 ContextThemeWrapper
     * 是另一种写法，但它在 API 23 以下要绕，收益也不比一行资源 id 大。
     */
    private fun dot(
        @ColorRes colorRes: Int,
    ): View {
        val size = dp(18)
        return View(this).apply {
            background = UiTokens.swatch(this@AppearanceActivity, ContextCompat.getColor(this@AppearanceActivity, colorRes))
            layoutParams =
                LinearLayout.LayoutParams(size, size).apply {
                    marginStart = dp(6)
                }
        }
    }

    private fun choose(skin: Skin) {
        if (skin.id == Skin.current().id) return
        EditorPrefs.skinId = skin.id
        recreate()
    }

    private fun text(
        value: String,
        color: Int,
        sizeSp: Float,
        title: Boolean = false,
        mono: Boolean = false,
    ): TextView = TextView(this).apply {
        text = value
        setTextColor(color)
        textSize = sizeSp
        typeface =
            when {
                mono -> Typeface.MONOSPACE
                title -> Typeface.create(UiTokens.font(this@AppearanceActivity, UiR.attr.ui_font_title), Typeface.NORMAL)
                else -> Typeface.create(UiTokens.font(this@AppearanceActivity, UiR.attr.ui_font_body), Typeface.NORMAL)
            }
        includeFontPadding = false
    }

    private fun sidePadding(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return if (widthDp >= 600) ((widthDp - 560) / 2f * resources.displayMetrics.density).toInt() else dp(20)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
