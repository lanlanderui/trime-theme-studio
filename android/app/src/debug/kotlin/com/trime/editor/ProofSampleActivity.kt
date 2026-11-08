/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.proof.ProofChapter
import com.trime.editor.ui.proof.ProofFormat
import com.trime.editor.ui.proof.ProofRow
import com.trime.editor.ui.proof.ProofRowSwitch
import com.trime.editor.ui.proof.ProofRowValue
import com.trime.editor.ui.skin.UiTokens
import com.trime.editor.ui.R as UiR

/**
 * 设计地基的样张页：四种行 + 章节头，用真值渲染。
 *
 * 只在调试包里。存在的理由很直接——「纸感」是一堆字号、行距、描边的组合，
 * 靠读代码判断不了；要拿真机截图跟 `doc/ui-proof/spec.html` 逐项对。等 P3/P4
 * 把行接进真页面之后，这里还可以留着当「组件改了先看一眼」的地方。
 */
class ProofSampleActivity : EditorActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(android.R.id.content)?.applySystemBarInsets()
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, ProofSampleFragment())
            .commit()
    }
}

class ProofSampleFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)
        setPreferenceScreen(screen)

        screen.addPreference(
            ProofChapter(context).apply {
                title = "候选栏与编码区"
                setCount(11, 2)
            },
        )
        listOf(
            Triple("候选区背景", "back_color", 0xFFFFFFFF.toInt()),
            Triple("候选文字", "candidate_text_color", 0xFF000000.toInt()),
            Triple("编码提示文字", "comment_text_color", 0xFFE1E3E7.toInt()),
            Triple("高亮候选背景", "hilited_candidate_back_color", 0xFF3266A0.toInt()),
        ).forEach { (name, key, color) ->
            screen.addPreference(
                ProofRow(context).apply {
                    proofName = name
                    proofKey = key
                    proofAnchor = "*color2"
                    swatchColor = color
                    hex = ProofFormat.hex(String.format("#%08X", color))
                },
            )
        }

        screen.addPreference(
            ProofChapter(context).apply {
                title = "尺寸"
                setCount(4)
            },
        )
        screen.addPreference(
            ProofRowValue(context).apply {
                proofName = "键高"
                proofKey = "key_height"
                setValue("引用锚点 jpgd4（48）")
            },
        )
        screen.addPreference(
            ProofRowValue(context).apply {
                proofName = "编码区高度"
                proofKey = "comment_height"
                setValue("", isUnset = true)
            },
        )
        screen.addPreference(
            ProofRowSwitch(context).apply {
                proofName = "自动句首大写"
                proofKey = "auto_caps"
                setChecked(true)
            },
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(UiTokens.color(requireContext(), UiR.attr.ui_paper))
    }
}
