/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeLoader
import com.trime.editor.databinding.ActivityDiagnosticsBinding
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.skin.UiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.trime.editor.ui.R as UiR

/**
 * Everything the theme linter found, grouped by how much it matters.
 *
 * The check itself is not new — the preview page has been running it and dumping
 * the findings into a text block. What this screen adds is the only thing that
 * makes a finding useful: a way to the place that causes it. A path like
 * `preset_keys/Keyboard_letter` is useless on its own; the row it belongs to is
 * one tap away.
 */
class DiagnosticsActivity : EditorActivity() {
    private lateinit var binding: ActivityDiagnosticsBinding

    private var themeId: String = ""
    private var themeName: String = ""
    private var findings: List<ThemeDiagnostics.Finding>? = null

    /** null = 全部；否则只显示这一档。 */
    private var filter: ThemeDiagnostics.Severity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.app_name) }
        supportActionBar?.subtitle = getString(R.string.dg_title)
        if (themeId.isEmpty()) {
            finish()
            return
        }
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor, menu)
        OutlineMenu.addTo(menu, this)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (OutlineMenu.handle(this, item, themeId, themeName)) return true
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

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                when (val result = ThemeLoader.loadTheme(themeId)) {
                    is ThemeLoader.ThemeLoadResult.Success -> result.findings
                    else -> null
                }
            }
            if (isFinishing || isDestroyed) return@launch
            findings = loaded
            render()
        }
    }

    private fun render() {
        val all = findings
        binding.summary.text =
            when {
                all == null -> getString(R.string.preview_findings_unknown)

                all.isEmpty() -> getString(R.string.dg_clean)

                else -> {
                    val errors = all.count { it.severity == ThemeDiagnostics.Severity.ERROR }
                    val warnings = all.count { it.severity == ThemeDiagnostics.Severity.WARNING }
                    // 提示也要报数：76 ≠ 0 + 73，少了三个「提示」会让人以为统计错了。
                    val hints = all.size - errors - warnings
                    getString(R.string.dg_summary, all.size, errors, warnings, hints)
                }
            }

        buildFilters(all.orEmpty())

        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                // 左右交给行自己（行里有 20dp），这里只留上下。
                setPadding(0, dp(4), 0, dp(24))
            }
        val scroll = ScrollView(this).apply { addView(column) }
        binding.content.removeAllViews()
        binding.content.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT),
        )

        // Both "the checks did not run" and "the checks found nothing" are said
        // in the summary already; repeating one of them here used to print
        // "没有发现问题" right under "静态检查未运行", which is the opposite.
        if (all.isNullOrEmpty()) return

        // Errors first: the list is read top-down and the first rows should be the
        // ones worth acting on. The enum is declared INFO → WARNING → ERROR, so this
        // has to be descending — the ascending sort it used to be put every 提示 on top.
        all.filter { filter == null || it.severity == filter }
            .sortedByDescending { it.severity.ordinal }
            .forEach { finding ->
                column.addView(row(finding))
            }
    }

    /**
     * 按严重度筛选：四块芯片，数字是真实条数。
     *
     * 只按严重度分，不按「配色 / 样式 / 动作」分：那个分类要靠 `jump()` 里的
     * 目标页去反推，一旦某条发现改投别处，分类就悄悄错了。严重度是数据本身带的。
     */
    private fun buildFilters(all: List<ThemeDiagnostics.Finding>) {
        val bar = binding.filters
        bar.removeAllViews()
        binding.filterBar.visibility = View.VISIBLE

        fun chip(
            label: String,
            count: Int,
            severity: ThemeDiagnostics.Severity?,
        ) {
            val selected = filter == severity
            val chip =
                TextView(this).apply {
                    text = "$label $count"
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setPadding(dp(11), dp(5), dp(11), dp(5))
                    background = chipBackground(selected)
                    setTextColor(
                        UiTokens.color(
                            this@DiagnosticsActivity,
                            if (selected) UiR.attr.ui_on_primary else UiR.attr.ui_ink_2,
                        ),
                    )
                    setOnClickListener {
                        filter = severity
                        render()
                    }
                }
            bar.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6) },
            )
        }

        chip(getString(R.string.dg_filter_all), all.size, null)
        chip(getString(R.string.dg_filter_error), all.count { it.severity == ThemeDiagnostics.Severity.ERROR }, ThemeDiagnostics.Severity.ERROR)
        chip(getString(R.string.dg_filter_warning), all.count { it.severity == ThemeDiagnostics.Severity.WARNING }, ThemeDiagnostics.Severity.WARNING)
        chip(getString(R.string.dg_filter_info), all.count { it.severity == ThemeDiagnostics.Severity.INFO }, ThemeDiagnostics.Severity.INFO)
    }

    /** 选中的芯片是主色实心，其余是描边——和配色页段控同一种语言。 */
    private fun chipBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        if (selected) {
            setColor(UiTokens.color(this@DiagnosticsActivity, UiR.attr.ui_primary))
        } else {
            setColor(UiTokens.color(this@DiagnosticsActivity, UiR.attr.ui_card))
            setStroke(dp(1), UiTokens.color(this@DiagnosticsActivity, UiR.attr.ui_rule))
        }
    }

    /**
     * 一条发现：严重度、位置、说明，下面一条缩进的分隔线。
     *
     * 严重度写在最上面一行而不是做成色块：这一页要扫的是「哪里错了」，
     * 色块会先把注意力吸到左边那条边上。
     */
    private fun row(finding: ThemeDiagnostics.Finding): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        // 卡片 + 涟漪：涟漪的形状由 mask 决定，缺了它水波会从方角里溢出来。
        background =
            RippleDrawable(
                ColorStateList.valueOf(0x1F000000),
                UiTokens.rounded(this@DiagnosticsActivity, UiR.attr.ui_radius_card),
                null,
            )
        setOnClickListener { jump(finding) }
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                topMargin = dp(3)
                bottomMargin = dp(3)
            }

        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
            }
        addView(body)

        body.addView(
            TextView(context).apply {
                text = severityLabel(finding.severity)
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(
                    UiTokens.color(
                        context,
                        if (finding.severity == ThemeDiagnostics.Severity.ERROR) {
                            UiR.attr.ui_accent
                        } else {
                            UiR.attr.ui_muted
                        },
                    ),
                )
            },
        )
        if (!finding.path.isNullOrEmpty()) {
            body.addView(
                TextView(context).apply {
                    text = finding.path
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(UiTokens.color(context, UiR.attr.ui_muted))
                },
            )
        }
        body.addView(
            TextView(context).apply {
                text = finding.message
                textSize = 13f
                setLineSpacing(0f, 1.25f)
                setTextColor(UiTokens.color(context, UiR.attr.ui_ink))
            },
        )
    }

    private fun severityLabel(severity: ThemeDiagnostics.Severity): String = when (severity) {
        ThemeDiagnostics.Severity.ERROR -> getString(R.string.dg_severity_error)
        ThemeDiagnostics.Severity.WARNING -> getString(R.string.dg_severity_warning)
        else -> getString(R.string.dg_severity_info)
    }

    /**
     * Opens the screen that owns [finding], via [DiagnosticsRouter].
     *
     * The `when` has no `else`: [EditorScreen] lists the screens that exist, so
     * a new one added without an intent here stops the build rather than
     * turning its rows into taps that do nothing.
     */
    private fun jump(finding: ThemeDiagnostics.Finding) {
        val route = DiagnosticsRouter.route(finding.path)
        val intent =
            when (route.screen) {
                EditorScreen.STYLE -> StyleEditorActivity.intent(this, themeId, themeName)

                EditorScreen.COLORS -> ColorEditorActivity.intent(this, themeId, themeName)

                EditorScreen.KEYBOARD_LIST -> KeyboardListActivity.intent(this, themeId, themeName)

                EditorScreen.KEYBOARD_EDITOR ->
                    KeyboardEditorActivity.intent(this, themeId, themeName, route.id, route.id)

                EditorScreen.PRESET_KEYS ->
                    PresetKeyListActivity.intent(this, themeId, themeName, query = route.id)

                EditorScreen.TOOL_BAR -> ToolBarActivity.intent(this, themeId, themeName)

                EditorScreen.CANDIDATE_WINDOW -> CandidateWindowActivity.intent(this, themeId, themeName)

                EditorScreen.NONE -> null
            }
        if (intent == null) {
            Toast.makeText(this, R.string.dg_no_jump, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(intent)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, DiagnosticsActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
