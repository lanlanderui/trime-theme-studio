/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.scalar.RawScalarSection
import com.osfans.trime.data.theme.scalar.ScalarCatalog
import com.osfans.trime.data.theme.scalar.ScalarField
import com.osfans.trime.data.theme.scalar.ScalarSectionSpec
import com.osfans.trime.data.theme.scalar.ThemeScalarReader
import com.osfans.trime.data.theme.scalar.ThemeScalarWriter
import com.trime.editor.databinding.ActivityScalarSectionsBinding
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.proof.ProofFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.trime.editor.ui.R as UiR

/**
 * The candidate window and the preedit line.
 *
 * Both sections are optional, and the theme this editor was built against writes
 * neither — libime's defaults are used. So the screen has two states per section:
 * "written here" (edit the lines) and "not written" (show the defaults, offer to
 * add the block). Pretending the second state has values would be a lie the first
 * save would have to take back.
 *
 * There is no preview board on this screen on purpose: the workbench's card draws
 * a fixed-share candidate strip, and a strip that does not move when `insets`
 * changes is worse than no strip at all.
 */
class CandidateWindowActivity : EditorActivity() {
    private lateinit var binding: ActivityScalarSectionsBinding

    private var themeId: String = ""
    private var themeName: String = ""
    private var sourceFile: File? = null
    private var sections: Map<String, RawScalarSection> = emptyMap()

    /**
     * Guards against a second commit landing while the first is still writing.
     *
     * One edit fires twice on a real keyboard: `IME_ACTION_DONE` commits, then
     * closing the IME drops focus and commits again. Both used to start a write
     * from the same "before" snapshot; the loser was refused by the file's
     * compare-and-swap and surfaced as "cannot write", which was a lie.
     */
    private var writing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScalarSectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.app_name) }
        supportActionBar?.subtitle = getString(R.string.cw_title)
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
            val loaded = withContext(Dispatchers.IO) { read() }
            if (isFinishing || isDestroyed) return@launch
            if (!loaded) {
                toast(R.string.cw_load_failed)
                return@launch
            }
            render()
        }
    }

    private fun read(): Boolean {
        val file = ThemeStyleWriter.editableSourceFile(themeId) ?: return false
        sourceFile = file
        sections = ScalarCatalog.sections.associate { it.name to ThemeScalarReader.read(file, it.name) }
        return true
    }

    private fun render() {
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(28))
            }
        val scroll = ScrollView(this).apply { addView(column) }
        binding.content.removeAllViews()
        binding.content.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT),
        )

        ScalarCatalog.sections.forEach { spec ->
            column.addView(heading(getString(sectionLabel(spec.name)), spec.fields.size))
            sectionHint(spec.name)?.let { column.addView(text(getString(it))) }
            val section = sections.getValue(spec.name)
            if (section.range == null) {
                column.addView(text(getString(R.string.cw_not_written)))
                column.addView(
                    Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                        text = getString(R.string.cw_create_section)
                        setOnClickListener { createSection(spec) }
                    },
                )
                return@forEach
            }

            val unlisted = if (spec.listUnlisted) ScalarCatalog.unlisted(section, spec) else emptyList()
            if (unlisted.isNotEmpty()) {
                column.addView(
                    text(
                        getString(R.string.cw_unlisted, unlisted.joinToString(", ") { it.joinToString(".") }),
                    ),
                )
            }

            spec.fields.forEach { field ->
                column.addView(fieldRow(spec, section, field))
            }
        }
    }

    private fun fieldRow(
        spec: ScalarSectionSpec,
        section: RawScalarSection,
        field: ScalarField,
    ): View {
        val value = ScalarCatalog.valueOf(section, field)
        val hasLine = section.leaf(field.path) != null
        // 行是 editor-ui 里的同一套布局：这张纸上的行只有一种长相，
        // 不能因为「这一页能就地输入」就长成一张表单。
        val row = layoutInflater.inflate(UiR.layout.pref_proof_field, null, false)
        row.findViewById<TextView>(UiR.id.proofName).text = getString(fieldLabel(spec.name, field.id))
        row.findViewById<TextView>(UiR.id.proofKey).text = (listOf(spec.name) + field.path).joinToString(".")

        val input = row.findViewById<EditText>(UiR.id.proofField)
        input.setText(value.orEmpty())
        input.hint = getString(R.string.cw_default_hint, field.defaultValue)
        input.inputType =
            if (field.kind == ScalarField.Kind.INT) {
                InputType.TYPE_CLASS_NUMBER
            } else {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        input.setOnEditorActionListener { view, _, _ ->
            commit(view as EditText, spec, field, value)
            true
        }
        input.setOnFocusChangeListener { view, focused ->
            if (!focused && view is EditText) commit(view, spec, field, value)
        }

        val action = row.findViewById<TextView>(UiR.id.proofAction)
        if (hasLine) {
            action.visibility = View.VISIBLE
            action.text = getString(R.string.cw_reset)
            action.setOnClickListener { reset(spec, field) }
        }
        return row
    }

    private fun commit(
        input: EditText,
        spec: ScalarSectionSpec,
        field: ScalarField,
        current: String?,
    ) {
        val next = input.text.toString().trim()
        // An empty box means "remove the line": the default takes over again, and a
        // `0` typed by accident cannot be mistaken for it.
        when {
            next == current.orEmpty() -> Unit
            next.isEmpty() -> reset(spec, field)
            else -> set(spec, field, next)
        }
    }

    private fun createSection(spec: ScalarSectionSpec) {
        // Created by writing its first field: the block then exists with one line,
        // and the rest are added by editing them.
        val first = spec.fields.first()
        set(spec, first, first.defaultValue, message = R.string.cw_section_created)
    }

    private fun set(
        spec: ScalarSectionSpec,
        field: ScalarField,
        value: String,
        message: Int = R.string.cw_saved,
    ) {
        val file = sourceFile ?: return
        write(message) {
            val written = ThemeScalarWriter.set(file, spec.name, field.path, ThemeStyleWriter.formatScalar(value))
            if (written) Outcome.Ok else Outcome.Failed(getString(R.string.cw_save_failed))
        }
    }

    private fun reset(
        spec: ScalarSectionSpec,
        field: ScalarField,
    ) {
        val file = sourceFile ?: return
        write(R.string.cw_saved) {
            // `reset` comments the line out, which is how the theme says "default":
            // the value stays readable and the runtime stops seeing it.
            val written = ThemeScalarWriter.reset(file, spec.name, field.path)
            if (written) Outcome.Ok else Outcome.Failed(getString(R.string.cw_save_failed))
        }
    }

    private sealed interface Outcome {
        data object Ok : Outcome

        data class Failed(
            val reason: String,
        ) : Outcome
    }

    private fun write(
        message: Int,
        action: () -> Outcome,
    ) {
        if (sourceFile == null || writing) return
        writing = true
        lifecycleScope.launch {
            val outcome =
                try {
                    withContext(Dispatchers.IO) { action() }
                } finally {
                    writing = false
                }
            if (isFinishing || isDestroyed) return@launch
            when (outcome) {
                is Outcome.Ok -> {
                    if (read()) render()
                    toast(message)
                }

                is Outcome.Failed ->
                    AlertDialog.Builder(this@CandidateWindowActivity)
                        .setTitle(R.string.cw_save_failed_title)
                        .setMessage(outcome.reason)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
            }
        }
    }

    /** 章头用的是和其他页同一个布局，连下面那条重线都一样。 */
    private fun heading(
        value: String,
        count: Int,
    ): View {
        val row = layoutInflater.inflate(UiR.layout.pref_proof_chapter, null, false)
        row.findViewById<TextView>(UiR.id.proofChapterTitle).text = value
        // 段名不再塞进标题里（原来写作「候选窗 window」），标题只留中文，
        // 项数放右列——和其他页的章头一致。
        row.findViewById<TextView>(UiR.id.proofChapterExtra).text = ProofFormat.count(count)
        return row
    }

    private fun text(value: String): View {
        val hint = layoutInflater.inflate(UiR.layout.view_proof_hint, null, false) as TextView
        hint.text = value
        return hint
    }

    private fun sectionLabel(name: String): Int = when (name) {
        "window" -> R.string.cw_section_window
        "style" -> R.string.cw_section_style
        else -> R.string.cw_section_preedit
    }

    /** Extra line under a heading, only where the section needs explaining. */
    private fun sectionHint(name: String): Int? = when (name) {
        "style" -> R.string.cw_section_style_hint
        else -> null
    }

    /**
     * Labels by `section/field-path`; unlisted fields never reach here.
     *
     * The `style` rows are the same fields the style screen shows, so their labels come
     * from `editor-ui`'s resources: one spelling, two screens.
     */
    private fun fieldLabel(
        section: String,
        id: String,
    ): Int = when ("$section/$id") {
        "window/insets/vertical" -> R.string.cw_window_insets_vertical
        "window/insets/horizontal" -> R.string.cw_window_insets_horizontal
        "window/item_padding/vertical" -> R.string.cw_window_item_padding_vertical
        "window/item_padding/horizontal" -> R.string.cw_window_item_padding_horizontal
        "window/min_width" -> R.string.cw_window_min_width
        "window/corner_radius" -> R.string.cw_window_corner_radius
        "window/border" -> R.string.cw_window_border
        "window/shadow" -> R.string.cw_window_shadow
        "window/alpha" -> R.string.cw_window_alpha
        "window/foreground/label_font_size" -> R.string.cw_window_label_font
        "window/foreground/text_font_size" -> R.string.cw_window_text_font
        "window/foreground/comment_font_size" -> R.string.cw_window_comment_font
        "preedit/horizontal_padding" -> R.string.cw_preedit_padding
        "preedit/top_start_radius" -> R.string.cw_preedit_top_start
        "preedit/top_end_radius" -> R.string.cw_preedit_top_end
        "preedit/alpha" -> R.string.cw_preedit_alpha
        "preedit/foreground/font_size" -> R.string.cw_preedit_font
        "style/candidate_view_height" -> UiR.string.style_candidate_view_height
        "style/candidate_text_size" -> UiR.string.style_candidate_text_size
        "style/candidate_padding" -> UiR.string.style_candidate_padding
        "style/candidate_spacing" -> UiR.string.style_candidate_spacing
        "style/candidate_border" -> UiR.string.style_candidate_border
        "style/candidate_border_round" -> UiR.string.style_candidate_border_round
        "style/candidate_corner_radius" -> UiR.string.style_candidate_corner_radius
        "style/candidate_text_vertical_bias" -> UiR.string.style_candidate_text_vertical_bias
        else -> R.string.cw_field_unknown
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, CandidateWindowActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
