package com.bl3axy.railplanner;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Rail Planner: the planner page (assets/index.html) in a full-screen WebView. */
public class MainActivity extends Activity {
    private WebView web;
    private String pendingShared;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_FILE = 7;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        pendingShared = sharedText(getIntent());
        web = new WebView(this);
        web.setBackgroundColor(0xFF0E1318);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);           // layouts are saved in localStorage
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                // links (if any) open in the browser, not inside the app
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Open layout"), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(web);
        if (saved != null) web.restoreState(saved);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILE && fileCallback != null) {
            Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            fileCallback.onReceiveValue(u == null ? null : new Uri[]{u});
            fileCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    /** a link shared to the app while it's already open */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String t = sharedText(intent);
        if (t != null) web.evaluateJavascript("window.onSharedText && window.onSharedText(" + JSONObject.quote(t) + ")", null);
    }

    private static String sharedText(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return null;
        CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
        return t == null ? null : t.toString();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // let the page close an open panel first
        web.evaluateJavascript("(window.RailPlanner && window.RailPlanner.back()) ? 1 : 0", v -> {
            if (!"1".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onPause() {
        // ask the page to save right away
        web.evaluateJavascript("window.RailPlanner && window.RailPlanner.saveNow()", null);
        super.onPause();
    }

    /** things the page can ask the phone to do */
    private class Bridge {
        /** fetch a URL on the local network (the Create Track Map server); answers via window.__httpDone */
        @JavascriptInterface
        public void httpGet(final String url, final String id) {
            new Thread(() -> {
                boolean ok = false;
                String text;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("Accept", "application/json");
                    int code = c.getResponseCode();
                    if (code / 100 != 2) {
                        text = "HTTP " + code;
                    } else {
                        try (InputStream in = c.getInputStream()) {
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                bo.write(buf, 0, n);
                                if (bo.size() > 40_000_000) throw new Exception("response too big");
                            }
                            text = bo.toString(StandardCharsets.UTF_8.name());
                            ok = true;
                        }
                    }
                } catch (Exception e) {
                    text = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
                final String js = "window.__httpDone(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(text) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        /** share an exported layout (to Drive, Messages, Files, ...) */
        @JavascriptInterface
        public void share(final String text, final String name) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, name);
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Save or send layout"));
            });
        }

        /** the link the app was opened with (shared from the browser), once */
        @JavascriptInterface
        public String takeSharedText() {
            String t = pendingShared;
            pendingShared = null;
            return t == null ? "" : t;
        }
    }
}
