/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.color

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ColorGroup
import com.osfans.trime.data.theme.ColorKeyGroups
import com.osfans.trime.data.theme.ColorLiteral
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ColorTable
import com.osfans.trime.data.theme.ThemeSchemeWriter
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.ThemeTextFile
import com.trime.editor.ui.DiskBackedEditor
import com.trime.editor.ui.EditorSection
import com.trime.editor.ui.databinding.FragmentColorSchemeBinding
import com.trime.editor.ui.dp
import com.trime.editor.ui.proof.ProofFormat
import com.trime.editor.ui.skin.UiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.trime.editor.ui.R as UiR

/** What the fragment asks its activity to do with the preview board. */
interface ColorEditorHost {
    /** Re-reads the theme from disk and redraws the board. */
    fun reloadBoard()

    /**
     * A scheme was added, renamed or deleted, and [edit] is the one the screen
     * should be editing now.
     *
     * A deleted scheme cannot stay in the editor, and a freshly created one is
     * what the user wants to look at — so the screen follows the edit rather
     * than leaving the rows pointing at a block that is no longer in the file.
     */
    fun onSchemeChanged(edit: String)

    /** 写盘之后重算样张下面那行统计（未设 / 本次已改）。 */
    fun refreshCaption()

    /** Which half of a day/night pair the board is painting with. */
    fun previewState(): PreviewState

    /** Forces the board into one half, or back onto the system's choice. */
    fun setPreviewMode(mode: NightMode)

    /** 方案胶囊：列出这个主题的所有方案，切到点开的那一个。 */
    fun showSchemePicker()

    /**
     * 页面菜单（切换配色 / Material You / 目录栏）。
     *
     * 菜单项定义在宿主里，fragment 只负责提供锚点：id 在两个模块各有一份 R，
     * 跨模块 inflate 出来的 item 对不上宿主的 `onOptionsItemSelected`。
     */
    fun showPageMenu(anchor: View)
}

/**
 * Which half of a day/night pair the board should show.
 *
 * [SYSTEM] is the real behaviour — the keyboard follows the device — and is
 * what the editor starts on. The two fixed modes exist because editing the
 * light block while the board shows the dark twin looks like a bug otherwise.
 */
enum class NightMode { SYSTEM, LIGHT, DARK }

/** [mode] the board was asked for, and the scheme it actually resolved to. */
data class PreviewState(
    val mode: NightMode,
    val schemeId: String,
)

/**
 * The four segments of the colour screen, and the colour groups each covers.
 *
 * 54 keys under four tabs rather than eight headings: the tabs name what a
 * keyboard is read as — what you type on, what it looks like while held, and
 * the scheme itself — and each one is short enough to keep in view. The
 * mapping is a property of the screen, so a theme that defines its own keys
 * still lands somewhere (see [ColorKeyGroups]).
 */
private enum class Tab(val labelRes: Int, val groups: List<ColorGroup>) {
    CANDIDATE(UiR.string.color_b2_tab_candidate, listOf(ColorGroup.CANDIDATE_BAR)),
    KEYBOARD(
        UiR.string.color_b2_tab_keyboard,
        listOf(ColorGroup.KEYBOARD, ColorGroup.KEY, ColorGroup.FUNCTION_KEY, ColorGroup.POPUP),
    ),
    STATUS(
        UiR.string.color_b2_tab_status,
        listOf(ColorGroup.PRESSED_KEY, ColorGroup.APP_CHROME, ColorGroup.OTHER),
    ),
    SCHEME(UiR.string.color_scheme_group, emptyList()),
}

/** One row's data, resolved the way the runtime would resolve it. */
private class RowData(
    val key: String,
    val label: String,
    /** What the file says — `0xff…`, `*anchor`, an image name, or null. */
    val shown: String?,
    /** The colour it actually paints with, or null when it is not a colour. */
    val resolved: Int?,
    /** `跟随 border_color` when this scheme inherits the key. */
    val anchor: String?,
)

/**
 * Edits the colours of one scheme (direction B · stage).
 *
 * The list of keys follows `preset_color_schemes.default`: that is the set the
 * theme author considers complete, so a scheme that only overrides a few
 * colours still shows every slot and marks the ones it inherits. Keys the
 * scheme defines on its own are appended after the template.
 *
 * One group per screen: the segmented control picks the group, the big swatch
 * is the first key of the group, and the rows below it are the rest. Saving
 * follows the same rules as the style editor — one line changes, the author's
 * comments and line endings survive, and a file that somebody else rewrote is
 * refused rather than clobbered.
 */
class ColorSchemeFragment :
    Fragment(),
    DiskBackedEditor {
    private var viewBinding: FragmentColorSchemeBinding? = null

    private val binding get() = viewBinding!!

    private lateinit var themeId: String
    private lateinit var schemeId: String
    private lateinit var sourceFile: File

    /** The file's contents the rows were built from; the baseline for conflicts. */
    private var expectedText: String = ""

    private val writeLock = Mutex()

    /** What this scheme defines, kept so the dialog can offer its colours. */
    private var schemeColors: Map<String, String> = emptyMap()

    /** Every scheme the file had when the rows were built, in file order. */
    private var knownSchemes: List<ThemeSchemeWriter.Scheme> = emptyList()

    /** The scheme's own values, re-read on every rebuild. */
    private var ownValues: Map<String, String> = emptyMap()

    /** Keys per tab, in rendering order. */
    private var tabKeys: Map<Tab, List<String>> = Tab.entries.associateWith { emptyList() }

    /** How many colour keys the file defines in total (per group count). */
    private var groupCount: Int = 0

    /** 这一次会话里成功写盘的次数，以及本方案没写的色键数（见 [caption]）。 */
    private var changedCount = 0
    private var unsetCount = 0

    private var currentTab: Tab = Tab.CANDIDATE

    private val host: ColorEditorHost? get() = activity as? ColorEditorHost

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewBinding = FragmentColorSchemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        themeId = arguments?.getString(ARG_THEME_ID).orEmpty()
        schemeId = arguments?.getString(ARG_SCHEME_ID).orEmpty()
        val file = ThemeStyleWriter.editableSourceFile(themeId)
        if (file == null || schemeId.isEmpty()) {
            showDisabled()
            return
        }
        sourceFile = file
        binding.schemeChip.setOnClickListener { host?.showSchemePicker() }
        binding.menuButton.setOnClickListener { host?.showPageMenu(it) }
        binding.dockNew.setOnClickListener { showNewSchemeDialog() }
        binding.dockRename.setOnClickListener { showRenameDialog() }
        binding.dockDelete.setOnClickListener {
            // 只剩一个方案时按钮照样在，点了说清为什么不能删——比直接把这一项
            // 藏掉更不容易让人以为界面出了问题。
            if (knownSchemes.size > 1) showDeleteDialog() else toast(UiR.string.color_scheme_last)
        }
        rebuild()
    }

    override fun onDestroyView() {
        viewBinding = null
        super.onDestroyView()
    }

    override fun isFileChanged(): Boolean {
        if (!::sourceFile.isInitialized) return false
        val current = ThemeTextFile.read(sourceFile)?.text ?: return false
        return current != expectedText
    }

    /** 样张下面的读数：还有多少色键没在本方案里写、这次改了几处。 */
    override fun caption(): String = ProofFormat.caption(unsetCount, changedCount)

    /**
     * 目录栏的节 = 四个段控。点节就是换段：这一屏的组织方式是一组一屏，
     * 滚到某一行没有「跳到另一组」来得直接。
     */
    override fun sections(): List<EditorSection> = Tab.entries.map { tab ->
        EditorSection(
            title = getString(tab.labelRes),
            position = tab.ordinal,
            count = if (tab == Tab.SCHEME) knownSchemes.size else tabKeys[tab]?.size ?: 0,
        )
    }

    override fun scrollTo(position: Int) {
        val tab = Tab.entries.getOrNull(position) ?: return
        selectTab(tab)
        viewBinding?.scroll?.scrollTo(0, 0)
    }

    private fun showDisabled() {
        val b = viewBinding ?: return
        b.contentHost.removeAllViews()
        b.contentHost.addView(
            TextView(requireContext()).apply {
                text = getString(UiR.string.style_no_file)
                setPadding(dp(20), dp(20), dp(20), dp(20))
                textSize = 13f
            },
        )
        b.nextGroup.visibility = View.GONE
    }

    /** Rebuilds the whole screen from the file as it is now. */
    private fun rebuild() {
        val b = viewBinding ?: return
        val document = ThemeTextFile.read(sourceFile)
        if (document == null) {
            showDisabled()
            return
        }
        expectedText = document.text

        val templateValues = ThemeSchemeWriter.readScheme(sourceFile, TEMPLATE_SCHEME)
        ownValues = ThemeSchemeWriter.readScheme(sourceFile, schemeId)
        schemeColors = ownValues
        knownSchemes = ThemeSchemeWriter.listSchemes(document.lines)
        val keys =
            templateValues.keys.filter { it.isColorKey() } +
                ownValues.keys.filter { it.isColorKey() && it !in templateValues }

        // Grouped the way a keyboard is read, so a colour is found by what it
        // paints instead of by remembering its YAML name. Groups the theme does
        // not use are dropped rather than shown empty.
        val grouped = ColorKeyGroups.group(keys)
        groupCount = grouped.size
        tabKeys =
            Tab.entries.associateWith { tab ->
                tab.groups.flatMap { group -> grouped.firstOrNull { it.first == group }?.second.orEmpty() }
            }

        // 没写在本方案里的色键数：这些走 fallback 链，行里标「跟随」。
        unsetCount = keys.count { it !in ownValues }

        b.schemeChip.text = displayNameOf(schemeId) + " ▾"
        b.subtitle.text = getString(UiR.string.color_b2_subtitle, groupCount, keys.size)
        renderTab(currentTab)
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        renderTab(tab)
    }

    private fun renderTab(tab: Tab) {
        val b = viewBinding ?: return
        b.contentHost.removeAllViews()
        when (tab) {
            Tab.SCHEME -> b.contentHost.addView(schemeCard())

            else -> {
                val keys = tabKeys[tab].orEmpty()
                if (keys.isEmpty()) {
                    b.contentHost.addView(
                        TextView(requireContext()).apply {
                            text = getString(UiR.string.color_scheme_empty)
                            setPadding(dp(20), dp(20), dp(20), dp(20))
                            textSize = 13f
                        },
                    )
                } else {
                    b.contentHost.addView(swatchBox(keys))
                }
            }
        }
        updateSegButtons()
        updateNextGroup(tab)
    }

    private fun updateSegButtons() {
        val b = viewBinding ?: return
        val views = listOf(b.seg0, b.seg1, b.seg2, b.seg3)
        Tab.entries.forEachIndexed { i, tab ->
            val v = views[i]
            val on = tab == currentTab
            v.setBackgroundResource(if (on) UiR.drawable.bg_badge else UiR.drawable.bg_seg)
            v.setTextColor(uiColor(if (on) UiR.attr.ui_on_primary else UiR.attr.ui_muted))
            v.setOnClickListener { if (!on) selectTab(tab) }
        }
    }

    /**
     * 「下一个组」入口：颜色段一个接一个翻，到「状态」为止。
     *
     * 「方案」不是一组色值，没有「N 项」可说，所以不进这条链——段控上点它
     * 一样到。最后一段后面什么也不显示，稿子也只画了一个入口。
     */
    private fun updateNextGroup(tab: Tab) {
        val b = viewBinding ?: return
        val next = Tab.entries.getOrNull(tab.ordinal + 1)?.takeIf { it != Tab.SCHEME }
        val keys = next?.let { tabKeys[it]?.takeIf { k -> k.isNotEmpty() } }
        if (next == null || keys == null) {
            b.nextGroup.visibility = View.GONE
            return
        }
        b.nextGroup.visibility = View.VISIBLE
        b.nextName.text = getString(UiR.string.color_b2_next_card, getString(next.labelRes), keys.size)
        b.nextFirst.text = getString(UiR.string.color_b2_next_first, keys.first())
        b.nextCard.setOnClickListener { selectTab(next) }
    }

    // ===== 一组的内容：巨型色块 + 同组行 =====

    private fun swatchBox(keys: List<String>): View {
        val box =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTokens.rounded(requireContext(), UiR.attr.ui_radius_card)
            }
        box.layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(16), dp(14), dp(16), 0) }
        box.addView(bigSwatch(rowData(keys.first())))
        keys.drop(1).forEachIndexed { i, key ->
            if (i > 0) box.addView(rowDivider())
            box.addView(colorRow(rowData(key)))
        }
        return box
    }

    /**
     * 巨型色块：这一组的第一个键，色块自身颜色做底。
     *
     * 字色按底色亮度走（深底白字、浅底深字）：稿子写死白字，而白色色块上
     * 的白字等于没有字——54 个键里什么颜色都有，不能赌。
     */
    private fun bigSwatch(data: RowData): View {
        val ink = data.resolved?.let(::inkOn) ?: uiColor(UiR.attr.ui_ink)
        val row =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                setPadding(dp(14), dp(14), dp(14), dp(14))
                data.resolved?.let { background = bigSwatchBackground(it) }
                    ?: setBackgroundColor(uiColor(UiR.attr.ui_card_high))
                setOnClickListener { showColorDialog(data.key, data.shown, data.resolved) }
            }
        row.layoutParams =
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112))
        row.addView(
            TextView(requireContext()).apply {
                text = data.label
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ink)
                maxLines = 2
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            TextView(requireContext()).apply {
                text = buildString {
                    append(data.key)
                    append('\n')
                    append(data.shown ?: ProofFormat.UNSET)
                }
                typeface = Typeface.MONOSPACE
                textSize = 10f
                setTextColor(ink.withAlpha(0.85f))
                gravity = Gravity.END
            },
        )
        return row
    }

    /** 上沿跟卡片同圆角、下沿直角：大块是卡片的第一行，不是压在卡片上的一块贴纸。 */
    private fun bigSwatchBackground(argb: Int): Drawable {
        val r = uiDimen(UiR.attr.ui_radius_card).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // cornerRadii 顺序：左上、右上、右下、左下。只有上面两角跟卡片收圆。
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(argb)
        }
    }

    private fun colorRow(data: RowData): View {
        val row =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { showColorDialog(data.key, data.shown, data.resolved) }
            }
        // 26dp 色块：解析不出颜色（背景图文件名之类）时留空占位，行不错位。
        row.addView(
            View(requireContext()).apply {
                data.resolved?.let { background = UiTokens.swatch(requireContext(), it) }
            },
            LinearLayout.LayoutParams(dp(26), dp(26)),
        )
        row.addView(
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(requireContext()).apply {
                        text = data.label
                        textSize = 12f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(uiColor(UiR.attr.ui_ink))
                    },
                )
                addView(
                    TextView(requireContext()).apply {
                        text = data.key
                        typeface = Typeface.MONOSPACE
                        textSize = 8.5f
                        setTextColor(uiColor(UiR.attr.ui_muted))
                    },
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            },
        )
        row.addView(
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(
                    TextView(requireContext()).apply {
                        text =
                            when {
                                data.resolved != null -> ProofFormat.hex(data.resolved)
                                data.shown == null -> ProofFormat.UNSET
                                else -> data.shown
                            }
                        typeface = Typeface.MONOSPACE
                        textSize = 9.5f
                        setTextColor(uiColor(UiR.attr.ui_muted))
                    },
                )
                data.anchor?.let {
                    addView(
                        TextView(requireContext()).apply {
                            text = it
                            typeface = Typeface.MONOSPACE
                            textSize = 8.5f
                            setTextColor(uiColor(UiR.attr.ui_muted))
                        },
                    )
                }
            },
        )
        return row
    }

    private fun rowDivider(): View = View(requireContext()).apply {
        setBackgroundColor(uiColor(UiR.attr.ui_rule))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    /**
     * 一个色键实际画出来的颜色与行数据。
     *
     * 主题里大量是 `*color2` 这种锚点，文本层面没有颜色，只有 YAML 解析后才有；
     * 解析拿不到就返回 null——宁可不画色块，也不画一个看起来像真的错颜色。
     */
    private fun rowData(key: String): RowData {
        val fallback = ColorManager.currentScope()?.theme?.fallbackColors.orEmpty()
        val own = ownValues[key]
        val source = if (own == null) ColorTable.resolveRawSource(key, ownValues, fallback) else null
        val shown = own ?: source?.second
        val resolved = resolvedColor(key, shown)
        val anchor =
            when {
                own?.startsWith("*") == true -> own
                source != null -> getString(UiR.string.color_scheme_value_inherited, source.first)
                else -> null
            }
        val label = COLOR_LABELS[key]?.let { getString(it) } ?: key
        return RowData(key, label, shown, resolved, anchor)
    }

    private fun resolvedColor(
        key: String,
        shown: String?,
    ): Int? {
        ColorLiteral.parse(shown)?.let { return it }
        val raw = ColorManager.currentScope()?.theme?.presetColorSchemes?.get(schemeId)?.get(key) ?: return null
        return ColorLiteral.parse(raw)
    }

    /**
     * 「方案」段：这一方案是什么、预览板在画哪一半、三件事在哪。
     *
     * 稿子没有这一屏，但「预览明暗」必须有地方说——它解释「为什么行里的色
     * 和上面键盘不一样」，而答案（时钟）不在这一页上。
     */
    private fun schemeCard(): View {
        val host = host
        val state = host?.previewState()
        val card =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = UiTokens.rounded(requireContext(), UiR.attr.ui_radius_card)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
        card.layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(16), dp(14), dp(16), 0) }
        card.addView(
            TextView(requireContext()).apply {
                text = getString(UiR.string.color_b2_scheme_name)
                textSize = 10f
                letterSpacing = 0.08f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(uiColor(UiR.attr.ui_muted))
            },
        )
        card.addView(
            TextView(requireContext()).apply {
                text = displayNameOf(schemeId)
                textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(uiColor(UiR.attr.ui_ink))
            },
        )
        card.addView(
            TextView(requireContext()).apply {
                text = getString(UiR.string.color_b2_scheme_id) + " · " + schemeId
                typeface = Typeface.MONOSPACE
                textSize = 10f
                setTextColor(uiColor(UiR.attr.ui_muted))
            },
        )
        card.addView(
            TextView(requireContext()).apply {
                text = getString(UiR.string.color_b2_scheme_stats, unsetCount, tabKeys.values.sumOf { it.size })
                textSize = 11f
                setTextColor(uiColor(UiR.attr.ui_ink_2))
            },
        )
        card.addView(
            View(requireContext()).apply {
                setBackgroundColor(uiColor(UiR.attr.ui_rule))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(12)
                    bottomMargin = dp(4)
                }
            },
        )
        // 预览明暗：行里的色和上面键盘不一样时，这一行是唯一的解释。
        val previewRow =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener {
                    if (host != null && state != null) showNightModeDialog(host, state.mode)
                }
            }
        previewRow.addView(
            TextView(requireContext()).apply {
                text = getString(UiR.string.color_preview_night)
                textSize = 12f
                setTextColor(uiColor(UiR.attr.ui_ink))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        previewRow.addView(
            TextView(requireContext()).apply {
                text = state?.let { getString(modeLabel(it.mode)) }.orEmpty()
                textSize = 12f
                setTextColor(uiColor(UiR.attr.ui_muted))
            },
        )
        card.addView(previewRow)
        card.addView(
            TextView(requireContext()).apply {
                text = getString(UiR.string.color_b2_scheme_hint)
                textSize = 10f
                setTextColor(uiColor(UiR.attr.ui_muted))
            },
        )
        return card
    }

    private fun modeLabel(mode: NightMode): Int = when (mode) {
        NightMode.SYSTEM -> UiR.string.color_preview_night_system
        NightMode.LIGHT -> UiR.string.color_preview_night_light
        NightMode.DARK -> UiR.string.color_preview_night_dark
    }

    private fun showNightModeDialog(
        host: ColorEditorHost,
        current: NightMode,
    ) {
        val modes = NightMode.entries
        val labels = modes.map { getString(modeLabel(it)) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(UiR.string.color_preview_night_title)
            .setSingleChoiceItems(labels, modes.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                host.setPreviewMode(modes[which])
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The label a scheme shows in the list, falling back to its id. */
    private fun displayNameOf(id: String): String = knownSchemes.firstOrNull { it.id == id }?.displayName ?: id

    // ===== 方案管理：新建 / 重命名 / 删除（写盘逻辑与原版一致） =====

    /**
     * A new block starting from the one being edited.
     *
     * The id is the YAML key other settings reference, so it is asked for
     * separately from the display name and only accepts what
     * [ThemeSchemeWriter] can read back. The copy keeps the colours this scheme
     * spells out and drops its `light_scheme` / `dark_scheme` links: pointing
     * the copy at the same twin would leave two schemes claiming one partner,
     * and deleting either would strand the other.
     */
    private fun showNewSchemeDialog() {
        val context = requireContext()
        val taken = knownSchemes.mapTo(HashSet()) { it.id }
        val idField =
            textField(context, uniqueId(schemeId, taken)).apply {
                // Restricting the keys stops an IME from turning an id into
                // candidates: what lands here has to survive a round trip
                // through the theme file.
                keyListener = DigitsKeyListener.getInstance(ID_CHARS)
            }
        val nameField =
            textField(context, getString(UiR.string.color_scheme_new_name_copied, displayNameOf(schemeId)))
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(UiR.string.color_scheme_new_title)
                .setView(
                    dialogBody(
                        context,
                        labelledField(context, getString(UiR.string.color_scheme_new_id), idField),
                        labelledField(context, getString(UiR.string.color_scheme_new_name), nameField),
                    ),
                )
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    createScheme(idField.text.toString().trim(), nameField.text.toString().trim(), taken)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        focusOnShow(dialog, idField)
        dialog.show()
    }

    private fun createScheme(
        id: String,
        name: String,
        taken: Set<String>,
    ) {
        if (!SCHEME_ID.matches(id)) {
            toast(UiR.string.color_scheme_new_id_invalid)
            return
        }
        if (id in taken) {
            toast(UiR.string.color_scheme_new_id_taken)
            return
        }
        if (name.isEmpty()) {
            toast(UiR.string.color_scheme_name_empty)
            return
        }
        val entries = LinkedHashMap<String, String>()
        entries["name"] = name
        schemeColors.forEach { (key, value) ->
            // `author` stays behind on purpose: the copy is the user's own
            // scheme now, and carrying someone else's address into a file they
            // may share would credit them with edits they never made.
            if (key !in METADATA_KEYS && value.isNotBlank()) entries[key] = value
        }
        commit(
            write = { baseline ->
                ThemeSchemeWriter.setSchemesChecked(
                    file = sourceFile,
                    schemes = mapOf(id to entries),
                    create = true,
                    expectedText = baseline,
                )
            },
            onWritten = {
                toast(getString(UiR.string.color_scheme_created, name))
                host?.onSchemeChanged(id)
            },
        )
    }

    /**
     * Renames the display label. The id is what `set_color_scheme` and the
     * day/night links point at, so it stays as it is — renaming it would break
     * settings the editor cannot see.
     */
    private fun showRenameDialog() {
        val context = requireContext()
        val field = textField(context, displayNameOf(schemeId))
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(UiR.string.color_scheme_rename_title)
                .setView(dialogBody(context, labelledField(context, getString(UiR.string.color_scheme_new_name), field)))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val name = field.text.toString().trim()
                    if (name.isEmpty()) {
                        toast(UiR.string.color_scheme_name_empty)
                    } else {
                        rename(name)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        focusOnShow(dialog, field)
        dialog.show()
    }

    /**
     * Puts the cursor in [field] and brings the keyboard up once the dialog is
     * on screen.
     *
     * A rename or a new id is typed, not read: opening with nothing focused
     * costs a tap and leaves the keypresses going nowhere until the user makes
     * it. The listener is what makes it stick — the window only exists after
     * the dialog is attached.
     */
    private fun focusOnShow(
        dialog: AlertDialog,
        field: EditText,
    ) {
        dialog.setOnShowListener {
            field.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private fun rename(name: String) = commit(
        write = { baseline -> ThemeSchemeWriter.renameSchemeChecked(sourceFile, schemeId, name, baseline) },
        onWritten = {
            toast(UiR.string.color_scheme_renamed)
            rebuild()
            host?.onSchemeChanged(schemeId)
        },
    )

    /**
     * Deleting asks the file who points at this scheme first.
     *
     * A `dark_scheme: steam` left behind by a deletion is not an error the user
     * will ever see: the keyboard simply stops switching at night. Naming the
     * schemes that would be stranded is the only warning they get.
     */
    private fun showDeleteDialog() {
        val referencing = ThemeSchemeWriter.referencingSchemes(sourceFile, schemeId)
        val note =
            if (referencing.isEmpty()) {
                getString(UiR.string.color_scheme_delete_safe)
            } else {
                getString(UiR.string.color_scheme_referenced, referencing.joinToString("、"))
            }
        val name = displayNameOf(schemeId)
        AlertDialog.Builder(requireContext())
            .setTitle(UiR.string.color_scheme_delete)
            .setMessage(getString(UiR.string.color_scheme_delete_confirm, name) + "\n\n" + note)
            .setPositiveButton(UiR.string.color_scheme_delete) { _, _ -> delete() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delete() = commit(
        write = { baseline -> ThemeSchemeWriter.deleteSchemeChecked(sourceFile, schemeId, baseline) },
        onWritten = {
            toast(UiR.string.color_scheme_deleted)
            val next = ThemeSchemeWriter.listSchemes(sourceFile).firstOrNull()?.id
            host?.onSchemeChanged(next ?: TEMPLATE_SCHEME)
        },
        onFailed = { toast(UiR.string.color_scheme_last) },
    )

    /**
     * One text field, sized for a dialog.
     *
     * [InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS] on purpose: an id and a hex
     * value are not words, and a predictive keyboard rewriting either of them
     * is how a user ends up with a scheme nothing can read.
     */
    private fun textField(
        context: Context,
        value: String,
    ): EditText = EditText(context).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSelection(text.length)
    }

    /**
     * [field] under a caption.
     *
     * The caption is a real label rather than the field's hint: both fields
     * arrive pre-filled, and a hint disappears as soon as there is text —
     * leaving the user unable to tell the id from the display name.
     */
    private fun labelledField(
        context: Context,
        caption: String,
        field: EditText,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = caption
                textSize = 12f
                setPadding(0, context.dp(10), 0, 0)
            },
        )
        addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun dialogBody(
        context: Context,
        vararg fields: View,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val horizontal = context.dp(20)
        setPadding(horizontal, context.dp(4), horizontal, 0)
        fields.forEach {
            addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /**
     * A free id close to [base]: `<base>_copy`, then `<base>_copy2` and so on.
     *
     * Prefilled rather than left blank so the common case is one tap, and
     * stripped to what the file can hold so a Chinese display name never
     * becomes an id the writer cannot find again.
     */
    private fun uniqueId(
        base: String,
        taken: Set<String>,
    ): String {
        val root = base.replace(NOT_IN_ID, "_").ifEmpty { "scheme" }
        var candidate = "${root}_copy"
        var n = 2
        while (candidate in taken) {
            candidate = "${root}_copy$n"
            n++
        }
        return candidate
    }

    // ===== 改一个色值（写盘逻辑与原版一致） =====

    /**
     * The colour dialog: a picker for the common case, with the text field kept
     * for exact values and for the keys that accept a background image file name
     * instead of a colour.
     *
     * Dragging the picker rewrites the field and typing moves the picker, so the
     * two never disagree. [syncing] breaks the loop their listeners would
     * otherwise form.
     */
    private fun showColorDialog(
        key: String,
        current: String?,
        resolved: Int? = null,
    ) {
        val context = requireContext()
        // 锚点（`*color2`）在文本里没有颜色：输入框保留原文，取色器和「改前」
        // 色块用解析出来的那个色，否则一点开就是一片白，跟行里显示的色块打架。
        val original = ColorLiteral.parse(current) ?: resolved ?: Color.WHITE

        val picker =
            ColorPickerView(context).apply {
                setColor(original)
            }
        val input =
            EditText(context).apply {
                setText(current.orEmpty())
                hint = getString(UiR.string.color_scheme_edit_hint)
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSelection(text.length)
            }

        var syncing = false
        val newSwatch = View(context)

        fun showNew(argb: Int) {
            newSwatch.background = context.checkerSwatch(argb)
        }

        showNew(picker.color)

        picker.onColorChanged = { argb ->
            showNew(argb)
            if (!syncing) {
                syncing = true
                input.setText(ColorLiteral.format(argb, current))
                input.setSelection(input.text.length)
                syncing = false
            }
        }
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (syncing) return
                    val parsed = ColorLiteral.parse(s?.toString()) ?: return
                    syncing = true
                    picker.setColor(parsed)
                    showNew(parsed)
                    syncing = false
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        val swatchHeight = context.dp(28)
        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val padding = context.dp(20)
                setPadding(padding, context.dp(12), padding, 0)
                addView(
                    buildComparisonRow(context, original, newSwatch, swatchHeight),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                addView(
                    picker,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) },
                )
                addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(buildUsedColorsRow(context, input, current), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

        AlertDialog.Builder(context)
            .setTitle(labelWithKey(key))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input.text.toString().trim()
                // 没改动就不写。这一点在锚点上尤其要紧：`*color2` 解析不成颜色，
                // 旧代码会先弹「颜色无效」；而如果放行，一按确定就把它写成字面量，
                // 主题里那套调色板链接从此断了，文件里再也看不出它原来指向谁。
                if (raw == current?.trim()) return@setPositiveButton
                if (ColorLiteral.parse(raw) == null) {
                    toast(UiR.string.color_scheme_color_invalid)
                    return@setPositiveButton
                }
                save(key, raw)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Original on the left, the picked colour on the right. */
    private fun buildComparisonRow(
        context: Context,
        original: Int,
        newSwatch: View,
        swatchHeight: Int,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
            labelledSwatch(context, getString(UiR.string.color_scheme_picker_original), newSwatch, swatchHeight, original),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) },
        )
        addView(
            labelledSwatch(context, getString(UiR.string.color_scheme_picker_new), newSwatch, swatchHeight, null),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun labelledSwatch(
        context: Context,
        caption: String,
        swatch: View,
        swatchHeight: Int,
        original: Int?,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = caption
                textSize = 12f
            },
        )
        if (original != null) {
            // The original is a separate, static swatch; only the new one is
            // reused by the picker callbacks.
            addView(
                View(context).apply { background = context.checkerSwatch(original) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, swatchHeight),
            )
        } else {
            addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, swatchHeight))
        }
    }

    /**
     * Swatches of the colours this scheme already uses, for pulling two keys
     * onto the same value without transcribing a hex string from another row.
     */
    private fun buildUsedColorsRow(
        context: Context,
        input: EditText,
        like: String?,
    ): View {
        val used = schemeColors.values.mapNotNull { ColorLiteral.parse(it) }.distinct()
        if (used.isEmpty()) return View(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(10), 0, 0)
            addView(
                TextView(context).apply {
                    text = getString(UiR.string.color_scheme_picker_used)
                    textSize = 12f
                },
            )
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            used.forEach { argb ->
                                addView(
                                    View(context).apply {
                                        background = context.checkerSwatch(argb)
                                        // Typing it into the field is enough: the
                                        // watcher moves the picker and the swatch.
                                        setOnClickListener { input.setText(ColorLiteral.format(argb, like)) }
                                    },
                                    LinearLayout.LayoutParams(context.dp(36), context.dp(28)).apply {
                                        marginEnd = context.dp(6)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    /**
     * Writes one colour, then refreshes the list and the preview.
     */
    private fun save(
        key: String,
        value: String,
    ) = commit(
        write = { baseline -> ThemeSchemeWriter.setColorChecked(sourceFile, schemeId, key, value, baseline) },
        onWritten = {
            rebuild()
            host?.reloadBoard()
            toast(UiR.string.color_scheme_color_saved)
        },
    )

    /**
     * Runs one write and answers its three outcomes the same way everywhere.
     *
     * [write] is handed the baseline to compare against: [expectedText] makes
     * the writer refuse a file somebody else rewrote, `null` accepts whatever
     * is on disk now — which is exactly the retry after a conflict. The writes
     * are serialised for the reason the style editor serialises them: two saves
     * racing would make the second one's conflict check look at the first one's
     * result, and a scheme regenerated in two passes could land half-written.
     */
    private fun commit(
        write: (baseline: String?) -> ThemeSchemeWriter.SaveResult,
        onWritten: () -> Unit,
        onFailed: () -> Unit = { toast(UiR.string.style_write_failed) },
    ) {
        fun attempt(baseline: String?) {
            viewLifecycleOwner.lifecycleScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        writeLock.withLock { write(baseline) }
                    }
                if (!isAdded || view == null) return@launch
                when (result.outcome) {
                    // The text the writer produced becomes the baseline for the
                    // next save, so a run of edits never reports a false conflict.
                    ThemeTextFile.WriteOutcome.WRITTEN -> {
                        expectedText = result.text.orEmpty()
                        // 「本次已改」数的是这一次会话里写成功的次数：它只回答
                        // 「我刚动过几处」，不参与任何写盘判断。
                        changedCount++
                        onWritten()
                        host?.refreshCaption()
                    }

                    ThemeTextFile.WriteOutcome.CONFLICT -> showConflictDialog { attempt(null) }

                    ThemeTextFile.WriteOutcome.FAILED -> onFailed()
                }
            }
        }
        attempt(expectedText.takeIf { it.isNotEmpty() })
    }

    /**
     * The file changed after this screen read it.
     *
     * Both answers are offered: read the file again (losing this one edit) or
     * replace it (losing whatever the other writer did). Choosing silently
     * would leave the rows showing colours that are no longer on disk.
     */
    private fun showConflictDialog(retry: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(UiR.string.style_conflict_title)
            .setMessage(getString(UiR.string.style_conflict_body, sourceFile.name))
            .setPositiveButton(UiR.string.style_conflict_overwrite) { _, _ -> retry() }
            .setNeutralButton(UiR.string.style_conflict_reload) { _, _ -> reloadFromDisk() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun reloadFromDisk() {
        rebuild()
        host?.reloadBoard()
        toast(UiR.string.style_reload_done)
    }

    /** `候选区背景 · back_color`, or the bare key when it has no label. */
    private fun labelWithKey(key: String): String {
        val label = COLOR_LABELS[key]?.let { getString(it) } ?: return key
        return "$label · $key"
    }

    /** `name` / `author` are metadata, not colours. */
    private fun String.isColorKey(): Boolean = this != "name" && this != "author" && this != "light_scheme" && this != "dark_scheme"

    // ===== 小工具 =====

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    @ColorInt
    private fun uiColor(
        @AttrRes attr: Int,
    ): Int = UiTokens.color(requireContext(), attr)

    private fun uiDimen(
        @AttrRes attr: Int,
    ): Int = UiTokens.dimen(requireContext(), attr)

    /** 大色块上的字色：深底白字、浅底深字（minSdk 21 没有 Color.luminance）。 */
    private fun inkOn(argb: Int): Int = if (luminance(argb) > 0.55f) INK_ON_LIGHT else INK_ON_DARK

    private fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun Int.withAlpha(fraction: Float): Int = (this and 0x00FFFFFF) or (((0xFF * fraction).toInt().coerceIn(0, 0xFF)) shl 24)

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    private fun toast(text: CharSequence) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_THEME_ID = "theme_id"
        private const val ARG_SCHEME_ID = "scheme_id"

        /** What [ThemeSchemeWriter] can read back as a scheme id. */
        private val SCHEME_ID = Regex("""^[A-Za-z0-9_.\-]+$""")

        /** Everything [SCHEME_ID] accepts, and nothing else. */
        private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-"

        /** Characters [SCHEME_ID] would reject, for turning a name into an id. */
        private val NOT_IN_ID = Regex("""[^A-Za-z0-9_.\-]""")

        /** Entries that describe a scheme rather than paint with it. */
        private val METADATA_KEYS = setOf("name", "author", "light_scheme", "dark_scheme")

        /** The scheme whose key set defines the complete list. */
        const val TEMPLATE_SCHEME = "default"

        /** 大色块上的深字（浅底色）与白字（深底色）。 */
        private val INK_ON_LIGHT = 0xFF231917.toInt()
        private val INK_ON_DARK = 0xFFFFFFFF.toInt()

        /** Human-readable names for the colour keys a theme uses. */
        val COLOR_LABELS: Map<String, Int> =
            mapOf(
                "back_color" to UiR.string.color_label_back_color,
                "border_color" to UiR.string.color_label_border_color,
                "candidate_background" to UiR.string.color_label_candidate_background,
                "candidate_border_color" to UiR.string.color_label_candidate_border_color,
                "candidate_separator_color" to UiR.string.color_label_candidate_separator_color,
                "candidate_text_color" to UiR.string.color_label_candidate_text_color,
                "comment_text_color" to UiR.string.color_label_comment_text_color,
                "hilited_back_color" to UiR.string.color_label_hilited_back_color,
                "hilited_candidate_back_color" to UiR.string.color_label_hilited_candidate_back_color,
                "hilited_candidate_button_color" to UiR.string.color_label_hilited_candidate_button_color,
                "hilited_candidate_text_color" to UiR.string.color_label_hilited_candidate_text_color,
                "hilited_comment_text_color" to UiR.string.color_label_hilited_comment_text_color,
                "hilited_key_back_color" to UiR.string.color_label_hilited_key_back_color,
                "hilited_key_border_color" to UiR.string.color_label_hilited_key_border_color,
                "hilited_key_symbol_color" to UiR.string.color_label_hilited_key_symbol_color,
                "hilited_key_text_color" to UiR.string.color_label_hilited_key_text_color,
                "hilited_label_color" to UiR.string.color_label_hilited_label_color,
                "hilited_off_key_back_color" to UiR.string.color_label_hilited_off_key_back_color,
                "hilited_off_key_border_color" to UiR.string.color_label_hilited_off_key_border_color,
                "hilited_off_key_symbol_color" to UiR.string.color_label_hilited_off_key_symbol_color,
                "hilited_off_key_text_color" to UiR.string.color_label_hilited_off_key_text_color,
                "hilited_on_key_back_color" to UiR.string.color_label_hilited_on_key_back_color,
                "hilited_on_key_border_color" to UiR.string.color_label_hilited_on_key_border_color,
                "hilited_on_key_symbol_color" to UiR.string.color_label_hilited_on_key_symbol_color,
                "hilited_on_key_text_color" to UiR.string.color_label_hilited_on_key_text_color,
                "hilited_popup_back_color" to UiR.string.color_label_hilited_popup_back_color,
                "hilited_popup_text_color" to UiR.string.color_label_hilited_popup_text_color,
                "hilited_text_color" to UiR.string.color_label_hilited_text_color,
                "key_back_color" to UiR.string.color_label_key_back_color,
                "key_border_color" to UiR.string.color_label_key_border_color,
                "key_symbol_color" to UiR.string.color_label_key_symbol_color,
                "key_text_color" to UiR.string.color_label_key_text_color,
                "keyboard_back_color" to UiR.string.color_label_keyboard_back_color,
                "keyboard_background" to UiR.string.color_label_keyboard_background,
                "label_color" to UiR.string.color_label_label_color,
                "liquid_keyboard_background" to UiR.string.color_label_liquid_keyboard_background,
                "long_text_back_color" to UiR.string.color_label_long_text_back_color,
                "long_text_color" to UiR.string.color_label_long_text_color,
                "navbar_background" to UiR.string.color_label_navbar_background,
                "normal_mode_color" to UiR.string.color_label_normal_mode_color,
                "off_key_back_color" to UiR.string.color_label_off_key_back_color,
                "off_key_border_color" to UiR.string.color_label_off_key_border_color,
                "off_key_symbol_color" to UiR.string.color_label_off_key_symbol_color,
                "off_key_text_color" to UiR.string.color_label_off_key_text_color,
                "on_key_back_color" to UiR.string.color_label_on_key_back_color,
                "on_key_border_color" to UiR.string.color_label_on_key_border_color,
                "on_key_symbol_color" to UiR.string.color_label_on_key_symbol_color,
                "on_key_text_color" to UiR.string.color_label_on_key_text_color,
                "popup_back_color" to UiR.string.color_label_popup_back_color,
                "popup_text_color" to UiR.string.color_label_popup_text_color,
                "root_background" to UiR.string.color_label_root_background,
                "shadow_color" to UiR.string.color_label_shadow_color,
                "text_back_color" to UiR.string.color_label_text_back_color,
                "text_color" to UiR.string.color_label_text_color,
            )

        fun newInstance(
            themeId: String,
            schemeId: String,
        ): ColorSchemeFragment = ColorSchemeFragment().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_THEME_ID, themeId)
                    putString(ARG_SCHEME_ID, schemeId)
                }
        }
    }
}
