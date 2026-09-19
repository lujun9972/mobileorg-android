package com.matburt.mobileorg.Gui.Theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.matburt.mobileorg.OrgData.MobileOrgApplication;

/**
 * Storage for the Custom theme: base selector, exposed colors and a revision
 * stamp used to detect color-only changes (themeName stays "Custom").
 * Empty storage seeds from the Dark preset.
 */
public class CustomThemeStore {
	public static final String KEY_BASE = "custom_theme_base";
	public static final String KEY_REVISION = "custom_theme_revision";
	public static final String KEY_GRAY = "custom_theme_gray";
	public static final String KEY_C1 = "custom_theme_c1";
	public static final String KEY_CA = "custom_theme_ca";
	public static final String KEY_C3 = "custom_theme_c3";
	public static final String KEY_C4 = "custom_theme_c4";
	public static final String KEY_FG = "custom_theme_fg";
	public static final String KEY_BG = "custom_theme_bg";
	public static final String KEY_LEVEL_COLORS = "custom_theme_levelColors";

	public static final String BASE_DARK = "Dark";
	public static final String BASE_LIGHT = "Light";
	public static final String BASE_MONO = "Monochrome";

	private static SharedPreferences prefs() {
		Context ctx = MobileOrgApplication.getContext();
		return PreferenceManager.getDefaultSharedPreferences(ctx);
	}

	public static String getBase() {
		return prefs().getString(KEY_BASE, BASE_DARK);
	}

	public static String getRevision() {
		return prefs().getString(KEY_REVISION, "");
	}

	/** All custom-theme pref keys (for config export). */
	public static String[] prefKeys() {
		return new String[] { KEY_BASE, KEY_REVISION, KEY_GRAY, KEY_C1, KEY_CA,
				KEY_C3, KEY_C4, KEY_FG, KEY_BG, KEY_LEVEL_COLORS };
	}
}
