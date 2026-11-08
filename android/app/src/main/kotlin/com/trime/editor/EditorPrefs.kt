/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.content.Context
import android.content.SharedPreferences
import com.osfans.trime.data.theme.ThemePreferenceStore
import java.io.File

/**
 * The editor's own settings, which are also the library's four theme prefs.
 *
 * The two are the same object on purpose: `ColorManager` resolves the active
 * scheme through `ThemeManager.prefs`, and the editor's preview has to be able
 * to say "show me this scheme" without a second, competing store. Implementing
 * the library interface here means there is exactly one answer to "which
 * scheme is the preview using".
 *
 * The directory is stored by absolute path rather than as a tree `Uri`: every
 * writer in `:theme-core` edits files by `File`, so a SAF picker would produce
 * a path this app cannot open.
 */
object EditorPrefs : ThemePreferenceStore {
    private const val STORE = "editor"
    private const val KEY_THEME_DIR = "theme_dir"
    private const val KEY_SELECTED_THEME = "selected_theme"
    private const val KEY_SCHEME = "normal_mode_color"
    private const val KEY_FOLLOW_SYSTEM = "follow_system_day_night"
    private const val KEY_GUIDE_SEEN = "guide_seen"
    private const val KEY_SKIN = "skin_id"
    private const val KEY_STAGE_BANDS = "stage_bands_visible"

    private const val DEFAULT_SCHEME = "default"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        }
    }

    private fun requirePrefs(): SharedPreferences = requireNotNull(prefs) { "EditorPrefs.install() has not been called" }

    /**
     * The directory the editor reads and writes themes in.
     *
     * Re-read on every access rather than captured once: a theme directory can
     * be changed while the process lives, and a copy taken at startup would
     * keep resolving files from the old one.
     */
    val themeDir: File?
        get() = externalThemeDir.takeIf { it.isNotBlank() }?.let(::File)

    fun setThemeDir(dir: File?) {
        externalThemeDir = dir?.absolutePath.orEmpty()
    }

    override var externalThemeDir: String
        get() = requirePrefs().getString(KEY_THEME_DIR, "").orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_THEME_DIR, value).apply()

    override var selectedTheme: String
        get() = requirePrefs().getString(KEY_SELECTED_THEME, "").orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_SELECTED_THEME, value).apply()

    override var normalModeColor: String
        get() = requirePrefs().getString(KEY_SCHEME, DEFAULT_SCHEME).orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_SCHEME, value).apply()

    override var followSystemDayNight: Boolean
        get() = requirePrefs().getBoolean(KEY_FOLLOW_SYSTEM, false)
        set(value) = requirePrefs().edit().putBoolean(KEY_FOLLOW_SYSTEM, value).apply()

    /**
     * Whether the three-step introduction has been read.
     *
     * Not a `ThemePreferenceStore` member: it says nothing about the theme, only
     * that this device has seen the explanation once. Kept in the same store
     * because a second preferences file for one boolean is worse.
     */
    var guideSeen: Boolean
        get() = requirePrefs().getBoolean(KEY_GUIDE_SEEN, false)
        set(value) = requirePrefs().edit().putBoolean(KEY_GUIDE_SEEN, value).apply()

    /**
     * 当前界面皮肤（`m3-tonal` / `m3-expressive`；旧 `proof` 由 `Skin.of` 回退层叠）。
     *
     * 存的是 id 字符串而不是序号：皮肤会增删，序号会在版本之间错位，而错位的
     * 表现是「升级之后界面突然换了」，用户无法解释。读到未知 id 时由
     * `Skin.of` 回退默认值，这里不做校验——存储层不该知道有哪些皮肤。
     */
    var skinId: String
        get() = requirePrefs().getString(KEY_SKIN, "").orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_SKIN, value).apply()

    /**
     * N-5：B3 舞台是否画候选栏与底部留白。默认 true（所见即所得），
     * 关掉就是设计稿里「只画键盘四行」的干净舞台。
     */
    var stageBandsVisible: Boolean
        get() = requirePrefs().getBoolean(KEY_STAGE_BANDS, true)
        set(value) = requirePrefs().edit().putBoolean(KEY_STAGE_BANDS, value).apply()
}
