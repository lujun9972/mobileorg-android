package com.matburt.mobileorg.test.Gui.Theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.matburt.mobileorg.Gui.Statistics.ChartThemeConfig;
import com.matburt.mobileorg.Gui.Theme.CustomThemeStore;
import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.Gui.Theme.HsvColorPickerView;
import com.matburt.mobileorg.Gui.Theme.MonoTheme;
import com.matburt.mobileorg.Gui.Theme.WhiteTheme;
import com.matburt.mobileorg.util.OrgUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;

/**
 * Custom theme store + theme-name resolution + preset replication tests.
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
		clearCustomKeys();
	}

	@After
	public void tearDown() {
		prefs.edit().putString("theme", origTheme).commit();
		clearCustomKeys();
	}

	private void clearCustomKeys() {
		SharedPreferences.Editor e = prefs.edit();
		for (String k : CustomThemeStore.prefKeys())
			e.remove(k);
		e.commit();
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

	@Test
	public void testCustomThemeSeedsFromDark() {
		prefs.edit().putString("theme", "Custom").commit();
		DefaultTheme custom = DefaultTheme.getTheme(
				InstrumentationRegistry.getInstrumentation().getTargetContext());
		DefaultTheme dark = new DefaultTheme();
		assertEquals(dark.gray, custom.gray);
		assertEquals(dark.c1Red, custom.c1Red);
		assertEquals(dark.caLGreen, custom.caLGreen);
		assertEquals(dark.c3Yellow, custom.c3Yellow);
		assertEquals(dark.c4Blue, custom.c4Blue);
		assertEquals(dark.defaultForeground, custom.defaultForeground);
		assertEquals(dark.defaultBackground, custom.defaultBackground);
		assertEquals("white", custom.defaultFontColor);
		assertEquals(10, custom.levelColors.length);
	}

	private void assertReplicates(DefaultTheme preset) {
		DefaultTheme custom = DefaultTheme.getTheme(
				InstrumentationRegistry.getInstrumentation().getTargetContext());
		assertEquals(preset.gray, custom.gray);
		assertEquals(preset.c1Red, custom.c1Red);
		assertEquals(preset.caLGreen, custom.caLGreen);
		assertEquals(preset.c3Yellow, custom.c3Yellow);
		assertEquals(preset.c4Blue, custom.c4Blue);
		assertEquals(preset.defaultForeground, custom.defaultForeground);
		assertEquals(preset.defaultBackground, custom.defaultBackground);
		assertEquals(preset.defaultFontColor, custom.defaultFontColor);
		for (int i = 0; i < 10; i++)
			assertEquals("level " + i,
					preset.levelColors[i % preset.levelColors.length],
					custom.levelColors[i]);
	}

	@Test
	public void testReplicatesWhitePreset() {
		prefs.edit().putString("theme", "Custom").commit();
		CustomThemeStore.saveAll(CustomThemeStore.presetValues("Light"), null);
		assertReplicates(new WhiteTheme());
	}

	@Test
	public void testReplicatesMonoPreset() {
		prefs.edit().putString("theme", "Custom").commit();
		CustomThemeStore.saveAll(CustomThemeStore.presetValues("Monochrome"), null);
		assertReplicates(new MonoTheme());
	}

	@Test
	public void testGarbageHexFallsBackToSeed() {
		prefs.edit().putString("theme", "Custom")
				.putString(CustomThemeStore.KEY_BG, "not-a-color").commit();
		DefaultTheme custom = DefaultTheme.getTheme(
				InstrumentationRegistry.getInstrumentation().getTargetContext());
		assertEquals(new DefaultTheme().defaultBackground, custom.defaultBackground);
	}

	@Test
	public void testSaveAllBumpsRevision() {
		String before = CustomThemeStore.getRevision();
		CustomThemeStore.saveAll(CustomThemeStore.presetValues("Dark"), null);
		String after = CustomThemeStore.getRevision();
		assertEquals(false, after.equals(before));
		assertEquals(false, after.isEmpty());
	}

	@Test
	public void testPickerRoundTrip() throws Throwable {
		final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
		final HsvColorPickerView[] holder = new HsvColorPickerView[1];
		Runnable inflate = () -> {
			HsvColorPickerView v = new HsvColorPickerView(ctx);
			v.setColor(0xFF112233);
			holder[0] = v;
		};
		InstrumentationRegistry.getInstrumentation().runOnMainSync(inflate);
		assertEquals(0xFF112233, holder[0].getColor());
	}
}
