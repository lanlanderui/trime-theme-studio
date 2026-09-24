package com.lanlanderui.trimedynamiccolors;

import java.util.Locale;

/** Maps Material color roles to the current Trime color-scheme keys. */
final class TrimeThemeGenerator {
    private TrimeThemeGenerator() {}

    static String generate(String displayName, DynamicPalette.Result palette) {
        String safeName = displayName == null ? "" : displayName.trim();
        if (safeName.isEmpty()) safeName = "Material You";

        StringBuilder yaml = new StringBuilder(4096);
        yaml.append("# 由同文动态配色生成\n");
        yaml.append("# 浅色、深色方案会跟随系统外观自动切换\n");
        yaml.append("preset_color_schemes:\n");
        yaml.append(generateEntries(safeName, palette));
        return yaml.toString();
    }

    static String generateEntries(String displayName, DynamicPalette.Result palette) {
        String safeName = displayName == null ? "" : displayName.trim();
        if (safeName.isEmpty()) safeName = "Material You";

        return generateNamedEntries(safeName, "material_you", "material_you_dark", palette);
    }

    static String generateNamedEntries(
            String displayName,
            String lightId,
            String darkId,
            DynamicPalette.Result palette
    ) {
        String safeName = displayName == null ? "" : displayName.trim();
        if (safeName.isEmpty()) safeName = "Material You";

        StringBuilder yaml = new StringBuilder(4000);
        appendScheme(yaml, lightId, safeName + "（浅色）", "dark_scheme",
                darkId, palette.light, palette.sourceLabel);
        appendScheme(yaml, darkId, safeName + "（深色）", "light_scheme",
                lightId, palette.dark, palette.sourceLabel);
        return yaml.toString();
    }

    private static void appendScheme(
            StringBuilder out,
            String id,
            String name,
            String linkedKey,
            String linkedValue,
            DynamicPalette.Scheme c,
            String sourceLabel
    ) {
        out.append("  ").append(id).append(":\n");
        line(out, "name", quote(name));
        line(out, "author", quote(sourceLabel));
        line(out, linkedKey, linkedValue);
        out.append("\n");

        line(out, "back_color", hex(c.surface));
        line(out, "border_color", hex(c.outlineVariant));
        line(out, "candidate_separator_color", hex(c.outlineVariant));
        line(out, "candidate_text_color", hex(c.onSurface));
        line(out, "comment_text_color", hex(c.onSurfaceVariant));
        line(out, "hilited_back_color", hex(c.primaryContainer));
        line(out, "hilited_candidate_back_color", hex(c.primaryContainer));
        line(out, "hilited_candidate_text_color", hex(c.onPrimaryContainer));
        line(out, "hilited_comment_text_color", hex(c.onPrimaryContainer));
        line(out, "hilited_text_color", hex(c.primary));
        out.append("\n");

        line(out, "keyboard_back_color", hex(c.surface));
        line(out, "key_back_color", hex(c.surfaceContainer));
        line(out, "key_border_color", hex(c.outlineVariant));
        line(out, "key_text_color", hex(c.onSurface));
        line(out, "key_symbol_color", hex(c.onSurfaceVariant));
        line(out, "label_color", hex(c.primary));
        line(out, "hilited_key_back_color", hex(c.primary));
        line(out, "hilited_key_border_color", hex(c.primary));
        line(out, "hilited_key_text_color", hex(c.onPrimary));
        line(out, "hilited_key_symbol_color", hex(c.onPrimary));
        line(out, "off_key_back_color", hex(c.secondaryContainer));
        line(out, "off_key_text_color", hex(c.onSecondaryContainer));
        line(out, "on_key_back_color", hex(c.primary));
        line(out, "on_key_text_color", hex(c.onPrimary));
        out.append("\n");

        line(out, "preview_back_color", hex(c.primary));
        line(out, "preview_text_color", hex(c.onPrimary));
        line(out, "text_color", hex(c.primary));
        line(out, "text_back_color", hex(c.surfaceContainer));
        line(out, "root_background", hex(c.surface));
        line(out, "candidate_background", hex(c.surface));
        line(out, "liquid_keyboard_background", hex(c.surface));
        line(out, "long_text_back_color", hex(c.surfaceContainer));
        out.append("\n");

        // Common custom tokens used by many Trime layouts, including this repository's template.
        line(out, "bkg", hex(c.surfaceContainer));
        line(out, "tkg", hex(c.onSurface));
        line(out, "benter", hex(c.primary));
        line(out, "tenter", hex(c.onPrimary));
        line(out, "bgn", hex(c.secondaryContainer));
        line(out, "tgn", hex(c.onSecondaryContainer));
        line(out, "bbs", hex(c.secondaryContainer));
        line(out, "tbs", hex(c.onSecondaryContainer));
        out.append("\n");
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append("    ").append(key).append(": ").append(value).append('\n');
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "0x%08X", color);
    }

    private static String quote(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ") + "\"";
    }
}
