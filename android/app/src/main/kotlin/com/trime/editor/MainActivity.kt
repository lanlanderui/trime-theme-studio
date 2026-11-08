/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.trime.editor.databinding.ActivityMainBinding
import com.trime.editor.ui.AppearanceActivity
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.skin.UiMode
import com.trime.editor.workbench.DirectoryPickerDialog
import com.trime.editor.workbench.StorageAccess
import com.trime.editor.workbench.ThemeActions
import com.trime.editor.workbench.ThemeCardAdapter
import com.trime.editor.workbench.ThemeEntry
import com.trime.editor.workbench.readThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The workbench: which directory the editor works in, and what is in it.
 *
 * Everything here is read-only. The screens that write come later, and they
 * will be opened from a row — the point of this screen is that a theme can be
 * looked at before anything is changed.
 */
class MainActivity : EditorActivity() {
    private lateinit var binding: ActivityMainBinding

    private val adapter = ThemeCardAdapter(::openTheme, ::showThemeMenu)

    /** 英雄区当前展示的那一份；英雄区自己也要能点进编辑，所以得留住它。 */
    private var currentHero: ThemeEntry? = null

    /** 「切换」对话框要列出目录里其余主题，留一份本次加载的结果。 */
    private var currentEntries: List<ThemeEntry> = emptyList()

    private var loadJob: Job? = null

    /** Coming back from the "all files access" screen: re-check, then load. */
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    /** N-1：导入主题文件用 SAF，不申请额外的存储权限。 */
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val dir = EditorPrefs.themeDir
            if (uri != null && dir != null) {
                lifecycleScope.launch {
                    applyOutcome(ThemeActions.importFrom(this@MainActivity, dir, uri))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        applyContentWidth()
        setSupportActionBar(binding.toolbar)

        // 舞台皮肤没有顶栏：导航交给英雄区里的 ⋯ 和底部 dock（设计稿 B1）。
        binding.toolbar.visibility = if (UiMode.caps.stageHeader) View.GONE else View.VISIBLE
        binding.heroSwatches.cornerRadiusPx = dp(10).toFloat()
        binding.themes.layoutManager = LinearLayoutManager(this)
        binding.themes.adapter = adapter
        binding.changeDir.setOnClickListener { chooseDirectory() }
        // N-7：切皮肤是常用动作，放在英雄区一眼可见处；dock 长按里保留一份备用。
        binding.heroAppearance.setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }
        // N-1：dock 换成设计稿的四项主题管理动作。
        binding.dockNew.setOnClickListener {
            promptCreate(getString(R.string.theme_new_default), R.string.theme_new_title)
        }
        binding.builtinCard.setOnClickListener {
            promptCreate(getString(R.string.builtin_default_name), R.string.builtin_title)
        }
        loadBuiltinMeta()
        binding.dockSwitch.setOnClickListener { showSwitchDialog() }
        binding.dockImport.setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
        binding.dockIme.setOnClickListener { InputMethodApp.launch(this) }
        // N-3：稿子英雄区右上没有 ⋯；「目录 / 刷新 / 外观 / 帮助」收进 dock 长按。
        listOf(binding.dock, binding.dockNew, binding.dockSwitch, binding.dockImport, binding.dockIme)
            .forEach { v ->
                v.setOnLongClickListener {
                    showMoreMenu(binding.dock)
                    true
                }
            }
        // 英雄区是这一页的主语：点进编辑，长按管这份主题（N-2）。
        binding.heroBlock.setOnClickListener { currentHero?.let(::openTheme) }
        binding.heroBlock.setOnLongClickListener {
            currentHero?.let { showThemeMenu(it, binding.heroBlock) }
            true
        }
        // 崩溃提示优先于首启引导：它解释「上次为什么没了」，引导讲「怎么用」。
        // 两者同时需要，只有「第一次启动就崩过」这一种情况，那时先解释崩溃。
        if (!reportLastCrash()) FirstRunGuide.showIfFirstRun(this)
    }

    /**
     * Says what happened last time, once per start.
     *
     * There is nothing this app restores on launch — no last-opened theme, no
     * unsaved draft — so "safe mode" has nothing to skip. What is worth doing is
     * the part a user cannot get anywhere else: naming the crash and offering the
     * log, instead of letting the app disappear quietly.
     *
     * @return whether a dialog was shown; the caller uses it to avoid stacking
     *   this on top of the first-run guide.
     */
    private fun reportLastCrash(): Boolean {
        if (!CrashLog.hasReport(this)) return false
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_body)
            .setNeutralButton(R.string.crash_view) { _, _ -> showCrashLog() }
            .setNegativeButton(R.string.crash_later, null)
            .setPositiveButton(R.string.crash_clear) { _, _ -> CrashLog.clear(this) }
            .show()
        return true
    }

    private fun showCrashLog() {
        val body = CrashLog.read(this) ?: return
        val view =
            TextView(this).apply {
                text = body
                textSize = 11f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                typeface = Typeface.MONOSPACE
            }
        val scroll = ScrollView(this).apply { addView(view) }
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_log_title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.crash_clear) { _, _ -> CrashLog.clear(this) }
            .show()
    }

    /**
     * 平板上的内容列限宽。
     *
     * 848dp 一行铺满，卡片会被拉成三条横杠，M3 的「卡片」概念就不成立了。
     * 限到 720dp 并居中，和「外观」页用的是同一个尺子。
     */
    private fun applyContentWidth() {
        val side = ((resources.displayMetrics.widthPixels - dp(720)) / 2).coerceAtLeast(0)
        if (side == 0) return
        binding.content.setPadding(side, 0, side, binding.content.paddingBottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        OutlineMenu.addTo(menu, this)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (OutlineMenu.handle(this, item, "", "")) return true
        when (item.itemId) {
            R.id.action_refresh -> refresh()
            R.id.action_back_to_ime -> InputMethodApp.launch(this)
            R.id.action_appearance -> startActivity(Intent(this, AppearanceActivity::class.java))
            R.id.action_help -> FirstRunGuide.show(this)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Access can be granted while the app is in the background, so re-check here. */
    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onStop() {
        loadJob?.cancel()
        loadJob = null
        super.onStop()
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = null
        if (!StorageAccess.hasAccess(this)) {
            showPlaceholder(
                title = getString(R.string.state_no_access_title),
                body = getString(R.string.state_no_access_body),
                action = getString(R.string.action_grant),
            ) { requestAccess() }
            return
        }
        val dir = EditorPrefs.themeDir
        binding.path.text = dir?.absolutePath ?: getString(R.string.dir_unset)
        if (dir == null) {
            showPlaceholder(
                title = getString(R.string.state_no_dir_title),
                body = getString(R.string.state_no_dir_body),
                action = getString(R.string.action_pick_dir),
            ) { chooseDirectory() }
            return
        }
        loadJob =
            lifecycleScope.launch {
                val entries = withContext(Dispatchers.IO) { readThemes(dir) }
                if (isFinishing || isDestroyed) return@launch
                present(entries)
            }
    }

    /**
     * 把「当前主题」放上英雄区，其余的交给列表。
     *
     * 当前主题若不在目录里（换过目录、文件被删），退回第一份——英雄区不能空着，
     * 它是这一页的主语；退回时标题仍然是那份主题自己的名字，不假装是「当前」。
     */
    private fun present(entries: List<ThemeEntry>) {
        if (entries.isEmpty()) {
            currentHero = null
            adapter.submit(emptyList())
            showPlaceholder(
                title = getString(R.string.state_empty_title),
                body = getString(R.string.state_empty_body),
                action = getString(R.string.action_change_dir),
            ) { chooseDirectory() }
            return
        }
        currentEntries = entries
        val hero = entries.firstOrNull { it.id == EditorPrefs.selectedTheme } ?: entries.first()
        currentHero = hero
        val others = entries.filter { it.id != hero.id }
        binding.heroName.text = hero.name
        binding.heroMeta.text = hero.id
        binding.heroSwatches.setColors(hero.swatches.take(5))
        binding.restLabel.text =
            if (others.isEmpty()) {
                getString(R.string.hero_no_other_theme)
            } else {
                getString(R.string.hero_other_themes, others.size)
            }
        adapter.submit(others)
        showList()
    }

    /** N-3：舞台没有 ActionBar，次级入口挂 dock 长按，用 PopupMenu 打开同一份菜单。 */
    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main, popup.menu)
        OutlineMenu.addTo(popup.menu, this)
        popup.menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        popup.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        popup.show()
    }

    /** N-1/N-2 共用的结果处理：一句话反馈 + 刷新；新建完顺手打开编辑。 */
    private fun applyOutcome(outcome: ThemeActions.Outcome, openAfterCreate: Boolean = false) {
        Toast.makeText(this, outcome.message, Toast.LENGTH_SHORT).show()
        if (!outcome.ok || outcome.id == null) return
        if (openAfterCreate) {
            EditorPrefs.selectedTheme = outcome.id
            startActivity(
                ThemePreviewActivity.intent(this, outcome.id, ThemeActions.displayBase(outcome.id)),
            )
        }
        refresh()
    }

    private fun nameInput(initial: String): EditText = EditText(this).apply {
        setText(initial)
        hint = getString(R.string.theme_name_hint)
        setSingleLine(true)
        setSelection(initial.length)
        setPadding(dp(20), dp(12), dp(20), dp(12))
    }

    /**
     * 内置模版的规模按真实行数显示，不写死在字符串里：模板由用户手工维护，
     * 换一份之后这里会自动跟着变。
     */
    private fun loadBuiltinMeta() {
        lifecycleScope.launch {
            val lines =
                withContext(Dispatchers.IO) {
                    runCatching {
                        assets.open(ThemeActions.TEMPLATE_ASSET).use { stream ->
                            stream.bufferedReader().useLines { it.count() }
                        }
                    }.getOrDefault(0)
                }
            binding.builtinMeta.text =
                if (lines > 0) {
                    getString(R.string.builtin_meta, lines)
                } else {
                    getString(R.string.builtin_meta_loading)
                }
        }
    }

    /**
     * 「新建」与「内置方案 → 保存到本地」是同一个动作：从随包模板复制一份，
     * 只改 `name:` 一行，落到用户主题目录，然后打开编辑。区别只在默认名与标题。
     */
    private fun promptCreate(
        defaultName: String,
        titleRes: Int,
    ) {
        val dir = EditorPrefs.themeDir ?: return
        val input = nameInput(defaultName)
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(R.string.builtin_save_hint)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    applyOutcome(
                        ThemeActions.create(this@MainActivity, dir, input.text.toString()),
                        openAfterCreate = true,
                    )
                }
            }.show()
    }

    /** N-1：切换。列出目录里其余主题，选中即打开。 */
    private fun showSwitchDialog() {
        val others = currentEntries.filter { it.id != currentHero?.id }
        if (others.isEmpty()) {
            Toast.makeText(this, R.string.theme_only_one, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_switch_title)
            .setItems(others.map { it.name }.toTypedArray()) { _, which -> openTheme(others[which]) }
            .show()
    }

    /** N-2：主题长按菜单（英雄区与列表行共用）。 */
    private fun showThemeMenu(entry: ThemeEntry, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, 1, Menu.NONE, R.string.theme_menu_rename)
        popup.menu.add(Menu.NONE, 2, Menu.NONE, R.string.theme_menu_duplicate)
        popup.menu.add(Menu.NONE, 3, Menu.NONE, R.string.theme_menu_open)
        popup.setOnMenuItemClickListener { item ->
            val dir = EditorPrefs.themeDir
            if (dir != null) {
                when (item.itemId) {
                    1 -> promptRename(entry)
                    2 -> lifecycleScope.launch { applyOutcome(ThemeActions.duplicate(this@MainActivity, dir, entry)) }
                    3 -> ThemeActions.openInFiles(this@MainActivity, ThemeActions.fileOf(dir, entry.id))
                }
            }
            true
        }
        popup.show()
    }

    /** N-2：重命名改的是文件名；当前主题跟着换 id，别让英雄区指向旧文件。 */
    private fun promptRename(entry: ThemeEntry) {
        val dir = EditorPrefs.themeDir ?: return
        val input = nameInput(ThemeActions.displayBase(entry.id))
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_menu_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val wasSelected = entry.id == EditorPrefs.selectedTheme
                    val outcome = ThemeActions.rename(this@MainActivity, dir, entry, input.text.toString())
                    if (outcome.ok && wasSelected) outcome.id?.let { EditorPrefs.selectedTheme = it }
                    applyOutcome(outcome)
                }
            }.show()
    }

    private fun openTheme(entry: ThemeEntry) {
        // 快捷入口（输入法按键）没有 extra 可用，靠这个记住「你正在编辑哪一份」。
        EditorPrefs.selectedTheme = entry.id
        startActivity(ThemePreviewActivity.intent(this, entry.id, entry.name))
    }

    private fun chooseDirectory() {
        DirectoryPickerDialog(this, EditorPrefs.themeDir) { dir ->
            EditorPrefs.setThemeDir(dir)
            refresh()
        }.show()
    }

    private fun requestAccess() {
        val settings = StorageAccess.settingsIntent(this)
        if (settings != null) {
            // Whether the app-specific screen exists is only knowable by
            // launching it; the global list is the fallback.
            try {
                settingsLauncher.launch(settings)
            } catch (_: ActivityNotFoundException) {
                try {
                    settingsLauncher.launch(StorageAccess.fallbackSettingsIntent())
                } catch (_: ActivityNotFoundException) {
                    askRuntimePermission()
                }
            }
        } else {
            askRuntimePermission()
        }
    }

    private fun askRuntimePermission() {
        if (StorageAccess.asksRuntimePermission()) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showList() {
        binding.themes.visibility = View.VISIBLE
        binding.placeholder.visibility = View.GONE
    }

    private fun showPlaceholder(
        title: String,
        body: String,
        action: String,
        onAction: () -> Unit,
    ) {
        // 列表不粘了，英雄区也不该留着上一份主题还能点进去。
        currentHero = null
        binding.themes.visibility = View.GONE
        binding.placeholder.visibility = View.VISIBLE
        binding.placeholderTitle.text = title
        binding.placeholderBody.text = body
        binding.placeholderAction.text = action
        binding.placeholderAction.setOnClickListener { onAction() }
    }
}
