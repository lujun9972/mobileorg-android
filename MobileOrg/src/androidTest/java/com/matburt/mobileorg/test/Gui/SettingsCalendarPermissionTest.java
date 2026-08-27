package com.matburt.mobileorg.test.Gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.Settings.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Behavioral spec: the Settings screen must not auto-prompt for calendar
 * permission unless the user actually enabled calendar sync, and must ask
 * at most once per install until permission is granted.
 *
 * Ordering matters: test4 grants the calendar permissions, and revoking a
 * granted permission force-kills the app process (MIUI PowerKeeper, and
 * modern AOSP alike) — which would kill the instrumentation itself. So the
 * grant-scenario test must run last, and tearDown must never revoke.
 * Gradle reinstalls both APKs after every run, so each run always starts
 * from the clean denied state.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
public class SettingsCalendarPermissionTest {

    private static final String KEY_CALENDAR_ENABLED = "calendarEnabled";
    private static final String KEY_PROMPT_SHOWN = "calendarPermissionPromptShown";
    private static final String APP_PKG = "com.matburt.mobileorg";

    private Context targetContext;
    private SharedPreferences prefs;

    @Before
    public void setUp() throws Exception {
        // The device must be awake and unlocked, otherwise the activity under
        // test never reaches RESUMED and ActivityScenario.launch times out.
        shell("input keyevent KEYCODE_WAKEUP");
        shell("wm dismiss-keyguard");
        Thread.sleep(500);
        targetContext = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(targetContext);
        revokeCalendarPermissions();
        prefs.edit()
                .putBoolean(KEY_CALENDAR_ENABLED, false)
                .remove(KEY_PROMPT_SHOWN)
                .commit();
    }

    @After
    public void tearDown() {
        dismissSystemDialogIfAny();
        // NOTE: never revoke permissions here — revoking a granted permission
        // force-kills the app process (i.e. the instrumentation itself).
        prefs.edit()
                .putBoolean(KEY_CALENDAR_ENABLED, false)
                .remove(KEY_PROMPT_SHOWN)
                .commit();
    }

    @Test
    public void test1_disabledCalendarDoesNotPromptForPermission() throws Exception {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            assertNoPermissionDialog(scenario, 2000);
        }
    }

    @Test
    public void test3_promptedOnceDoesNotPromptAgain() throws Exception {
        prefs.edit()
                .putBoolean(KEY_CALENDAR_ENABLED, true)
                .putBoolean(KEY_PROMPT_SHOWN, true)
                .commit();
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            assertNoPermissionDialog(scenario, 2000);
        }
    }

    @Test
    public void test2_enabledCalendarPromptsOnceAndRecordsFlag() throws Exception {
        prefs.edit().putBoolean(KEY_CALENDAR_ENABLED, true).commit();
        // launch() returns once the activity first reaches RESUMED; the
        // permission dialog then covers and pauses it. Focus-window polling
        // is unreliable across vendors (MIUI's LBE dialog), so detect the
        // dialog through our own activity's lifecycle instead.
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            assertTrue("首次启用日历同步应弹出权限申请",
                    waitForActivityPaused(scenario, 5000));
        }
        assertTrue("申请时应置位提示标志",
                prefs.getBoolean(KEY_PROMPT_SHOWN, false));
    }

    @Test
    public void test4_grantedPermissionClearsPromptFlag() throws Exception {
        grantCalendarPermissions();
        prefs.edit()
                .putBoolean(KEY_CALENDAR_ENABLED, true)
                .putBoolean(KEY_PROMPT_SHOWN, true)
                .commit();
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            assertNoPermissionDialog(scenario, 2000);
        }
        assertFalse("授权后应清除申请标志（日后撤销权限可再次申请）",
                prefs.getBoolean(KEY_PROMPT_SHOWN, false));
    }

    // ----- helpers -----


    private static String currentFocus() throws Exception {
        String out = shell("dumpsys window windows | grep mCurrentFocus");
        // Line looks like: "  mCurrentFocus=Window{abc u0 pkg/.Activity}"
        int i = out.indexOf("mCurrentFocus=");
        return i >= 0 ? out.substring(i) : out;
    }

    private static void dismissSystemDialogIfAny() {
        try {
            String focus = currentFocus();
            if (focus != null && !focus.contains(APP_PKG)) {
                shell("input keyevent 4"); // BACK dismisses permission dialogs (deny)
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * A system permission dialog covering the activity pauses it; if the
     * activity stays RESUMED for the whole window, no dialog was shown.
     */
    private static void assertNoPermissionDialog(ActivityScenario<?> scenario, long ms)
            throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            assertTrue("不应弹出日历权限申请（activity 被对话框暂停）",
                    scenario.getState() == Lifecycle.State.RESUMED);
            Thread.sleep(200);
        }
    }

    private static boolean waitForActivityPaused(ActivityScenario<?> scenario, long ms)
            throws Exception {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            // Lifecycle.State has no PAUSED granularity — a dialog-covered
            // (paused) activity drops back to STARTED.
            if (scenario.getState() == Lifecycle.State.STARTED) return true;
            Thread.sleep(100);
        }
        return false;
    }

    private static void revokeCalendarPermissions() {
        shell("pm revoke " + APP_PKG + " android.permission.READ_CALENDAR");
        shell("pm revoke " + APP_PKG + " android.permission.WRITE_CALENDAR");
    }

    private static void grantCalendarPermissions() {
        shell("pm grant " + APP_PKG + " android.permission.READ_CALENDAR");
        shell("pm grant " + APP_PKG + " android.permission.WRITE_CALENDAR");
    }

    private static String shell(String cmd) {
        try {
            ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(cmd);
            if (pfd == null) return "";
            StringBuilder sb = new StringBuilder();
            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
