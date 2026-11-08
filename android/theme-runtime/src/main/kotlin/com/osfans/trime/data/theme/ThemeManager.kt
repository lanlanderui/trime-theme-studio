/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.forEachListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    /**
     * 主题相关的偏好项，由宿主注入：输入法接 `AppPrefs`，编辑器接自己的存储。
     *
     * 不注入就抛异常而不是给默认值——默认值会让「用户选的配色」在下一次启动时
     * 静默变回默认，属于最难发现的那类错误。
     */
    @Volatile
    var preferenceStore: ThemePreferenceStore? = null

    /** The installed store; public so callers can read theme settings without a second lookup path. */
    val prefs: ThemePreferenceStore
        get() = preferenceStore
            ?: error("ThemeManager.preferenceStore has not been installed by the host")

    fun getAllThemes(): List<ThemeItem> {
        val sharedThemes = ThemeFilesManager.listThemes(ThemeDirectory.sharedDir)
        val userThemes = ThemeFilesManager.listThemes(ThemeDirectory.themeDir)
        // Editing a theme copies it out of `shared` into the theme dir, so the
        // same id is then listed by both and the picker showed it twice. Keep
        // one row per id, and let the user copy win: it is the file the editors
        // write to, and the one that shadows the other on load.
        val userById = userThemes.associateBy { it.configId }
        val sharedIds = sharedThemes.mapTo(HashSet()) { it.configId }
        return sharedThemes.map { userById[it.configId] ?: it } +
            userThemes.filter { it.configId !in sharedIds }
    }

    private lateinit var _activeTheme: Theme

    private var _activeFindings: List<ThemeDiagnostics.Finding>? = null

    /**
     * What static checks found in the active theme, or null when it was read
     * from its deployed artifact and never checked. Serves the diagnostics
     * screen: the findings belong to the load that produced the active theme.
     */
    val activeFindings: List<ThemeDiagnostics.Finding>?
        get() {
            ensureActiveTheme()
            return _activeFindings
        }

    /**
     * Why the active theme is not the one in the file, or null when it is.
     *
     * Editors read this to say "your edit will not show up" up front: a theme
     * that failed to expand silently renders librime's deployed artifact, and
     * the symptom is indistinguishable from a broken editor.
     */
    val activeThemeFallback: String?
        get() {
            ensureActiveTheme()
            return _activeThemeFallback
        }

    private var _activeThemeFallback: String? = null

    /**
     * Loads the theme on first use.
     *
     * Synchronized because the warm-up on a background thread races the first
     * caller on the main thread; without it both would decode the theme and one
     * `lateinit` write could be seen half-done.
     */
    @Synchronized
    private fun ensureActiveTheme() {
        if (!::_activeTheme.isInitialized) {
            _activeTheme = evaluateActiveTheme()
        }
    }

    var activeTheme: Theme
        get() {
            ensureActiveTheme()
            return _activeTheme
        }
        private set(value) {
            if (::_activeTheme.isInitialized && _activeTheme == value) return
            _activeTheme = value
            fireChange()
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEachListener { it.onThemeChange(_activeTheme) }
    }

    private data class ResolvedTheme(
        val configId: String,
        val theme: Theme,
        val findings: List<ThemeDiagnostics.Finding>?,
        /** The loader's own words for why the file was not used, if it was not. */
        val fallbackReason: String? = null,
        /** What the load read, so the cache can tell whether it is still valid. */
        val sources: ThemeSourceSnapshot? = null,
    )

    /**
     * Reports a load that had to use librime's deployed artifact.
     *
     * Without this the app looks like it is editing a theme while rendering a
     * different one: the file's own findings are replaced wholesale, so the
     * mismatch has to be stated instead of inferred.
     */
    private fun findingsOf(result: ThemeLoader.ThemeLoadResult.Success): List<ThemeDiagnostics.Finding>? {
        val reason = result.fallbackReason ?: return result.findings
        val finding =
            ThemeDiagnostics.Finding(
                severity = ThemeDiagnostics.Severity.ERROR,
                code = ThemeDiagnostics.Code.THEME_FALLBACK,
                message =
                "Showing librime's deployed artifact: the theme file was not used ($reason). " +
                    "Edits to the file will not appear.",
            )
        return listOf(finding) + result.findings.orEmpty()
    }

    /*
     * Switching themes pays a full read + decode + lint every time, even when
     * nothing changed — going back and forth between two themes is the common
     * case, and it always paid twice. An entry is reused only while everything
     * the load read is still what it read: the theme file, the resources it
     * `__include`s, its `.custom` patch, and the deployed artifact it may have
     * fallen back to. Checking that costs a few `stat` calls; the decode it
     * saves costs milliseconds.
     */
    private data class CachedTheme(
        val resolved: ResolvedTheme,
        /** Files the load read; the entry is valid only while they are unchanged. */
        val sources: ThemeSourceSnapshot?,
        /** [ThemeTextFile.writeGeneration] when the load ran. */
        val generation: Long,
    ) {
        fun isCurrent(): Boolean = generation == ThemeTextFile.writeGeneration &&
            (sources == null || sources.isCurrent { ThemeLoader.findSourceFile(it) })
    }

    private val loadedThemes =
        object : LinkedHashMap<String, CachedTheme>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTheme>) = size > 3
        }

    /**
     * Guards [loadedThemes]: decoding runs on [Dispatchers.IO] while the first
     * read runs on the main thread, and an access-ordered map cannot take
     * concurrent reads and writes. Only existence and timestamps are checked
     * while it is held, never file contents.
     */
    private val cacheLock = Any()

    private fun cachedTheme(id: String): ResolvedTheme? = synchronized(cacheLock) {
        val entry = loadedThemes[id]
        if (entry != null && entry.isCurrent()) {
            entry.resolved
        } else {
            loadedThemes.remove(id)
            null
        }
    }

    private fun cacheTheme(id: String, resolved: ResolvedTheme) {
        // A fallback answers with another theme's id; caching it under the
        // requested id would pin it there, since nothing about that entry
        // changes when the missing file finally appears.
        if (resolved.configId != id) return
        synchronized(cacheLock) {
            loadedThemes[id] = CachedTheme(resolved, resolved.sources, ThemeTextFile.writeGeneration)
        }
    }

    private fun getThemeById(id: String): ResolvedTheme {
        cachedTheme(id)?.let { return it }
        val loaded = loadThemeUncached(id)
        cacheTheme(id, loaded)
        return loaded
    }

    private fun loadThemeUncached(id: String): ResolvedTheme {
        when (val result = ThemeLoader.loadTheme(id)) {
            is ThemeLoader.ThemeLoadResult.Success ->
                return ResolvedTheme(id, result.theme, findingsOf(result), result.fallbackReason, result.sources)

            is ThemeLoader.ThemeLoadResult.Failure -> Timber.w(result.error)
        }

        if (id != "trime") {
            when (val result = ThemeLoader.loadTheme("trime")) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    Timber.w("Theme '$id' is unavailable, fallback to default theme 'trime'")
                    return ResolvedTheme("trime", result.theme, findingsOf(result), result.fallbackReason, result.sources)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> Timber.w(result.error)
            }
        }

        var lastFailure: ThemeLoader.ThemeLoadError? = null
        for (fallbackId in getAllThemes().map { it.configId }.distinct()) {
            when (val result = ThemeLoader.loadTheme(fallbackId)) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    Timber.w("Theme '$id' is unavailable, fallback to available theme '$fallbackId'")
                    return ResolvedTheme(fallbackId, result.theme, findingsOf(result), result.fallbackReason, result.sources)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> lastFailure = result.error
            }
        }

        Timber.w(lastFailure, "No valid theme available")
        error("No valid theme available")
    }

    private fun evaluateActiveTheme(): Theme {
        val selectedThemeId = prefs.selectedTheme
        val resolvedTheme = getThemeById(selectedThemeId)
        val newTheme = resolvedTheme.theme
        if (resolvedTheme.configId != selectedThemeId) {
            prefs.selectedTheme = resolvedTheme.configId
        }
        applyTheme(resolvedTheme)
        return newTheme
    }

    /**
     * Directory the active theme's assets were resolved from.
     *
     * Picking another external theme directory can leave the config byte for
     * byte identical while `fonts/` and `backgrounds/` next to it differ, so a
     * structurally equal theme still has to be re-attached when this moves.
     */
    private var activeThemeDir: String? = null

    private fun applyTheme(resolvedTheme: ResolvedTheme) {
        val theme = resolvedTheme.theme
        // The findings describe the file this load read; they are not tied to the
        // views, so they are refreshed even when the theme itself is unchanged.
        _activeFindings = resolvedTheme.findings
        _activeThemeFallback = resolvedTheme.fallbackReason
        val themeDir = ThemeDirectory.themeDir?.absolutePath.orEmpty()
        val sameTheme = ::_activeTheme.isInitialized && _activeTheme == theme
        // A structurally equal theme suppresses the change notification below, so the
        // UI tree keeps its views and their injected scope. Replace neither the
        // caches nor the scope in that case, or later scheme changes would update
        // the new global scope while existing views still read the old one.
        if (sameTheme && themeDir == activeThemeDir) return
        activeThemeDir = themeDir
        ColorManager.attachTheme(theme)
        if (sameTheme) {
            // Same config, different assets: the scope has to be replaced and the
            // views have to rebind, so the change is announced despite equality.
            _activeTheme = theme
            fireChange()
        } else {
            activeTheme = theme
        }
    }

    fun init(configuration: Configuration) {
        ensureActiveTheme()
        ColorManager.init(configuration)
    }

    /**
     * Switches to theme [configId], falling back when it is unavailable.
     * Loading runs on [Dispatchers.IO]; state changes and listener callbacks run on the main thread.
     * @return the config id actually in effect; differs from [configId] when a fallback was used.
     */
    suspend fun selectTheme(configId: String): String {
        val resolvedTheme = withContext(Dispatchers.IO) { getThemeById(configId) }
        return withContext(Dispatchers.Main.immediate) {
            applyTheme(resolvedTheme)
            prefs.selectedTheme = resolvedTheme.configId
            resolvedTheme.configId
        }
    }

    /**
     * Reloads the active theme if the files it was decoded from changed.
     *
     * The editor is a separate app writing the same `trime.yaml`, so "the theme
     * changed" arrives as a file changing, never as a call. Every screen that
     * draws the keyboard asks this when it comes to the front; a `true` answer
     * means the change was applied and the listeners were told, which is what
     * rebuilds the views.
     *
     * Cheap when nothing changed — a `stat` per source file and a hash of each
     * — and deliberately silent when the staleness probe itself fails: an
     * unreadable theme is a reload that reports its own error.
     *
     * @return whether the theme was reloaded.
     */
    suspend fun reloadIfStale(): Boolean {
        // Nothing loaded yet means nothing cached, and the next normal access
        // reads the file anyway.
        if (!::_activeTheme.isInitialized) return false
        val id = prefs.selectedTheme
        val stale =
            withContext(Dispatchers.IO) {
                synchronized(cacheLock) {
                    val entry = loadedThemes[id] ?: return@synchronized false
                    !entry.isCurrent()
                }
            }
        if (!stale) return false
        selectTheme(id)
        return true
    }

    // 「同步到设备加密存储」是输入法自己的启动迁移逻辑（它要读 DeviceProtected 的
    // SharedPreferences），编辑器不需要，所以留在宿主侧，共享层不碰 Context。
}
