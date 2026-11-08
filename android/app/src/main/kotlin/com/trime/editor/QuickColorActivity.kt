/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeDirectory
import com.osfans.trime.data.theme.ThemeFilesManager
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.data.theme.material.MaterialYouStore
import com.osfans.trime.data.theme.material.MaterialYouWriter
import com.osfans.trime.data.theme.material.SavedPalette
import com.osfans.trime.data.theme.material.SystemDynamicColors
import com.trime.editor.ui.EditorActivity
import com.trime.editor.workbench.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 给输入法按键调用的无界面入口。
 *
 * 主题里写一个
 *
 * ```
 * DynamicColor: { command: run, label: 更新配色, option: "com.trime.editor/.QuickApplyActivity" }
 * ```
 *
 * 键盘上点一下就会拉起这个 Activity：读系统壁纸 → 生成明暗两套 → 写回主题 → 收工。
 * 输入法通过 `ComponentName.unflattenFromString` 启动，`option` 里带不了 extra，
 * 所以主题只能自己找：显式 `theme_id` extra（用 `intent:` URI 形式时）→ 编辑器
 * 记住的上次主题 → 目录里最近改过的那份。
 *
 * 只做一件事、只留一句 toast：从键盘上按一下的动作不该弹出一个界面。
 * 写盘走的仍是 [ThemeStyleWriter.editableSourceFile]，`.revision` 会让输入法
 * 下次露面时重新读主题。
 */
abstract class QuickColorActivity : EditorActivity() {
    /** 具体要做的那件事，返回一句结果文案的 string 资源 id。 */
    protected abstract suspend fun act(file: File): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 编辑器按路径读写，Android 11+ 需要「所有文件访问」。新装的包还没授过权时
        // 写盘只会静默失败；直接从按键跳到授权页，用户开完再按一次就能用，
        // 比「弹一句提示然后什么都没发生」少一个「去哪开」的追问。
        if (!StorageAccess.hasAccess(this)) {
            toast(R.string.quick_no_access)
            val appSettings = StorageAccess.settingsIntent(this)
            val opened = appSettings != null && runCatching { startActivity(appSettings) }.isSuccess
            if (!opened) runCatching { startActivity(StorageAccess.fallbackSettingsIntent()) }
            finish()
            return
        }
        val file = resolveFile()
        if (file == null) {
            toast(R.string.quick_no_theme)
            finish()
            return
        }
        lifecycleScope.launch {
            toast(act(file))
            finish()
        }
    }

    private fun resolveFile(): File? {
        ensureThemeDir()
        val ids =
            buildList {
                intent.getStringExtra(EXTRA_THEME_ID)?.takeIf { it.isNotBlank() }?.let(::add)
                EditorPrefs.selectedTheme.takeIf { it.isNotBlank() }?.let(::add)
                ThemeFilesManager.listThemes(ThemeDirectory.themeDir).firstOrNull()?.let { add(it.configId) }
            }
        // 先用只读的 findSourceFile 筛出「真的有这份主题」，再取可写文件：
        // editableSourceFile 会把 shared 里的主题复制到用户目录，不能挨个候选乱试。
        val id = ids.firstOrNull { ThemeStyleWriter.findSourceFile(it) != null } ?: return null
        return ThemeStyleWriter.editableSourceFile(id)
    }

    /**
     * 从没打开过编辑器的人第一次按键：目录还没选过。
     *
     * 只在标准 rime 目录里确实有主题文件时才替他选上（并记住），否则宁可提示
     * 「先打开一个主题」，也不去猜一个可能放错地方的目录。
     */
    private fun ensureThemeDir() {
        if (EditorPrefs.themeDir != null) return
        val fallback = File(Environment.getExternalStorageDirectory(), "rime")
        val hasThemes = fallback.listFiles { _, name -> name.endsWith("trime.yaml") }?.isNotEmpty() == true
        if (hasThemes) EditorPrefs.setThemeDir(fallback)
    }

    private fun toast(
        @StringRes resId: Int,
    ) {
        // applicationContext：Activity 已经 finish 了，toast 还得留着。
        Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val EXTRA_THEME_ID = "theme_id"
    }
}

/** 按当前系统壁纸重算 `material_you` / `material_you_dark` 两套。 */
open class QuickApplyActivity : QuickColorActivity() {
    override suspend fun act(file: File): Int {
        val colors =
            withContext(Dispatchers.Default) {
                val palettes = SystemDynamicColors.read(this@QuickApplyActivity) ?: return@withContext null
                MaterialYouWriter.fromPalettes(palettes)
            } ?: return R.string.quick_apply_no_system

        val result =
            withContext(Dispatchers.IO) {
                MaterialYouWriter.write(
                    file = file,
                    colors = colors,
                    lightName = getString(R.string.material_you_light_name),
                    darkName = getString(R.string.material_you_dark_name),
                )
            }
        return when (result.outcome) {
            ThemeTextFile.WriteOutcome.WRITTEN -> R.string.quick_apply_done
            else -> R.string.quick_apply_failed
        }
    }
}

/** 把主题里正在用的那套动态配色存进 `dynamic_colors.json`（可复用、可备份）。 */
open class QuickSaveActivity : QuickColorActivity() {
    override suspend fun act(file: File): Int {
        val colors =
            withContext(Dispatchers.IO) { MaterialYouWriter.readCurrent(file) }
                ?: return R.string.quick_save_no_palette
        val store = MaterialYouStore.defaultFile() ?: return R.string.quick_save_no_dir
        val now = System.currentTimeMillis()
        val palette =
            SavedPalette(
                id = "my_$now",
                name = getString(R.string.material_you_light_name),
                createdAt = now,
                source = SavedPalette.SOURCE_CURRENT,
                accent =
                colors.light["hilited_key_back_color"]
                    ?: colors.light.values.firstOrNull().orEmpty(),
                light = colors.light,
                dark = colors.dark,
            )
        return withContext(Dispatchers.IO) {
            val known = MaterialYouStore.load(store)
            when {
                MaterialYouStore.contains(known, palette) -> R.string.quick_save_already
                MaterialYouStore.add(palette, store) -> R.string.quick_save_done
                else -> R.string.quick_save_failed
            }
        }
    }
}
