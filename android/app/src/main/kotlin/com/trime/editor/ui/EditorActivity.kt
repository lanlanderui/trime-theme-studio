/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.trime.editor.ui.skin.Skin
import com.trime.editor.ui.skin.UiMode
import com.trime.editor.ui.skin.UiTokens
import com.trime.editor.ui.R as UiR

/**
 * 所有页面的基类：把当前皮肤盖到主题上，然后才去 inflate。
 *
 * 用 `theme.applyStyle(...)` 而不是 `setTheme(...)`：三个快捷入口在清单里挂了
 * `Theme.TrimeEditor.QuickAction`（半透明、不画界面）。`setTheme` 会把清单主题
 * 整个换掉，表现为点快捷入口时闪一下白屏；`applyStyle` 只在既有主题上叠一层，
 * 半透明仍然是半透明。
 *
 * 为什么还要在 `onResume` 里再查一次：皮肤是在「外观」页切换的，切换后栈里的
 * 上一层还留着旧主题。栈最深三层，就地重建比广播 / 让用户手动退出去更省事。
 *
 * 关于 [applyChrome]：舞台皮肤的两处结构差异（色块头、悬浮工具条）不改页面代码，
 * 由基类在 `onPostCreate` 里认「工具栏」和 `ui_dock` 标签统一改造。页面因此只需
 * 描述「这里是个工具栏」「这里是个底栏」，不需要知道当前是哪套皮肤。
 */
open class EditorActivity : AppCompatActivity() {
    private var appliedSkin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applySkin()
        super.onCreate(savedInstanceState)
        // 窗口底色也要跟着皮肤走：页面之间切换时露出来的那一条缝，
        // 用的是 window background，不是任何 View 的底色。
        window.setBackgroundDrawable(ColorDrawable(uiColor(UiR.attr.ui_paper)))
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applyChrome()
    }

    override fun onResume() {
        super.onResume()
        if (appliedSkin != Skin.current().id) recreate()
    }

    private fun applySkin() {
        val skin = Skin.current()
        appliedSkin = skin.id
        skin.applyTo(this)
    }

    private fun applyChrome() {
        val caps = UiMode.caps
        if (!caps.stageHeader && !caps.floatingDock) return
        val root = findViewById<View>(android.R.id.content) as? ViewGroup ?: return
        if (caps.stageHeader) findToolbar(root)?.let(::makeStage)
        if (caps.floatingDock) findByTag(root, DOCK_TAG, mutableListOf()).forEach(::makeDock)
    }

    /** 顶栏变成「压在上面的色块」：通到两侧，只在下沿收圆角。 */
    private fun makeStage(toolbar: Toolbar) {
        toolbar.background = UiTokens.stageHeader(this)
        toolbar.elevation = 0f
        (toolbar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.setMargins(0, 0, 0, 0)
            // 34sp 标题 + 副标题塞不进 56dp 的标准顶栏：舞台是「一块头」，本来就更高。
            lp.height = skinDp(104)
            toolbar.layoutParams = lp
        }
        toolbar.setPadding(toolbar.paddingLeft, toolbar.paddingTop, toolbar.paddingRight, skinDp(14))
    }

    /** 底栏变成悬浮胶囊：四角全圆 + 阴影 + 外边距。 */
    private fun makeDock(view: View) {
        view.background = UiTokens.dock(this)
        view.elevation = skinDp(6).toFloat()
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val gap = skinDp(10)
            lp.setMargins(gap, 0, gap, gap)
            view.layoutParams = lp
        }
    }

    private fun findToolbar(parent: ViewGroup): Toolbar? {
        for (i in 0 until parent.childCount) {
            when (val child = parent.getChildAt(i)) {
                is Toolbar -> return child
                is ViewGroup -> findToolbar(child)?.let { return it }
            }
        }
        return null
    }

    private fun findByTag(
        parent: ViewGroup,
        tag: String,
        out: MutableList<View>,
    ): List<View> {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (tag == child.tag) out += child
            if (child is ViewGroup) findByTag(child, tag, out)
        }
        return out
    }

    @ColorInt
    protected fun uiColor(
        @AttrRes attr: Int,
    ): Int = UiTokens.color(this, attr)

    protected fun uiDimen(
        @AttrRes attr: Int,
    ): Int = UiTokens.dimen(this, attr)

    protected fun skinDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DOCK_TAG = "ui_dock"
    }
}
