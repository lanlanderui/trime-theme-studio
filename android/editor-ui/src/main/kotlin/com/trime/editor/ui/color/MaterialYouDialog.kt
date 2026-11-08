/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.ui.color

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.data.theme.ColorLiteral
import com.osfans.trime.data.theme.material.MaterialYouColors
import com.osfans.trime.data.theme.material.MaterialYouWriter
import com.osfans.trime.data.theme.material.SystemDynamicColors
import com.trime.editor.ui.R
import com.trime.editor.ui.dp

/**
 * Asks where the palette should come from, and shows what it will look like.
 *
 * Two sources, because they are not the same thing: Android 12+ publishes the
 * wallpaper's own five families, while a seed colour is expanded into five
 * families this app derives. The first is "my phone's colours", the second is
 * "these colours", and only the first is unavailable on older systems.
 */
object MaterialYouDialog {
    /** What the dialog decided to generate, and what to remember about it. */
    data class Generated(
        val colors: MaterialYouColors,
        val accent: Int,
        /** `system` or `seed`, as the favourites file spells it. */
        val source: String,
    )

    /** The keys the two preview strips show: surface, key, caption, accent, highlight. */
    private val PREVIEW_KEYS =
        listOf(
            "back_color",
            "key_back_color",
            "key_text_color",
            "on_key_back_color",
            "hilited_candidate_back_color",
        )

    fun show(
        context: Context,
        initialSeed: Int,
        onConfirm: (Generated) -> Unit,
    ) {
        val systemPalettes = SystemDynamicColors.read(context)
        val systemSeed = SystemDynamicColors.seed(context)
        val systemColors = systemPalettes?.let(MaterialYouWriter::fromPalettes)

        val picker =
            ColorPickerView(context).apply {
                setColor(systemSeed ?: initialSeed)
            }
        val systemButton =
            RadioButton(context).apply {
                id = View.generateViewId()
                setText(R.string.material_you_source_system)
                isChecked = systemColors != null
                isEnabled = systemColors != null
            }
        val seedButton =
            RadioButton(context).apply {
                id = View.generateViewId()
                setText(R.string.material_you_source_seed)
                isChecked = systemColors == null
            }
        val sources =
            RadioGroup(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(systemButton)
                addView(seedButton)
            }

        val lightRow = swatchRow(context)
        val darkRow = swatchRow(context)

        fun current(): Generated = if (systemButton.isChecked && systemColors != null) {
            Generated(systemColors, systemSeed ?: initialSeed, SOURCE_SYSTEM)
        } else {
            Generated(MaterialYouWriter.fromSeed(picker.color), picker.color, SOURCE_SEED)
        }

        fun render() {
            val generated = current()
            fill(lightRow, context, generated.colors.light)
            fill(darkRow, context, generated.colors.dark)
        }

        sources.setOnCheckedChangeListener { _, _ -> render() }
        picker.onColorChanged = {
            // Dragging the picker is a statement about where the colours come
            // from, so the source follows instead of silently ignoring it.
            if (!seedButton.isChecked) seedButton.isChecked = true
            render()
        }

        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val horizontal = context.dp(20)
                setPadding(horizontal, context.dp(4), horizontal, 0)
                addView(sources, matchWidth())
                if (systemColors == null) {
                    addView(
                        TextView(context).apply {
                            setText(R.string.material_you_source_unavailable)
                            textSize = 12f
                        },
                        matchWidth(),
                    )
                }
                addView(picker, matchWidth())
                addView(caption(context, R.string.material_you_preview_light), matchWidth())
                addView(lightRow, matchWidth())
                addView(caption(context, R.string.material_you_preview_dark), matchWidth())
                addView(darkRow, matchWidth())
            }

        render()
        AlertDialog.Builder(context)
            .setTitle(R.string.material_you_title)
            .setView(body)
            .setPositiveButton(R.string.material_you_write) { _, _ -> onConfirm(current()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun swatchRow(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, context.dp(2), 0, context.dp(6))
    }

    private fun caption(
        context: Context,
        text: Int,
    ): TextView = TextView(context).apply {
        setText(text)
        textSize = 12f
        setPadding(0, context.dp(8), 0, 0)
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /** Re-draws one strip; the colours are the scheme's own, in table order. */
    private fun fill(
        row: LinearLayout,
        context: Context,
        colors: Map<String, String>,
    ) {
        row.removeAllViews()
        PREVIEW_KEYS.forEach { key ->
            val argb = colors[key]?.let { ColorLiteral.parse(it) }
            row.addView(
                View(context).apply {
                    background = context.checkerSwatch(argb ?: 0)
                },
                LinearLayout.LayoutParams(0, context.dp(28), 1f).apply { marginEnd = context.dp(4) },
            )
        }
    }

    const val SOURCE_SYSTEM = "system"
    const val SOURCE_SEED = "seed"
}
