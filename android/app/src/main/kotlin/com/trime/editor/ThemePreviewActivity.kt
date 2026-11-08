/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadError
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.trime.editor.databinding.ActivityThemePreviewBinding
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.preview.ThemeBoard
import com.trime.editor.ui.skin.UiTokens
import kotlinx.coroutines.launch
import timber.log.Timber
import com.trime.editor.ui.R as UiR

/**
 * A theme, drawn by the same code the keyboard uses.
 *
 * The board is not a mock and not a thumbnail: `KeyColorBoardView` builds a real
 * `Keyboard` at the width it is given and paints it with `KeyFaceRenderer`, so
 * what appears here is what the IME will draw — provided the width matches. That
 * is why this screen has no horizontal padding around the board.
 *
 * Only one theme can be drawn per process: `ColorManager` holds the active
 * scope, and every `Key` resolves its colours through it. So the screen attaches
 * the theme first and rebuilds on every scheme change.
 */
class ThemePreviewActivity : EditorActivity() {
    private lateinit var binding: ActivityThemePreviewBinding
    private lateinit var board: ThemeBoard

    private var themeId: String = ""
    private var themeName: String = ""
    private var theme: Theme? = null

    /** True once a theme has been drawn; a failed load must not offer editing. */
    private var hasLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME) ?: getString(R.string.app_name)
        supportActionBar?.title = themeName
        if (themeId.isEmpty()) {
            finish()
            return
        }
        board = ThemeBoard.attach(binding.boardHost)
        loadTheme()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.preview, menu)
        OutlineMenu.addTo(menu, this)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the style editor: the file may have been rewritten,
        // so the board is rebuilt from disk rather than assumed unchanged.
        if (hasLoaded) loadTheme()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (OutlineMenu.handle(this, item, themeId, themeName)) return true
        // 编辑入口全部搬到页面卡片上，菜单里不再重复一份（见 menu/preview.xml）。
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_back_to_ime -> {
                InputMethodApp.launch(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadTheme() {
        lifecycleScope.launch {
            val result = board.load(themeId)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeLoadResult.Success -> showTheme(result)
                is ThemeLoadResult.Failure -> showFailure(themeId, result.error)
            }
        }
    }

    private fun showTheme(success: ThemeLoadResult.Success) {
        val loaded = success.theme
        theme = loaded
        hasLoaded = true
        binding.boardHost.visibility = View.VISIBLE
        buildSchemeChips(loaded)
        showFindings(success.findings)
        showInfo(loaded)
        invalidateOptionsMenu()
    }

    private fun showFailure(
        themeId: String,
        error: ThemeLoadError,
    ) {
        Timber.w("Theme %s failed to load: %s", themeId, error.message)
        hasLoaded = false
        binding.boardHost.visibility = View.GONE
        binding.schemeBar.visibility = View.GONE
        binding.diagBox.visibility = View.GONE
        // 主题没读出来时不能留着上一份的编辑入口：点了会拿旧 themeId 开页面。
        binding.infoBlock.visibility = View.GONE
        binding.infoBlock.removeAllViews()
        binding.detail.visibility = View.VISIBLE
        binding.detail.text = buildString {
            append(getString(R.string.preview_load_failed))
            append("\n\n")
            // 「文件不在」是用户自己能解决的一件事（换主题 / 把文件拷过来），
            // 所以用中文说清楚该去看什么；其它错误保留加载器原话，方便贴出来查。
            append(
                if (error is ThemeLoadError.SourceNotFound) {
                    getString(R.string.preview_source_missing, "$themeId.yaml")
                } else {
                    error.message.orEmpty()
                },
            )
        }
        invalidateOptionsMenu()
    }

    private fun buildSchemeChips(loaded: Theme) {
        val ids = loaded.presetColorSchemes.keys
        if (ids.isEmpty()) {
            binding.schemeBar.visibility = View.GONE
            return
        }
        binding.schemeBar.visibility = View.VISIBLE
        val container = binding.schemes
        container.removeAllViews()
        // `default` first, then the rest in the order the file declares them:
        // this is the light/dark pair most themes ship.
        val ordered = listOf(DEFAULT_SCHEME).filter { it in ids } + ids.filter { it != DEFAULT_SCHEME }
        addChip(container, getString(R.string.preview_scheme_follow_system), followSystem = true)
        ordered.forEach { addChip(container, it, followSystem = false) }
        refreshChipState()
    }

    private fun addChip(
        container: LinearLayout,
        label: String,
        followSystem: Boolean,
    ) {
        val chip =
            TextView(this).apply {
                text = label
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(dp(13), dp(7), dp(13), dp(7))
                setOnClickListener {
                    if (followSystem) {
                        EditorPrefs.followSystemDayNight = true
                        // Re-attaching resolves the scheme from the system again.
                        theme?.let { ColorManager.attachTheme(it) }
                    } else {
                        // Forcing one scheme: stop following the system, or the
                        // resolver would immediately override the choice.
                        EditorPrefs.followSystemDayNight = false
                        ColorManager.setColorScheme(label)
                    }
                    refreshChipState()
                    board.refresh()
                }
                tag = if (followSystem) FOLLOW_SYSTEM_TAG else label
                background = chipBackground(selected = false)
                setTextColor(UiTokens.color(this@ThemePreviewActivity, UiR.attr.ui_ink_2))
            }
        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        params.marginEnd = dp(8)
        container.addView(chip, params)
    }

    /**
     * Marks the chip the colours are actually coming from.
     *
     * Resolution, not the stored preference, decides: with "follow system" set
     * the resolver decides which of the theme's schemes is in use, so the chip
     * that lights up has to be read back from the active scheme.
     */
    private fun refreshChipState() {
        val active = runCatching { ColorManager.activeColorScheme }.getOrNull()
        val following = EditorPrefs.followSystemDayNight
        // The resolver hands back one of the theme's own scheme maps, so the id
        // in use is whichever entry compares equal to it.
        val activeId = theme?.presetColorSchemes?.entries?.firstOrNull { it.value == active }?.key
        binding.schemes.children().forEach { chip ->
            val view = chip as TextView
            val selected =
                if (view.tag == FOLLOW_SYSTEM_TAG) {
                    following
                } else {
                    !following && view.tag == activeId
                }
            view.background = chipBackground(selected)
            view.setTextColor(
                UiTokens.color(
                    this,
                    if (selected) UiR.attr.ui_on_primary else UiR.attr.ui_ink_2,
                ),
            )
        }
    }

    /**
     * 这份主题的规模 + 编辑入口，同一批卡片。
     *
     * 原来四行只是数字，真正能点进去的入口全塞在右上角 ⋯ 里——看着像一份
     * 报表，其实该点的是这几行。现在每一行都直接跳到它数的那个东西的编辑页；
     * 菜单里剩下、统计卡没覆盖的入口，再单独列一组「快捷编辑」。
     *
     * 不写「作者」：主题级没有这个字段（只有每个键盘自己有 `author`），
     * 硬凑一个出来就是编。也不写行数——那要把 200KB 再数一遍。
     */
    private fun showInfo(theme: Theme) {
        val box = binding.infoBlock
        box.removeAllViews()
        box.visibility = View.VISIBLE

        fun chapter(title: String) {
            val header = layoutInflater.inflate(UiR.layout.pref_proof_chapter, box, false)
            header.findViewById<TextView>(UiR.id.proofChapterTitle).text = title
            header.findViewById<TextView>(UiR.id.proofChapterExtra).visibility = View.GONE
            box.addView(header)
        }

        /** 一行统计/入口：右侧「值 ›」既是读数也是「可以点」的提示。 */
        fun row(
            label: String,
            key: String,
            value: String,
            onClick: () -> Unit,
        ) {
            val view = layoutInflater.inflate(UiR.layout.pref_proof_value, box, false)
            view.findViewById<TextView>(UiR.id.proofName).text = label
            view.findViewById<TextView>(UiR.id.proofKey).text = key
            view.findViewById<TextView>(UiR.id.proofValue).text = "$value  ›"
            view.isClickable = true
            view.setOnClickListener { onClick() }
            box.addView(view)
        }

        val colors = { startActivity(ColorEditorActivity.intent(this, themeId, themeName)) }
        val layouts = { startActivity(KeyboardListActivity.intent(this, themeId, themeName)) }
        val keys = { startActivity(PresetKeyListActivity.intent(this, themeId, themeName)) }

        chapter(getString(R.string.preview_info_title))
        row(
            getString(R.string.preview_info_schemes),
            "preset_color_schemes",
            getString(R.string.preview_info_count, theme.presetColorSchemes.size),
            colors,
        )
        row(
            getString(R.string.preview_info_keyboards),
            "preset_keyboards",
            getString(R.string.preview_info_count, theme.presetKeyboards.size),
            layouts,
        )
        row(
            getString(R.string.preview_info_keys),
            "preset_keys",
            getString(R.string.preview_info_count, theme.presetKeys.size),
            keys,
        )
        // `dark_scheme` 属于配色方案，编辑器里就是「方案」那一段。
        row(
            getString(R.string.preview_info_pairs),
            "dark_scheme",
            getString(
                R.string.preview_info_count,
                theme.presetColorSchemes.count { it.value.containsKey("dark_scheme") },
            ),
            colors,
        )

        // 菜单里剩下的入口，统计卡没有对应数字，单独列一行一卡。
        chapter(getString(R.string.preview_edit_title))
        fun shortcut(
            label: String,
            key: String,
            onClick: () -> Unit,
        ) = row(label, key, getString(R.string.preview_edit_go), onClick)

        shortcut(getString(R.string.action_edit_style), "style") {
            startActivity(StyleEditorActivity.intent(this, themeId, themeName))
        }
        shortcut(getString(R.string.action_edit_window), "window") {
            startActivity(CandidateWindowActivity.intent(this, themeId, themeName))
        }
        shortcut(getString(R.string.action_edit_toolbar), "tool_bar") {
            startActivity(ToolBarActivity.intent(this, themeId, themeName))
        }
        shortcut(getString(R.string.action_diagnostics), "diagnostics") {
            startActivity(DiagnosticsActivity.intent(this, themeId, themeName))
        }
        if (InputMethodApp.isInstalled(this)) {
            shortcut(getString(R.string.action_back_to_ime), "ime") { InputMethodApp.launch(this) }
        }
    }

    /**
     * 诊断在预览页只占一块：一个数、一句话、一个入口。
     *
     * 没运行过静态检查时退回一行字——「未运行」和「0 处」是两件事，不能都画成 0。
     */
    private fun showFindings(findings: List<ThemeDiagnostics.Finding>?) {
        if (findings == null) {
            binding.diagBox.visibility = View.GONE
            binding.detail.visibility = View.VISIBLE
            binding.detail.text = getString(R.string.preview_findings_unknown)
            return
        }
        val clean = findings.isEmpty()
        binding.diagBox.visibility = View.VISIBLE
        binding.detail.visibility = View.GONE
        binding.diagCount.text = findings.size.toString()
        binding.diagLabel.text =
            getString(
                if (clean) R.string.preview_findings_clean_label else R.string.preview_findings_count_label,
            )
        binding.diagCount.setTextColor(
            UiTokens.color(
                this,
                if (clean) UiR.attr.ui_ink_2 else UiR.attr.ui_accent,
            ),
        )
        binding.diagGo.visibility = if (clean) View.GONE else View.VISIBLE
        // 整张卡都是入口：只留右侧「逐条查看」可点，人去点中间那行大字会没反应。
        val openDiagnostics = { startActivity(DiagnosticsActivity.intent(this, themeId, themeName)) }
        binding.diagGo.setOnClickListener { openDiagnostics() }
        binding.diagBox.setOnClickListener { openDiagnostics() }
    }

    /**
     * 选中的方案是主色实心胶囊，其余是描边胶囊——和配色页段控、诊断页筛选
     * 同一种语言。圆角走 `ui_radius_button`，三套皮肤各自成立。
     */
    private fun chipBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = UiTokens.dimen(this@ThemePreviewActivity, UiR.attr.ui_radius_button).toFloat()
        if (selected) {
            setColor(UiTokens.color(this@ThemePreviewActivity, UiR.attr.ui_primary))
        } else {
            setColor(UiTokens.color(this@ThemePreviewActivity, UiR.attr.ui_card))
            setStroke(
                dp(1),
                UiTokens.color(this@ThemePreviewActivity, UiR.attr.ui_rule),
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun android.view.ViewGroup.children(): Sequence<View> = (0 until childCount).asSequence().map { getChildAt(it) }

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"
        private const val DEFAULT_SCHEME = "default"
        private const val FOLLOW_SYSTEM_TAG = "\u0000system"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, ThemePreviewActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
