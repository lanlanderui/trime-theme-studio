/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * The wallpaper-derived palettes Android 12+ publishes as resources.
 *
 * Reading them is the only way to get *the* system colours: the platform does
 * not expose the wallpaper's seed, only the finished tonal palettes. Below
 * Android 12 there are no such resources, and [read] returns null rather than
 * inventing an approximation — a "dynamic" colour that is not dynamic would be
 * worse than a disabled button.
 */
object SystemDynamicColors {
    /**
     * Whether the platform publishes wallpaper palettes.
     *
     * Annotated so lint accepts `if (!isAvailable) return` as an API-level
     * guard for the resource lookups below — without it, every read is reported
     * as a call into API 31 on a minSdk 21 build.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * The tones the platform ships.
     *
     * Eleven decades plus the two the surfaces need — which is why a generated
     * scheme can ask for 99 and 95 and get the system's own colour instead of a
     * re-cut one.
     */
    private val PUBLISHED_TONES = TONE_STOPS + listOf(99, 95)

    /** The palettes, or null when the platform does not publish any. */
    fun read(context: Context): MaterialPalettes? {
        if (!isAvailable) return null
        val tones =
            PaletteRole.entries.associateWith { role ->
                PUBLISHED_TONES.associateWith { tone -> colorOf(context, role, tone) }
            }
        return runCatching { MaterialPalettes.fromTones(tones) }.getOrNull()
    }

    /**
     * A colour to show as "this is what the system gave us".
     *
     * Accent 1's 40 is the primary accent in light mode, which is the single
     * most recognisable swatch of the wallpaper palette.
     */
    @ColorInt
    fun seed(context: Context): Int? {
        if (!isAvailable) return null
        return runCatching { ContextCompat.getColor(context, resource(PaletteRole.PRIMARY, 40)) }.getOrNull()
    }

    @ColorInt
    @RequiresApi(Build.VERSION_CODES.S)
    private fun colorOf(
        context: Context,
        role: PaletteRole,
        tone: Int,
    ): Int = ContextCompat.getColor(context, resource(role, tone))

    /**
     * The resource a role/tone pairs with.
     *
     * The platform numbers these by *lightness from white*, not by tone:
     * `system_accent1_0` is white (tone 100) and `system_accent1_1000` is black
     * (tone 0), so tone 40 is `system_accent1_600` and tone 90 is `_100`.
     * Reading the suffix as a tone puts every colour at the wrong end — a dark
     * scheme comes out light, which is exactly how this was found.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun resource(
        role: PaletteRole,
        tone: Int,
    ): Int {
        val index = STOP_SUFFIXES.indexOf((100 - tone) * 10)
        require(index >= 0) { "tone $tone is not published" }
        return when (role) {
            PaletteRole.PRIMARY -> ACCENT1[index]
            PaletteRole.SECONDARY -> ACCENT2[index]
            PaletteRole.TERTIARY -> ACCENT3[index]
            PaletteRole.NEUTRAL -> NEUTRAL1[index]
            PaletteRole.NEUTRAL_VARIANT -> NEUTRAL2[index]
        }
    }

    /** The suffixes the platform defines, in ascending order. */
    private val STOP_SUFFIXES = intArrayOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

    private val ACCENT1 =
        intArrayOf(
            android.R.color.system_accent1_0,
            android.R.color.system_accent1_10,
            android.R.color.system_accent1_50,
            android.R.color.system_accent1_100,
            android.R.color.system_accent1_200,
            android.R.color.system_accent1_300,
            android.R.color.system_accent1_400,
            android.R.color.system_accent1_500,
            android.R.color.system_accent1_600,
            android.R.color.system_accent1_700,
            android.R.color.system_accent1_800,
            android.R.color.system_accent1_900,
            android.R.color.system_accent1_1000,
        )

    private val ACCENT2 =
        intArrayOf(
            android.R.color.system_accent2_0,
            android.R.color.system_accent2_10,
            android.R.color.system_accent2_50,
            android.R.color.system_accent2_100,
            android.R.color.system_accent2_200,
            android.R.color.system_accent2_300,
            android.R.color.system_accent2_400,
            android.R.color.system_accent2_500,
            android.R.color.system_accent2_600,
            android.R.color.system_accent2_700,
            android.R.color.system_accent2_800,
            android.R.color.system_accent2_900,
            android.R.color.system_accent2_1000,
        )

    private val ACCENT3 =
        intArrayOf(
            android.R.color.system_accent3_0,
            android.R.color.system_accent3_10,
            android.R.color.system_accent3_50,
            android.R.color.system_accent3_100,
            android.R.color.system_accent3_200,
            android.R.color.system_accent3_300,
            android.R.color.system_accent3_400,
            android.R.color.system_accent3_500,
            android.R.color.system_accent3_600,
            android.R.color.system_accent3_700,
            android.R.color.system_accent3_800,
            android.R.color.system_accent3_900,
            android.R.color.system_accent3_1000,
        )

    private val NEUTRAL1 =
        intArrayOf(
            android.R.color.system_neutral1_0,
            android.R.color.system_neutral1_10,
            android.R.color.system_neutral1_50,
            android.R.color.system_neutral1_100,
            android.R.color.system_neutral1_200,
            android.R.color.system_neutral1_300,
            android.R.color.system_neutral1_400,
            android.R.color.system_neutral1_500,
            android.R.color.system_neutral1_600,
            android.R.color.system_neutral1_700,
            android.R.color.system_neutral1_800,
            android.R.color.system_neutral1_900,
            android.R.color.system_neutral1_1000,
        )

    private val NEUTRAL2 =
        intArrayOf(
            android.R.color.system_neutral2_0,
            android.R.color.system_neutral2_10,
            android.R.color.system_neutral2_50,
            android.R.color.system_neutral2_100,
            android.R.color.system_neutral2_200,
            android.R.color.system_neutral2_300,
            android.R.color.system_neutral2_400,
            android.R.color.system_neutral2_500,
            android.R.color.system_neutral2_600,
            android.R.color.system_neutral2_700,
            android.R.color.system_neutral2_800,
            android.R.color.system_neutral2_900,
            android.R.color.system_neutral2_1000,
        )
}
