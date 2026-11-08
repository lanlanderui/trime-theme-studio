/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.trime.editor.ui.EditorSection
import com.trime.editor.ui.proof.OutlineRail
import com.trime.editor.ui.skin.UiTokens
import com.trime.editor.ui.R as UiR

/**
 * 九个页面共用的「目录」入口与抽屉。
 *
 * 目录按钮曾经只写在 `menu/editor.xml` 里，点击处理又只写在 [ThemeEditorActivity]
 * （样式 / 配色的基类）里：于是另外五个用了同一份菜单的页面「按钮在、点了没反应」，
 * 剩下四个用别的菜单的页面连按钮都没有。现在菜单项、当前章、抽屉都收在这里，
 * 每页只要 `addTo` + `handle` 两行，不会出现「有一页忘了接」。
 *
 * 只有两栏容器页有常驻 rail，其余页面在平板上也没有——所以抽屉对谁都开放。
 */
object OutlineMenu {
    private const val WIDTH_DP = 168

    /** 菜单 XML 里已经声明过的页面不重复添加（例如 `menu/editor.xml`）。 */
    fun addTo(
        menu: Menu,
        context: Context,
    ) {
        if (menu.findItem(R.id.action_outline) != null) return
        menu
            .add(Menu.NONE, R.id.action_outline, Menu.NONE, context.getString(UiR.string.outline_open))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    /**
     * 接住目录点击；返回 true 表示已消费。
     *
     * 只有两栏容器页有「节」可滚，其它页传空列表，目录栏就只列九个章。
     */
    fun handle(
        activity: AppCompatActivity,
        item: MenuItem,
        themeId: String,
        themeName: String,
        sections: List<EditorSection> = emptyList(),
        onSection: (Int) -> Unit = {},
    ): Boolean {
        if (item.itemId != R.id.action_outline) return false
        show(activity, themeId, themeName, sections, onSection)
        return true
    }

    /**
     * 从左边滑出一整条 [OutlineRail]：和常驻栏同一个控件、同一份章，点完即收。
     *
     * 手机上不给它留一列（168dp 的栏放在 400dp 宽的屏上，内容只剩一半），
     * 但平板上那些没有常驻栏的页面也用这个入口。
     */
    private fun show(
        activity: AppCompatActivity,
        themeId: String,
        themeName: String,
        sections: List<EditorSection>,
        onSection: (Int) -> Unit,
    ) {
        val rail = OutlineRail(activity)
        val dialog = Dialog(activity)
        rail.bind(
            currentChapter = chapterOf(activity),
            sections = sections,
            onChapter = { chapter ->
                dialog.dismiss()
                // 工作台还没选主题，跳到任何编辑页都会立刻 finish；说清楚，别静默。
                if (themeId.isEmpty() && chapter != "workbench") {
                    Toast
                        .makeText(activity, R.string.outline_need_theme, Toast.LENGTH_SHORT)
                        .show()
                    return@bind
                }
                PageRouter.intent(activity, chapter, themeId, themeName)?.let { activity.startActivity(it) }
            },
            onSection = { position ->
                dialog.dismiss()
                onSection(position)
            },
        )
        dialog.setContentView(rail)
        dialog.window?.let { window ->
            // 贴左边、整屏高；底色用纸色，否则对话框主题会在栏外面留一圈白边。
            val width = (WIDTH_DP * activity.resources.displayMetrics.density).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setGravity(Gravity.START)
            window.setBackgroundDrawable(
                ColorDrawable(UiTokens.color(activity, UiR.attr.ui_paper)),
            )
            window.setDimAmount(0.35f)
        }
        dialog.show()
    }

    /** 当前页属于哪一章，决定目录里哪一个字点亮。 */
    private fun chapterOf(activity: AppCompatActivity): String = when (activity) {
        is MainActivity -> "workbench"
        is ThemePreviewActivity -> "preview"
        is StyleEditorActivity -> "style"
        is ColorEditorActivity -> "colors"
        is KeyboardListActivity, is KeyboardEditorActivity -> "layout"
        is PresetKeyListActivity -> "keys"
        is ToolBarActivity -> "toolbar"
        is CandidateWindowActivity -> "window"
        is DiagnosticsActivity -> "diagnostics"
        else -> ""
    }
}
