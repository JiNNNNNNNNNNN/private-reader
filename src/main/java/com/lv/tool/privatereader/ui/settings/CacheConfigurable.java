package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 缓存设置界面
 */
public class CacheConfigurable implements Configurable {
    private final Project project;
    private JBCheckBox enableCacheCheckBox;
    private JSpinner maxCacheSizeSpinner;
    private ComboBox<String> cacheExpirationCombo;
    private boolean isModified = false;

    public CacheConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Private Reader Cache";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enableCacheCheckBox = new JBCheckBox("启用缓存");
        enableCacheCheckBox.addActionListener(e -> isModified = true);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(100, 10, 1000, 10);
        maxCacheSizeSpinner = new JSpinner(spinnerModel);
        maxCacheSizeSpinner.addChangeListener(e -> isModified = true);

        String[] expirationOptions = {"1天", "3天", "7天", "15天", "30天"};
        cacheExpirationCombo = new ComboBox<>(expirationOptions);
        cacheExpirationCombo.addActionListener(e -> isModified = true);

        JPanel panel = FormBuilder.createFormBuilder()
            .addComponent(enableCacheCheckBox)
            .addLabeledComponent(new JBLabel("最大缓存大小 (MB):"), maxCacheSizeSpinner)
            .addLabeledComponent(new JBLabel("缓存过期时间:"), cacheExpirationCombo)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();

        panel.setPreferredSize(new Dimension(300, 150));
        return panel;
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void apply() throws ConfigurationException {
        CacheSettings settings = project.getService(CacheSettings.class);
        settings.setEnableCache(enableCacheCheckBox.isSelected());
        settings.setMaxCacheSize((Integer) maxCacheSizeSpinner.getValue());
        settings.setCacheExpiration(getExpirationDays());
        isModified = false;
    }

    @Override
    public void reset() {
        CacheSettings settings = project.getService(CacheSettings.class);
        enableCacheCheckBox.setSelected(settings.isEnableCache());
        maxCacheSizeSpinner.setValue(settings.getMaxCacheSize());
        setExpirationComboBox(settings.getCacheExpiration());
        isModified = false;
    }

    private int getExpirationDays() {
        String selected = (String) cacheExpirationCombo.getSelectedItem();
        if (selected == null) return 7;
        return Integer.parseInt(selected.replaceAll("\\D+", ""));
    }

    private void setExpirationComboBox(int days) {
        String target = days + "天";
        for (int i = 0; i < cacheExpirationCombo.getItemCount(); i++) {
            if (cacheExpirationCombo.getItemAt(i).equals(target)) {
                cacheExpirationCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    @Override
    public void disposeUIResources() {
        enableCacheCheckBox = null;
        maxCacheSizeSpinner = null;
        cacheExpirationCombo = null;
    }
} 