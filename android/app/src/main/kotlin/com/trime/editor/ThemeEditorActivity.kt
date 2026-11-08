/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadError
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.trime.editor.databinding.ActivityThemeEditorBinding
import com.trime.editor.ui.DiskBackedEditor
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.preview.ThemeBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.trime.editor.ui.R as UiR

/**
 * A theme editor screen: the keyboard it affects on top, a form for one part of
 * the theme below.
 *
 * Both editors -- style values and colour schemes -- need the same three steps
 * in the same order (read the file, make it the process's active theme, build
 * the board at the width it was given) and the same answer to "the theme could
 * not be read". Doing that once is what keeps the two screens from disagreeing
 * about what a preview is.
 *
 * The form is attached only after the theme loaded: its rows are built from the
 * same file the board draws, and an editor for a theme that will not load can
 * only produce a file the preview cannot show.
 */
abstract class ThemeEditorActivity : EditorActivity() {
    private lateinit var binding: ActivityThemeEditorBinding
    protected lateinit var board: ThemeBoard
    protected var themeId: String = ""
    protected var themeName: String = ""

    private var editorAttached = false

    /** The in-flight "did the file move" check; cancelled on pause. */
    private var staleCheck: Job? = null

    /** What this screen edits, shown under the theme name. */
    @get:StringRes
    protected abstract val subtitleRes: Int

    /** The form for this screen; called once, after the theme loaded. */
    protected abstract fun createEditor(): Fragment

    /** 这一页在目录栏里属于哪一章（[com.trime.editor.ui.proof.Outline] 的 id）。 */
    protected abstract val outlineChapter: String

    /**
     * 舞台皮肤下把 ActionBar 收起来（设计稿 B 三屏都没有顶栏）。
     *
     * 页面自己负责导航与菜单入口（返回胶囊 / ⋯），和 B1 的 heroMenu 同一套。
     * 基类只提供开关：默认 false，样式页还是 Preference 界面，收掉顶栏它没有
     * 地方放菜单。
     */
    protected open val stageHidesToolbar: Boolean get() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME) ?: getString(R.string.app_name)
        supportActionBar?.title = themeName
        supportActionBar?.subtitle = getString(subtitleRes)
        if (themeId.isEmpty()) {
            finish()
            return
        }
        // 舞台皮肤没有顶栏：标题与菜单由页面自己画（见 [stageHidesToolbar]）。
        // setSupportActionBar 之后设 GONE 不影响菜单分发——ActionBar 还在，
        // 只是不占地方；PopupMenu 走的还是同一条 onOptionsItemSelected。
        binding.toolbar.visibility = if (stageHidesToolbar) View.GONE else View.VISIBLE
        board = ThemeBoard.attach(binding.boardHost)
        // 两栏必须在第一次布局之前定下来：预览板是按实际宽度算键宽的，
        // 先按整幅宽度画一遍、再把目录栏塞进去，键盘就会画到框外去。
        applyTwoPane()
        binding.staleBanner.setOnClickListener { adoptFileFromDisk() }
        loadTheme()
    }

    /**
     * Takes the file the other app wrote and starts over from it.
     *
     * Both halves of the screen are rebuilt: the rows, and the keyboard above
     * them. The form asks the host to redo the board as part of the reload, so
     * this is not a second load — a screen that reloaded twice would parse the
     * same file twice and show the second result.
     */
    private fun adoptFileFromDisk() {
        val editor = currentEditor() ?: return
        binding.staleBanner.visibility = View.GONE
        editor.reloadFromDisk()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 目录抽屉对所有页面都一样，只是这一页额外有「节」可以滚。
        if (OutlineMenu.handle(
                this,
                item,
                themeId,
                themeName,
                currentEditor()?.sections().orEmpty(),
                ::scrollToSection,
            )
        ) {
            return true
        }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // A phone without the keyboard has nothing to go back to; the item
        // would only ever answer "not installed".
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        // 平板上的目录栏是常驻的，再给一个入口就是重复。
        menu.findItem(R.id.action_outline)?.isVisible = !resources.getBoolean(UiR.bool.ui_two_pane)
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * Re-checks the file every time the screen comes back to the front.
     *
     * The other app can be the one that wrote the file while this screen was
     * behind it, and neither the timestamp nor the size is trustworthy there
     * (a FUSE mount keeps the mtime of a replaced file). So the only sound
     * answer is to compare the bytes — off the main thread, once per resume.
     */
    override fun onResume() {
        super.onResume()
        checkFileChanged()
    }

    override fun onPause() {
        staleCheck?.cancel()
        staleCheck = null
        super.onPause()
    }

    private fun checkFileChanged() {
        val editor = currentEditor() ?: return
        staleCheck?.cancel()
        staleCheck =
            lifecycleScope.launch {
                val changed = withContext(Dispatchers.IO) { editor.isFileChanged() }
                if (isFinishing || isDestroyed) return@launch
                binding.staleBanner.visibility = if (changed) View.VISIBLE else View.GONE
            }
    }

    private fun currentEditor(): DiskBackedEditor? = supportFragmentManager.findFragmentById(R.id.settings) as? DiskBackedEditor

    /**
     * Re-reads the theme and redraws the board.
     *
     * Not a repaint: a saved value can move key sizes, heights and labels, and
     * those live in the `Keyboard` the board built. Only a fresh load picks them
     * up.
     */
    protected fun reloadTheme() {
        lifecycleScope.launch { board.load(themeId) }
    }

    private fun loadTheme() {
        lifecycleScope.launch {
            val result = board.load(themeId)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeLoadResult.Success -> attachEditor()
                is ThemeLoadResult.Failure -> showFailure(themeId, result.error)
            }
        }
    }

    private fun attachEditor() {
        binding.boardHost.visibility = View.VISIBLE
        if (editorAttached) return
        editorAttached = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, createEditor())
            .commit()
        // 目录栏要从表单里读章节，表单必须先真的建起来（commit 是异步的）。
        supportFragmentManager.executePendingTransactions()
        bindOutline()
        updateCaption()
    }

    /**
     * Swaps the form for another one without reloading the theme.
     *
     * Used when the screen edits one item out of a list (one colour scheme, one
     * keyboard) and the user picks a different one: the theme itself did not
     * change, so re-reading it would only cost a pass over the file.
     */
    protected fun replaceEditor(fragment: Fragment) {
        editorAttached = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .commit()
        supportFragmentManager.executePendingTransactions()
        bindOutline()
        updateCaption()
    }

    /**
     * 样张下面那行统计：由当前这一页自己算（见 [DiskBackedEditor.caption]）。
     *
     * 空串就整行收起来——大部分页面没有统计可说，留一条空行只会让人以为哪里没加载出来。
     */
    fun updateCaption() {
        val text = currentEditor()?.caption().orEmpty()
        binding.caption.text = text
        binding.caption.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * 两栏还是单栏，只在这里判定一次（`proof_two_pane` 由 `values-sw600dp` 覆盖）。
     * 必须在第一次布局之前调用：预览板按实际宽度算键宽。
     */
    private fun applyTwoPane() {
        val twoPane = resources.getBoolean(UiR.bool.ui_two_pane)
        binding.outline.visibility = if (twoPane) View.VISIBLE else View.GONE
        binding.outlineDivider.visibility = if (twoPane) View.VISIBLE else View.GONE
    }

    /**
     * 目录栏按当前页的节重新渲染。
     *
     * 每次重建表单都要重来一遍：节是从 Preference 树里读出来的（见
     * [DiskBackedEditor.sections]），缓一份就要记得在哪失效，忘一次就是
     * 「点了跳到别处」。读一次的成本是几十个对象，不值得省。
     */
    private fun bindOutline() {
        if (!resources.getBoolean(UiR.bool.ui_two_pane)) return
        binding.outline.bind(
            currentChapter = outlineChapter,
            sections = currentEditor()?.sections().orEmpty(),
            onChapter = { chapter ->
                PageRouter.intent(this, chapter, themeId, themeName)?.let { startActivity(it) }
            },
            onSection = ::scrollToSection,
        )
    }

    /** 节 = 滚到这一行。位置由表单自己算（可见项序号），这里只负责滚。 */
    private fun scrollToSection(position: Int) {
        val fragment = supportFragmentManager.findFragmentById(R.id.settings)
        // Preference 系的表单：滚到那一行。
        if (fragment is PreferenceFragmentCompat) {
            val list = fragment.listView
            val manager = list.layoutManager as? LinearLayoutManager
            if (manager != null) manager.scrollToPositionWithOffset(position, 0) else list.scrollToPosition(position)
            return
        }
        // 自绘的屏（配色页一组一屏）：position 是段号，跳组。
        (fragment as? DiskBackedEditor)?.scrollTo(position)
    }

    private fun showFailure(
        themeId: String,
        error: ThemeLoadError,
    ) {
        Timber.w("Theme %s failed to load for editing: %s", themeId, error.message)
        binding.boardHost.visibility = View.GONE
        val message =
            TextView(this).apply {
                text =
                    buildString {
                        append(getString(R.string.preview_load_failed))
                        append("\n\n")
                        append(error.message.orEmpty())
                    }
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
        binding.settings.addView(
            message,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        /** The extras every editor screen reads. */
        fun putExtras(
            intent: Intent,
            themeId: String,
            themeName: String,
        ): Intent = intent
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)

        fun intentOf(
            context: Context,
            activity: Class<out ThemeEditorActivity>,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, activity).also { putExtras(it, themeId, themeName) }
    }
}
