/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui

/**
 * A form built from a theme file that another app can change underneath it.
 *
 * The keyboard and the editor are separate apps editing the same file, so
 * "the file moved" is the normal case, not an error. A form that quietly kept
 * its old rows would then write over the other app's change on the next tap —
 * which the writer would report as a conflict, leaving the user with a dialog
 * and no idea what happened.
 *
 * So each form answers two questions: whether the file still holds the text it
 * was built from, and how to start over from the file as it is now. The screen
 * that hosts the form shows the banner and calls [reloadFromDisk] when tapped;
 * nothing reloads on its own, because a form that rebuilds while the user is
 * mid-edit loses the edit.
 */
interface DiskBackedEditor {
    /** Whether the file on disk no longer holds the text this form was built from. */
    fun isFileChanged(): Boolean

    /** Rebuilds the form, and its conflict baseline, from the file as it is now. */
    fun reloadFromDisk()

    /**
     * The sections this form is divided into, in list order, with their positions.
     *
     * The rail renders exactly this — it does not keep its own list of headings.
     * A second copy would drift the first time a group is added or reordered,
     * and the failure mode is a jump that lands somewhere plausible.
     */
    fun sections(): List<EditorSection> = emptyList()

    /**
     * 跳到某一节。默认什么都不做：Preference 系的表单由宿主直接滚 RecyclerView
     * （见 ThemeEditorActivity.scrollToSection），自绘的屏（如配色页「一组一屏」）
     * 自己解释 position——对它们来说，「跳到这一组」比「滚到某一行」更直接。
     */
    fun scrollTo(position: Int) {}

    /**
     * 样张下面那行统计，例如「未设 8 · 本次已改 3」；空串表示这一页没有统计可说，
     * 界面会把整行收起来。
     */
    fun caption(): String = ""
}
