package com.matburt.mobileorg.Gui.Theme;

import android.graphics.Color;

public class CustomTheme extends DefaultTheme {

	public CustomTheme() {
		super();
		java.util.Map<String, String> v = CustomThemeStore.loadAll();
		gray              = parse(v, CustomThemeStore.KEY_GRAY, gray);
		c1Red             = parse(v, CustomThemeStore.KEY_C1, c1Red);
		caLGreen          = parse(v, CustomThemeStore.KEY_CA, caLGreen);
		c3Yellow          = parse(v, CustomThemeStore.KEY_C3, c3Yellow);
		c4Blue            = parse(v, CustomThemeStore.KEY_C4, c4Blue);
		defaultForeground = parse(v, CustomThemeStore.KEY_FG, defaultForeground);
		defaultBackground = parse(v, CustomThemeStore.KEY_BG, defaultBackground);
		levelColors       = parseLevels(v.get(CustomThemeStore.KEY_LEVEL_COLORS), levelColors);
		// defaultFontColor is derived from background lightness (not user-exposed)
		defaultFontColor  = isLightColor(defaultBackground) ? "black" : "white";
	}

	private static int parse(java.util.Map<String, String> v, String key, int fallback) {
		String s = v.get(key);
		if (s == null) return fallback;
		try { return Color.parseColor(s); }
		catch (IllegalArgumentException e) { return fallback; }
	}

	private static int[] parseLevels(String csv, int[] fallback) {
		if (csv == null || csv.isEmpty()) return fallback;
		try {
			String[] parts = csv.split(",");
			int[] out = new int[fallback.length];
			for (int i = 0; i < out.length; i++)
				out[i] = Color.parseColor(parts[i % parts.length].trim());
			return out;
		} catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
			return fallback;
		}
	}

	/** ITU-R 601 luma; > 0.5 means a light background needs dark text. */
	public static boolean isLightColor(int color) {
		int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
		return (0.299 * r + 0.587 * g + 0.114 * b) > 127.5;
	}
}
