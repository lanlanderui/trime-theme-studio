package com.lanlanderui.trimedynamiccolors;

import android.graphics.Color;

/** Generates a complete light/dark semantic palette from one user-selected seed color. */
final class SeedPalette {
    private SeedPalette() {}

    static DynamicPalette.Result create(int seed) {
        float[] hsl = toHsl(seed);
        float hue = hsl[0];
        float seedSaturation = hsl[1];
        float primarySaturation = clamp(seedSaturation, 0.48f, 0.88f);
        float secondarySaturation = clamp(0.18f + seedSaturation * 0.28f, 0.22f, 0.46f);
        float tertiarySaturation = clamp(0.36f + seedSaturation * 0.28f, 0.40f, 0.72f);
        float neutralSaturation = Math.min(0.10f, seedSaturation * 0.12f);
        float neutralVariantSaturation = Math.min(0.18f, 0.06f + seedSaturation * 0.15f);
        float tertiaryHue = (hue + 60f) % 360f;

        int lightPrimary = tone(hue, primarySaturation, 0.40f);
        int lightPrimaryContainer = tone(hue, primarySaturation, 0.90f);
        int lightSecondaryContainer = tone(hue, secondarySaturation, 0.90f);
        int darkPrimary = tone(hue, primarySaturation, 0.80f);
        int darkPrimaryContainer = tone(hue, primarySaturation, 0.30f);
        int darkSecondaryContainer = tone(hue, secondarySaturation, 0.30f);

        DynamicPalette.Scheme light = new DynamicPalette.Scheme(
                lightPrimary,
                bestOnColor(lightPrimary),
                lightPrimaryContainer,
                bestOnColor(lightPrimaryContainer),
                tone(hue, secondarySaturation, 0.40f),
                lightSecondaryContainer,
                bestOnColor(lightSecondaryContainer),
                tone(tertiaryHue, tertiarySaturation, 0.40f),
                tone(hue, neutralSaturation, 0.99f),
                tone(hue, neutralSaturation, 0.95f),
                tone(hue, neutralSaturation, 0.90f),
                tone(hue, neutralSaturation, 0.10f),
                tone(hue, neutralVariantSaturation, 0.30f),
                tone(hue, neutralVariantSaturation, 0.50f),
                tone(hue, neutralVariantSaturation, 0.80f)
        );

        DynamicPalette.Scheme dark = new DynamicPalette.Scheme(
                darkPrimary,
                bestOnColor(darkPrimary),
                darkPrimaryContainer,
                bestOnColor(darkPrimaryContainer),
                tone(hue, secondarySaturation, 0.80f),
                darkSecondaryContainer,
                bestOnColor(darkSecondaryContainer),
                tone(tertiaryHue, tertiarySaturation, 0.80f),
                tone(hue, neutralSaturation, 0.10f),
                tone(hue, neutralSaturation, 0.20f),
                tone(hue, neutralSaturation, 0.30f),
                tone(hue, neutralSaturation, 0.90f),
                tone(hue, neutralVariantSaturation, 0.80f),
                tone(hue, neutralVariantSaturation, 0.60f),
                tone(hue, neutralVariantSaturation, 0.30f)
        );

        int[] swatches = {
                light.primary,
                light.secondary,
                light.tertiary,
                tone(hue, neutralSaturation, 0.50f),
                tone(hue, neutralVariantSaturation, 0.50f)
        };
        return new DynamicPalette.Result(false, "自选主色生成", light, dark, swatches);
    }

    static String hex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private static float[] toHsl(int color) {
        float red = Color.red(color) / 255f;
        float green = Color.green(color) / 255f;
        float blue = Color.blue(color) / 255f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float lightness = (max + min) / 2f;
        float hue;
        float saturation;
        if (delta == 0f) {
            hue = 0f;
            saturation = 0f;
        } else {
            saturation = delta / (1f - Math.abs(2f * lightness - 1f));
            if (max == red) hue = 60f * (((green - blue) / delta) % 6f);
            else if (max == green) hue = 60f * (((blue - red) / delta) + 2f);
            else hue = 60f * (((red - green) / delta) + 4f);
            if (hue < 0f) hue += 360f;
        }
        return new float[]{hue, saturation, lightness};
    }

    private static int tone(float hue, float saturation, float lightness) {
        float chroma = (1f - Math.abs(2f * lightness - 1f)) * saturation;
        float section = hue / 60f;
        float x = chroma * (1f - Math.abs(section % 2f - 1f));
        float red = 0f;
        float green = 0f;
        float blue = 0f;
        if (section < 1f) { red = chroma; green = x; }
        else if (section < 2f) { red = x; green = chroma; }
        else if (section < 3f) { green = chroma; blue = x; }
        else if (section < 4f) { green = x; blue = chroma; }
        else if (section < 5f) { red = x; blue = chroma; }
        else { red = chroma; blue = x; }
        float match = lightness - chroma / 2f;
        return Color.rgb(
                Math.round((red + match) * 255f),
                Math.round((green + match) * 255f),
                Math.round((blue + match) * 255f));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int bestOnColor(int background) {
        return contrast(background, Color.BLACK) >= contrast(background, Color.WHITE)
                ? Color.BLACK : Color.WHITE;
    }

    private static double contrast(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        double red = linear(Color.red(color) / 255.0);
        double green = linear(Color.green(color) / 255.0);
        double blue = linear(Color.blue(color) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(double component) {
        return component <= 0.04045
                ? component / 12.92
                : Math.pow((component + 0.055) / 1.055, 2.4);
    }
}
