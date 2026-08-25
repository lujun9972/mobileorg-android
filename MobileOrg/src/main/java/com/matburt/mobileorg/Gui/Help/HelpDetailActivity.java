package com.matburt.mobileorg.Gui.Help;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.PreferenceUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HelpDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ASSET_PATH = "asset_path";

    private WebView webView;
    public boolean pageFinished; // 测试轮询用（测试在 test.Gui 包，须 public）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_detail);

        webView = findViewById(R.id.help_webview);
        webView.setWebViewClient(new HelpWebViewClient());
        webView.getSettings().setBuiltInZoomControls(true);
        webView.setBackgroundColor(
                DefaultTheme.getTheme(this).defaultBackground);

        String assetPath = getIntent().getStringExtra(EXTRA_ASSET_PATH);
        if (assetPath == null) {
            displayError();
            return;
        }
        loadAsset(assetPath);
    }

    private void loadAsset(String assetPath) {
        try {
            String html = readAsset(assetPath);
            if (isDarkTheme())
                html = html.replace("<html", "<html class=\"dark\"");
            webView.loadDataWithBaseURL(
                    "file:///android_asset/help/", html, "text/html", "UTF-8", null);
        } catch (IOException e) {
            displayError();
        }
    }

    private String readAsset(String path) throws IOException {
        InputStream in = getAssets().open(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        in.close();
        return sb.toString();
    }

    private boolean isDarkTheme() {
        return !"Light".equals(PreferenceUtils.getThemeName());
    }

    private void displayError() {
        webView.loadDataWithBaseURL(null,
                "<html><body style='background:#101010;color:#ccc'>"
                        + getString(R.string.help_detail_error)
                        + "</body></html>",
                "text/html", "UTF-8", null);
    }

    private class HelpWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("file://"))
                return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageFinished = true;
        }
    }
}
