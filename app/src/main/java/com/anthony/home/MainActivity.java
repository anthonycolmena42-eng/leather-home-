package com.anthony.home;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Leather Home: a full-screen web dashboard that acts as the TV's home screen. */
public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;
    private String appsCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        handleIntent(getIntent());

        web = new WebView(this);
        web.setBackgroundColor(0xFF120B07);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Home");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus(View.FOCUS_DOWN);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        if (web != null) web.evaluateJavascript("window.refresh && refresh()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        appsCache = null; // pick up newly installed / disabled apps
        if (web != null) web.evaluateJavascript("window.refresh && refresh()", null);
    }

    /** It's the home screen: Back does nothing. */
    @Override
    public void onBackPressed() { }

    /** adb shell am start -n com.anthony.home/.MainActivity --es focus "Item one|Item two" */
    private void handleIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("focus")) return;
        try {
            JSONArray arr = new JSONArray();
            for (String t : intent.getStringExtra("focus").split("\\|")) {
                if (t.trim().isEmpty()) continue;
                arr.put(new JSONObject().put("t", t.trim()).put("done", false));
            }
            prefs.edit().putString("focus", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private String toDataUrl(Drawable d) {
        if (d == null) return "";
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 320;
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 180;
        if (w > 640) { h = h * 640 / w; w = 640; }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    public class Bridge {
        /** Installed TV apps as JSON: [{name, pkg, banner, icon}] */
        @JavascriptInterface
        public String apps() {
            if (appsCache != null) return appsCache;
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            JSONArray arr = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                try {
                    Drawable banner = ri.activityInfo.loadBanner(pm);
                    if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                    arr.put(new JSONObject()
                            .put("name", ri.loadLabel(pm).toString())
                            .put("pkg", pkg)
                            .put("banner", toDataUrl(banner))
                            .put("icon", banner == null ? toDataUrl(ri.loadIcon(pm)) : ""));
                } catch (Exception ignored) { }
            }
            appsCache = arr.toString();
            return appsCache;
        }

        @JavascriptInterface
        public void launch(String pkg) {
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return;
            final Intent go = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(go));
        }

        @JavascriptInterface
        public void settings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }

        @JavascriptInterface
        public String getFocus() { return prefs.getString("focus", "[]"); }

        @JavascriptInterface
        public void setFocus(String json) { prefs.edit().putString("focus", json).apply(); }
    }
}
