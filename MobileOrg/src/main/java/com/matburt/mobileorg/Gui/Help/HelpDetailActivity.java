package com.matburt.mobileorg.Gui.Help;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;

import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HelpDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ASSET_PATH = "asset_path";
    private static final String INTERNAL_LINK_BASE = "file:///android_asset/help/";

    private WebView webView;
    private HelpWebViewClient webViewClient;
    @VisibleForTesting // 测试轮询用（测试在 test.Gui 包，须 public）
    public boolean pageFinished;
    @VisibleForTesting // 断言站内链接导航实际加载的页面（getUrl 在 historyUrl=null 时恒为 about:blank）
    public String lastLoadedAssetPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_detail);
        // Manifest label 由框架 hoist、不经 AppCompat locale 解析；
        // 显式 setTitle 才能让 API<33 切换语言后 ActionBar 标题跟随。
        setTitle(R.string.help_title);

        webView = findViewById(R.id.help_webview);
        webViewClient = new HelpWebViewClient();
        webView.setWebViewClient(webViewClient);
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
        lastLoadedAssetPath = assetPath;
        try {
            String html = readAsset(assetPath);
            if (OrgUtils.isDarkTheme())
                html = html.replace("<html", "<html class=\"dark\"");
            // baseUrl 取 assetPath 所在目录，使页面内相对链接（sync.html 等）
            // 解析到同 locale 子目录；css/图片在 html 中以 ../ 引用根目录共享资源
            String baseUrl = "file:///android_asset/"
                    + assetPath.substring(0, assetPath.lastIndexOf('/') + 1);
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
        } catch (IOException e) {
            displayError();
        }
    }

    private String readAsset(String path) throws IOException {
        InputStream in = getAssets().open(path);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            in.close();
        }
    }

    private void displayError() {
        String background = String.format("#%06X",
                0xFFFFFF & DefaultTheme.getTheme(this).defaultBackground);
        String foreground = OrgUtils.isDarkTheme() ? "#ccc" : "#333";
        webView.loadDataWithBaseURL(null,
                "<html><body style='background:" + background + ";color:" + foreground + "'>"
                        + getString(R.string.help_detail_error)
                        + "</body></html>",
                "text/html", "UTF-8", null);
    }

    @VisibleForTesting // 测试直接驱动站内链接导航（JS 合成点击不触发回调）
    public HelpWebViewClient getWebViewClientForTest() {
        return webViewClient;
    }

    public class HelpWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(INTERNAL_LINK_BASE)) {
                // 站内链接：经 loadAsset 重新加载，保留 dark class 注入与加载失败兜底
                loadAsset(url.substring("file:///android_asset/".length()));
                return true;
            }
            if (url.startsWith("file://"))
                return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(HelpDetailActivity.this,
                        R.string.help_link_no_handler, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageFinished = true;
        }
    }
}
