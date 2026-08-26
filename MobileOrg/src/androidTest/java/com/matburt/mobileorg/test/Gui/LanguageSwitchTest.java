package com.matburt.mobileorg.test.Gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Settings.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LanguageSwitchTest {

    private Context targetContext;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        targetContext = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(targetContext);
    }

    @After
    public void tearDown() {
        // Restore follow-system state so other tests are not polluted.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        prefs.edit().putString(SettingsActivity.KEY_APP_LANGUAGE, "system").commit();
    }

    @Test
    public void appLanguagePreferenceAppliesApplicationLocales() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                // Commit AFTER launch: syncLanguagePreference() in onCreate writes
                // back a value derived from the (still empty) app locales and would
                // otherwise overwrite the pre-seeded "zh".
                prefs.edit().putString(SettingsActivity.KEY_APP_LANGUAGE, "zh").commit();
                activity.onSharedPreferenceChanged(prefs, SettingsActivity.KEY_APP_LANGUAGE);
                LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
                assertTrue("app_language=zh 应设置应用 locale 为 zh，实际: " + locales.toLanguageTags(),
                        locales.toLanguageTags().startsWith("zh"));
            });
        }
    }

    @Test
    public void systemLanguageValueClearsApplicationLocales() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
                // Same ordering note as above: commit after the activity's own
                // syncLanguagePreference() pass, then simulate the listener that a
                // real ListPreference change would fire.
                prefs.edit().putString(SettingsActivity.KEY_APP_LANGUAGE, "system").commit();
                activity.onSharedPreferenceChanged(prefs, SettingsActivity.KEY_APP_LANGUAGE);
                assertTrue("app_language=system 应清空应用 locale",
                        AppCompatDelegate.getApplicationLocales().isEmpty());
            });
        }
    }

    @Test
    public void appLocaleOverridesSystemLanguageOnNonAppCompatActivity() {
        // Device system locale is zh; force en and verify the PreferenceActivity
        // (framework, not AppCompatActivity) renders English via wrapForAppLocales.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("en",
                        activity.getResources().getConfiguration().locale.getLanguage());
                assertEquals("Settings", activity.getString(R.string.menu_settings));
            });
        }
    }

    @Test
    public void zhLocaleResolvesChineseResourcesOnNonAppCompatActivity() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"));
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("zh",
                        activity.getResources().getConfiguration().locale.getLanguage());
                assertEquals("设置", activity.getString(R.string.menu_settings));
                assertEquals("保存", activity.getString(R.string.menu_save));
            });
        }
    }

    @Test
    public void themeEntriesLocalizedButStoredValuesUntouched() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"));
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                String[] entries = activity.getResources().getStringArray(R.array.themesEntries);
                String[] values = activity.getResources().getStringArray(R.array.themes);
                assertEquals("深色", entries[1]);
                assertEquals("Dark", values[1]);
            });
        }
    }
}
