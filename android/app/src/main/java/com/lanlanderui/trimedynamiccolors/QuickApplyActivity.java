package com.lanlanderui.trimedynamiccolors;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Exported component launched by a Trime custom key to refresh colors without opening the UI. */
public final class QuickApplyActivity extends Activity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uriValue = preferences.getString(KEY_THEME_URI, "");
        if (uriValue == null || uriValue.isEmpty()) {
            Toast.makeText(this, "请先选择一个同文主题 YAML", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        Uri target = Uri.parse(uriValue);
        String fileName = preferences.getString(KEY_THEME_NAME, "主题文件");
        String schemeName = preferences.getString(KEY_SCHEME_NAME, "Material You");
        Toast.makeText(this, "正在应用当前配色……", Toast.LENGTH_SHORT).show();

        new Thread(() -> applyLatestColors(target, fileName, schemeName),
                "trime-quick-apply").start();
    }

    private void applyLatestColors(Uri target, String fileName, String schemeName) {
        try {
            SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean manual = SOURCE_MANUAL.equals(
                    preferences.getString(KEY_COLOR_SOURCE, "system"));
            DynamicPalette.Result palette = manual
                    ? SeedPalette.create(preferences.getInt(KEY_MANUAL_SEED, 0xFF6750A4))
                    : DynamicPalette.load(this);
            String entries = TrimeThemeGenerator.generateEntries(schemeName, palette);
            String original = readText(target);
            TrimeThemePatcher.Result result = TrimeThemePatcher.patch(original, entries);
            saveBackup(target, original);
            writeText(target, result.yaml);
            requestTrimeDeploy();
            runOnUiThread(() -> {
                Toast.makeText(this,
                        "已更新 " + fileName + "，并请求同文重新部署",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        } catch (Exception error) {
            runOnUiThread(() -> {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = "无法访问主题文件";
                Toast.makeText(this,
                        "快速更新失败：" + message + "。请打开 App 重新选择文件",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法读取所选文件");
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
    }

    private void saveBackup(Uri uri, String original) throws Exception {
        try (FileOutputStream output = openFileOutput(BACKUP_FILE, MODE_PRIVATE)) {
            output.write(original.getBytes(StandardCharsets.UTF_8));
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_BACKUP_URI, uri.toString())
                .apply();
    }

    private void writeText(Uri uri, String text) throws Exception {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "rwt")) {
            if (output == null) throw new IllegalStateException("文件提供方拒绝写入");
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void requestTrimeDeploy() {
        try {
            Intent deploy = new Intent(TRIME_DEPLOY_ACTION);
            deploy.setPackage(TRIME_PACKAGE);
            sendBroadcast(deploy);
        } catch (Exception ignored) {
            // The YAML update has already succeeded; deployment can still be run manually.
        }
    }
}
