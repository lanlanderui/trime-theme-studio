/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

/**
 * 目录栏的模型：章（九个页面）、节（当前页的分组），以及「点一节该滚到第几行」。
 *
 * 位置映射单独拿出来算，是因为它是这一版最容易错、也最容易假装对的地方：
 * `PreferenceGroupAdapter` 的 position 只数**可见**项，而章头本身也占一个位置。
 * 一旦某一行 `isVisible = false`，后面所有节就整体错一格——点「按键」会落到
 * 「键盘背景」的最后一行，画面看着「有反应」，其实是错位。
 *
 * 所以这里不碰 Android，纯列表进、纯 map 出，错位由单测拦（见 OutlineTest）。
 */
object Outline {
    data class Chapter(
        val id: String,
        /** 目录栏左侧那一个汉字。九个不重复，扫一眼就知道在第几章。 */
        val glyph: String,
        val title: String,
    )

    /** 一行偏好设置，只保留定位需要的两件事：它是不是章头、它可不可见。 */
    data class Row(
        val chapter: String? = null,
        val visible: Boolean = true,
    )

    val chapters: List<Chapter> =
        listOf(
            Chapter("workbench", "台", "工作台"),
            Chapter("preview", "览", "预览"),
            Chapter("style", "式", "样式"),
            Chapter("colors", "色", "配色"),
            Chapter("layout", "布", "布局"),
            Chapter("keys", "动", "动作"),
            Chapter("toolbar", "条", "工具条"),
            Chapter("window", "窗", "候选窗"),
            Chapter("diagnostics", "查", "检查"),
        )

    /**
     * 每章的节名。空列表表示这一章不展开——工作台与预览本来就没有分组，
     * 布局的五十个键盘按 D9 放到主区顶部下拉，不进目录栏。
     */
    private val sections: Map<String, List<String>> =
        mapOf(
            "style" to listOf("文字", "尺寸", "间距", "其他"),
            "colors" to
                listOf(
                    "候选栏与编码区",
                    "键盘背景",
                    "按键",
                    "功能键（OFF / ON）",
                    "按键按下",
                    "气泡与长标签",
                    "界面",
                    "本主题自定义",
                ),
            "keys" to listOf("导航", "编辑", "符号", "输入法", "其他"),
            "toolbar" to listOf("示意条", "主按钮", "buttons", "间距与字体"),
            "window" to listOf("window", "preedit"),
            "diagnostics" to listOf("配色", "样式", "动作", "布局", "工具条"),
        )

    fun sectionsOf(chapterId: String): List<String> = sections[chapterId].orEmpty()

    /**
     * 章标题 → adapter position。
     *
     * 只数可见行；同名章只认第一次出现（重复的章头多半是同一段被加了两次，
     * 后一次的位置对用户没有意义，但错用它就会跳到别的地方）。
     */
    fun chapterPositions(rows: List<Row>): Map<String, Int> {
        val positions = LinkedHashMap<String, Int>()
        var position = 0
        for (row in rows) {
            if (!row.visible) continue
            val chapter = row.chapter
            if (chapter != null && !positions.containsKey(chapter)) {
                positions[chapter] = position
            }
            position++
        }
        return positions
    }

    /** 找不到就回到顶部：跳错地方比不跳更让人迷惑。 */
    fun positionOf(
        rows: List<Row>,
        chapter: String,
    ): Int = chapterPositions(rows)[chapter] ?: 0
}
