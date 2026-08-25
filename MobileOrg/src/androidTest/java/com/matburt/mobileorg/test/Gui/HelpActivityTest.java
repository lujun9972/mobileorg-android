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

    @Rule
    public ActivityTestRule<HelpDetailActivity> detailRule = new ActivityTestRule<>(
            HelpDetailActivity.class, true, false);

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
}
