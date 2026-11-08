/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui

/**
 * 目录栏里的一个节：标题、位置、这一节有几项。
 *
 * 位置是「可见项」的序号，和 `PreferenceGroupAdapter` 数法一致；点它时用
 * `scrollToPositionWithOffset(position, 0)`。位置算错的表现很讨厌——画面
 * 会动，但停在别处，看着像「点了没反应」的反面：像点了，其实错了。
 */
data class EditorSection(
    val title: String,
    val position: Int,
    val count: Int = 0,
)
