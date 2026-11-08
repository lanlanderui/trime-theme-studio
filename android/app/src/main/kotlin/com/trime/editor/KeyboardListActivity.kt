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
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.kbd.KeyboardCatalog
import com.osfans.trime.data.theme.kbd.KeyboardEntry
import com.osfans.trime.data.theme.kbd.LayoutEngine
import com.osfans.trime.data.theme.kbd.ThemeKeyboardReader
import com.osfans.trime.data.theme.kbd.ThemePatchDetector
import com.trime.editor.databinding.ActivityKeyboardListBinding
import com.trime.editor.kbd.KeyboardCardAdapter
import com.trime.editor.kbd.KeyboardRow
import com.trime.editor.ui.EditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Every layout the theme file declares, in file order.
 *
 * The file order is kept on purpose: the author grouped these layouts for a
 * reason, and a list sorted by name would scatter `bqrw1…bqrw6` — the six
 * variants of the same panel — into places the user has to hunt through.
 *
 * A card carries three facts before it is opened: how many rows and keys it
 * builds, whether it is an alias of another layout, and whether a
 * `.custom.yaml` is going to overrule it. Those are exactly the three ways an
 * edit here can fail to show up on the keyboard.
 */
class KeyboardListActivity : EditorActivity() {
    private lateinit var binding: ActivityKeyboardListBinding

    private val adapter = KeyboardCardAdapter(::open)

    private var themeId: String = ""
    private var themeName: String = ""

    /** Everything loaded from the file, before the search box filters it. */
    private var rows: List<KeyboardRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.kbd_list_title) }
        supportActionBar?.subtitle = getString(R.string.kbd_list_title)
        if (themeId.isEmpty()) {
            finish()
            return
        }

        binding.keyboards.layoutManager = LinearLayoutManager(this)
        binding.keyboards.adapter = adapter
        // 卡片化之后不再需要分隔线：一张卡自己就是一个条目，线会从圆角外面穿过去。
        binding.search.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s?.toString().orEmpty())

                override fun afterTextChanged(s: android.text.Editable?) = Unit
            },
        )
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

    /** Reloads after a save: a width change can move a key onto another row. */
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && themeId.isNotEmpty()) load()
    }

    private fun load() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { read() }
            if (isFinishing || isDestroyed) return@launch
            rows = result
            filter(binding.search.text?.toString().orEmpty())
        }
    }

    private fun read(): List<KeyboardRow> {
        val file =
            ThemeStyleWriter.editableSourceFile(themeId) ?: run {
                Timber.w("No writable theme file for %s", themeId)
                return emptyList()
            }
        val raws = ThemeKeyboardReader.read(file)
        if (raws.isEmpty()) return emptyList()

        // Decoded from the same file the writer patches, not from whatever the
        // loader would resolve by id: a user copy shadows the shared theme, and
        // a thumbnail of the other one would describe a layout this screen
        // cannot change.
        val layouts =
            when (val loaded = ThemeLoader.loadFromSource(themeId, file)) {
                is ThemeLoader.ThemeLoadResult.Success -> loaded.theme.presetKeyboards

                else -> {
                    Timber.w("Layouts of %s could not be decoded; cards show no thumbnail", themeId)
                    emptyMap()
                }
            }
        val patched = ThemePatchDetector.patchedLayouts(themeId, layouts.keys)
        val width = resources.displayMetrics.widthPixels - dp(40)
        return KeyboardCatalog.build(raws, layouts, patched).map { rowOf(it, width) }
    }

    private fun rowOf(
        entry: KeyboardEntry,
        widthPx: Int,
    ): KeyboardRow {
        val rows = entry.layout?.let { LayoutEngine.rows(it, widthPx).size } ?: 0
        val marks = mutableListOf<String>()
        if (entry.isAlias) marks += getString(R.string.kbd_badge_alias, entry.aliasTarget.orEmpty())
        if (entry.hasSpacer) marks += getString(R.string.kbd_badge_spacer)
        if (entry.patched) marks += getString(R.string.kbd_badge_patched)
        entry.unsupported?.let { marks += getString(R.string.kbd_badge_unsupported, it) }
        return KeyboardRow(
            entry = entry,
            meta = getString(R.string.kbd_card_meta, rows, entry.keyCount),
            badge = marks.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        )
    }

    private fun filter(query: String) {
        val needle = query.trim().lowercase()
        val matched =
            if (needle.isEmpty()) {
                rows
            } else {
                rows.filter { it.entry.id.lowercase().contains(needle) || it.entry.displayName.lowercase().contains(needle) }
            }
        adapter.submit(matched)
        val empty = matched.isEmpty()
        binding.keyboards.visibility = if (empty) View.GONE else View.VISIBLE
        binding.placeholder.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) {
            binding.placeholder.text =
                if (rows.isEmpty()) {
                    getString(R.string.kbd_empty)
                } else {
                    getString(R.string.kbd_no_match, query.trim())
                }
        }
    }

    private fun open(row: KeyboardRow) {
        val entry = row.entry
        if (!entry.editable) {
            // A dead tap is the worst outcome: say which of the three reasons it
            // was, so the user knows whether to fix the file or the patch.
            AlertDialog.Builder(this)
                .setTitle(R.string.kbd_cannot_edit_title)
                .setMessage(row.badge ?: getString(R.string.kbd_cannot_edit_body))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        startActivity(KeyboardEditorActivity.intent(this, themeId, themeName, entry.id, entry.displayName))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, KeyboardListActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
