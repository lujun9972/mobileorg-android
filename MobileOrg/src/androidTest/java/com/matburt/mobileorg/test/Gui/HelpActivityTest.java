package com.matburt.mobileorg.test.Gui;

import android.app.Instrumentation;
import android.content.Intent;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.matburt.mobileorg.Gui.Help.HelpActivity;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Help.HelpDetailActivity;
import com.matburt.mobileorg.Gui.Help.HelpTopic;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

@RunWith(AndroidJUnit4.class)
public class HelpActivityTest {

    @Rule
    public ActivityTestRule<HelpActivity> activityRule =
            new ActivityTestRule<>(HelpActivity.class);

    @Rule
    public ActivityTestRule<HelpDetailActivity> detailRule = new ActivityTestRule<>(
            HelpDetailActivity.class, true, false);

    @Test
    public void listShowsAllTopics() {
        RecyclerView rv = activityRule.getActivity()
                .findViewById(R.id.help_recycler);
        assertEquals(HelpTopic.TOPICS.length, rv.getAdapter().getItemCount());
    }

    @Test
    public void aboutBlockShowsVersion() throws Exception {
        HelpActivity activity = activityRule.getActivity();
        TextView version = activity.findViewById(R.id.help_about_version);
        String versionName = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0).versionName;
        assertNotNull(versionName);
        assertEquals(activity.getString(R.string.help_about_version, versionName),
                version.getText().toString());
    }

    @Test
    public void clickTopicOpensDetail() throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                HelpDetailActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);

        RecyclerView rv = activityRule.getActivity().findViewById(R.id.help_recycler);
        instrumentation.runOnMainSync(() -> rv.getChildAt(0).performClick());

        android.app.Activity detail = monitor.waitForActivityWithTimeout(5000);
        assertNotNull(detail);
        String expectedPath = HelpTopic.getAssetPath(
                activityRule.getActivity(), HelpTopic.TOPICS[0]);
        assertEquals(expectedPath,
                detail.getIntent().getStringExtra(HelpDetailActivity.EXTRA_ASSET_PATH));
    }

    @Test
    public void detailLoadsAssetHtml() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(HelpDetailActivity.EXTRA_ASSET_PATH, "help/en/quick-start.html");
        HelpDetailActivity detail = detailRule.launchActivity(intent);
        long deadline = System.currentTimeMillis() + 5000;
        while (!detail.pageFinished && System.currentTimeMillis() < deadline)
            SystemClock.sleep(100);
        assertTrue(detail.pageFinished);
    }

    @Test
    public void detailInternalLinkNavigationLoadsTargetAsset() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(HelpDetailActivity.EXTRA_ASSET_PATH, "help/en/quick-start.html");
        HelpDetailActivity detail = detailRule.launchActivity(intent);
        long deadline = System.currentTimeMillis() + 5000;
        while (!detail.pageFinished && System.currentTimeMillis() < deadline)
            SystemClock.sleep(100);
        assertTrue(detail.pageFinished);

        // 模拟用户点击站内链接：shouldOverrideUrlLoading 应接管并加载目标 asset
        // （JS 合成 click 在部分 WebView 版本不触发导航回调，故直接驱动回调本身）
        detail.pageFinished = false;
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final boolean[] handled = new boolean[1];
        instrumentation.runOnMainSync(() -> handled[0] = detail.getWebViewClientForTest()
                .shouldOverrideUrlLoading(null, "file:///android_asset/help/en/sync.html"));
        assertTrue("站内链接应由客户端接管加载", handled[0]);
        deadline = System.currentTimeMillis() + 8000;
        while (!detail.pageFinished && System.currentTimeMillis() < deadline)
            SystemClock.sleep(100);
        assertTrue("目标页面应完成加载", detail.pageFinished);
        // historyUrl=null 时 getUrl() 恒为 about:blank，以实际加载路径断言导航目标
        assertEquals("help/en/sync.html", detail.lastLoadedAssetPath);
    }
}
