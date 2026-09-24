package com.lanlanderui.trimedynamiccolors;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

/** Reads Android's wallpaper-derived tonal palettes without third-party libraries. */
final class DynamicPalette {
    private DynamicPalette() {}

    static Result load(Context context) {
        if (Build.VERSION.SDK_INT < 31 || !hasSystemPalette(context)) {
            return fallback();
        }

        try {
            Scheme light = new Scheme(
                color(context, "system_accent1_600"),
                color(context, "system_accent1_0"),
                color(context, "system_accent1_100"),
                color(context, "system_accent1_900"),
                color(context, "system_accent2_600"),
                color(context, "system_accent2_100"),
                color(context, "system_accent2_900"),
                color(context, "system_accent3_600"),
                color(context, "system_neutral1_10"),
                color(context, "system_neutral1_50"),
                color(context, "system_neutral1_100"),
                color(context, "system_neutral1_900"),
                color(context, "system_neutral2_700"),
                color(context, "system_neutral2_500"),
                color(context, "system_neutral2_200")
        );

            Scheme dark = new Scheme(
                color(context, "system_accent1_200"),
                color(context, "system_accent1_800"),
                color(context, "system_accent1_700"),
                color(context, "system_accent1_100"),
                color(context, "system_accent2_200"),
                color(context, "system_accent2_700"),
                color(context, "system_accent2_100"),
                color(context, "system_accent3_200"),
                color(context, "system_neutral1_900"),
                color(context, "system_neutral1_800"),
                color(context, "system_neutral1_700"),
                color(context, "system_neutral1_100"),
                color(context, "system_neutral2_200"),
                color(context, "system_neutral2_400"),
                color(context, "system_neutral2_700")
        );

            int[] swatches = {
                color(context, "system_accent1_600"),
                color(context, "system_accent2_600"),
                color(context, "system_accent3_600"),
                color(context, "system_neutral1_500"),
                color(context, "system_neutral2_500")
        };
        return new Result(true, "Android Material You", light, dark, swatches);
        } catch (RuntimeException ignored) {
            // A few vendor builds expose only part of the Android 12 palette.
            return fallback();
        }
    }

    private static boolean hasSystemPalette(Context context) {
        Resources resources = context.getResources();
        return resources.getIdentifier("system_accent1_600", "color", "android") != 0
                && resources.getIdentifier("system_neutral1_900", "color", "android") != 0;
    }

    private static int color(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "color", "android");
        if (id == 0) throw new IllegalStateException("Missing Android system color: " + name);
        return context.getResources().getColor(id, context.getTheme());
    }

    private static Result fallback() {
        Scheme light = new Scheme(
                0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D,
                0xFF625B71, 0xFFE8DEF8, 0xFF1D192B, 0xFF7D5260,
                0xFFFFFBFE, 0xFFF7F2FA, 0xFFECE6F0, 0xFF1D1B20,
                0xFF49454F, 0xFF79747E, 0xFFCAC4D0
        );
        Scheme dark = new Scheme(
                0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF,
                0xFFCCC2DC, 0xFF4A4458, 0xFFE8DEF8, 0xFFEFB8C8,
                0xFF141218, 0xFF211F26, 0xFF2B2930, 0xFFE6E0E9,
                0xFFCAC4D0, 0xFF938F99, 0xFF49454F
        );
        return new Result(false, "Android 备用配色", light, dark,
                new int[]{0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFF79747E, 0xFF49454F});
    }

    static final class Result {
        final boolean isDynamic;
        final String sourceLabel;
        final Scheme light;
        final Scheme dark;
        final int[] swatches;

        Result(boolean isDynamic, Scheme light, Scheme dark, int[] swatches) {
            this(isDynamic, isDynamic ? "Android Material You" : "Android 备用配色",
                    light, dark, swatches);
        }

        Result(
                boolean isDynamic,
                String sourceLabel,
                Scheme light,
                Scheme dark,
                int[] swatches
        ) {
            this.isDynamic = isDynamic;
            this.sourceLabel = sourceLabel;
            this.light = light;
            this.dark = dark;
            this.swatches = swatches;
        }
    }

    static final class Scheme {
        final int primary;
        final int onPrimary;
        final int primaryContainer;
        final int onPrimaryContainer;
        final int secondary;
        final int secondaryContainer;
        final int onSecondaryContainer;
        final int tertiary;
        final int surface;
        final int surfaceContainer;
        final int surfaceContainerHigh;
        final int onSurface;
        final int onSurfaceVariant;
        final int outline;
        final int outlineVariant;

        Scheme(
                int primary,
                int onPrimary,
                int primaryContainer,
                int onPrimaryContainer,
                int secondary,
                int secondaryContainer,
                int onSecondaryContainer,
                int tertiary,
                int surface,
                int surfaceContainer,
                int surfaceContainerHigh,
                int onSurface,
                int onSurfaceVariant,
                int outline,
                int outlineVariant
        ) {
            this.primary = primary;
            this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
            this.secondary = secondary;
            this.secondaryContainer = secondaryContainer;
            this.onSecondaryContainer = onSecondaryContainer;
            this.tertiary = tertiary;
            this.surface = surface;
            this.surfaceContainer = surfaceContainer;
            this.surfaceContainerHigh = surfaceContainerHigh;
            this.onSurface = onSurface;
            this.onSurfaceVariant = onSurfaceVariant;
            this.outline = outline;
            this.outlineVariant = outlineVariant;
        }
    }
}
