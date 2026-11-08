/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.material

/**
 * The five families a Material scheme is built from.
 *
 * Names match the platform's own resources (`system_accent1_*` and friends), so
 * the system source and the generated one can be spoken of the same way.
 */
enum class PaletteRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
    NEUTRAL_VARIANT,
}

/** The tone stops the platform publishes: 0, 10, 20 … 100. */
val TONE_STOPS = (0..100 step 10).toList()

/**
 * Five tonal palettes, sampled at any tone a scheme names.
 *
 * Both sources produce this same shape: the platform hands over eleven tones
 * per family, a seed colour is expanded into the whole scale. Everything
 * downstream — the role mapping, the preview, the saved copies — only ever
 * asks for a tone.
 */
class MaterialPalettes(
    private val palettes: Map<PaletteRole, TonalPalette>,
    private val published: Map<PaletteRole, Map<Int, Int>> = emptyMap(),
) {
    /**
     * The colour of [role] at [tone].
     *
     * A tone the platform published comes back verbatim — it *is* the system's
     * colour. Any other tone is cut from the family's own hue and chroma, which
     * is what lets a scheme ask for the 99 a surface needs while the platform
     * only ships multiples of ten.
     */
    fun color(
        role: PaletteRole,
        tone: Int,
    ): Int {
        published[role]?.get(tone)?.let { return it }
        val palette = requireNotNull(palettes[role]) { "no palette for $role" }
        return palette.tone(tone)
    }

    /** The colour a role/tone pairs with, as the theme writes colours. */
    fun literal(
        role: PaletteRole,
        tone: Int,
        alpha: Int = OPAQUE,
    ): String = literal(color(role, tone), alpha)

    companion object {
        /** Fully opaque; only shadows ask for anything else. */
        const val OPAQUE = 0xFF

        /** The tone an accent is shown at in light mode; also how a family is re-derived. */
        private const val ANCHOR_TONE = 40

        /**
         * Expands [seed] the way Android 12+ does (`SchemeTonalSpot`): the
         * primary family keeps the seed's chroma unless it is pale, the
         * secondary stays deliberately close to grey, the tertiary rotates the
         * hue by 60° so accent #3 still reads as a different colour.
         */
        fun fromSeed(seed: Int): MaterialPalettes {
            val cam = Cam16.fromArgb(seed)
            val hue = cam.hue
            val chroma = cam.chroma
            return of(
                PaletteRole.PRIMARY to TonalPalette.of(hue, maxOf(chroma, 48.0)),
                PaletteRole.SECONDARY to TonalPalette.of(hue, 16.0),
                PaletteRole.TERTIARY to TonalPalette.of(hue + 60.0, 24.0),
                PaletteRole.NEUTRAL to TonalPalette.of(hue, 6.0),
                PaletteRole.NEUTRAL_VARIANT to TonalPalette.of(hue, 8.0),
            )
        }

        fun of(vararg palettes: Pair<PaletteRole, TonalPalette>): MaterialPalettes = MaterialPalettes(palettes.toMap())

        /**
         * Builds from tones that already exist, i.e. read from platform resources.
         *
         * The published tones are kept exactly; the palette behind them is
         * re-derived from the anchor tone so the tones the platform does not
         * ship can still be asked for.
         */
        fun fromTones(tones: Map<PaletteRole, Map<Int, Int>>): MaterialPalettes {
            val missing =
                PaletteRole.entries.filter { role ->
                    TONE_STOPS.any { tones[role]?.containsKey(it) != true }
                }
            require(missing.isEmpty()) { "incomplete palettes: $missing" }
            val derived =
                tones.mapValues { (_, stops) ->
                    val cam = Cam16.fromArgb(stops.getValue(ANCHOR_TONE))
                    TonalPalette.of(cam.hue, cam.chroma)
                }
            return MaterialPalettes(derived, tones)
        }

        fun literal(
            argb: Int,
            alpha: Int = OPAQUE,
        ): String = if (alpha == OPAQUE) {
            String.format(java.util.Locale.ROOT, "0x%06x", argb and 0xFFFFFF)
        } else {
            // Eight digits, the way the template spells a translucent shadow.
            String.format(java.util.Locale.ROOT, "0x%08x", alpha shl 24 or (argb and 0xFFFFFF))
        }
    }
}
