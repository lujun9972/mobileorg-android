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

	public static java.util.Map<String, String> loadAll() {
		SharedPreferences p = prefs();
		java.util.Map<String, String> out = new java.util.HashMap<>();
		for (String k : prefKeys())
			out.put(k, p.getString(k, null));
		return out;
	}

	/** Persist values and bump the revision. editor=null → apply here. */
	public static void saveAll(java.util.Map<String, String> values,
			SharedPreferences.Editor editor) {
		SharedPreferences p = prefs();
		boolean own = (editor == null);
		if (own) editor = p.edit();
		if (values != null)
			for (java.util.Map.Entry<String, String> e : values.entrySet())
				if (e.getValue() != null) editor.putString(e.getKey(), e.getValue());
		editor.putString(KEY_REVISION, String.valueOf(System.currentTimeMillis()));
		if (own) editor.apply();
	}

	/** The exposed values of a built-in preset, levelColors modulo-filled to 10. */
	public static java.util.Map<String, String> presetValues(String presetName) {
		DefaultTheme t;
		if (BASE_LIGHT.equals(presetName))      t = new WhiteTheme();
		else if (BASE_MONO.equals(presetName))  t = new MonoTheme();
		else                                    t = new DefaultTheme();

		java.util.Map<String, String> m = new java.util.HashMap<>();
		m.put(KEY_GRAY, hex(t.gray));
		m.put(KEY_C1,   hex(t.c1Red));
		m.put(KEY_CA,   hex(t.caLGreen));
		m.put(KEY_C3,   hex(t.c3Yellow));
		m.put(KEY_C4,   hex(t.c4Blue));
		m.put(KEY_FG,   hex(t.defaultForeground));
		m.put(KEY_BG,   hex(t.defaultBackground));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10; i++) {
			if (i > 0) sb.append(',');
			sb.append(hex(t.levelColors[i % t.levelColors.length]));
		}
		m.put(KEY_LEVEL_COLORS, sb.toString());
		return m;
	}

	private static String hex(int color) {
		return String.format("#%06X", 0xFFFFFF & color);
	}
}
