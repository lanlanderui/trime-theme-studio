/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.kbd

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.trime.editor.R

/**
 * Edits one `preset_keys` entry.
 *
 * Only the fields the user actually changed are handed back: a key that never
 * declared `option:` must not grow an empty one because the form had a box for
 * it, and a save that changes nothing must not rewrite the line.
 *
 * The switches are handled the same way — `functional: false` and "no
 * `functional:` at all" mean the same thing to the runtime, so leaving the
 * switch alone leaves the file alone.
 */
object PresetKeyEditorDialog {
    /** Field names as the file spells them; the writer patches by name. */
    private val TEXT_FIELDS =
        listOf(
            "label" to R.string.key_field_label,
            "send" to R.string.key_field_send,
            "select" to R.string.key_field_select,
            "command" to R.string.key_field_command,
            "option" to R.string.key_field_option,
            "text" to R.string.key_field_text,
            "toggle" to R.string.key_field_toggle,
            "states" to R.string.key_field_states,
            "preview" to R.string.key_field_preview,
        )

    private val SWITCH_FIELDS =
        listOf(
            "functional" to R.string.key_field_functional,
            "repeatable" to R.string.key_field_repeatable,
        )

    fun show(
        context: Context,
        id: String,
        current: Map<String, String?>,
        onSave: (Map<String, String?>) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
            }
        val fields = LinkedHashMap<String, EditText>()
        val switches = LinkedHashMap<String, Switch>()

        TEXT_FIELDS.forEach { (name, titleRes) ->
            column.addView(
                TextView(context).apply {
                    text = context.getString(titleRes)
                    textSize = 12f
                    setPadding(0, dp(8), 0, dp(2))
                },
            )
            val field =
                EditText(context).apply {
                    setText(current[name].orEmpty())
                    setTextSize(15f)
                    inputType = InputType.TYPE_CLASS_TEXT
                    setSingleLine(name != "states")
                }
            fields[name] = field
            column.addView(field)
        }

        SWITCH_FIELDS.forEach { (name, titleRes) ->
            val switch =
                Switch(context).apply {
                    text = context.getString(titleRes)
                    textSize = 14f
                    // Absent means false, which is also what the switch shows.
                    isChecked = current[name]?.equals("true", ignoreCase = true) == true
                    setPadding(0, dp(12), 0, dp(2))
                }
            switches[name] = switch
            column.addView(switch)
        }

        val scroll =
            ScrollView(context).apply {
                addView(column)
            }

        AlertDialog.Builder(context)
            .setTitle(id)
            .setView(scroll)
            .setPositiveButton(R.string.key_save) { _, _ ->
                val changes = LinkedHashMap<String, String?>()
                fields.forEach { (name, field) ->
                    val typed = field.text.toString().trim()
                    val old = current[name].orEmpty()
                    // `states` is a YAML list; accepting `中文, 西文` is friendlier
                    // than asking the user to type the brackets.
                    val next =
                        if (name == "states" && typed.isNotEmpty() && !typed.startsWith("[")) {
                            "[ " + typed.split(',', '，').joinToString(", ") { it.trim() } + " ]"
                        } else {
                            typed
                        }
                    when {
                        next == old -> Unit

                        // Clearing a field removes it: an empty `label: ''` is a
                        // pinned empty caption, which is not what "no label" means.
                        next.isEmpty() -> changes[name] = null

                        else -> changes[name] = next
                    }
                }
                switches.forEach { (name, switch) ->
                    val was = current[name]?.equals("true", ignoreCase = true) == true
                    if (switch.isChecked != was) changes[name] = if (switch.isChecked) "true" else "false"
                }
                if (changes.isNotEmpty()) onSave(changes)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
