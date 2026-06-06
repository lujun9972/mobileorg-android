package com.matburt.mobileorg.Gui.Statistics;

import com.matburt.mobileorg.util.PreferenceUtils;

public class ChartThemeConfig {
    public final int barColor;
    public final int lineColor;
    public final int textColor;
    public final int gridColor;
    public final int backgroundColor;
    public final int streakCardColor;

    private static final ChartThemeConfig LIGHT = new ChartThemeConfig(
        0xFF4CAF50, 0xFF2196F3, 0xFF212121, 0xFFE0E0E0, 0xFFFFFFFF, 0xFFFFF3E0);
    private static final ChartThemeConfig DARK = new ChartThemeConfig(
        0xFF81C784, 0xFF64B5F6, 0xFFFFFFFF, 0xFF424242, 0xFF1E1E1E, 0xFF3E2723);
    private static final ChartThemeConfig MONO = new ChartThemeConfig(
        0xFF795548, 0xFF607D8B, 0xFF3E2723, 0xFFD7CCC8, 0xFFF5F0E8, 0xFFEFEBE9);

    private ChartThemeConfig(int bar, int line, int text, int grid, int bg, int streak) {
        this.barColor = bar;
        this.lineColor = line;
        this.textColor = text;
        this.gridColor = grid;
        this.backgroundColor = bg;
        this.streakCardColor = streak;
    }

    public static ChartThemeConfig current() {
        String theme = PreferenceUtils.getThemeName();
        if ("Light".equals(theme)) return LIGHT;
        if ("Monochrome".equals(theme)) return MONO;
        return DARK;
    }
}
