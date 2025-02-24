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
    private static final int LIGHT_THEME_COLOR = -14013910; // RGB: 43, 43, 43
    private static final int DARK_THEME_COLOR = -4473925;   // RGB: 187, 187, 187
    private static final String EDITOR_FONT_KEY = "Label.font";
    private static final String EDITOR_COLOR_KEY = "Editor.foreground";
    private static final String DARK_THEME_KEY = "ui.is.dark";

    private String fontFamily = getSystemFontFamily();
    private int fontSize = getSystemFontSize();
    private boolean isBold = false;
    private Integer textColorRGB = null;

    private static String getSystemFontFamily() {
        Font font = UIManager.getFont(EDITOR_FONT_KEY);
        return font != null ? font.getFamily() : DEFAULT_FONT_FAMILY;
    }

    private static int getSystemFontSize() {
        Font font = UIManager.getFont(EDITOR_FONT_KEY);
        return font != null ? font.getSize() : DEFAULT_FONT_SIZE;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public int getFontSize() {
        return fontSize;
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

    public Integer getTextColorRGB() {
        if (textColorRGB != null) {
            return textColorRGB;
        }
        
        Color editorForeground = UIManager.getColor(EDITOR_COLOR_KEY);
        if (editorForeground != null) {
            return editorForeground.getRGB();
        }
        
        return UIManager.getBoolean(DARK_THEME_KEY) ? DARK_THEME_COLOR : LIGHT_THEME_COLOR;
    }

    public void setTextColorRGB(Integer colorRGB) {
        this.textColorRGB = colorRGB;
    }

    public Color getTextColor() {
        return new Color(getTextColorRGB());
    }

    public void setTextColor(Color color) {
        setTextColorRGB(color != null ? color.getRGB() : null);
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