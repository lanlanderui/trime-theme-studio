/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.proof

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.trime.editor.ui.R
import com.trime.editor.ui.skin.UiTokens

/** PreferenceViewHolder.findViewById 不带泛型，四次强制转换不如收成一处。 */
private fun PreferenceViewHolder.textView(id: Int): TextView? = findViewById(id) as? TextView

private fun PreferenceViewHolder.view(id: Int): View? = findViewById(id)

/**
 * 四列行：中文名 / 键名＋锚点 / 色块 / 十六进制。
 *
 * 它仍然是 [Preference]，不是自绘 View：配色页 980 行里的读写、弹层、方案切换
 * 都挂在 Preference 的回调上，换一套控件等于把这些重写一遍。换的只有布局与
 * 绑定——这正是这一版 UI 改动的边界。
 */
class ProofRow
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var proofName: CharSequence = ""
    var proofKey: CharSequence = ""
    var proofAnchor: CharSequence? = null
    var swatchColor: Int? = null
    var hex: CharSequence = ""

    init {
        layoutResource = R.layout.pref_proof_row
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.textView(R.id.proofName)?.text = proofName
        holder.textView(R.id.proofKey)?.text = proofKey
        holder.textView(R.id.proofAnchor)?.apply {
            text = proofAnchor ?: ""
            visibility = if (proofAnchor.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        val swatch = holder.view(R.id.proofSwatch)
        val color = swatchColor
        if (swatch != null) {
            if (color == null) {
                swatch.visibility = View.INVISIBLE
            } else {
                swatch.visibility = View.VISIBLE
                swatch.background = ProofTokens.swatch(context, color)
            }
        }
        holder.textView(R.id.proofHex)?.text = hex
    }
}

/**
 * 单值行：左中文名＋键名，右一个等宽值。
 *
 * 「未设置」比空字符串诚实——空字符串在这里既可能是没写、也可能是写了空，
 * 在行里看不出区别。
 */
class ProofRowValue
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var proofName: CharSequence = ""
    var proofKey: CharSequence = ""
    var value: CharSequence = ""
    var unset: Boolean = false

    /** 有些行不是「某个键的值」，而是对一个动作的说明（撤销、换配色）：没有键可写。 */
    var showKey: Boolean = true

    init {
        layoutResource = R.layout.pref_proof_value
    }

    fun setValue(
        text: CharSequence,
        isUnset: Boolean = false,
    ) {
        value = text
        unset = isUnset
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.textView(R.id.proofName)?.text = proofName
        holder.textView(R.id.proofKey)?.apply {
            text = proofKey
            visibility = if (showKey) View.VISIBLE else View.GONE
        }
        holder.textView(R.id.proofValue)?.apply {
            text = if (unset) ProofFormat.UNSET else value
            setTextColor(
                UiTokens.color(
                    context,
                    if (unset) R.attr.ui_muted else R.attr.ui_ink,
                ),
            )
        }
    }
}

/**
 * 开关行。
 *
 * 用普通 [Preference] + [SwitchCompat]，不用 `SwitchPreferenceCompat`：后者的
 * 绑定认死了 androidx 内部的 switchWidget id，换布局就等于跟它的内部实现签
 * 合同。开关状态由页面通过 [setChecked] 给进来、通过 `callChangeListener`
 * 交回去——写盘仍然在页面那一侧，这里不碰文件。
 */
class ProofRowSwitch
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {
    var proofName: CharSequence = ""
    var proofKey: CharSequence = ""

    var isChecked: Boolean = false
        private set

    init {
        layoutResource = R.layout.pref_proof_switch
    }

    fun setChecked(checked: Boolean) {
        if (isChecked == checked) return
        isChecked = checked
        notifyChanged()
    }

    override fun onClick() {
        if (callChangeListener(!isChecked)) setChecked(!isChecked)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.textView(R.id.proofName)?.text = proofName
        holder.textView(R.id.proofKey)?.text = proofKey

        val switch = holder.view(R.id.proofSwitch) as? SwitchCompat ?: return
        switch.trackTintList = ProofTokens.switchTrack(context)
        switch.thumbTintList = ProofTokens.switchThumb(context)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = isChecked
        switch.setOnCheckedChangeListener { _, checked ->
            when {
                checked == isChecked -> Unit

                callChangeListener(checked) -> isChecked = checked

                // 监听器说不行就拨回去，否则界面与文件从此各说一套。
                else -> switch.isChecked = isChecked
            }
        }
    }
}

/**
 * 章节头：衬线组名 +「11 项 / 已改 2」。
 *
 * 仍然用 [PreferenceCategory] 是为了让「属于这一章」在 Preference 树里成立——
 * 将来说要折叠、要按章跳转，靠的是这层结构，不是视觉上的分组。
 */
class ProofChapter
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PreferenceCategory(context, attrs) {
    var extra: CharSequence = ""

    init {
        layoutResource = R.layout.pref_proof_chapter
    }

    fun setCount(
        count: Int,
        changed: Int = 0,
    ) {
        extra = ProofFormat.count(count, changed)
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.textView(R.id.proofChapterTitle)?.text = title
        holder.textView(R.id.proofChapterExtra)?.text = extra
    }
}
