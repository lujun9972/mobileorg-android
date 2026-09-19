package com.matburt.mobileorg.test.Gui.Theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.matburt.mobileorg.Gui.Statistics.ChartThemeConfig;
import com.matburt.mobileorg.Gui.Theme.CustomThemeStore;
import com.matburt.mobileorg.util.OrgUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

/**
 * Custom theme store + theme-name resolution tests.
 * Prefs-only (no provider). Every test restores the "theme" key and
 * removes custom_theme_* keys so other test classes are unaffected.
 */
@RunWith(AndroidJUnit4.class)
public class CustomThemeTest {

	private SharedPreferences prefs;
	private String origTheme;

	@Before
	public void setUp() {
		Context ctx = ApplicationProvider.getApplicationContext();
		prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		origTheme = prefs.getString("theme", "Dark");
		prefs.edit().remove(CustomThemeStore.KEY_BASE).commit();
	}

	@After
	public void tearDown() {
		prefs.edit().putString("theme", origTheme)
				.remove(CustomThemeStore.KEY_BASE).commit();
	}

	@Test
	public void testBaseDefaultsToDark() {
		assertEquals("Dark", CustomThemeStore.getBase());
	}

	@Test
	public void testEffectiveNameResolvesCustomToBase() {
		prefs.edit().putString("theme", "Custom").commit();
		assertEquals("Dark", OrgUtils.getEffectiveThemeName());
		prefs.edit().putString(CustomThemeStore.KEY_BASE, "Light").commit();
		assertEquals("Light", OrgUtils.getEffectiveThemeName());
	}

	@Test
	public void testIsDarkThemeFollowsBase() {
		prefs.edit().putString("theme", "Custom").commit();
		assertEquals(true, OrgUtils.isDarkTheme());
		prefs.edit().putString(CustomThemeStore.KEY_BASE, "Light").commit();
		assertEquals(false, OrgUtils.isDarkTheme());
	}

	@Test
	public void testChartThemeConfigFollowsBase() {
		prefs.edit().putString("theme", "Custom").commit();
		int darkBg = ChartThemeConfig.current().backgroundColor;
		prefs.edit().putString(CustomThemeStore.KEY_BASE, "Light").commit();
		int lightBg = ChartThemeConfig.current().backgroundColor;
		assertEquals(false, darkBg == lightBg);
	}
}
