package com.lv.tool.privatereader.settings;

/**
 * 主题预设类
 * 管理成对的亮色/暗色主题
 */
public class ThemePreset {
    private final String name;
    private final Theme lightTheme;
    private final Theme darkTheme;

    public ThemePreset(String name, Theme lightTheme, Theme darkTheme) {
        this.name = name;
        this.lightTheme = lightTheme;
        this.darkTheme = darkTheme;
    }

    public String getName() {
        return name;
    }

    public Theme getLightTheme() {
        return lightTheme;
    }

    public Theme getDarkTheme() {
        return darkTheme;
    }

    public Theme getTheme(boolean isDark) {
        return isDark ? darkTheme : lightTheme;
    }

    // 默认主题预设
    public static ThemePreset defaultPreset() {
        return new ThemePreset(
            "Default",
            Theme.lightTheme(),
            Theme.darkTheme()
        );
    }
} 