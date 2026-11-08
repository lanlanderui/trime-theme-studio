/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.graphics.drawable.Drawable
import com.osfans.trime.util.ColorUtils
import timber.log.Timber

/**
 * The runtime state of an activated theme: its config plus the currently
 * active color scheme, the pre-compiled [ColorTable] and the typed [colors]
 * view over it.
 *
 * A scope outlives scheme switches — switching schemes only rebuilds the
 * table and [colors] — but not theme switches: activating another theme
 * replaces the scope (see [ColorManager.attachTheme]). Views must therefore
 * re-read colors from [colors] (or through [ColorManager]) every time they
 * bind or draw; values read once at construction go stale on scheme changes.
 */
class ThemeScope(
    val theme: Theme,
    private val parseColor: (String) -> Int? = { value ->
        runCatching { ColorUtils.parseColor(value) }.getOrNull()
    },
) {
    /** The currently active color scheme, or null before the first activation. */
    var activeColorScheme: ColorScheme? = null
        private set

    private var colorsValue: ThemeColors? = null

    internal var colorTable: ColorTable? = null
        private set

    /** Typed view over the colors of the active scheme. */
    val colors: ThemeColors
        get() = requireNotNull(colorsValue) { "ThemeScope is not initialized" }

    /**
     * Activates [scheme], rebuilding the color table and [colors].
     *
     * Normal switching goes through [ColorManager], which also keeps the
     * preferences and listeners in step; this is the primitive it and the
     * editors' preview scopes use.
     */
    fun updateScheme(scheme: ColorScheme) {
        activeColorScheme = scheme
        val table = ColorTable.resolve(scheme, theme.fallbackColors, parseColor)
        colorTable = table
        colorsValue = ThemeColors(table)
        // Values the theme file cannot resolve are reported once at load, per
        // scheme (see ThemeDiagnostics); this is the demand-driven remainder.
        if (table.unresolvedKeys.isNotEmpty()) {
            Timber.w("Unknown color key: %s", table.unresolvedKeys.joinToString { it.key })
        }
    }

    /** Resolves a color key through this scope; theme-only keys resolve via the fallback chain. */
    fun color(key: String): Int = ColorManager.resolveColor(this, key)

    /** Resolves a drawable key (a color or an image asset) through this scope. */
    fun drawable(key: String): Drawable? = ColorManager.resolveDrawable(this, key)

    /**
     * Builds a decorated drawable (rounded corners, optional border) from the
     * color or image asset resolved for [colorKey] in this scope.
     */
    fun decorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = ColorManager.resolveDecorDrawable(this, colorKey, borderColorKey, borderPx, cornerRadius, alpha)

    companion object {
        /**
         * A scope over [theme], painted with the colours the active theme is
         * currently using.
         *
         * The theme editors preview a draft that has not been written to disk
         * yet, so they cannot use [ColorManager]'s scope — that one is bound to
         * the theme the IME is running. Borrowing the active scheme keeps a
         * preview in the same colours the keyboard is in, while [theme] supplies
         * the draft's layout.
         *
         * Returns null before the first theme activation, when there is no
         * scheme to borrow.
         */
        fun previewScopeOf(theme: Theme): ThemeScope? = ColorManager.currentScope()?.let { active ->
            val scheme = active.activeColorScheme ?: return@let null
            ThemeScope(theme).also { it.updateScheme(scheme) }
        }
    }
}
