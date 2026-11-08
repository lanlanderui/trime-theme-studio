/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import android.content.Context
import android.os.Environment
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.trime.editor.R
import com.trime.editor.databinding.DialogPickDirBinding
import java.io.File

/**
 * Picks a directory and returns its absolute path.
 *
 * Deliberately not `ACTION_OPEN_DOCUMENT_TREE`: that hands back a `content://`
 * tree that no writer in this app can open with `File`, and the theme editors
 * edit files in place. Browsing the filesystem keeps the path usable.
 *
 * The dialog stays on screen while browsing — going up and down only swaps the
 * list — so reaching a deep directory is not a stack of dialogs to dismiss.
 */
class DirectoryPickerDialog(
    context: Context,
    start: File?,
    private val onPick: (File) -> Unit,
) {
    private val binding = DialogPickDirBinding.inflate(LayoutInflater.from(context))

    private val adapter =
        ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, mutableListOf())

    private var current: File = start?.takeIf { it.isDirectory } ?: defaultRoot()
    private var entries: List<File> = emptyList()

    private val dialog =
        AlertDialog.Builder(context)
            .setTitle(R.string.pick_dir_title)
            .setView(binding.root)
            .setPositiveButton(R.string.pick_dir_use) { _, _ -> onPick(current) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

    init {
        binding.dirs.adapter = adapter
        binding.dirs.setOnItemClickListener { _, _, position, _ ->
            val target = entries.getOrNull(position) ?: return@setOnItemClickListener
            if (target.isDirectory && target.canRead()) {
                current = target
                browse()
            }
        }
        browse()
    }

    fun show() = dialog.show()

    private fun browse() {
        binding.path.text = current.absolutePath
        val parent = current.parentFile
        val children =
            current.listFiles { file -> file.isDirectory && !file.isHidden && file.canRead() }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        entries = listOfNotNull(parent) + children
        val labels =
            entries.map { dir ->
                if (dir == parent) binding.path.context.getString(R.string.pick_dir_up) else dir.name
            }
        adapter.clear()
        adapter.addAll(labels)
        adapter.notifyDataSetChanged()
    }

    private fun defaultRoot(): File {
        val root = File(Environment.getExternalStorageDirectory()?.absolutePath ?: "/")
        val rime = File(root, RIME_DIR)
        return if (rime.isDirectory) rime else root
    }

    private companion object {
        const val RIME_DIR = "rime"
    }
}
