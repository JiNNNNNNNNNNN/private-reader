package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.settings.PluginSettingsListener;
import org.jetbrains.annotations.NotNull;

/**
 * 私人阅读器窗口工厂
 */
public class PrivateReaderWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        PrivateReaderPanel readerPanel = new PrivateReaderPanel(project);
        toolWindow.getContentManager().addContent(
            ContentFactory.getInstance().createContent(readerPanel, "", false)
        );

        // 监听插件设置变更
        ApplicationManager.getApplication()
            .getMessageBus()
            .connect()
            .subscribe(PluginSettingsListener.TOPIC, () -> {
                boolean shouldBeAvailable = shouldBeAvailable(project);
                toolWindow.setAvailable(shouldBeAvailable);
                if (shouldBeAvailable) {
                    readerPanel.refresh();
                }
            });
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("私人阅读器");
        toolWindow.setStripeTitle("私人阅读器");
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        PluginSettings settings = ApplicationManager.getApplication().getService(PluginSettings.class);
        return settings != null && settings.isEnabled();
    }
} 