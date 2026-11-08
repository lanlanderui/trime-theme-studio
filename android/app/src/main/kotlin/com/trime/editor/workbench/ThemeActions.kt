/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor.workbench

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.trime.editor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主题文件级的动作：新建 / 复制 / 重命名 / 导入 / 在文件管理器中打开。
 *
 * 设计稿 B1 的 dock 和长按菜单都指向这一组（N-1 / N-2）。工作台原本是只读的，
 * 这里集中放所有「动文件名」的代码，别处只管调：
 *
 * - 文件名规则固定成 `<名字>.trime.yaml`，[displayBase] 负责在 UI 上把
 *   `.trime` 去掉；id 仍是 `nameWithoutExtension`（`名字.trime`），和
 *   [com.osfans.trime.data.theme.ThemeFilesManager.listThemes] 一致。
 * - 只做字节级复制 + 一次 `name:` 行改写，不重序列化 YAML：注释、缩进、
 *   CRLF 全部保留（theme-core 的写盘铁律）。
 * - 目标名冲突时[uniqueBase]加序号，绝不覆盖已有文件。
 */
object ThemeActions {
    const val YAML_SUFFIX = ".trime.yaml"
    private const val ID_SUFFIX = ".trime"
    const val TEMPLATE_ASSET = "templates/template.trime.yaml"

    data class Outcome(
        val ok: Boolean,
        val id: String? = null,
        val message: String = "",
    )

    /** 文件名主体：去掉 `.trime`（id 里还带着它，UI 上不显示）。 */
    fun displayBase(id: String): String = id.removeSuffix(ID_SUFFIX)

    fun fileOf(dir: File, id: String): File = File(dir, "$id.yaml")

    private fun idOf(base: String): String = base + ID_SUFFIX

    /** 已存在就加 2、3……；返回不含后缀的主体。 */
    fun uniqueBase(dir: File, wanted: String): String {
        val clean = wanted.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifEmpty { "主题" }
        if (!File(dir, idOf(clean) + ".yaml").exists()) return clean
        var n = 2
        while (File(dir, idOf("$clean$n") + ".yaml").exists()) n++
        return "$clean$n"
    }

    /**
     * 只换第一处 `name:` 行的值，行内注释、行尾、其余字节原样保留。
     * 模板/副本不改这行的话，两份主题在列表里会同名，分不清谁是谁。
     *
     * 内置模版的 `name:` 行后面还挂着 `# 主题名称…` 的说明，一并保住。
     * 单独留出来给单测：改写必须保住注释、缩进与 CRLF，且只碰第一行。
     */
    fun withName(source: ByteArray, name: String): ByteArray {
        val text = String(source, Charsets.UTF_8)
        val re = Regex("""(?m)^(name:\s*)([^#\r\n]*?)([ \t]*#[^\r\n]*)?$""")
        val match = re.find(text) ?: return source
        val quoted = "\"${name.replace("\"", "\\\"")}\""
        val comment = match.groupValues.getOrNull(3).orEmpty()
        return text.replaceRange(match.range, match.groupValues[1] + quoted + comment).toByteArray(Charsets.UTF_8)
    }

    /** 新建：从随包模板复制一份，并把主题内部的 `name:` 改成用户给的名字。 */
    suspend fun create(context: Context, dir: File, wanted: String): Outcome = withContext(Dispatchers.IO) {
        val base = uniqueBase(dir, wanted)
        val target = File(dir, idOf(base) + ".yaml")
        runCatching {
            val template = context.assets.open(TEMPLATE_ASSET).use { it.readBytes() }
            target.writeBytes(withName(template, base))
        }.fold(
            onSuccess = { Outcome(true, idOf(base), context.getString(R.string.theme_created, base)) },
            onFailure = { Outcome(false, message = context.getString(R.string.theme_failed, it.message.orEmpty())) },
        )
    }

    /** 复制：整份字节复制，只改 `name:` 行。 */
    suspend fun duplicate(context: Context, dir: File, entry: ThemeEntry): Outcome = withContext(Dispatchers.IO) {
        val source = fileOf(dir, entry.id)
        val base = uniqueBase(dir, displayBase(entry.id) + "-副本")
        val target = File(dir, idOf(base) + ".yaml")
        runCatching {
            target.writeBytes(withName(source.readBytes(), base))
        }.fold(
            onSuccess = { Outcome(true, idOf(base), context.getString(R.string.theme_copied, base)) },
            onFailure = { Outcome(false, message = context.getString(R.string.theme_failed, it.message.orEmpty())) },
        )
    }

    enum class RenameError { UNCHANGED, EXISTS, IO }

    data class RenameResult(
        val ok: Boolean,
        val newId: String? = null,
        val error: RenameError? = null,
        val cause: String = "",
    )

    /**
     * 改名的文件级部分，不碰 Android：只改文件名与主题内 `name:` 行，
     * 目标已存在就拒绝，其余字节（注释 / 缩进 / CRLF）原样搬过去。
     * 单独留出来给单测——UI 自动化里输入法会吞文本，成功路径只能靠它钉住。
     */
    fun renameFile(dir: File, oldId: String, wanted: String): RenameResult {
        val clean = wanted.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
        if (clean.isEmpty() || clean == displayBase(oldId)) return RenameResult(false, error = RenameError.UNCHANGED)
        val source = File(dir, "$oldId.yaml")
        val target = File(dir, idOf(clean) + ".yaml")
        if (!source.isFile) return RenameResult(false, error = RenameError.IO, cause = "source missing")
        if (target.exists()) return RenameResult(false, error = RenameError.EXISTS)
        return runCatching {
            target.writeBytes(withName(source.readBytes(), clean))
            source.delete()
            RenameResult(true, idOf(clean))
        }.getOrElse { RenameResult(false, error = RenameError.IO, cause = it.message.orEmpty()) }
    }

    /** 重命名：把 [renameFile] 的结果翻成用户能读的一句话。 */
    suspend fun rename(context: Context, dir: File, entry: ThemeEntry, wanted: String): Outcome = withContext(Dispatchers.IO) {
        val r = renameFile(dir, entry.id, wanted)
        if (r.ok) {
            Outcome(true, r.newId, context.getString(R.string.theme_renamed, r.newId?.let(::displayBase).orEmpty()))
        } else {
            Outcome(
                false,
                message = when (r.error) {
                    RenameError.UNCHANGED -> context.getString(R.string.theme_rename_unchanged)
                    RenameError.EXISTS -> context.getString(R.string.theme_exists, wanted.trim())
                    else -> context.getString(R.string.theme_failed, r.cause)
                },
            )
        }
    }

    /** 导入：SAF 选中的 yaml 复制进主题目录；非主题文件拒收。 */
    suspend fun importFrom(context: Context, dir: File, uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val display = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }.orEmpty()
        val base = uniqueBase(
            dir,
            display.removeSuffix(".yaml").removeSuffix(ID_SUFFIX).ifEmpty { "导入主题" },
        )
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext Outcome(false, message = context.getString(R.string.theme_import_failed))
        val text = String(bytes, Charsets.UTF_8)
        if (!text.contains("preset_color_schemes") && !text.contains("style:")) {
            return@withContext Outcome(false, message = context.getString(R.string.theme_not_theme))
        }
        val target = File(dir, idOf(base) + ".yaml")
        runCatching { target.writeBytes(withName(bytes, base)) }.fold(
            onSuccess = { Outcome(true, idOf(base), context.getString(R.string.theme_imported, base)) },
            onFailure = { Outcome(false, message = context.getString(R.string.theme_failed, it.message.orEmpty())) },
        )
    }

    /**
     * 在文件管理器中打开：FileProvider 给 content URI，交给系统选择器。
     * 没有能处理的应用时退回「复制路径」，保证这个动作永远有结果。
     */
    fun openInFiles(context: Context, file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "theme", uri)
            }
            val launched = runCatching {
                context.startActivity(Intent.createChooser(view, context.getString(R.string.theme_open_in_files)))
                true
            }.getOrDefault(false)
            if (launched) return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", file.absolutePath))
        Toast.makeText(context, context.getString(R.string.theme_path_copied), Toast.LENGTH_SHORT).show()
    }
}
