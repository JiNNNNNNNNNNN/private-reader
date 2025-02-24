package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.settings.PluginSettingsListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PluginConfigurable implements Configurable {
    private JPanel mainPanel;
    private JBCheckBox enabledCheckBox;
    private final PluginSettings settings;

    public PluginConfigurable() {
        this.settings = ApplicationManager.getApplication().getService(PluginSettings.class);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Private Reader Plugin Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enabledCheckBox = new JBCheckBox("启用插件");
        enabledCheckBox.setSelected(settings.isEnabled());
        
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(enabledCheckBox)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return settings.isEnabled() != enabledCheckBox.isSelected();
    }

    @Override
    public void apply() {
        boolean wasEnabled = settings.isEnabled();
        settings.setEnabled(enabledCheckBox.isSelected());
        
        // 如果启用状态发生变化，发布事件
        if (wasEnabled != settings.isEnabled()) {
            ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(PluginSettingsListener.TOPIC)
                .settingsChanged();
        }
    }

    @Override
    public void reset() {
        enabledCheckBox.setSelected(settings.isEnabled());
    }
} 