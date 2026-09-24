package com.lanlanderui.trimedynamiccolors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Safely adds or replaces only this app's two schemes in an existing Trime YAML file. */
final class TrimeThemePatcher {
    private static final String SECTION = "preset_color_schemes:";
    private static final String KEY_SECTION = "preset_keys:";

    private TrimeThemePatcher() {}

    static Result patch(String source, String generatedEntries) {
        if (source == null) source = "";
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(Arrays.asList(normalized.split("\n", -1)));

        int sectionStart = findSection(lines);
        if (sectionStart < 0) {
            StringBuilder out = new StringBuilder(normalized.length() + generatedEntries.length() + 96);
            out.append(normalized);
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            if (out.length() > 1 && out.charAt(out.length() - 2) != '\n') out.append('\n');
            out.append("# Material You 动态配色（由同文动态配色 App 写入）\n");
            out.append(SECTION).append('\n');
            out.append(generatedEntries);
            return new Result(out.toString().replace("\n", newline), true, false);
        }

        int sectionEnd = findSectionEnd(lines, sectionStart + 1);
        int childIndent = findChildIndent(lines, sectionStart + 1, sectionEnd);
        int indentStep = findIndentStep(lines, sectionStart + 1, sectionEnd, childIndent);
        List<String> body = new ArrayList<>();
        boolean replaced = false;

        for (int i = sectionStart + 1; i < sectionEnd;) {
            String line = lines.get(i);
            if (isManagedComment(line, childIndent)) {
                i++;
                continue;
            }
            if (isTargetScheme(line, childIndent)) {
                replaced = true;
                i++;
                while (i < sectionEnd && !startsSibling(lines.get(i), childIndent)) i++;
                continue;
            }
            body.add(line);
            i++;
        }

        while (!body.isEmpty() && body.get(body.size() - 1).isEmpty()) {
            body.remove(body.size() - 1);
        }
        if (!body.isEmpty()) body.add("");
        body.add(spaces(childIndent) + "# Material You 动态配色（由 App 管理）");
        body.addAll(reindentEntries(generatedEntries, childIndent, indentStep));
        body.add("");

        List<String> output = new ArrayList<>(lines.size() + 90);
        output.addAll(lines.subList(0, sectionStart + 1));
        output.addAll(body);
        output.addAll(lines.subList(sectionEnd, lines.size()));
        return new Result(String.join(newline, output), false, replaced);
    }

    /** Appends a uniquely named fixed snapshot without touching any existing scheme. */
    static Result appendSnapshot(String source, String generatedEntries) {
        if (source == null) source = "";
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(Arrays.asList(normalized.split("\n", -1)));
        int sectionStart = findSection(lines);

        if (sectionStart < 0) {
            StringBuilder out = new StringBuilder(normalized.length() + generatedEntries.length() + 96);
            out.append(normalized);
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            if (out.length() > 1 && out.charAt(out.length() - 2) != '\n') out.append('\n');
            out.append("# 永久保存的配色快照（由同文动态配色 App 写入）\n");
            out.append(SECTION).append('\n');
            out.append(generatedEntries);
            return new Result(out.toString().replace("\n", newline), true, false);
        }

        int sectionEnd = findSectionEnd(lines, sectionStart + 1);
        int childIndent = findChildIndent(lines, sectionStart + 1, sectionEnd);
        int indentStep = findIndentStep(lines, sectionStart + 1, sectionEnd, childIndent);
        List<String> addition = new ArrayList<>();
        addition.add(spaces(childIndent) + "# 永久保存的配色快照");
        addition.addAll(reindentEntries(generatedEntries, childIndent, indentStep));
        addition.add("");

        int insertAt = sectionEnd;
        while (insertAt > sectionStart + 1 && lines.get(insertAt - 1).isEmpty()) insertAt--;
        List<String> output = new ArrayList<>(lines.size() + addition.size() + 1);
        output.addAll(lines.subList(0, insertAt));
        if (insertAt > sectionStart + 1 && !lines.get(insertAt - 1).isEmpty()) output.add("");
        output.addAll(addition);
        output.addAll(lines.subList(insertAt, lines.size()));
        return new Result(String.join(newline, output), false, false);
    }

    /** Adds or replaces only this app's two launch keys in preset_keys. */
    static Result patchQuickKeys(String source, String generatedEntries) {
        if (source == null) source = "";
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(Arrays.asList(normalized.split("\n", -1)));
        int sectionStart = findSection(lines, KEY_SECTION);

        if (sectionStart < 0) {
            StringBuilder out = new StringBuilder(normalized.length() + generatedEntries.length() + 96);
            out.append(normalized);
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            if (out.length() > 1 && out.charAt(out.length() - 2) != '\n') out.append('\n');
            out.append("# 同文动态配色快捷键（由 App 管理）\n");
            out.append(KEY_SECTION).append('\n');
            out.append(generatedEntries);
            return new Result(out.toString().replace("\n", newline), true, false);
        }

        int sectionEnd = findSectionEnd(lines, sectionStart + 1);
        int childIndent = findChildIndent(lines, sectionStart + 1, sectionEnd);
        int indentStep = findIndentStep(lines, sectionStart + 1, sectionEnd, childIndent);
        List<String> body = new ArrayList<>();
        boolean replaced = false;

        for (int i = sectionStart + 1; i < sectionEnd;) {
            String line = lines.get(i);
            if (isManagedKeyComment(line, childIndent)) {
                i++;
                continue;
            }
            if (isTargetQuickKey(line, childIndent)) {
                replaced = true;
                i++;
                while (i < sectionEnd && !startsSibling(lines.get(i), childIndent)) i++;
                continue;
            }
            body.add(line);
            i++;
        }

        while (!body.isEmpty() && body.get(body.size() - 1).isEmpty()) {
            body.remove(body.size() - 1);
        }
        if (!body.isEmpty()) body.add("");
        body.add(spaces(childIndent) + "# 同文动态配色快捷键（由 App 管理）");
        body.addAll(reindentEntries(generatedEntries, childIndent, indentStep));
        body.add("");

        List<String> output = new ArrayList<>(lines.size() + 14);
        output.addAll(lines.subList(0, sectionStart + 1));
        output.addAll(body);
        output.addAll(lines.subList(sectionEnd, lines.size()));
        return new Result(String.join(newline, output), false, replaced);
    }

    private static int findSection(List<String> lines) {
        return findSection(lines, SECTION);
    }

    private static int findSection(List<String> lines, String section) {
        for (int i = 0; i < lines.size(); i++) {
            String line = stripBom(lines.get(i));
            if (indent(line) == 0 && line.trim().equals(section)) return i;
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (indent(lines.get(i)) == 0) return i;
        }
        return lines.size();
    }

    private static int findChildIndent(List<String> lines, int from, int to) {
        int smallest = Integer.MAX_VALUE;
        for (int i = from; i < to; i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) continue;
            int amount = indent(lines.get(i));
            if (amount > 0) smallest = Math.min(smallest, amount);
        }
        return smallest == Integer.MAX_VALUE ? 2 : smallest;
    }

    private static int findIndentStep(List<String> lines, int from, int to, int childIndent) {
        int smallest = Integer.MAX_VALUE;
        for (int i = from; i < to; i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int amount = indent(lines.get(i));
            if (amount > childIndent) smallest = Math.min(smallest, amount - childIndent);
        }
        return smallest == Integer.MAX_VALUE ? 2 : smallest;
    }

    private static boolean isTargetScheme(String line, int childIndent) {
        if (indent(line) != childIndent) return false;
        String trimmed = line.trim();
        return isNamedHeader(trimmed, "material_you")
                || isNamedHeader(trimmed, "material_you_dark");
    }

    private static boolean isNamedHeader(String trimmed, String name) {
        return trimmed.startsWith(name + ":")
                || trimmed.startsWith("\"" + name + "\":")
                || trimmed.startsWith("'" + name + "':");
    }

    private static boolean startsSibling(String line, int childIndent) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.startsWith("#")) return indent(line) <= childIndent;
        return indent(line) <= childIndent;
    }

    private static boolean isManagedComment(String line, int childIndent) {
        return indent(line) == childIndent
                && line.trim().equals("# Material You 动态配色（由 App 管理）");
    }

    private static boolean isTargetQuickKey(String line, int childIndent) {
        if (indent(line) != childIndent) return false;
        String trimmed = line.trim();
        return isNamedHeader(trimmed, "DynamicColor")
                || isNamedHeader(trimmed, "SaveDynamicColor");
    }

    private static boolean isManagedKeyComment(String line, int childIndent) {
        return indent(line) == childIndent
                && line.trim().equals("# 同文动态配色快捷键（由 App 管理）");
    }

    private static List<String> reindentEntries(String entries, int childIndent, int indentStep) {
        String normalized = entries.replace("\r\n", "\n").replace('\r', '\n');
        String[] sourceLines = normalized.split("\n", -1);
        List<String> result = new ArrayList<>(sourceLines.length);
        for (String line : sourceLines) {
            if (line.isEmpty()) {
                result.add("");
            } else {
                int sourceIndent = indent(line);
                int targetIndent = sourceIndent <= 2
                        ? childIndent
                        : childIndent + indentStep + Math.max(0, sourceIndent - 4);
                result.add(spaces(targetIndent) + line.trim());
            }
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static int indent(String line) {
        int count = 0;
        while (count < line.length()) {
            char value = line.charAt(count);
            if (value == ' ') count++;
            else if (value == '\t') count += 2;
            else break;
        }
        return count;
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == '\uFEFF' ? line.substring(1) : line;
    }

    private static String spaces(int count) {
        StringBuilder value = new StringBuilder(count);
        for (int i = 0; i < count; i++) value.append(' ');
        return value.toString();
    }

    static final class Result {
        final String yaml;
        final boolean addedSection;
        final boolean replacedExisting;

        Result(String yaml, boolean addedSection, boolean replacedExisting) {
            this.yaml = yaml;
            this.addedSection = addedSection;
            this.replacedExisting = replacedExisting;
        }
    }
}
