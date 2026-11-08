/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.trime.editor.databinding.ItemThemeBinding

/**
 * 「其余主题」的行。
 *
 * 一行只说三件事：它叫什么、它是哪个文件、它长什么样。长相用左侧 44dp 方块表示——
 * 方块填的是**这份主题自己的第一个色**，字取对比色，所以浅色主题上是黑字、深色主题上是白字。
 * 这和旧版「一条 46 色细带」的取舍不同：细带能看出配色全貌，但读不出「深浅」，
 * 而这一页要回答的正是「我要打开的是哪一份」。
 *
 * 当前主题不在这里——它在工作台的英雄区（`MainActivity.present`）。
 */
class ThemeCardAdapter(
    private val onOpen: (ThemeEntry) -> Unit,
    private val onLongClick: (ThemeEntry, View) -> Unit = { _, _ -> },
) : RecyclerView.Adapter<ThemeCardAdapter.Holder>() {
    private var entries: List<ThemeEntry> = emptyList()

    fun submit(list: List<ThemeEntry>) {
        val previous = entries
        entries = list
        DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previous.size

                override fun getNewListSize(): Int = list.size

                override fun areItemsTheSame(
                    oldPos: Int,
                    newPos: Int,
                ): Boolean = previous[oldPos].id == list[newPos].id

                override fun areContentsTheSame(
                    oldPos: Int,
                    newPos: Int,
                ): Boolean = previous[oldPos] == list[newPos]
            },
        ).dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding = ItemThemeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.bind(entries[position])
    }

    inner class Holder(
        private val binding: ItemThemeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: ThemeEntry) {
            binding.name.text = entry.name
            binding.meta.text = entry.id
            binding.block.text = entry.name.take(1)
            binding.failure.text = entry.failure
            binding.failure.visibility = if (entry.failure == null) View.GONE else View.VISIBLE

            entry.swatches.firstOrNull()?.let { color ->
                binding.block.background = blockDrawable(color)
                binding.block.setTextColor(contrastOn(color))
            }
            binding.root.setOnClickListener { onOpen(entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry, binding.root)
                true
            }
        }

        /** 方块：16dp 圆角，比列表里的色块更方——它是「一本书的封面」，不是色票。 */
        private fun blockDrawable(
            color: Int,
        ): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * binding.root.resources.displayMetrics.density
            setColor(color)
        }

        /**
         * 方块上的字用黑还是白。
         *
         * 用感知亮度（0.299/0.587/0.114）而不是 HSL 的 L：主题色经常是高饱和的
         * 中亮度色（比如 #3D5A66），HSL 的 L 会判成「深色 → 白字」，
         * 但白字在它上面其实发灰；亮度判据更接近眼睛看到的结果。
         */
        private fun contrastOn(color: Int): Int {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            return if (luminance > 0.6f) Color.BLACK else Color.WHITE
        }
    }
}
