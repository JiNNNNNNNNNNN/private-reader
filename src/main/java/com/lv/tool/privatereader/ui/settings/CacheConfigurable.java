package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 缓存设置界面
 */
public class CacheConfigurable implements Configurable {
    private JPanel mainPanel;
    private JBCheckBox enableCacheCheckBox;
    private JSpinner maxCacheSizeSpinner;
    private JSpinner maxCacheAgeSpinner;
    private final CacheSettings settings;

    public CacheConfigurable() {
        settings = new CacheSettings();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Private Reader Cache Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enableCacheCheckBox = new JBCheckBox("启用缓存");
        
        SpinnerNumberModel sizeModel = new SpinnerNumberModel(100, 10, 1000, 10);
        maxCacheSizeSpinner = new JSpinner(sizeModel);
        
        SpinnerNumberModel ageModel = new SpinnerNumberModel(7, 1, 30, 1);
        maxCacheAgeSpinner = new JSpinner(ageModel);
        
        // 加载当前设置
        enableCacheCheckBox.setSelected(settings.isEnableCache());
        maxCacheSizeSpinner.setValue(settings.getMaxCacheSize());
        maxCacheAgeSpinner.setValue(settings.getMaxCacheAge());
        
        // 构建设置面板
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(enableCacheCheckBox)
                .addLabeledComponent(new JBLabel("最大缓存大小 (MB): "), maxCacheSizeSpinner)
                .addLabeledComponent(new JBLabel("缓存过期时间 (天): "), maxCacheAgeSpinner)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return settings.isEnableCache() != enableCacheCheckBox.isSelected() ||
               settings.getMaxCacheSize() != (Integer) maxCacheSizeSpinner.getValue() ||
               settings.getMaxCacheAge() != (Integer) maxCacheAgeSpinner.getValue();
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.setEnableCache(enableCacheCheckBox.isSelected());
        settings.setMaxCacheSize((Integer) maxCacheSizeSpinner.getValue());
        settings.setMaxCacheAge((Integer) maxCacheAgeSpinner.getValue());
    }

    @Override
    public void reset() {
        enableCacheCheckBox.setSelected(settings.isEnableCache());
        maxCacheSizeSpinner.setValue(settings.getMaxCacheSize());
        maxCacheAgeSpinner.setValue(settings.getMaxCacheAge());
    }
} 