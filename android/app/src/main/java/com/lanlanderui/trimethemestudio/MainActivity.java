package com.lanlanderui.trimethemestudio;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_CREATE_FILE = 1002;
    private static final int REQUEST_OPEN_TREE = 1003;
    private static final String PREFS_NAME = "rime_files";
    private static final String KEY_LAST_FILE_URI = "last_file_uri";
    private static final String KEY_LAST_TREE_URI = "last_tree_uri";

    private WebView webView;
    private Uri currentFileUri;
    private String pendingSaveText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                restoreLastAccess();
            }
        });
        webView.addJavascriptInterface(new RimeFileBridge(), "AndroidRime");
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            notifyWeb("已取消文件操作");
            return;
        }

        Uri uri = data.getData();
        persistUriPermission(uri, data);

        try {
            if (requestCode == REQUEST_OPEN_FILE) {
                openUriInEditor(uri);
            } else if (requestCode == REQUEST_CREATE_FILE) {
                writeTextToUri(uri, pendingSaveText);
                currentFileUri = uri;
                prefs().edit().putString(KEY_LAST_FILE_URI, uri.toString()).apply();
                notifyWeb("已保存 " + displayName(uri));
            } else if (requestCode == REQUEST_OPEN_TREE) {
                prefs().edit().putString(KEY_LAST_TREE_URI, uri.toString()).apply();
                sendFileList(uri, prefs().getString(KEY_LAST_FILE_URI, ""));
            }
        } catch (Exception error) {
            notifyWeb("文件操作失败：" + error.getMessage());
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void restoreLastAccess() {
        SharedPreferences preferences = prefs();
        String treeString = preferences.getString(KEY_LAST_TREE_URI, "");
        String fileString = preferences.getString(KEY_LAST_FILE_URI, "");

        if (treeString != null && !treeString.isEmpty()) {
            try {
                sendFileList(Uri.parse(treeString), fileString);
                return;
            } catch (Exception ignored) {
            }
        }

        if (fileString != null && !fileString.isEmpty()) {
            try {
                openUriInEditor(Uri.parse(fileString));
            } catch (Exception ignored) {
            }
        }
    }

    private void persistUriPermission(Uri uri, Intent data) {
        int flags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (flags == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // Some providers expose temporary permissions only.
        }
    }

    private void openUriInEditor(Uri uri) throws Exception {
        String text = readTextFromUri(uri);
        currentFileUri = uri;
        prefs().edit().putString(KEY_LAST_FILE_URI, uri.toString()).apply();
        evaluate("window.TrimeAndroidBridge && window.TrimeAndroidBridge.receiveFile("
                + JSONObject.quote(displayName(uri)) + ","
                + JSONObject.quote(text) + ","
                + JSONObject.quote(uri.toString()) + ")");
    }

    private void sendFileList(Uri treeUri, String preferredUri) throws Exception {
        JSONArray files = listYamlFiles(treeUri);
        evaluate("window.TrimeAndroidBridge && window.TrimeAndroidBridge.receiveFileList("
                + JSONObject.quote(files.toString()) + ","
                + JSONObject.quote(preferredUri == null ? "" : preferredUri) + ")");
    }

    private String readTextFromUri(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法读取文件");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void writeTextToUri(Uri uri, String text) throws Exception {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("无法写入文件");
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONArray listYamlFiles(Uri treeUri) throws Exception {
        JSONArray files = new JSONArray();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
        );
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                projection,
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC"
        )) {
            if (cursor == null) return files;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) || !isYamlName(name)) {
                    continue;
                }
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn));
                JSONObject file = new JSONObject();
                file.put("name", name);
                file.put("uri", docUri.toString());
                files.put(file);
            }
        }
        return files;
    }

    private boolean isYamlName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[] { OpenableColumns.DISPLAY_NAME },
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (Exception ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "trime.yaml" : fallback;
    }

    private void evaluate(String script) {
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private void notifyWeb(String message) {
        evaluate("window.TrimeAndroidBridge && window.TrimeAndroidBridge.notify("
                + JSONObject.quote(message) + ")");
    }

    public class RimeFileBridge {
        @JavascriptInterface
        public void openFile() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "text/*",
                        "application/x-yaml",
                        "application/octet-stream"
                });
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_OPEN_FILE);
            });
        }

        @JavascriptInterface
        public void openFolder() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_OPEN_TREE);
            });
        }

        @JavascriptInterface
        public void openTreeFile(String uriString) {
            runOnUiThread(() -> {
                try {
                    openUriInEditor(Uri.parse(uriString));
                } catch (Exception error) {
                    notifyWeb("读取配置失败：" + error.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String text) {
            runOnUiThread(() -> {
                if (currentFileUri == null) {
                    saveAsFile("trime.edited.yaml", text);
                    return;
                }
                try {
                    writeTextToUri(currentFileUri, text);
                    notifyWeb("已保存 " + displayName(currentFileUri));
                } catch (Exception error) {
                    notifyWeb("保存失败：" + error.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void saveAsFile(String name, String text) {
            pendingSaveText = text == null ? "" : text;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/yaml");
                intent.putExtra(Intent.EXTRA_TITLE, (name == null || name.isEmpty())
                        ? "trime.edited.yaml"
                        : name);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_CREATE_FILE);
            });
        }
    }
}
