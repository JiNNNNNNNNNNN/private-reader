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
    private JBCheckBox enablePreloadCheckBox;
    private JSpinner preloadCountSpinner;
    private JSpinner preloadDelaySpinner;
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
        
        // 预加载设置
        enablePreloadCheckBox = new JBCheckBox("启用章节预加载（后台自动缓存后续章节）");
        
        SpinnerNumberModel countModel = new SpinnerNumberModel(50, 1, 100, 5);
        preloadCountSpinner = new JSpinner(countModel);
        
        SpinnerNumberModel delayModel = new SpinnerNumberModel(1000, 500, 5000, 100);
        preloadDelaySpinner = new JSpinner(delayModel);
        
        // 加载当前设置
        enableCacheCheckBox.setSelected(settings.isEnableCache());
        maxCacheSizeSpinner.setValue(settings.getMaxCacheSize());
        maxCacheAgeSpinner.setValue(settings.getMaxCacheAge());
        enablePreloadCheckBox.setSelected(settings.isEnablePreload());
        preloadCountSpinner.setValue(settings.getPreloadCount());
        preloadDelaySpinner.setValue(settings.getPreloadDelay());
        
        // 构建设置面板
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("基本缓存设置"))
                .addComponent(enableCacheCheckBox)
                .addLabeledComponent(new JBLabel("最大缓存大小 (MB): "), maxCacheSizeSpinner)
                .addLabeledComponent(new JBLabel("缓存过期时间 (天): "), maxCacheAgeSpinner)
                .addSeparator()
                .addComponent(new JBLabel("章节预加载设置"))
                .addComponent(enablePreloadCheckBox)
                .addLabeledComponent(new JBLabel("预加载章节数量: "), preloadCountSpinner)
                .addLabeledComponent(new JBLabel("预加载请求间隔 (毫秒): "), preloadDelaySpinner)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        // 添加预加载选项的启用/禁用联动
        enablePreloadCheckBox.addChangeListener(e -> {
            boolean enabled = enablePreloadCheckBox.isSelected();
            preloadCountSpinner.setEnabled(enabled);
            preloadDelaySpinner.setEnabled(enabled);
        });
        
        // 初始状态
        preloadCountSpinner.setEnabled(enablePreloadCheckBox.isSelected());
        preloadDelaySpinner.setEnabled(enablePreloadCheckBox.isSelected());
        
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return settings.isEnableCache() != enableCacheCheckBox.isSelected() ||
               settings.getMaxCacheSize() != (Integer) maxCacheSizeSpinner.getValue() ||
               settings.getMaxCacheAge() != (Integer) maxCacheAgeSpinner.getValue() ||
               settings.isEnablePreload() != enablePreloadCheckBox.isSelected() ||
               settings.getPreloadCount() != (Integer) preloadCountSpinner.getValue() ||
               settings.getPreloadDelay() != (Integer) preloadDelaySpinner.getValue();
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.setEnableCache(enableCacheCheckBox.isSelected());
        settings.setMaxCacheSize((Integer) maxCacheSizeSpinner.getValue());
        settings.setMaxCacheAge((Integer) maxCacheAgeSpinner.getValue());
        settings.setEnablePreload(enablePreloadCheckBox.isSelected());
        settings.setPreloadCount((Integer) preloadCountSpinner.getValue());
        settings.setPreloadDelay((Integer) preloadDelaySpinner.getValue());
    }

    @Override
    public void reset() {
        enableCacheCheckBox.setSelected(settings.isEnableCache());
        maxCacheSizeSpinner.setValue(settings.getMaxCacheSize());
        maxCacheAgeSpinner.setValue(settings.getMaxCacheAge());
        enablePreloadCheckBox.setSelected(settings.isEnablePreload());
        preloadCountSpinner.setValue(settings.getPreloadCount());
        preloadDelaySpinner.setValue(settings.getPreloadDelay());
        
        // 更新启用状态
        preloadCountSpinner.setEnabled(enablePreloadCheckBox.isSelected());
        preloadDelaySpinner.setEnabled(enablePreloadCheckBox.isSelected());
    }
} 