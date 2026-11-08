/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.kbd.PresetKeyCatalog
import com.osfans.trime.data.theme.kbd.PresetKeyEntry
import com.osfans.trime.data.theme.kbd.RawPresetKey
import com.osfans.trime.data.theme.kbd.RawPresetSection
import com.osfans.trime.data.theme.kbd.ThemeKeyboardWriter
import com.osfans.trime.data.theme.kbd.ThemePresetKeyReader
import com.osfans.trime.data.theme.kbd.ThemePresetKeyWriter
import com.trime.editor.databinding.ActivityPresetKeyListBinding
import com.trime.editor.kbd.PresetKeyAdapter
import com.trime.editor.kbd.PresetKeyEditorDialog
import com.trime.editor.kbd.PresetKeyRow
import com.trime.editor.ui.EditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Every action `preset_keys` declares, in the order the picker would offer it.
 *
 * This is the dictionary the other two editors bind *to*: a key's `click`, a
 * toolbar button's `action` and this list all name the same entries. Showing it
 * on its own screen is what makes those bindings editable without inventing a
 * second vocabulary.
 *
 * The list is sorted by kind rather than by file order — 240 entries in file
 * order is a wall of letters with the three `command:` keys buried in it.
 */
class PresetKeyListActivity : EditorActivity() {
    private lateinit var binding: ActivityPresetKeyListBinding

    private val adapter = PresetKeyAdapter(::open)

    private var themeId: String = ""
    private var themeName: String = ""
    private var sourceFile: File? = null

    /** Raw entries by id, for the editor's "what did it look like" side. */
    private var raw: Map<String, RawPresetKey> = emptyMap()
    private var section: RawPresetSection? = null
    private var rows: List<PresetKeyRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresetKeyListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.app_name) }
        supportActionBar?.subtitle = getString(R.string.key_list_title)
        if (themeId.isEmpty()) {
            finish()
            return
        }

        binding.keys.layoutManager = LinearLayoutManager(this)
        binding.keys.adapter = adapter
        // 卡片化之后不再需要分隔线：一张卡自己就是一个条目，线会从圆角外面穿过去。
        intent.getStringExtra(EXTRA_QUERY)?.takeIf { it.isNotBlank() }?.let { query ->
            // Arriving from a diagnostic: put the reader on the entry it named.
            binding.search.setText(query)
            binding.search.setSelection(query.length)
        }
        binding.search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filter(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
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

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { read() }
            if (isFinishing || isDestroyed) return@launch
            if (loaded == null) {
                toast(R.string.key_load_failed)
                return@launch
            }
            rows = loaded
            filter(binding.search.text?.toString().orEmpty())
        }
    }

    private fun read(): List<PresetKeyRow>? {
        val file = ThemeStyleWriter.editableSourceFile(themeId) ?: return null
        sourceFile = file
        val read = ThemePresetKeyReader.read(file) ?: return emptyList()
        section = read
        raw = read.keys.associateBy { it.id }
        val keys =
            when (val loaded = ThemeLoader.loadFromSource(themeId, file)) {
                is ThemeLoader.ThemeLoadResult.Success -> loaded.theme.presetKeys

                else -> {
                    Timber.w("preset_keys of %s could not be decoded", themeId)
                    emptyMap()
                }
            }
        // Entries the file spells out come first; anything the loader resolved
        // from an include that the file does not declare is not editable here.
        return PresetKeyCatalog.build(keys)
            .filter { it.id in raw }
            .map { PresetKeyRow(it, it.detail) }
    }

    private fun filter(query: String) {
        val needle = query.trim().lowercase()
        val matched =
            if (needle.isEmpty()) {
                rows
            } else {
                rows.filter {
                    it.entry.id.lowercase().contains(needle) ||
                        it.entry.displayName.lowercase().contains(needle) ||
                        it.entry.detail.lowercase().contains(needle)
                }
            }
        adapter.submit(matched)
        val empty = matched.isEmpty()
        binding.keys.visibility = if (empty) View.GONE else View.VISIBLE
        binding.placeholder.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) {
            binding.placeholder.text =
                if (rows.isEmpty()) getString(R.string.key_empty) else getString(R.string.key_no_match, query.trim())
        }
    }

    private fun open(row: PresetKeyRow) {
        val file = sourceFile ?: return
        val section = section ?: return
        val entry: PresetKeyEntry = row.entry
        val current = raw[entry.id] ?: return
        PresetKeyEditorDialog.show(
            context = this,
            id = entry.id,
            current = FIELD_NAMES.associateWith { current[it] },
        ) { changes ->
            lifecycleScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        ThemePresetKeyWriter.update(file, section, entry.id, changes)
                    }
                if (isFinishing || isDestroyed) return@launch
                when (result) {
                    is ThemeKeyboardWriter.Result.Success -> {
                        toast(R.string.key_saved)
                        load()
                    }

                    is ThemeKeyboardWriter.Result.Failure ->
                        AlertDialog.Builder(this@PresetKeyListActivity)
                            .setTitle(R.string.key_save_failed)
                            .setMessage(result.reason)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                }
            }
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"
        private const val EXTRA_QUERY = "query"

        /** The fields the editor form exposes, in file spelling. */
        private val FIELD_NAMES =
            listOf(
                "label",
                "send",
                "select",
                "command",
                "option",
                "text",
                "toggle",
                "states",
                "preview",
                "functional",
                "repeatable",
            )

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
            query: String = "",
        ): Intent = Intent(context, PresetKeyListActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
            .putExtra(EXTRA_QUERY, query)
    }
}
