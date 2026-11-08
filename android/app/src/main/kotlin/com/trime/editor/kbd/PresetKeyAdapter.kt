/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.kbd

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.kbd.PresetKeyEntry
import com.trime.editor.databinding.ItemPresetKeyBinding

/** One action row, with the wording already decided by the screen. */
data class PresetKeyRow(
    val entry: PresetKeyEntry,
    /** What the key does, e.g. `send: BackSpace`; empty when it says nothing. */
    val detail: String,
)

class PresetKeyAdapter(
    private val onOpen: (PresetKeyRow) -> Unit,
) : RecyclerView.Adapter<PresetKeyAdapter.Holder>() {
    private var rows: List<PresetKeyRow> = emptyList()

    fun submit(list: List<PresetKeyRow>) {
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
        val binding = ItemPresetKeyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.bind(rows[position], onOpen)
    }

    class Holder(
        private val binding: ItemPresetKeyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: PresetKeyRow,
            onOpen: (PresetKeyRow) -> Unit,
        ) {
            binding.name.text = row.entry.displayName
            // The id is what actually goes into the file, so it stays visible
            // next to the name the author chose.
            binding.detail.text = if (row.detail.isEmpty()) row.entry.id else "${row.entry.id} · ${row.detail}"
            binding.root.setOnClickListener { onOpen(row) }
        }
    }
}
