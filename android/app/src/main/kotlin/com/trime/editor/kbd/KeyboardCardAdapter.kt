/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.kbd

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.kbd.KeyboardEntry
import com.trime.editor.databinding.ItemKeyboardBinding

/**
 * One layout, with the wording already decided.
 *
 * The badge text is built by the screen rather than here: it comes from string
 * resources, and an adapter that resolves strings is an adapter that cannot be
 * reused for the picker this list will grow into.
 */
data class KeyboardRow(
    val entry: KeyboardEntry,
    /** `8 行 · 42 键`, the two numbers that describe a layout at a glance. */
    val meta: String,
    /** Why this card is not a plain editable layout, or null. */
    val badge: String?,
)

class KeyboardCardAdapter(
    private val onOpen: (KeyboardRow) -> Unit,
) : RecyclerView.Adapter<KeyboardCardAdapter.Holder>() {
    private var rows: List<KeyboardRow> = emptyList()

    fun submit(list: List<KeyboardRow>) {
        val previous = rows
        rows = list
        DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previous.size

                override fun getNewListSize(): Int = list.size

                override fun areItemsTheSame(
                    oldPos: Int,
                    newPos: Int,
                ): Boolean = previous[oldPos].entry.id == list[newPos].entry.id

                override fun areContentsTheSame(
                    oldPos: Int,
                    newPos: Int,
                ): Boolean = previous[oldPos] == list[newPos]
            },
        ).dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding = ItemKeyboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.bind(rows[position], onOpen)
    }

    class Holder(
        private val binding: ItemKeyboardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: KeyboardRow,
            onOpen: (KeyboardRow) -> Unit,
        ) {
            binding.thumb.config = row.entry.layout
            binding.name.text = row.entry.displayName
            binding.meta.text = row.meta
            binding.badge.text = row.badge
            binding.badge.visibility = if (row.badge == null) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onOpen(row) }
        }
    }
}
