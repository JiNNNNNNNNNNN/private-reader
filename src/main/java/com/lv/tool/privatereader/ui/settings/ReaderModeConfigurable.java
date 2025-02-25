package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.lv.tool.privatereader.settings.ReaderModeSettingsListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 阅读模式设置界面
 */
public class ReaderModeConfigurable implements Configurable {
    private JPanel mainPanel;
    private JBCheckBox notificationModeCheckBox;
    private final ReaderModeSettings settings;

    public ReaderModeConfigurable() {
        settings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "阅读模式设置";
    }

    @Override
    public @Nullable JComponent createComponent() {
        notificationModeCheckBox = new JBCheckBox("使用通知栏模式阅读");
        
        // 加载当前设置
        notificationModeCheckBox.setSelected(settings.isNotificationMode());
        
        // 构建设置面板
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("阅读模式选择"))
                .addComponent(notificationModeCheckBox)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return settings.isNotificationMode() != notificationModeCheckBox.isSelected();
    }

    @Override
    public void apply() throws ConfigurationException {
        boolean oldValue = settings.isNotificationMode();
        boolean newValue = notificationModeCheckBox.isSelected();
        
        if (oldValue != newValue) {
            settings.setNotificationMode(newValue);
            
            // 通知设置变更
            ApplicationManager.getApplication()
                    .getMessageBus()
                    .syncPublisher(ReaderModeSettings.TOPIC)
                    .readerModeSettingsChanged();
        }
    }

    @Override
    public void reset() {
        notificationModeCheckBox.setSelected(settings.isNotificationMode());
    }
} 