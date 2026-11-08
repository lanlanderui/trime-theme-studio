// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.forEachListener
import com.osfans.trime.util.isNightMode
import timber.log.Timber

/**
 * Global entry point for the colors and drawables of the active theme.
 *
 * The activation state itself lives in the current [ThemeScope], which
 * [attachTheme] replaces on every theme switch; scheme switches update the
 * same scope in place. String-keyed lookups below ([getColor], [getDrawable])
 * resolve through that scope so that keys only a theme can define (per-key
 * keyboard colors, image-valued entries) keep working, while [activeColorScheme]
 * and the scope's [ThemeScope.colors] expose the typed view for UI code.
 */
object ColorManager {
    private var scope: ThemeScope? = null
    private val prefs = ThemeManager.prefs

    private var isNightMode = false

    val activeColorScheme: ColorScheme
        get() = requireNotNull(requireScope().activeColorScheme) { "ColorManager is not initialized" }

    private var bitmapCache: LruCache<String, Bitmap>? = null

    private var generation = 0L

    /** Ceiling for one decoded background; see [decodeBitmap]. */
    private const val MAX_IMAGE_PIXELS = 16_777_216L

    /**
     * Bumped whenever the active scheme changes. Scheme-dependent caches
     * (e.g. per-key colors in Key) re-resolve when it moves.
     */
    val colorGeneration: Long
        get() = generation

    fun interface OnColorChangeListener {
        fun onColorChange(theme: Theme)
    }

    private val onChangeListeners = WeakHashSet<OnColorChangeListener>()

    fun addOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEachListener { it.onColorChange(requireScope().theme) }
    }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        activateScheme(notify = false)

        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = maxMemory / 8
        bitmapCache =
            object : LruCache<String, Bitmap>(cacheSize.toInt()) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount / 1024
            }
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        isNightMode = isNight
        activateScheme(notify = true)
    }

    /** The current theme scope, or null before the first theme is attached. */
    fun currentScope(): ThemeScope? = scope

    /**
     * Attaches a theme, replacing the current scope. Theme switches notify
     * through ThemeManager, so no color listener fires here.
     */
    fun attachTheme(theme: Theme) {
        bitmapCache?.evictAll()
        // Keyed by scope identity, so every theme switch would otherwise leave
        // its resolutions behind — and a theme dir switch would keep serving
        // the paths resolved from the previous one.
        resolvedImagePaths.clear()
        scope = ThemeScope(theme)
        activateScheme(notify = false)
    }

    /**
     * Switches to the scheme [schemeId] and remembers it as the user's choice.
     * The scheme is resolved from the preference afterwards, so the day/night
     * links a scheme may declare are honoured, like an automatic switch.
     */
    fun setColorScheme(schemeId: String) {
        prefs.normalModeColor = schemeId
        activateScheme(notify = true)
    }

    private fun requireScope(): ThemeScope = requireNotNull(scope) { "ColorManager is not initialized" }

    private fun resolveActiveScheme(theme: Theme): ColorScheme = ColorSchemeResolver.resolve(
        schemes = theme.presetColorSchemes,
        selectedSchemeId = prefs.normalModeColor,
        followSystemDayNight = prefs.followSystemDayNight,
        isNightMode = isNightMode,
    )

    /**
     * Re-resolves the active scheme from the prefs and current theme, or
     * activates the given one. Listener notification fires only when the
     * scheme actually changed.
     */
    private fun activateScheme(
        scheme: ColorScheme? = null,
        notify: Boolean,
    ) {
        val activeScope = scope ?: return
        val target = scheme ?: resolveActiveScheme(activeScope.theme)
        if (activeScope.activeColorScheme == target) return
        activeScope.updateScheme(target)
        generation++
        if (notify) fireChange()
    }

    private fun backgroundFolder(scope: ThemeScope) = scope.theme.style.backgroundFolder

    /**
     * Resolves a color key against the given scope. Exposed so UI code can
     * look up keys only a theme can define through an injected scope.
     */
    @ColorInt
    internal fun resolveColor(
        scope: ThemeScope,
        key: String,
    ): Int {
        val tableEntry = ColorKey.from(key)?.let { scope.colorTable?.get(it) }
        if (tableEntry is ColorTable.Value.Color) return tableEntry.argb
        // Keys defined only by a theme resolve through the same chain rules.
        val scheme = requireNotNull(scope.activeColorScheme)
        val raw = ColorTable.resolveRaw(key, scheme, scope.theme.fallbackColors)
        return try {
            if (raw == null) throw IllegalArgumentException("$key not found")
            ColorUtils.parseColor(raw)
        } catch (_: IllegalArgumentException) {
            ColorUtils.parseColor(key)
        }
    }

    /** Resolves a drawable key (color or image asset) against the given scope. */
    internal fun resolveDrawable(
        scope: ThemeScope,
        key: String,
    ): Drawable? {
        val tableEntry = ColorKey.from(key)?.let { scope.colorTable?.get(it) }
        if (tableEntry != null) {
            return when (tableEntry) {
                is ColorTable.Value.Color -> GradientDrawable().apply { setColor(tableEntry.argb) }
                is ColorTable.Value.Image -> imageDrawable(scope, tableEntry.path)
                ColorTable.Value.None -> parseDrawable(scope, key)
            }
        }
        // Keys defined only by a theme resolve through the same chain rules.
        val scheme = requireNotNull(scope.activeColorScheme)
        val raw = ColorTable.resolveRaw(key, scheme, scope.theme.fallbackColors)
        return parseDrawable(scope, raw ?: key)
    }

    private fun parseDrawable(
        scope: ThemeScope,
        value: String,
    ): Drawable? {
        if (value.isEmpty()) return null
        if (ColorTable.isImageValue(value)) return imageDrawable(scope, value)
        val color = runCatching { ColorUtils.parseColor(value) }.getOrDefault(Color.TRANSPARENT)
        return GradientDrawable().apply { setColor(color) }
    }

    private fun imageDrawable(
        scope: ThemeScope,
        value: String,
    ): Drawable? {
        val path = resolveImageFilePath(scope, value)
        val bitmap =
            bitmapCache?.get(path)
                ?: decodeBitmap(path)?.also {
                    bitmapCache?.put(path, it)
                } ?: return null
        if (path.endsWith(".9.png")) {
            val chunk = bitmap.ninePatchChunk
            return if (NinePatch.isNinePatchChunk(chunk)) {
                // for compiled nine patch image
                NinePatchDrawable(Resources.getSystem(), bitmap, chunk, Rect(), null)
            } else {
                // for source nine patch image
                NinePatchBitmapFactory.createNinePatchDrawable(Resources.getSystem(), bitmap)
            }
        }
        return bitmap.toDrawable(Resources.getSystem())
    }

    /**
     * Decodes an image asset, halving it when it is large enough to matter.
     *
     * A background is a full-bleed picture, and a theme author may well ship one
     * at camera resolution: 8000×6000 is 190 MB once decoded, which the cache
     * then keeps. The limit sits far above any screen this runs on, so ordinary
     * backgrounds decode exactly as before; only absurd ones are subsampled.
     */
    private fun decodeBitmap(path: String): Bitmap? {
        // A nine-patch carries its stretch markers in the outermost pixels, so
        // it must not be resampled whatever its size.
        if (path.endsWith(".9.png")) return BitmapFactory.decodeFile(path)
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MAX_IMAGE_PIXELS) {
            sample *= 2
        }
        if (sample > 1) {
            Timber.w("Subsampling %s by %d: %d×%d is over the limit", path, sample, bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /*
     * Looked up on every draw, so the existence checks happen once per value:
     * two `exists()` calls on external storage per frame is pure waste, and the
     * answer cannot change without the theme being reloaded (new instance).
     */
    private val resolvedImagePaths = HashMap<String, String>()

    private fun resolveImageFilePath(
        scope: ThemeScope,
        value: String,
    ): String = resolvedImagePaths.getOrPut("$scope/$value") {
        val dir = ThemeDirectory.themeDir ?: return@getOrPut value
        val default = dir.resolve("backgrounds/${backgroundFolder(scope)}/$value")
        if (!default.exists()) {
            val fallback = dir.resolve("backgrounds/$value")
            if (fallback.exists()) return@getOrPut fallback.absolutePath
        }
        default.absolutePath
    }

    @ColorInt
    fun getColor(key: String): Int = resolveColor(requireScope(), key)

    fun getDrawable(key: String): Drawable? = resolveDrawable(requireScope(), key)

    internal fun resolveDecorDrawable(
        scope: ThemeScope,
        colorKey: String,
        borderColorKey: String?,
        borderPx: Int,
        cornerRadius: Float,
        alpha: Int,
    ): Drawable? = when (val drawable = resolveDrawable(scope, colorKey)) {
        is GradientDrawable ->
            drawable.also {
                it.cornerRadius = cornerRadius
                it.alpha = MathUtils.clamp(alpha, 0, 255)
                if (!borderColorKey.isNullOrEmpty()) {
                    try {
                        val borderColor = resolveColor(scope, borderColorKey)
                        it.setStroke(borderPx, borderColor)
                    } catch (_: Exception) {
                    }
                }
            }

        else -> drawable?.also { it.alpha = MathUtils.clamp(alpha, 0, 255) }
    }

    fun getDecorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = resolveDecorDrawable(requireScope(), colorKey, borderColorKey, borderPx, cornerRadius, alpha)
}
