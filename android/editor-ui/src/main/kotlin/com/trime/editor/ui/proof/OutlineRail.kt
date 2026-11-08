/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.trime.editor.ui.EditorSection
import com.trime.editor.ui.R
import com.trime.editor.ui.dp
import com.trime.editor.ui.skin.UiTokens

/**
 * 目录栏：九章一列，当前章下面展开它的节。
 *
 * 两件事分开：**章**换页（`startActivity`，沿用现有九个 Activity），**节**只
 * 滚动当前列表（`scrollToPositionWithOffset`）。所以这一栏不持有任何页面内容，
 * 只拿到「当前章 + 这一页的节」就够——页面长什么样由页面自己算（见
 * [PreferenceOutline]），栏里不另存一份。
 */
class OutlineRail
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val chapterBox: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(UiTokens.color(context, R.attr.ui_paper))
        LayoutInflater.from(context).inflate(R.layout.view_outline_rail, this, true)
        chapterBox = findViewById(R.id.outlineChapters)
    }

    /**
     * @param currentChapter 当前页的章 id（[Outline.chapters] 里的 id）
     * @param sections 当前页的节，位置由 [PreferenceOutline] 算好
     */
    fun bind(
        currentChapter: String,
        sections: List<EditorSection>,
        onChapter: (String) -> Unit,
        onSection: (Int) -> Unit,
    ) {
        chapterBox.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (chapter in Outline.chapters) {
            val active = chapter.id == currentChapter
            val row = inflater.inflate(R.layout.view_outline_chapter, chapterBox, false)
            row.findViewById<View>(R.id.outlineMark).visibility = if (active) View.VISIBLE else View.GONE
            row.findViewById<TextView>(R.id.outlineGlyph).apply {
                text = chapter.glyph
                setTextColor(color(if (active) R.attr.ui_accent else R.attr.ui_ink_2))
                background = if (active) glyphBox() else null
            }
            row.findViewById<TextView>(R.id.outlineTitle).apply {
                text = chapter.title
                setTextColor(color(if (active) R.attr.ui_ink else R.attr.ui_ink_2))
                typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            row.setOnClickListener { onChapter(chapter.id) }
            chapterBox.addView(row)

            if (active) addSections(inflater, sections, onSection)
        }
    }

    private fun addSections(
        inflater: LayoutInflater,
        sections: List<EditorSection>,
        onSection: (Int) -> Unit,
    ) {
        if (sections.isEmpty()) return
        for (section in sections) {
            val row = inflater.inflate(R.layout.view_outline_section, chapterBox, false)
            row.findViewById<TextView>(R.id.outlineSectionTitle).text = section.title
            row.findViewById<TextView>(R.id.outlineSectionCount).apply {
                text = section.count.toString()
                visibility = if (section.count > 0) View.VISIBLE else View.GONE
            }
            row.setOnClickListener { onSection(section.position) }
            chapterBox.addView(row)
        }
    }

    private fun color(id: Int): Int = UiTokens.color(context, id)

    /** 当前章那个汉字外面的一圈朱红细边，和左边缘的竖条一起指认「你在这一章」。 */
    private fun glyphBox(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dp(3).toFloat()
        setColor(0)
        setStroke(context.dp(1), color(R.attr.ui_accent))
    }
}
