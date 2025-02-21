package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.settings.ReaderSettings;
import com.lv.tool.privatereader.settings.ReaderSettingsListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ReaderConfigurable implements Configurable {
    private JPanel mainPanel;
    private ComboBox<String> fontFamilyComboBox;
    private ComboBox<Integer> fontSizeComboBox;
    private JCheckBox boldCheckBox;
    private ColorPanel colorPanel;
    private final ReaderSettings settings;

    public ReaderConfigurable() {
        this.settings = ApplicationManager.getApplication().getService(ReaderSettings.class);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Reader Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 获取系统所有字体
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontFamilies = ge.getAvailableFontFamilyNames();
        fontFamilyComboBox = new ComboBox<>(fontFamilies);
        
        // 字体大小选项
        Integer[] sizes = {12, 14, 16, 18, 20, 22, 24, 26, 28, 30};
        fontSizeComboBox = new ComboBox<>(sizes);
        
        boldCheckBox = new JCheckBox("粗体");
        
        // 创建颜色选择器
        colorPanel = new ColorPanel();
        
        // 加载当前设置
        fontFamilyComboBox.setSelectedItem(settings.getFontFamily());
        fontSizeComboBox.setSelectedItem(settings.getFontSize());
        boldCheckBox.setSelected(settings.isBold());
        colorPanel.setSelectedColor(settings.getTextColor());
        
        // 构建设置面板
        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("字体: "), fontFamilyComboBox)
                .addLabeledComponent(new JBLabel("大小: "), fontSizeComboBox)
                .addComponent(boldCheckBox)
                .addLabeledComponent(new JBLabel("颜色: "), colorPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return !settings.getFontFamily().equals(fontFamilyComboBox.getSelectedItem()) ||
               settings.getFontSize() != (Integer) fontSizeComboBox.getSelectedItem() ||
               settings.isBold() != boldCheckBox.isSelected() ||
               !settings.getTextColor().equals(colorPanel.getSelectedColor());
    }

    @Override
    public void apply() {
        settings.setFontFamily((String) fontFamilyComboBox.getSelectedItem());
        settings.setFontSize((Integer) fontSizeComboBox.getSelectedItem());
        settings.setBold(boldCheckBox.isSelected());
        settings.setTextColor(colorPanel.getSelectedColor());
        
        // 通知设置变更
        ApplicationManager.getApplication()
            .getMessageBus()
            .syncPublisher(ReaderSettingsListener.TOPIC)
            .settingsChanged();
    }

    @Override
    public void reset() {
        fontFamilyComboBox.setSelectedItem(settings.getFontFamily());
        fontSizeComboBox.setSelectedItem(settings.getFontSize());
        boldCheckBox.setSelected(settings.isBold());
        colorPanel.setSelectedColor(settings.getTextColor());
    }
} 