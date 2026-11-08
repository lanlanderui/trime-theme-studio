/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ColorLiteral
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeSchemeWriter
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.data.theme.material.MaterialScheme
import com.osfans.trime.data.theme.material.MaterialYouStore
import com.osfans.trime.data.theme.material.MaterialYouWriter
import com.osfans.trime.data.theme.material.SavedPalette
import com.trime.editor.ui.color.ColorEditorHost
import com.trime.editor.ui.color.ColorSchemeFragment
import com.trime.editor.ui.color.MaterialYouDialog
import com.trime.editor.ui.color.NightMode
import com.trime.editor.ui.color.PreviewState
import com.trime.editor.ui.skin.UiMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One colour scheme, with the keyboard painted by it directly above.
 *
 * Two schemes are on screen at once in spirit: the one being edited sets the
 * colours of every row, and the board is drawn from whichever scheme is active
 * in the app. The screen keeps them the same by activating the scheme it edits,
 * which is also what the IME would do — so the board cannot quietly show a
 * different palette than the one being edited.
 */
class ColorEditorActivity :
    ThemeEditorActivity(),
    ColorEditorHost {
    private var schemeId: String = ColorSchemeFragment.TEMPLATE_SCHEME

    /** How the board decides between a pair's two halves; see [NightMode]. */
    private var nightMode: NightMode = NightMode.SYSTEM

    override val subtitleRes: Int get() = R.string.color_editor_title

    override val outlineChapter: String get() = "colors"

    /** 舞台皮肤没有顶栏：标题行与 ⋯ 在配色页里（设计稿 B2-01）。 */
    override val stageHidesToolbar: Boolean get() = UiMode.caps.stageHeader

    override fun refreshCaption() = updateCaption()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        // Read before the base class loads the theme: the scheme picks which
        // colours the board resolves, so it has to be in place before the first
        // draw rather than swapped in afterwards.
        schemeId = intent.getStringExtra(EXTRA_SCHEME_ID).orEmpty().ifEmpty { activeSchemeId() }
        super.onCreate(savedInstanceState)
        updateSubtitle()
    }

    override fun createEditor(): Fragment = ColorSchemeFragment.newInstance(themeId, schemeId)

    /** A colour change never moves a key, but it does change every pixel. */
    override fun reloadBoard() = reloadTheme()

    /** Creating, renaming or deleting a scheme decides which one is on screen. */
    override fun onSchemeChanged(edit: String) = switchTo(edit)

    override fun previewState(): PreviewState = PreviewState(nightMode, activeSchemeId())

    /**
     * Forces the board into the light or dark half of a pair.
     *
     * [ColorManager.onSystemNightModeChange] is the same entry the platform
     * calls when the clock crosses sunset, so the board resolves and repaints
     * exactly as it would on its own — the override is only *who* decides.
     * Nothing is persisted: this is a preview, not a preference.
     */
    override fun setPreviewMode(mode: NightMode) {
        nightMode = mode
        val night =
            when (mode) {
                NightMode.SYSTEM -> systemIsNight()
                NightMode.LIGHT -> false
                NightMode.DARK -> true
            }
        ColorManager.onSystemNightModeChange(night)
        // The colours are baked into the keys the board built, so a repaint is
        // not enough: the keyboard has to be resolved again.
        reloadTheme()
    }

    private fun systemIsNight(): Boolean = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.color_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val schemes = ThemeSchemeWriter.listSchemes(ThemeStyleWriter.editableSourceFile(themeId) ?: return super.onPrepareOptionsMenu(menu))
        menu.findItem(R.id.action_switch_scheme)?.isVisible = schemes.size > 1
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_switch_scheme -> showSchemePicker()
            R.id.action_material_you -> showMaterialYou()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Generates a wallpaper-derived pair and writes both blocks.
     *
     * The seed offered first is the scheme's own accent, so "regenerate from
     * what I am looking at" is one tap; the wallpaper's palettes are the other
     * source, and on Android 12+ they are what the dialog opens on.
     */
    private fun showMaterialYou() {
        val file = ThemeStyleWriter.editableSourceFile(themeId)
        if (file == null) {
            toast(R.string.material_you_no_file)
            return
        }
        MaterialYouDialog.show(this, accentSeed(file)) { generated ->
            writeMaterialYou(file, generated)
        }
    }

    private fun writeMaterialYou(
        file: File,
        generated: MaterialYouDialog.Generated,
    ) {
        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    MaterialYouWriter.write(
                        file = file,
                        colors = generated.colors,
                        lightName = getString(R.string.material_you_light_name),
                        darkName = getString(R.string.material_you_dark_name),
                    )
                }
            when (result.outcome) {
                ThemeTextFile.WriteOutcome.WRITTEN -> {
                    toast(R.string.material_you_written)
                    // Edit the half the board is about to paint: a theme with a
                    // day/night link resolves to the dark twin at night no
                    // matter which id was asked for, and rows describing the
                    // other half would look like the write went to the wrong
                    // block.
                    val night =
                        when (nightMode) {
                            NightMode.SYSTEM -> systemIsNight()
                            NightMode.LIGHT -> false
                            NightMode.DARK -> true
                        }
                    val target = if (night) MaterialYouWriter.DARK_ID else MaterialYouWriter.LIGHT_ID
                    // The rows were built from a file that no longer exists in
                    // that shape, so the screen is replaced even when the user
                    // was already editing one of the two blocks.
                    switchTo(target, reload = true)
                    toastArchive(generated)
                }

                else -> toast(R.string.write_failed)
            }
        }
    }

    /** Records the palette in `dynamic_colors.json`, which is what makes it reusable. */
    private suspend fun toastArchive(generated: MaterialYouDialog.Generated): Boolean {
        val store = MaterialYouStore.defaultFile() ?: return false
        val palette =
            SavedPalette(
                id = "my_" + System.currentTimeMillis(),
                name = getString(R.string.material_you_light_name),
                createdAt = System.currentTimeMillis(),
                source = generated.source,
                accent = MaterialScheme.literal(generated.accent),
                light = generated.colors.light,
                dark = generated.colors.dark,
            )
        val saved =
            withContext(Dispatchers.IO) {
                val known = MaterialYouStore.load(store)
                // Re-generating the same colours is not a failure; it is not a
                // new favourite either.
                if (MaterialYouStore.contains(known, palette)) true else MaterialYouStore.add(palette, store)
            }
        toast(if (saved) R.string.material_you_stored else R.string.material_you_store_failed)
        return saved
    }

    /** The accent the board is painting with, for the dialog's starting colour. */
    private fun accentSeed(file: File): Int {
        val values = ThemeSchemeWriter.readScheme(file, schemeId)
        val keys = listOf("text_color", "hilited_text_color", "on_key_back_color", "back_color")
        return keys.firstNotNullOfOrNull { key -> ColorLiteral.parse(values[key]) } ?: FALLBACK_SEED
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    /**
     * 方案胶囊的下拉：列出所有方案，切到点开的那一个。
     *
     * 板子会跟着走——颜色通过「当前方案」解析，留着上一个不管，键盘画的就会
     * 是另一套调色板（见 [switchTo]）。
     */
    override fun showSchemePicker() {
        val file = ThemeStyleWriter.editableSourceFile(themeId) ?: return
        val schemes = ThemeSchemeWriter.listSchemes(file)
        if (schemes.isEmpty()) return
        val names = schemes.map { it.displayName }.toTypedArray()
        val checked = schemes.indexOfFirst { it.id == schemeId }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.action_switch_scheme)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                switchTo(schemes[which].id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 页面上的 ⋯：菜单项和 ActionBar 里是同一份（切换配色 / Material You / 目录）。
     *
     * 舞台皮肤收掉了 ActionBar，这三个入口没有别的地方可去；PopupMenu 的点
     * 击转回 activity 的 onOptionsItemSelected，和 ActionBar 走完全相同的路。
     */
    override fun showPageMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.color_editor, popup.menu)
        OutlineMenu.addTo(popup.menu, this)
        popup.menu.findItem(R.id.action_switch_scheme)?.isVisible = knownSchemeCount() > 1
        popup.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        popup.show()
    }

    private fun knownSchemeCount(): Int = ThemeStyleWriter.editableSourceFile(themeId)?.let { ThemeSchemeWriter.listSchemes(it).size } ?: 0

    /**
     * Edits another scheme.
     *
     * The board follows: colours resolve through the active scheme, so leaving
     * the previous one active would show a palette the rows no longer describe.
     * A rename keeps the same id, so only the labels move.
     *
     * [reload] is for the writes that added schemes to the file: the board's
     * copy of the theme was read before they existed, so repainting it would
     * show the old palette and there would be nothing to switch to.
     */
    private fun switchTo(
        id: String,
        reload: Boolean = false,
    ) {
        if (id == schemeId && !reload) {
            updateSubtitle()
            return
        }
        schemeId = id
        ColorManager.setColorScheme(id)
        updateSubtitle()
        replaceEditor(ColorSchemeFragment.newInstance(themeId, id))
        // The menu lists the schemes, and there is one more or one less now.
        invalidateOptionsMenu()
        if (reload) reloadTheme() else board.refresh()
    }

    private fun updateSubtitle() {
        supportActionBar?.subtitle = getString(R.string.color_editor_title) + " · " + schemeId
    }

    /** The scheme the app is painting with, or the template when none is set. */
    private fun activeSchemeId(): String {
        val scope = ColorManager.currentScope()
        val active = scope?.activeColorScheme
        val fromTheme =
            if (scope != null && active != null) {
                scope.theme.presetColorSchemes.entries.firstOrNull { it.value == active }?.key
            } else {
                null
            }
        return fromTheme ?: ColorSchemeFragment.TEMPLATE_SCHEME
    }

    companion object {
        private const val EXTRA_SCHEME_ID = "scheme_id"

        /** Material's own default accent: what "no better idea" looks like. */
        private const val FALLBACK_SEED = 0xFF6750A4.toInt()

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
            schemeId: String? = null,
        ): Intent = intentOf(context, ColorEditorActivity::class.java, themeId, themeName)
            .putExtra(EXTRA_SCHEME_ID, schemeId)
    }
}
