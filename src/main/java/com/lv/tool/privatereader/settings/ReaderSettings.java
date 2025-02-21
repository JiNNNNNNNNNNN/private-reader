package com.lv.tool.privatereader.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

@Service(Service.Level.APP)
@State(
    name = "ReaderSettings",
    storages = @Storage("private-reader-font-settings.xml")
)
public final class ReaderSettings implements PersistentStateComponent<ReaderSettings> {
    private static final String DEFAULT_FONT_FAMILY = "Microsoft YaHei";
    private static final int DEFAULT_FONT_SIZE = 16;
    private static final Color LIGHT_THEME_COLOR = new Color(43, 43, 43);
    private static final Color DARK_THEME_COLOR = new Color(187, 187, 187);
    private static final String EDITOR_FONT_KEY = "Label.font";
    private static final String EDITOR_COLOR_KEY = "Editor.foreground";
    private static final String DARK_THEME_KEY = "ui.is.dark";

    private String fontFamily = getSystemFontFamily();
    private int fontSize = getSystemFontSize();
    private boolean isBold = false;
    private Integer textColorRGB = null; // 存储用户自定义的颜色

    private static String getSystemFontFamily() {
        Font font = UIManager.getFont(EDITOR_FONT_KEY);
        return font != null ? font.getFamily() : DEFAULT_FONT_FAMILY;
    }

    private static int getSystemFontSize() {
        Font font = UIManager.getFont(EDITOR_FONT_KEY);
        return font != null ? font.getSize() : DEFAULT_FONT_SIZE;
    }

    public String getFontFamily() {
        Font editorFont = UIManager.getFont(EDITOR_FONT_KEY);
        return editorFont != null ? editorFont.getFamily() : fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public int getFontSize() {
        Font editorFont = UIManager.getFont(EDITOR_FONT_KEY);
        return editorFont != null ? editorFont.getSize() : fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public boolean isBold() {
        return isBold;
    }

    public void setBold(boolean bold) {
        isBold = bold;
    }

    public Color getTextColor() {
        // 如果用户设置了自定义颜色，使用自定义颜色
        if (textColorRGB != null) {
            return new Color(textColorRGB);
        }
        
        // 否则使用 IDE 当前主题的颜色
        Color editorForeground = UIManager.getColor(EDITOR_COLOR_KEY);
        if (editorForeground != null) {
            return editorForeground;
        }
        
        // 如果获取不到编辑器颜色，根据当前主题返回默认颜色
        return UIManager.getBoolean(DARK_THEME_KEY) ? DARK_THEME_COLOR : LIGHT_THEME_COLOR;
    }

    public void setTextColor(Color color) {
        this.textColorRGB = color != null ? color.getRGB() : null;
    }

    @Override
    public @Nullable ReaderSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ReaderSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 