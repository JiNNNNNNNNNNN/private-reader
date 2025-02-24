package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import com.lv.tool.privatereader.settings.NotificationReaderSettingsListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;

public class NotificationReaderConfigurable implements Configurable {
    private JPanel mainPanel;
    private JSpinner pageSizeSpinner;
    private JCheckBox showPageNumberCheckBox;
    private final NotificationReaderSettings settings;
    private boolean isModified = false;

    public NotificationReaderConfigurable() {
        this.settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "通知栏阅读设置";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 创建每页字数设置
        SpinnerNumberModel pageSizeModel = new SpinnerNumberModel(80, Integer.MIN_VALUE, Integer.MAX_VALUE, 10);
        pageSizeSpinner = new JSpinner(pageSizeModel);
        
        // 创建显示页码设置
        showPageNumberCheckBox = new JCheckBox("显示页码");
        
        // 加载当前设置
        pageSizeSpinner.setValue(settings.getPageSize());
        showPageNumberCheckBox.setSelected(settings.isShowPageNumber());
        
        // 添加即时预览功能
        pageSizeSpinner.addChangeListener(e -> {
            isModified = true;
            if (settings.getPageSize() != (Integer) pageSizeSpinner.getValue()) {
                settings.setPageSize((Integer) pageSizeSpinner.getValue());
                notifySettingsChanged();
            }
        });
        
        showPageNumberCheckBox.addItemListener(e -> {
            isModified = true;
            if (settings.isShowPageNumber() != showPageNumberCheckBox.isSelected()) {
                settings.setShowPageNumber(showPageNumberCheckBox.isSelected());
                notifySettingsChanged();
            }
        });
        
        // 构建设置面板
        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("每页字数: "), pageSizeSpinner)
                .addComponent(showPageNumberCheckBox)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        return mainPanel;
    }

    private void notifySettingsChanged() {
        ApplicationManager.getApplication()
            .getMessageBus()
            .syncPublisher(NotificationReaderSettingsListener.TOPIC)
            .settingsChanged();
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void apply() {
        settings.setPageSize((Integer) pageSizeSpinner.getValue());
        settings.setShowPageNumber(showPageNumberCheckBox.isSelected());
        isModified = false;
    }

    @Override
    public void reset() {
        pageSizeSpinner.setValue(settings.getPageSize());
        showPageNumberCheckBox.setSelected(settings.isShowPageNumber());
        isModified = false;
    }
} 