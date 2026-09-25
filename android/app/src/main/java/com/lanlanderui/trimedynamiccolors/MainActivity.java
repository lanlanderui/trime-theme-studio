package com.lanlanderui.trimedynamiccolors;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 42;
    private static final int REQUEST_THEME_FILE = 43;
    private static final String STATE_DARK = "preview_dark";
    private static final String STATE_NAME = "scheme_name";
    private static final String PREFS_NAME = "theme_file";
    private static final String KEY_THEME_URI = "theme_uri";
    private static final String KEY_THEME_NAME = "theme_name";
    private static final String KEY_BACKUP_URI = "backup_uri";
    private static final String KEY_SCHEME_NAME = "scheme_name";
    private static final String KEY_COLOR_SOURCE = "color_source";
    private static final String KEY_MANUAL_SEED = "manual_seed";
    private static final String SOURCE_MANUAL = "manual";
    private static final String BACKUP_FILE = "last-theme-backup.yaml";
    private static final String TRIME_PACKAGE = "com.osfans.trime";
    private static final String TRIME_DEPLOY_ACTION = "com.osfans.trime.deploy";
    private static final String QUICK_KEYS_YAML =
            "  DynamicColor:\n"
                    + "    command: run\n"
                    + "    label: \"更新配色\"\n"
                    + "    option: \"com.lanlanderui.trimedynamiccolors/.QuickApplyActivity\"\n"
                    + "  SaveDynamicColor:\n"
                    + "    command: run\n"
                    + "    label: \"收藏配色\"\n"
                    + "    option: \"com.lanlanderui.trimedynamiccolors/.QuickSaveActivity\"\n";

    private DynamicPalette.Result palette;
    private boolean manualMode;
    private int manualSeed;
    private boolean previewDark;
    private EditText nameField;
    private EditText codeView;
    private TextView selectedFileStatus;
    private Uri selectedThemeUri;
    private String selectedThemeName = "";
    private String pendingExport = "";

    private static final class SchemeManagerSession {
        final Uri target;
        final String originalYaml;
        String draftYaml;
        List<TrimeThemePatcher.SchemeInfo> schemes;
        int editCount;

        SchemeManagerSession(
                Uri target,
                String originalYaml,
                List<TrimeThemePatcher.SchemeInfo> schemes) {
            this.target = target;
            this.originalYaml = originalYaml;
            this.draftYaml = originalYaml;
            this.schemes = schemes;
        }

        boolean hasChanges() {
            return !originalYaml.equals(draftYaml);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manualMode = SOURCE_MANUAL.equals(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_COLOR_SOURCE, "system"));
        manualSeed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_MANUAL_SEED, 0xFF6750A4);
        palette = manualMode ? SeedPalette.create(manualSeed) : DynamicPalette.load(this);
        String savedUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_THEME_URI, "");
        if (savedUri != null && !savedUri.isEmpty()) selectedThemeUri = Uri.parse(savedUri);
        selectedThemeName = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_THEME_NAME, "");
        previewDark = savedInstanceState != null
                ? savedInstanceState.getBoolean(STATE_DARK)
                : isSystemDark();
        String savedName = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_SCHEME_NAME, "Material You");
        String name = savedInstanceState == null
                ? savedName
                : savedInstanceState.getString(STATE_NAME, "Material You");
        buildUi(name);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DARK, previewDark);
        outState.putString(STATE_NAME, currentName());
    }

    private void buildUi(String schemeName) {
        DynamicPalette.Scheme colors = previewDark ? palette.dark : palette.light;
        updateSystemBars(colors);

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(colors.surface);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        page.addView(scroll, match());

        LinearLayout content = column();
        int side = dp(20);
        content.setPadding(side, dp(22), side, dp(42));
        scroll.addView(content, matchWrap());

        content.addView(buildHeader(colors));
        content.addView(space(20));
        content.addView(buildStatusCard(colors));
        content.addView(space(22));

        content.addView(sectionTitle("配色来源", colors));
        content.addView(sectionCaption("使用系统动态色，或指定一个主色自动补全整套颜色", colors));
        content.addView(space(12));
        content.addView(buildColorSourceSelector(colors));
        content.addView(space(22));

        content.addView(sectionTitle("生成色板", colors));
        content.addView(sectionCaption(
                manualMode ? "基于 " + SeedPalette.hex(manualSeed) + " 自动生成" : "来自当前壁纸与系统配色设置",
                colors));
        content.addView(space(12));
        content.addView(buildSwatches(colors));
        content.addView(space(26));

        content.addView(sectionTitle("键盘预览", colors));
        content.addView(sectionCaption("切换外观只影响预览，导出会同时包含两套方案", colors));
        content.addView(space(12));
        content.addView(buildAppearanceSwitch(colors));
        content.addView(space(12));

        LinearLayout previewCard = card(colors, 10, 0);
        KeyboardPreviewView preview = new KeyboardPreviewView(this);
        preview.setScheme(colors);
        previewCard.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(316)));
        content.addView(previewCard);
        content.addView(space(26));

        content.addView(sectionTitle("方案设置", colors));
        content.addView(sectionCaption("方案 ID 固定为 material_you，名称可自定义", colors));
        content.addView(space(12));
        nameField = new EditText(this);
        nameField.setSingleLine(true);
        nameField.setText(schemeName);
        nameField.setTextSize(16);
        nameField.setTextColor(colors.onSurface);
        nameField.setHintTextColor(withAlpha(colors.onSurfaceVariant, 0.72f));
        nameField.setHint("方案名称");
        nameField.setPadding(dp(16), 0, dp(16), 0);
        nameField.setBackground(roundRect(colors.surfaceContainer, 14, colors.outlineVariant, 1));
        content.addView(nameField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        content.addView(space(26));

        content.addView(sectionTitle("写入同文主题", colors));
        content.addView(sectionCaption("选择 Rime 目录中的主题 YAML，只更新动态配色节点", colors));
        content.addView(space(12));
        content.addView(buildThemeFileCard(colors));
        content.addView(space(26));

        content.addView(sectionTitle("主题代码", colors));
        content.addView(sectionCaption("仍可复制、导出或分享完整配色片段", colors));
        content.addView(space(12));

        codeView = new EditText(this);
        codeView.setText(TrimeThemeGenerator.generate(schemeName, palette));
        codeView.setTextColor(colors.onSurface);
        codeView.setTextSize(12);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setGravity(Gravity.TOP | Gravity.START);
        codeView.setTextIsSelectable(true);
        codeView.setKeyListener(null);
        codeView.setHorizontallyScrolling(true);
        codeView.setVerticalScrollBarEnabled(true);
        codeView.setPadding(dp(15), dp(14), dp(15), dp(14));
        codeView.setBackground(roundRect(colors.surfaceContainer, 16, colors.outlineVariant, 1));
        content.addView(codeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        nameField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                codeView.setText(TrimeThemeGenerator.generate(s.toString(), palette));
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(KEY_SCHEME_NAME, s.toString())
                        .apply();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        content.addView(space(14));
        content.addView(buildActions(colors));
        content.addView(space(18));

        TextView hint = text(
                "一键写入会保留原文件的布局、注释和其它配色。写入后请在同文输入法中重新部署，并选择 material_you。",
                13, colors.onSurfaceVariant, Typeface.NORMAL);
        hint.setLineSpacing(dp(2), 1f);
        content.addView(hint);

        setContentView(page);
    }

    private View buildHeader(DynamicPalette.Scheme colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = text("T", 21, colors.onPrimary, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundRect(colors.primary, 14, Color.TRANSPARENT, 0));
        row.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = column();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(13), 0, dp(8), 0);
        row.addView(titles, titleParams);
        titles.addView(text("同文动态配色", 24, colors.onSurface, Typeface.BOLD));
        titles.addView(text("Material You → Trime", 12, colors.onSurfaceVariant, Typeface.NORMAL));

        Button refresh = button(manualMode ? "改色" : "刷新", false, colors);
        refresh.setOnClickListener(v -> {
            if (manualMode) {
                showSeedColorDialog();
            } else {
                palette = DynamicPalette.load(this);
                buildUi(currentName());
                Toast.makeText(this, "已重新读取系统配色", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(refresh, new LinearLayout.LayoutParams(dp(76), dp(44)));
        return row;
    }

    private View buildStatusCard(DynamicPalette.Scheme colors) {
        LinearLayout card = card(colors, 16, 16);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setBackground(oval(manualMode ? manualSeed
                : palette.isDynamic ? colors.primary : colors.tertiary));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.setMargins(0, 0, dp(12), 0);
        card.addView(dot, dotParams);

        LinearLayout copy = column();
        card.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(
                manualMode
                        ? "自选主色 " + SeedPalette.hex(manualSeed)
                        : palette.isDynamic ? "已读取系统动态配色" : "当前使用内置备用配色",
                15, colors.onSurface, Typeface.BOLD));
        copy.addView(text(
                manualMode
                        ? "已自动补全浅色、深色与功能键颜色"
                        : palette.isDynamic
                        ? "Android " + Build.VERSION.RELEASE + " · 随壁纸配色变化"
                        : "动态配色需要 Android 12 或更高版本",
                12, colors.onSurfaceVariant, Typeface.NORMAL));

        if (!manualMode && !palette.isDynamic && Build.VERSION.SDK_INT >= 31) {
            Button settings = button("设置", false, colors);
            settings.setOnClickListener(v -> openWallpaperSettings());
            card.addView(settings, new LinearLayout.LayoutParams(dp(72), dp(42)));
        }
        return card;
    }

    private View buildColorSourceSelector(DynamicPalette.Scheme colors) {
        LinearLayout container = column();
        LinearLayout switcher = new LinearLayout(this);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        switcher.setPadding(dp(4), dp(4), dp(4), dp(4));
        switcher.setBackground(roundRect(colors.surfaceContainer, 16, Color.TRANSPARENT, 0));

        Button system = toggleButton("系统动态色", !manualMode, colors);
        Button manual = toggleButton("自选主色", manualMode, colors);
        system.setOnClickListener(v -> useSystemColors());
        manual.setOnClickListener(v -> showSeedColorDialog());
        switcher.addView(system, new LinearLayout.LayoutParams(0, dp(46), 1f));
        switcher.addView(manual, new LinearLayout.LayoutParams(0, dp(46), 1f));
        container.addView(switcher);

        if (manualMode) {
            LinearLayout selected = new LinearLayout(this);
            selected.setOrientation(LinearLayout.HORIZONTAL);
            selected.setGravity(Gravity.CENTER_VERTICAL);
            selected.setPadding(dp(14), dp(12), dp(10), dp(4));
            TextView swatch = new TextView(this);
            swatch.setBackground(roundRect(manualSeed, 12, colors.outlineVariant, 1));
            selected.addView(swatch, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView value = text(SeedPalette.hex(manualSeed), 15, colors.onSurface, Typeface.BOLD);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            valueParams.setMargins(dp(12), 0, dp(8), 0);
            selected.addView(value, valueParams);
            Button change = button("选择颜色", false, colors);
            change.setOnClickListener(v -> showSeedColorDialog());
            selected.addView(change, new LinearLayout.LayoutParams(dp(100), dp(42)));
            container.addView(selected);
        }
        return container;
    }

    private View buildSwatches(DynamicPalette.Scheme colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < palette.swatches.length; i++) {
            int color = palette.swatches[i];
            TextView swatch = new TextView(this);
            swatch.setContentDescription("生成色板颜色 " + (i + 1));
            swatch.setBackground(roundRect(color, 16, colors.outlineVariant, 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1f);
            if (i > 0) params.setMargins(dp(8), 0, 0, 0);
            row.addView(swatch, params);
        }
        return row;
    }

    private View buildAppearanceSwitch(DynamicPalette.Scheme colors) {
        LinearLayout switcher = new LinearLayout(this);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        switcher.setPadding(dp(4), dp(4), dp(4), dp(4));
        switcher.setBackground(roundRect(colors.surfaceContainer, 16, Color.TRANSPARENT, 0));

        Button light = toggleButton("浅色", !previewDark, colors);
        Button dark = toggleButton("深色", previewDark, colors);
        light.setOnClickListener(v -> changePreview(false));
        dark.setOnClickListener(v -> changePreview(true));
        switcher.addView(light, new LinearLayout.LayoutParams(0, dp(44), 1f));
        switcher.addView(dark, new LinearLayout.LayoutParams(0, dp(44), 1f));
        return switcher;
    }

    private View buildThemeFileCard(DynamicPalette.Scheme colors) {
        LinearLayout container = card(colors, 16, 16);
        selectedFileStatus = text(
                selectedThemeUri == null
                        ? "尚未选择主题文件"
                        : "已选择：" + selectedThemeName,
                15, colors.onSurface, Typeface.BOLD);
        container.addView(selectedFileStatus);

        TextView detail = text(
                selectedThemeUri == null
                        ? "点击下方按钮，从 Rime 目录选择 .trime.yaml 或 .yaml 文件"
                        : "App 已记住该文件的读写授权，下次打开可以直接更新",
                12, colors.onSurfaceVariant, Typeface.NORMAL);
        detail.setPadding(0, dp(4), 0, 0);
        container.addView(detail);
        container.addView(space(14));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button(selectedThemeUri == null ? "选择 YAML" : "更换文件", false, colors);
        Button write = button("一键写入", true, colors);
        choose.setOnClickListener(v -> chooseThemeFile());
        write.setEnabled(selectedThemeUri != null);
        write.setAlpha(selectedThemeUri == null ? 0.45f : 1f);
        write.setOnClickListener(v -> writeIntoSelectedTheme(write));
        actions.addView(choose, new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams writeParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        writeParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(write, writeParams);
        container.addView(actions);

        container.addView(space(8));
        Button manageSchemes = button("管理已有配色方案", false, colors);
        manageSchemes.setEnabled(selectedThemeUri != null);
        manageSchemes.setAlpha(selectedThemeUri == null ? 0.45f : 1f);
        manageSchemes.setOnClickListener(v -> openSchemeManager(manageSchemes));
        container.addView(manageSchemes, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        container.addView(space(8));
        Button saveSnapshot = button("永久保存当前配色", false, colors);
        saveSnapshot.setEnabled(selectedThemeUri != null);
        saveSnapshot.setAlpha(selectedThemeUri == null ? 0.45f : 1f);
        saveSnapshot.setOnClickListener(v -> showSaveSnapshotDialog(saveSnapshot));
        container.addView(saveSnapshot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        container.addView(space(8));
        Button writeQuickKeys = button("一键写入快捷键配置", false, colors);
        writeQuickKeys.setEnabled(selectedThemeUri != null);
        writeQuickKeys.setAlpha(selectedThemeUri == null ? 0.45f : 1f);
        writeQuickKeys.setOnClickListener(v -> writeQuickKeys(writeQuickKeys));
        container.addView(writeQuickKeys, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        String backupUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_BACKUP_URI, "");
        if (selectedThemeUri != null
                && selectedThemeUri.toString().equals(backupUri)
                && getFileStreamPath(BACKUP_FILE).isFile()) {
            container.addView(space(8));
            Button restore = button("撤销上次写入", false, colors);
            restore.setOnClickListener(v -> restoreLastBackup(restore));
            container.addView(restore, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }
        return container;
    }

    private void useSystemColors() {
        if (!manualMode) return;
        String name = currentName();
        manualMode = false;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_COLOR_SOURCE, "system")
                .apply();
        palette = DynamicPalette.load(this);
        buildUi(name);
    }

    private void showSeedColorDialog() {
        float[] hsv = new float[3];
        Color.colorToHSV(manualSeed, hsv);
        int[] selected = {manualSeed};

        LinearLayout content = column();
        content.setPadding(dp(22), dp(6), dp(22), 0);
        TextView preview = new TextView(this);
        preview.setGravity(Gravity.CENTER);
        preview.setText("主色预览");
        preview.setTextColor(Color.WHITE);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        preview.setBackground(roundRect(selected[0], 16, Color.TRANSPARENT, 0));
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        content.addView(space(12));

        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setText(SeedPalette.hex(manualSeed));
        hex.setSelectAllOnFocus(true);
        hex.setHint("#6750A4");
        content.addView(hex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView hueLabel = text("色相", 13, Color.DKGRAY, Typeface.BOLD);
        content.addView(hueLabel);
        SeekBar hue = new SeekBar(this);
        hue.setMax(359);
        hue.setProgress(Math.round(hsv[0]));
        content.addView(hue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView saturationLabel = text("鲜艳度", 13, Color.DKGRAY, Typeface.BOLD);
        content.addView(saturationLabel);
        SeekBar saturation = new SeekBar(this);
        saturation.setMax(100);
        saturation.setProgress(Math.round(hsv[1] * 100f));
        content.addView(saturation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView note = text(
                "亮度和对比度会分别按浅色、深色主题自动生成。也可以直接输入十六进制颜色。",
                12, Color.DKGRAY, Typeface.NORMAL);
        note.setLineSpacing(dp(2), 1f);
        content.addView(note);

        Runnable updatePreview = () -> {
            selected[0] = Color.HSVToColor(new float[]{
                    hue.getProgress(), saturation.getProgress() / 100f, 0.78f
            });
            preview.setBackground(roundRect(selected[0], 16, Color.TRANSPARENT, 0));
            hex.setText(SeedPalette.hex(selected[0]));
        };
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        hue.setOnSeekBarChangeListener(listener);
        saturation.setOnSeekBarChangeListener(listener);

        new AlertDialog.Builder(this)
                .setTitle("选择主色")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("生成配色", (dialog, which) -> {
                    Integer typed = parseColor(hex.getText().toString());
                    manualSeed = typed == null ? selected[0] : typed;
                    manualMode = true;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(KEY_COLOR_SOURCE, SOURCE_MANUAL)
                            .putInt(KEY_MANUAL_SEED, manualSeed)
                            .apply();
                    palette = SeedPalette.create(manualSeed);
                    buildUi(currentName());
                })
                .show();
    }

    private Integer parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.matches("(?i)[0-9a-f]{6}")) value = "#" + value;
        if (value.matches("(?i)0x[0-9a-f]{6}")) value = "#" + value.substring(2);
        try {
            return Color.parseColor(value) | 0xFF000000;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void showSaveSnapshotDialog(Button action) {
        if (selectedThemeUri == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.format(Locale.ROOT, "%s 收藏", currentName()));
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(16), 0, dp(16), 0);
        new AlertDialog.Builder(this)
                .setTitle("永久保存当前配色")
                .setMessage("将以独立名称追加到所选 YAML，后续动态更新不会覆盖它。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "我的配色";
                    saveSnapshot(name, action);
                })
                .show();
    }

    private void saveSnapshot(String name, Button action) {
        if (selectedThemeUri == null) return;
        Uri target = selectedThemeUri;
        DynamicPalette.Result snapshot = palette;
        String id = "saved_" + new SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
        String entries = TrimeThemeGenerator.generateNamedEntries(
                name, id, id + "_dark", snapshot);
        action.setEnabled(false);
        selectedFileStatus.setText("正在永久保存配色……");

        new Thread(() -> {
            try {
                String original = readText(target);
                TrimeThemePatcher.Result result =
                        TrimeThemePatcher.appendSnapshot(original, entries);
                saveBackup(target, original);
                writeText(target, result.yaml);
                requestTrimeDeploy();
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    selectedFileStatus.setText(String.format(
                            Locale.ROOT, "已保存固定配色：%s", name));
                    Toast.makeText(this,
                            "永久配色已保存，可在同文中选择“" + name + "”",
                            Toast.LENGTH_LONG).show();
                    buildUi(currentName());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    selectedFileStatus.setText(String.format(
                            Locale.ROOT, "保存失败：%s", safeMessage(error)));
                    Toast.makeText(this,
                            "没有修改文件：" + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        }, "trime-snapshot-writer").start();
    }

    private View buildActions(DynamicPalette.Scheme colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button copy = button("复制", true, colors);
        Button export = button("导出", false, colors);
        Button share = button("分享", false, colors);
        copy.setOnClickListener(v -> copyYaml());
        export.setOnClickListener(v -> exportYaml());
        share.setOnClickListener(v -> shareYaml());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        row.addView(copy, params);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(52), 1f);
        second.setMargins(dp(8), 0, 0, 0);
        row.addView(export, second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, dp(52), 1f);
        third.setMargins(dp(8), 0, 0, 0);
        row.addView(share, third);
        return row;
    }

    private void changePreview(boolean dark) {
        if (previewDark == dark) return;
        String name = currentName();
        previewDark = dark;
        buildUi(name);
    }

    private String currentName() {
        return nameField == null ? "Material You" : nameField.getText().toString();
    }

    private String currentYaml() {
        return TrimeThemeGenerator.generate(currentName(), palette);
    }

    private void chooseThemeFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/*", "application/x-yaml", "application/yaml", "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_THEME_FILE);
    }

    private void openSchemeManager(Button action) {
        if (selectedThemeUri == null) return;
        Uri target = selectedThemeUri;
        action.setEnabled(false);
        selectedFileStatus.setText("正在读取已有配色方案……");
        new Thread(() -> {
            try {
                String original = readText(target);
                List<TrimeThemePatcher.SchemeInfo> schemes =
                        TrimeThemePatcher.listSchemes(original);
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    if (schemes.isEmpty()) {
                        selectedFileStatus.setText("所选文件中没有找到 preset_color_schemes");
                        Toast.makeText(this, "没有找到可管理的配色方案", Toast.LENGTH_LONG).show();
                        return;
                    }
                    selectedFileStatus.setText(String.format(
                            Locale.ROOT, "已读取 %d 个配色方案", schemes.size()));
                    showSchemeManagerDialog(new SchemeManagerSession(target, original, schemes));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    selectedFileStatus.setText("读取方案失败：" + safeMessage(error));
                    Toast.makeText(this,
                            "没有修改文件：" + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        }, "trime-scheme-reader").start();
    }

    private void showSchemeManagerDialog(SchemeManagerSession session) {
        DynamicPalette.Scheme colors = previewDark ? palette.dark : palette.light;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = column();
        list.setPadding(dp(4), dp(8), dp(4), dp(8));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text(
                session.hasChanges()
                        ? String.format(Locale.ROOT,
                                "已有 %d 项调整待保存。继续编辑，完成后点击“保存并部署”。",
                                session.editCount)
                        : "改名和删除会先保存在临时草稿中，点击“保存并部署”后才会写入文件。",
                12, colors.onSurfaceVariant, Typeface.NORMAL);
        hint.setLineSpacing(dp(2), 1f);
        list.addView(hint);
        list.addView(space(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(String.format(
                        Locale.ROOT, "配色方案管理（%d）", session.schemes.size()))
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存并部署", null)
                .create();

        boolean canDelete = session.schemes.size() > 1;
        for (int index = 0; index < session.schemes.size(); index++) {
            TrimeThemePatcher.SchemeInfo scheme = session.schemes.get(index);
            LinearLayout item = card(colors, 13, 12);
            item.addView(text(scheme.name, 16, colors.onSurface, Typeface.BOLD));
            TextView id = text("ID：" + scheme.id, 12,
                    colors.onSurfaceVariant, Typeface.NORMAL);
            id.setPadding(0, dp(3), 0, 0);
            item.addView(id);
            item.addView(space(10));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button rename = button("修改名称", false, colors);
            Button delete = button("删除", false, colors);
            delete.setTextColor(previewDark ? 0xFFFFB4AB : 0xFFBA1A1A);
            delete.setEnabled(canDelete);
            delete.setAlpha(canDelete ? 1f : 0.42f);
            rename.setOnClickListener(v -> showRenameSchemeDialog(dialog, session, scheme));
            delete.setOnClickListener(v -> showDeleteSchemeDialog(dialog, session, scheme));
            actions.addView(rename, new LinearLayout.LayoutParams(0, dp(42), 1f));
            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            deleteParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(delete, deleteParams);
            item.addView(actions);
            list.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (index < session.schemes.size() - 1) list.addView(space(9));
        }
        dialog.show();
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setEnabled(session.hasChanges());
        save.setOnClickListener(v -> {
            dialog.dismiss();
            applySchemeManagerChanges(session);
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            if (!session.hasChanges()) {
                dialog.dismiss();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("放弃未保存的调整？")
                    .setMessage("当前所有改名和删除都还没有写入主题文件。")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("放弃", (confirm, which) -> dialog.dismiss())
                    .show();
        });
    }

    private void showRenameSchemeDialog(
            AlertDialog manager,
            SchemeManagerSession session,
            TrimeThemePatcher.SchemeInfo scheme) {
        DynamicPalette.Scheme colors = previewDark ? palette.dark : palette.light;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(scheme.name);
        input.setSelectAllOnFocus(true);
        input.setTextColor(colors.onSurface);
        input.setHintTextColor(withAlpha(colors.onSurfaceVariant, 0.72f));
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(roundRect(colors.surfaceContainer, 14, colors.outlineVariant, 1));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修改方案名称")
                .setMessage("方案 ID：" + scheme.id
                        + "\n只修改显示名称，不会改变方案 ID 和浅／深色切换关系。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError("方案名称不能为空");
                        return;
                    }
                    if (name.equals(scheme.name)) {
                        dialog.dismiss();
                        return;
                    }
                    try {
                        session.draftYaml = TrimeThemePatcher.renameScheme(
                                session.draftYaml, scheme.id, name);
                        session.schemes = TrimeThemePatcher.listSchemes(session.draftYaml);
                        session.editCount++;
                    } catch (Exception error) {
                        input.setError(safeMessage(error));
                        return;
                    }
                    dialog.dismiss();
                    manager.dismiss();
                    showSchemeManagerDialog(session);
                }));
        dialog.show();
    }

    private void showDeleteSchemeDialog(
            AlertDialog manager,
            SchemeManagerSession session,
            TrimeThemePatcher.SchemeInfo scheme) {
        new AlertDialog.Builder(this)
                .setTitle("删除配色方案")
                .setMessage("确定删除“" + scheme.name + "”吗？\n\n方案 ID：" + scheme.id
                        + "\n如果其他方案引用了这个 ID，对应的浅／深色自动切换可能失效。"
                        + "\n\n确认后只会加入待保存调整，尚不会写入文件或重新部署。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        session.draftYaml = TrimeThemePatcher.deleteScheme(
                                session.draftYaml, scheme.id);
                        session.schemes = TrimeThemePatcher.listSchemes(session.draftYaml);
                        session.editCount++;
                        manager.dismiss();
                        showSchemeManagerDialog(session);
                    } catch (Exception error) {
                        Toast.makeText(this,
                                "无法删除：" + safeMessage(error), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void applySchemeManagerChanges(SchemeManagerSession session) {
        if (!session.hasChanges()) return;
        String editorName = currentName();
        selectedFileStatus.setText("正在保存全部方案调整……");
        new Thread(() -> {
            try {
                String current = readText(session.target);
                if (!current.equals(session.originalYaml)) {
                    throw new IllegalStateException("主题文件已在管理期间发生变化，请重新打开方案管理器");
                }
                saveBackup(session.target, current);
                writeText(session.target, session.draftYaml);
                requestTrimeDeploy();
                runOnUiThread(() -> {
                    buildUi(editorName);
                    selectedFileStatus.setText(String.format(
                            Locale.ROOT, "已保存 %d 项方案调整", session.editCount));
                    Toast.makeText(this,
                            "全部调整已写入，仅请求了一次同文重新部署",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    selectedFileStatus.setText("保存方案调整失败：" + safeMessage(error));
                    Toast.makeText(this,
                            "没有修改文件：" + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        }, "trime-scheme-batch-writer").start();
    }

    private void writeIntoSelectedTheme(Button action) {
        if (selectedThemeUri == null) return;
        Uri target = selectedThemeUri;
        String entries = TrimeThemeGenerator.generateEntries(currentName(), palette);
        action.setEnabled(false);
        selectedFileStatus.setText("正在安全合并配色……");

        new Thread(() -> {
            try {
                String original = readText(target);
                TrimeThemePatcher.Result result = TrimeThemePatcher.patch(original, entries);
                saveBackup(target, original);
                writeText(target, result.yaml);
                requestTrimeDeploy();
                String message = result.replacedExisting
                        ? "已更新 " + selectedThemeName + " 中的动态配色"
                        : "已写入 " + selectedThemeName;
                runOnUiThread(() -> {
                    selectedFileStatus.setText(message);
                    action.setEnabled(true);
                    Toast.makeText(this, "写入成功，已请求同文重新部署", Toast.LENGTH_LONG).show();
                    buildUi(currentName());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    selectedFileStatus.setText(String.format(
                            Locale.ROOT, "写入失败：%s", safeMessage(error)));
                    action.setEnabled(true);
                    Toast.makeText(this, "没有修改文件：" + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        }, "trime-theme-writer").start();
    }

    private void writeQuickKeys(Button action) {
        if (selectedThemeUri == null) return;
        Uri target = selectedThemeUri;
        action.setEnabled(false);
        selectedFileStatus.setText("正在写入快捷键配置……");

        new Thread(() -> {
            try {
                String original = readText(target);
                TrimeThemePatcher.Result result =
                        TrimeThemePatcher.patchQuickKeys(original, QUICK_KEYS_YAML);
                saveBackup(target, original);
                writeText(target, result.yaml);
                requestTrimeDeploy();
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    selectedFileStatus.setText("已写入更新配色与收藏配色快捷键");
                    Toast.makeText(this,
                            "快捷键配置写入成功，已请求同文重新部署",
                            Toast.LENGTH_LONG).show();
                    buildUi(currentName());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    selectedFileStatus.setText(String.format(
                            Locale.ROOT, "快捷键写入失败：%s", safeMessage(error)));
                    Toast.makeText(this,
                            "没有修改文件：" + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        }, "trime-quick-key-writer").start();
    }

    private void restoreLastBackup(Button action) {
        if (selectedThemeUri == null) return;
        Uri target = selectedThemeUri;
        action.setEnabled(false);
        new Thread(() -> {
            try (FileInputStream input = openFileInput(BACKUP_FILE)) {
                writeText(target, readText(input));
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    selectedFileStatus.setText("已恢复写入前的文件");
                    Toast.makeText(this, "已撤销上次写入", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    Toast.makeText(this, "撤销失败：" + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        }, "trime-theme-restore").start();
    }

    private void saveBackup(Uri uri, String original) throws Exception {
        try (FileOutputStream output = openFileOutput(BACKUP_FILE, MODE_PRIVATE)) {
            output.write(original.getBytes(StandardCharsets.UTF_8));
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_BACKUP_URI, uri.toString())
                .apply();
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法读取所选文件");
            return readText(input);
        }
    }

    private String readText(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > 16 * 1024 * 1024) {
                throw new IllegalStateException("主题文件超过 16 MB");
            }
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private void writeText(Uri uri, String text) throws Exception {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "rwt")) {
            if (output == null) throw new IllegalStateException("文件提供方拒绝写入");
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void copyYaml() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Trime Material You theme", currentYaml()));
        Toast.makeText(this, "主题代码已复制", Toast.LENGTH_SHORT).show();
    }

    private void exportYaml() {
        pendingExport = currentYaml();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/yaml");
        intent.putExtra(Intent.EXTRA_TITLE, "material_you.trime.yaml");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void shareYaml() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Trime Material You 配色");
        intent.putExtra(Intent.EXTRA_TEXT, currentYaml());
        startActivity(Intent.createChooser(intent, "分享主题代码"));
    }

    private void openWallpaperSettings() {
        try {
            startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
        } catch (Exception ignored) {
            Toast.makeText(this, "请在系统设置中打开壁纸与样式", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestTrimeDeploy() {
        try {
            Intent deploy = new Intent(TRIME_DEPLOY_ACTION);
            deploy.setPackage(TRIME_PACKAGE);
            sendBroadcast(deploy);
        } catch (Exception ignored) {
            // The YAML write is already complete; deployment can still be run manually.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQUEST_THEME_FILE) {
            String name = displayName(uri);
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".yaml") && !lower.endsWith(".yml")) {
                Toast.makeText(this, "请选择 .yaml 或 .yml 主题文件", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                int granted = data.getFlags();
                boolean canRead = (granted & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
                boolean canWrite = (granted & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
                if (canRead && canWrite) {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } else if (canWrite) {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } else if (canRead) {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (Exception ignored) {
                // Some document providers only grant access while the app process is alive.
            }
            selectedThemeUri = uri;
            selectedThemeName = name;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_THEME_URI, uri.toString())
                    .putString(KEY_THEME_NAME, name)
                    .apply();
            buildUi(currentName());
            Toast.makeText(this, "已选择 " + name, Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestCode != REQUEST_EXPORT) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("无法打开目标文件");
            output.write(pendingExport.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "已导出 material_you.trime.yaml", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "导出失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Exception ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "theme.trime.yaml" : fallback;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message;
    }

    private View sectionTitle(String value, DynamicPalette.Scheme colors) {
        return text(value, 19, colors.onSurface, Typeface.BOLD);
    }

    private View sectionCaption(String value, DynamicPalette.Scheme colors) {
        TextView caption = text(value, 12, colors.onSurfaceVariant, Typeface.NORMAL);
        caption.setPadding(0, dp(3), 0, 0);
        return caption;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        view.setGravity(Gravity.START);
        return view;
    }

    private Button button(String label, boolean primary, DynamicPalette.Scheme colors) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(primary ? colors.onPrimary : colors.primary);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(roundRect(
                primary ? colors.primary : colors.surfaceContainer,
                14,
                primary ? Color.TRANSPARENT : colors.outlineVariant,
                primary ? 0 : 1));
        return button;
    }

    private Button toggleButton(String label, boolean selected, DynamicPalette.Scheme colors) {
        Button button = button(label, selected, colors);
        button.setBackground(roundRect(
                selected ? colors.primary : Color.TRANSPARENT,
                13, Color.TRANSPARENT, 0));
        button.setTextColor(selected ? colors.onPrimary : colors.onSurfaceVariant);
        return button;
    }

    private LinearLayout card(DynamicPalette.Scheme colors, int paddingHorizontal, int paddingVertical) {
        LinearLayout card = column();
        card.setPadding(dp(paddingHorizontal), dp(paddingVertical),
                dp(paddingHorizontal), dp(paddingVertical));
        card.setBackground(roundRect(colors.surfaceContainer, 20, colors.outlineVariant, 1));
        card.setElevation(dp(1));
        return card;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private View space(int heightDp) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return space;
    }

    private GradientDrawable roundRect(int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private GradientDrawable oval(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    private void updateSystemBars(DynamicPalette.Scheme colors) {
        Window window = getWindow();
        window.setStatusBarColor(colors.surface);
        window.setNavigationBarColor(colors.surface);
        int flags = 0;
        if (isLight(colors.surface)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26 && isLight(colors.surface)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 28) window.setNavigationBarDividerColor(colors.outlineVariant);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private boolean isSystemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean isLight(int color) {
        double luminance = 0.2126 * Color.red(color)
                + 0.7152 * Color.green(color)
                + 0.0722 * Color.blue(color);
        return luminance > 150;
    }

    private int withAlpha(int color, float fraction) {
        return Color.argb(Math.round(255 * fraction), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private ViewGroup.LayoutParams matchWrap() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
