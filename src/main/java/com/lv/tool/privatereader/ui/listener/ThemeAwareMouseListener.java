package com.lv.tool.privatereader.ui.listener;

import com.lv.tool.privatereader.settings.Theme;
import javax.swing.JButton;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Color;

/**
 * 主题感知的鼠标监听器
 * 根据当前主题提供合适的鼠标悬停效果
 */
public class ThemeAwareMouseListener extends MouseAdapter {
    private final Theme theme;
    private static final float HOVER_BRIGHTNESS_FACTOR = 0.1f;

    public ThemeAwareMouseListener(Theme theme) {
        this.theme = theme;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (e.getComponent() instanceof JButton) {
            JButton button = (JButton) e.getComponent();
            if (button.isEnabled()) {
                button.setContentAreaFilled(true);
                button.setBackground(adjustBrightness(theme.getBackgroundColor(), 
                    theme.isDark() ? HOVER_BRIGHTNESS_FACTOR : -HOVER_BRIGHTNESS_FACTOR));
            }
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (e.getComponent() instanceof JButton) {
            JButton button = (JButton) e.getComponent();
            button.setContentAreaFilled(false);
            button.setBackground(theme.getBackgroundColor());
        }
    }

    private Color adjustBrightness(Color color, float factor) {
        float[] hsb = Color.RGBtoHSB(
            color.getRed(), 
            color.getGreen(), 
            color.getBlue(), 
            null
        );
        
        // 调整亮度，确保在0-1范围内
        hsb[2] = Math.max(0f, Math.min(1f, hsb[2] + factor));
        
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }
} 