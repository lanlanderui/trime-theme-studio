/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.style

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeSchemeWriter
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.ThemeTextFile
import com.trime.editor.ui.DiskBackedEditor
import com.trime.editor.ui.EditorSection
import com.trime.editor.ui.R
import com.trime.editor.ui.proof.PreferenceOutline
import com.trime.editor.ui.proof.ProofChapter
import com.trime.editor.ui.proof.ProofFormat
import com.trime.editor.ui.proof.ProofRowSwitch
import com.trime.editor.ui.proof.ProofRowValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What the fragment asks its activity to do with the preview board. */
interface StyleEditorHost {
    /** Re-reads the theme from disk and redraws the board. */
    fun reloadBoard()

    /** Switches the colour scheme the board is drawn with. */
    fun applyScheme(schemeId: String)

    /** 写盘之后重算样张下面那行统计（未设 / 本次已改）。 */
    fun refreshCaption()
}

/**
 * Edits the `style:` block of a theme file.
 *
 * Every change goes into the YAML itself, not into shared preferences, so the
 * theme stays one portable document. Only the edited line is rewritten — the
 * writer keeps the author's comments, indentation and line endings, and the
 * rest of the file is byte-for-byte what it was.
 *
 * Saves are serialised and take the snapshot the rows were built from as their
 * baseline: if the file changed after this screen read it, the save is refused
 * and the user chooses between reloading and overwriting.
 *
 * A file changed by the other app is *reported* rather than absorbed — see
 * [isFileChanged]. Adopting the new text silently would leave the rows showing
 * one value, the board above showing another, and no way to tell which one the
 * next tap writes.
 */
class StyleEditorFragment :
    PreferenceFragmentCompat(),
    DiskBackedEditor {
    private lateinit var themeId: String
    private lateinit var sourceFile: File

    /** The file's contents the rows were built from; the baseline for conflicts. */
    private var expectedText: String = ""

    /** 这一次会话里成功写盘的次数，以及本主题没写的字段数（见 [caption]）。 */
    private var changedCount = 0
    private var unsetCount = 0

    /**
     * Serialises saves.
     *
     * Each save reads the whole file, edits one line and replaces it, refusing
     * to do so if the contents changed in between. Two saves running at once
     * would therefore reject the second one ("changed outside the app") even
     * though nothing outside touched it.
     */
    private val writeLock = Mutex()

    private val host: StyleEditorHost? get() = activity as? StyleEditorHost

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        themeId = arguments?.getString(ARG_THEME_ID).orEmpty()
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        // The writable copy, not merely "where the theme is": with an empty
        // theme dir the read side falls back to another directory, and a write
        // there would be invisible in the one the user chose.
        val file = ThemeStyleWriter.editableSourceFile(themeId)
        if (file == null) {
            addDisabledRow(R.string.style_no_file)
            return
        }
        sourceFile = file
        rebuild()
    }

    override fun isFileChanged(): Boolean {
        if (!::sourceFile.isInitialized) return false
        val current = ThemeTextFile.read(sourceFile)?.text ?: return false
        return current != expectedText
    }

    /**
     * Re-reads the file, then redraws both the rows and the keyboard above them.
     *
     * The board has to be rebuilt, not repainted: it holds the `Keyboard` the
     * old file described, and a style value can move keys.
     */
    override fun reloadFromDisk() {
        rebuild()
        host?.reloadBoard()
        toast(R.string.style_reload_done)
    }

    /** 样张下面的读数：还有多少字段本主题没写、这次改了几处。 */
    override fun caption(): String = ProofFormat.caption(unsetCount, changedCount)

    /** 目录栏的章节：从刚建好的偏好树里读，不另抄一份清单。 */
    override fun sections(): List<EditorSection> = PreferenceOutline.sectionsOf(preferenceScreen)

    /** Rebuilds the whole screen from the file as it is now. */
    private fun rebuild() {
        val screen = preferenceScreen ?: return
        screen.removeAll()
        val document = ThemeTextFile.read(sourceFile)
        if (document == null) {
            addDisabledRow(R.string.style_no_file)
            return
        }
        expectedText = document.text
        // 本主题没写的字段（`Entry.source == ABSENT`）：它们走默认值。
        unsetCount =
            styleGroups.sumOf { group ->
                group.fields.count {
                    ThemeStyleWriter.readEntry(document.lines, it.key).source == ThemeStyleWriter.Source.ABSENT
                }
            }
        addFileCategory()
        addSchemeCategory()
        styleGroups.forEach { group ->
            val category =
                ProofChapter(requireContext()).apply {
                    title = getString(group.title)
                    setCount(group.fields.size)
                }
            screen.addPreference(category)
            group.fields.forEach { field -> category.addPreference(buildPreference(field, document.lines)) }
        }
    }

    private fun addDisabledRow(titleRes: Int) {
        preferenceScreen?.addPreference(
            Preference(requireContext()).apply {
                title = getString(titleRes)
                isEnabled = false
            },
        )
    }

    /** The two actions that act on the file rather than on a value in it. */
    private fun addFileCategory() {
        val context = requireContext()
        val category = ProofChapter(context).apply { title = getString(R.string.style_group_file) }
        preferenceScreen?.addPreference(category)

        category.addPreference(
            ProofRowValue(context).apply {
                key = KEY_UNDO
                proofName = getString(R.string.style_undo)
                showKey = false
                setValue(sourceFile.name)
                isEnabled = ThemeTextFile.hasBackup(sourceFile)
                setOnPreferenceClickListener {
                    restoreBackup()
                    true
                }
            },
        )
        category.addPreference(
            ProofRowValue(context).apply {
                key = KEY_RELOAD
                proofName = getString(R.string.style_reload)
                showKey = false
                setValue(sourceFile.absolutePath)
                setOnPreferenceClickListener {
                    reloadFromDisk()
                    true
                }
            },
        )
    }

    /**
     * Colour settings folded into one row.
     *
     * A theme can ship dozens of schemes, and listing every one pushes the style
     * fields off screen. Picking here switches the scheme the board is drawn
     * with; the scheme's own colours are edited on their own screen.
     */
    private fun addSchemeCategory() {
        val context = requireContext()
        val schemes = ThemeSchemeWriter.listSchemes(sourceFile)
        if (schemes.isEmpty()) return
        // The active scheme is the one the board is drawn with, so the row has
        // to name that one and not the stored preference: with "follow system"
        // set, the resolver picks a scheme and the preference is not consulted.
        val scope = ColorManager.currentScope()
        val active =
            scope?.activeColorScheme?.let { current ->
                scope.theme.presetColorSchemes.entries.firstOrNull { it.value == current }?.key
            } ?: schemes.firstOrNull()?.id.orEmpty()

        val category = ProofChapter(context).apply { title = getString(R.string.studio_schemes_title) }
        preferenceScreen?.addPreference(category)
        category.addPreference(
            ProofRowValue(context).apply {
                key = KEY_SCHEME
                proofName = getString(R.string.studio_current_scheme)
                proofKey = active
                setValue(schemes.firstOrNull { it.id == active }?.displayName ?: active)
                setOnPreferenceClickListener {
                    showSchemePicker(schemes, active)
                    true
                }
            },
        )
    }

    private fun showSchemePicker(
        schemes: List<ThemeSchemeWriter.Scheme>,
        active: String,
    ) {
        val names = schemes.map { it.displayName }.toTypedArray()
        val checked = schemes.indexOfFirst { it.id == active }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.studio_choose_scheme)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                val scheme = schemes[which]
                if (scheme.id != active) {
                    ColorManager.setColorScheme(scheme.id)
                    // Colours are resolved through the scheme that was just
                    // activated, so a repaint is all the board needs — the
                    // layout did not change.
                    host?.applyScheme(scheme.id)
                    (findPreference<Preference>(KEY_SCHEME) as? ProofRowValue)?.let {
                        it.proofKey = scheme.id
                        it.setValue(scheme.displayName)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildPreference(
        field: StyleField,
        lines: List<String>,
    ): Preference {
        // [lines] is the snapshot this screen was built from, so it is only
        // good for the initial summary; both listeners below read the file
        // again, off the main thread, because it may have been edited elsewhere.
        val entry = ThemeStyleWriter.readEntry(lines, field.key)
        if (field.type == StyleFieldType.BOOL) {
            return ProofRowSwitch(requireContext()).apply {
                key = field.key
                proofName = getString(field.title)
                proofKey = field.key
                setChecked(boolOf(entry, lines))
                setOnPreferenceChangeListener { preference, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val current = readCurrent(field)
                        if (!isAdded || view == null) return@launch
                        val next = !(current?.equals("true", ignoreCase = true) ?: false)
                        (preference as ProofRowSwitch).setChecked(next)
                        commit(field, next.toString())
                    }
                    // Keep the switch under our control until the file agrees.
                    false
                }
            }
        }
        return ProofRowValue(requireContext()).apply {
            key = field.key
            proofName = getString(field.title)
            proofKey = field.key
            // 值就是文件里那个值（或它解析出来的锚点值）；没写这一项时显示「未设置」，
            // 而不是一个空框——空框和「写了空」在界面上分不出来。
            setValue(summaryOf(field, entry, lines), entry.source == ThemeStyleWriter.Source.ABSENT)
            setOnPreferenceClickListener {
                // The snapshot above is only for display; the dialog always
                // starts from what the file says now. Reading it takes a full
                // pass over the theme, so the dialog opens when it returns.
                viewLifecycleOwner.lifecycleScope.launch {
                    val current = readCurrent(field)
                    if (!isAdded || view == null) return@launch
                    if (field.type == StyleFieldType.ENUM) {
                        showEnumDialog(field, current)
                    } else {
                        showScalarDialog(field, current)
                    }
                }
                true
            }
        }
    }

    /**
     * The value the user should see in the input box.
     *
     * For a literal that is the text on the line. For `key_height: *jpgd4` it is
     * whatever `&jpgd4` was defined as — offering the anchor's *name* there
     * would put `jpgd4` into a number field and write it back on OK, turning a
     * working theme into one that cannot be read. Null when the anchor holds a
     * block: those cannot be edited as one value, and an empty box that refuses
     * blank input says so more honestly than a name that looks like a value.
     */
    private suspend fun readCurrent(field: StyleField): String? = withContext(Dispatchers.IO) {
        val lines = ThemeStyleWriter.readLines(sourceFile)
        val entry = ThemeStyleWriter.readEntry(lines, field.key)
        if (entry.source == ThemeStyleWriter.Source.ANCHOR) {
            ThemeStyleWriter.resolveAnchor(lines, entry.value.orEmpty())
        } else {
            entry.value
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::sourceFile.isInitialized) return
        // A file that moved is left alone: the host puts up its banner, and the
        // rows on screen stay the ones this form was built from until the user
        // asks for the new ones. Only a file that is what it was is re-read —
        // that keeps the undo row honest after a save made from elsewhere in
        // this app, and costs one read.
        if (isFileChanged()) return
        val document = ThemeTextFile.read(sourceFile) ?: return
        expectedText = document.text
        styleGroups.forEach { group ->
            group.fields.forEach { field ->
                val entry = ThemeStyleWriter.readEntry(document.lines, field.key)
                bindRow(field, entry, document.lines)
            }
        }
        findPreference<Preference>(KEY_UNDO)?.isEnabled = ThemeTextFile.hasBackup(sourceFile)
    }

    private fun summaryOf(
        field: StyleField,
        entry: ThemeStyleWriter.Entry,
        lines: List<String>,
    ): String = when (entry.source) {
        ThemeStyleWriter.Source.LITERAL -> literalSummary(field, entry.value.orEmpty())
        ThemeStyleWriter.Source.ANCHOR -> anchorSummary(entry, lines)
        ThemeStyleWriter.Source.ABSENT -> getString(R.string.style_summary_absent)
    }

    /** A boolean entry, resolved through its anchor when it has one. */
    private fun boolOf(
        entry: ThemeStyleWriter.Entry,
        lines: List<String>,
    ): Boolean {
        val raw =
            if (entry.source == ThemeStyleWriter.Source.ANCHOR) {
                ThemeStyleWriter.resolveAnchor(lines, entry.value.orEmpty())
            } else {
                entry.value
            }
        return raw?.equals("true", ignoreCase = true) == true
    }

    /** Names the anchor and, when it can be resolved, the number behind it. */
    private fun anchorSummary(
        entry: ThemeStyleWriter.Entry,
        lines: List<String>,
    ): String {
        val name = entry.value.orEmpty()
        val resolved = ThemeStyleWriter.resolveAnchor(lines, name)
        return if (resolved == null) {
            getString(R.string.style_summary_anchor, name)
        } else {
            getString(R.string.style_summary_anchor_value, name, resolved)
        }
    }

    /** An enum value can be a number (`enter_label_mode`); show its label instead. */
    private fun literalSummary(
        field: StyleField,
        value: String,
    ): String {
        if (field.type != StyleFieldType.ENUM || field.optionLabels.size != field.options.size) return value
        val index = field.options.indexOf(value)
        return if (index >= 0) getString(field.optionLabels[index]) else value
    }

    private fun showScalarDialog(
        field: StyleField,
        current: String?,
    ) {
        val input =
            EditText(requireContext()).apply {
                setText(current.orEmpty())
                hint = field.key
                inputType =
                    when (field.type) {
                        StyleFieldType.INT -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED

                        StyleFieldType.FLOAT ->
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or
                                InputType.TYPE_NUMBER_FLAG_DECIMAL

                        else -> InputType.TYPE_CLASS_TEXT
                    }
                setSelection(text.length)
            }
        val container =
            FrameLayout(requireContext()).apply {
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(input)
            }

        AlertDialog.Builder(requireContext())
            .setTitle(field.title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input.text.toString()
                if (validate(field, raw)) commit(field, raw)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEnumDialog(
        field: StyleField,
        current: String?,
    ) {
        val labels =
            if (field.optionLabels.size == field.options.size) {
                field.optionLabels.map { getString(it) }.toTypedArray()
            } else {
                field.options.toTypedArray()
            }
        val checked = field.options.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(field.title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                commit(field, field.options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun validate(
        field: StyleField,
        raw: String,
    ): Boolean {
        if (field.type == StyleFieldType.TEXT) return true
        if (raw.isBlank()) {
            toast(R.string.style_invalid_value)
            return false
        }
        val number = raw.trim().toFloatOrNull()
        if (number == null) {
            toast(R.string.style_invalid_value)
            return false
        }
        val min = field.min
        val max = field.max
        if ((min != null && number < min) || (max != null && number > max)) {
            // The message carries one bounds string: a field may set only one
            // side, and printing 0 for the missing one claimed a minimum that
            // does not exist.
            toast(getString(R.string.style_out_of_range, rangeText(min, max)))
            return false
        }
        return true
    }

    /** The allowed range as shown in the message; an unset side is left out. */
    private fun rangeText(
        min: Float?,
        max: Float?,
    ): String = listOfNotNull(min, max).joinToString(" ~ ", transform = ::formatBound)

    /** Drops a trailing ".0" so a bound reads like the number in the theme file. */
    private fun formatBound(value: Float): String {
        val whole = value.toInt()
        return if (value == whole.toFloat()) whole.toString() else value.toString()
    }

    /**
     * Writes one field, then refreshes its row and the preview.
     *
     * Reading, editing and replacing a several-thousand-line theme is a file
     * job, so it runs off the main thread; only the row and the message come
     * back to it. The row is refreshed from what the writer just wrote instead
     * of reading the file a second time.
     */
    private fun commit(
        field: StyleField,
        raw: String,
    ) {
        val value = if (field.type == StyleFieldType.TEXT) ThemeStyleWriter.formatScalar(raw) else raw.trim()
        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    writeLock.withLock {
                        ThemeStyleWriter.writeEntryChecked(sourceFile, field.key, value, expectedText)
                    }
                }
            // The view may be gone by the time the file is written.
            if (!isAdded || view == null) return@launch
            when (result.outcome) {
                ThemeTextFile.WriteOutcome.WRITTEN -> {
                    // The text the writer produced becomes the baseline for the
                    // next save, so a run of edits never reports a false conflict.
                    expectedText = result.text.orEmpty()
                    // 「本次已改」只数这一次会话里写成功的次数，不参与写盘判断。
                    changedCount++
                    applyWritten(field, value)
                    host?.reloadBoard()
                    host?.refreshCaption()
                }

                ThemeTextFile.WriteOutcome.CONFLICT -> showConflictDialog(field, value)

                ThemeTextFile.WriteOutcome.FAILED -> toast(R.string.style_write_failed)
            }
        }
    }

    /**
     * 把一行重新填成文件里的样子。
     *
     * 行换成纸感之后不再有 `summary`：写盘回来只改 `summary` 会静默失败，
     * 界面上就一直是旧值——而文件已经变了。所以这里按真实类型分派。
     */
    private fun bindRow(
        field: StyleField,
        entry: ThemeStyleWriter.Entry,
        lines: List<String>,
    ) {
        val preference = findPreference<Preference>(field.key) ?: return
        val text = summaryOf(field, entry, lines)
        when (preference) {
            is ProofRowSwitch -> preference.setChecked(boolOf(entry, lines))
            is ProofRowValue -> preference.setValue(text, entry.source == ThemeStyleWriter.Source.ABSENT)
            else -> preference.summary = text
        }
    }

    private fun applyWritten(
        field: StyleField,
        value: String,
    ) {
        bindRow(field, ThemeStyleWriter.writtenEntry(value), emptyList())
        // The save just made a backup, so the row that undoes it has to become
        // tappable now — it was built when there was nothing to undo.
        findPreference<Preference>(KEY_UNDO)?.isEnabled = ThemeTextFile.hasBackup(sourceFile)
        toast(R.string.style_saved)
    }

    /**
     * The file changed after this screen read it.
     *
     * Refusing silently would leave the rows showing values that are no longer
     * on disk, so both answers are offered: read the file again (losing this one
     * edit) or replace it (losing whatever the other writer did).
     */
    private fun showConflictDialog(
        field: StyleField,
        value: String,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.style_conflict_title)
            .setMessage(getString(R.string.style_conflict_body, sourceFile.name))
            .setPositiveButton(R.string.style_conflict_overwrite) { _, _ -> overwrite(field, value) }
            .setNeutralButton(R.string.style_conflict_reload) { _, _ ->
                rebuild()
                host?.reloadBoard()
                toast(R.string.style_reload_done)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Writes without a baseline: the file as it is now plus this one edit. */
    private fun overwrite(
        field: StyleField,
        value: String,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    writeLock.withLock {
                        ThemeStyleWriter.writeEntryChecked(sourceFile, field.key, value, expectedText = null)
                    }
                }
            if (!isAdded || view == null) return@launch
            when (result.outcome) {
                ThemeTextFile.WriteOutcome.WRITTEN -> {
                    expectedText = result.text.orEmpty()
                    applyWritten(field, value)
                    // Other rows may have been changed by whoever wrote the file,
                    // so the whole screen is rebuilt rather than one summary.
                    rebuild()
                    host?.reloadBoard()
                }

                else -> toast(R.string.style_write_failed)
            }
        }
    }

    private fun restoreBackup() {
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { writeLock.withLock { ThemeTextFile.restoreBackup(sourceFile) } }
            if (!isAdded || view == null) return@launch
            if (outcome != ThemeTextFile.WriteOutcome.WRITTEN) {
                toast(R.string.style_undo_unavailable)
                return@launch
            }
            rebuild()
            host?.reloadBoard()
            toast(R.string.style_undo_done)
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    /** For messages that carry arguments; the res-id form cannot format. */
    private fun toast(message: CharSequence) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_THEME_ID = "theme_id"
        private const val KEY_UNDO = "file_undo"
        private const val KEY_RELOAD = "file_reload"
        private const val KEY_SCHEME = "studio_current_scheme"

        fun newInstance(themeId: String): StyleEditorFragment = StyleEditorFragment().apply {
            arguments = Bundle().apply { putString(ARG_THEME_ID, themeId) }
        }
    }
}
