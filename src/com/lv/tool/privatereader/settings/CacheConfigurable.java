package com.lv.tool.privatereader.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.cache.ChapterCacheManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 缓存配置页面
 */
public final class CacheConfigurable implements Configurable {
    private final Project project;
    private JPanel mainPanel;
    private JBTextField maxCacheSizeField;
    private boolean isModified = false;

    public CacheConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "私人书库缓存设置";
    }

    @Override
    public @Nullable JComponent createComponent() {
        maxCacheSizeField = new JBTextField();
        maxCacheSizeField.setColumns(10);
        maxCacheSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { isModified = true; }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { isModified = true; }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { isModified = true; }
        });

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("最大缓存大小 (MB): "), maxCacheSizeField)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void apply() throws ConfigurationException {
        try {
            int maxSize = Integer.parseInt(maxCacheSizeField.getText().trim());
            if (maxSize <= 0) {
                throw new NumberFormatException("缓存大小必须大于0");
            }
            // 保存设置
            CacheSettings.getInstance(project).setMaxCacheSize(maxSize);
            // 通知缓存管理器
            project.getService(ChapterCacheManager.class).checkAndEvictCache();
            isModified = false;
        } catch (NumberFormatException e) {
            Messages.showErrorDialog("请输入有效的数字", "输入错误");
            throw new ConfigurationException("无效的缓存大小设置");
        }
    }

    @Override
    public void reset() {
        maxCacheSizeField.setText(String.valueOf(CacheSettings.getInstance(project).getMaxCacheSize()));
        isModified = false;
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        maxCacheSizeField = null;
    }
} 