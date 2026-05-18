package com.matburt.mobileorg.Gui.Theme;

import android.graphics.Color;

/**
 * Warm Paper monochrome theme — soft, eye-friendly tones inspired by
 * e-ink readers and aged parchment. All colors stay in the warm gray/brown
 * family so the palette remains monochrome while preserving visual hierarchy.
 */
public class MonoTheme extends DefaultTheme {

	public MonoTheme() {
		super();

		// Base palette: warm grays with brown undertones
		// Dark end (for text emphasis)
		c0Black    = Color.rgb(0x2C, 0x28, 0x24);  // deep warm charcoal
		c1Red      = Color.rgb(0x4A, 0x3F, 0x35);  // dark warm brown (active TODO)
		c2Green    = Color.rgb(0x6B, 0x5B, 0x4E);  // medium brown
		c3Yellow   = Color.rgb(0x5C, 0x4F, 0x43);  // warm brown (priority)
		c4Blue     = Color.rgb(0x7A, 0x6E, 0x62);  // warm gray (links)
		c5Purple   = Color.rgb(0x8B, 0x7E, 0x72);  // medium warm gray
		c6Cyan     = Color.rgb(0x6B, 0x5B, 0x4E);  // medium brown
		c7White    = Color.rgb(0x3D, 0x38, 0x32);  // warm charcoal (normal text)

		// Light end (for subdued elements)
		c9LRed     = Color.rgb(0x9E, 0x94, 0x88);  // warm light gray
		caLGreen   = Color.rgb(0xB0, 0xA6, 0x9A);  // light warm gray (inactive TODO)
		cbLYellow  = Color.rgb(0x8B, 0x80, 0x74);  // warm gray
		ccLBlue    = Color.rgb(0x7A, 0x6E, 0x62);  // warm gray
		cdLPurple  = Color.rgb(0x9E, 0x94, 0x88);  // warm light gray
		ceLCyan    = Color.rgb(0x8B, 0x80, 0x74);  // warm gray
		cfLWhite   = Color.rgb(0x3D, 0x38, 0x32);  // warm charcoal

		// Level indentation: alternating warm tones for subtle hierarchy
		levelColors = new int[] {
			Color.rgb(0x3D, 0x38, 0x32),  // warm charcoal
			Color.rgb(0x5C, 0x4F, 0x43),  // warm brown
			Color.rgb(0x7A, 0x6E, 0x62),  // warm gray
			Color.rgb(0x5C, 0x4F, 0x43),  // warm brown
			Color.rgb(0x8B, 0x80, 0x74),  // medium warm gray
		};

		gray = Color.rgb(0x9E, 0x94, 0x88);  // tags, COMMENT, Archive

		defaultFontColor = "black";
		defaultBackground = Color.rgb(0xF5, 0xF0, 0xE8);  // warm parchment
		defaultForeground = Color.rgb(0x3D, 0x38, 0x32);  // warm charcoal
	}
}
