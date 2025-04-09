package com.lv.tool.privatereader.settings;

import java.awt.Color;

/**
 * 主题定义类
 * 包含主题的所有颜色和样式定义
 */
public class Theme {
    private final String name;
    private final Color backgroundColor;
    private final Color textColor;
    private final Color accentColor;
    private final boolean isDark;

    private Theme(Builder builder) {
        this.name = builder.name;
        this.backgroundColor = builder.backgroundColor;
        this.textColor = builder.textColor;
        this.accentColor = builder.accentColor;
        this.isDark = builder.isDark;
    }

    public String getName() {
        return name;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public Color getTextColor() {
        return textColor;
    }

    public Color getAccentColor() {
        return accentColor;
    }

    public boolean isDark() {
        return isDark;
    }

    public static class Builder {
        private String name = "Custom Theme";
        private Color backgroundColor = Color.WHITE;
        private Color textColor = Color.BLACK;
        private Color accentColor = new Color(0, 120, 215);
        private boolean isDark = false;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setBackgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder setTextColor(Color textColor) {
            this.textColor = textColor;
            return this;
        }

        public Builder setAccentColor(Color accentColor) {
            this.accentColor = accentColor;
            return this;
        }

        public Builder setDark(boolean dark) {
            isDark = dark;
            return this;
        }

        public Theme build() {
            return new Theme(this);
        }
    }

    // 预定义主题
    public static Theme lightTheme() {
        return new Builder()
            .setName("Light Theme")
            .setBackgroundColor(Color.WHITE)
            .setTextColor(new Color(43, 43, 43))
            .setAccentColor(new Color(0, 120, 215))
            .setDark(false)
            .build();
    }

    public static Theme darkTheme() {
        return new Builder()
            .setName("Dark Theme")
            .setBackgroundColor(new Color(30, 30, 30))
            .setTextColor(new Color(187, 187, 187))
            .setAccentColor(new Color(0, 120, 215))
            .setDark(true)
            .build();
    }
} 