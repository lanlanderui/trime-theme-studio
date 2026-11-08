/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.trime.editor.ui.EditorSection

/**
 * 从真实的偏好树里读出章节、位置和项数。
 *
 * 目录栏的节名**不另抄一份清单**。抄一份就会跟页面分家：谁往页面里加一组、
 * 或者调一下顺序，目录还指着老地方，点下去只是「有动作」，落点却是错的。
 * 这里直接走 [PreferenceScreen]——页面长什么样，目录就是什么样。
 *
 * 数位置的方式必须和 `PreferenceGroupAdapter` 一致：深度优先，每个可见项
 * （章头也算一项）占一个位置，隐藏项不占。
 */
object PreferenceOutline {
    fun sectionsOf(screen: PreferenceScreen?): List<EditorSection> {
        if (screen == null) return emptyList()
        val starts = ArrayList<Pair<String, Int>>()
        val counter = intArrayOf(0)
        walk(screen, counter, starts)
        val total = counter[0]
        return starts.mapIndexed { index, (title, position) ->
            // 一节到下一节之间隔着的行，就是这一节的项数；最后一段到列表末尾。
            val end = starts.getOrNull(index + 1)?.second ?: total
            EditorSection(title = title, position = position, count = (end - position - 1).coerceAtLeast(0))
        }
    }

    private fun walk(
        group: PreferenceGroup,
        counter: IntArray,
        out: MutableList<Pair<String, Int>>,
    ) {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index) ?: continue
            if (!preference.isVisible) continue
            if (preference is PreferenceCategory) {
                out += (preference.title?.toString().orEmpty()) to counter[0]
            }
            counter[0]++
            // 章头下面还挂着行：它们同样是列表里的项，位置要接着数。
            if (preference is PreferenceGroup) walk(preference, counter, out)
        }
    }
}
