package com.lv.tool.privatereader.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.settings.PluginSettingsListener;
import org.jetbrains.annotations.NotNull;

/**
 * 私人阅读器窗口工厂
 */
public class PrivateReaderWindowFactory implements ToolWindowFactory, Disposable {
    private PrivateReaderPanel readerPanel;
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 获取已经初始化的面板
        readerPanel = PrivateReaderPanel.getInstance(project);
        if (readerPanel == null) {
            // 如果还没有初始化，创建新的面板
            readerPanel = new PrivateReaderPanel(project);
        }
        
        Content content = ContentFactory.getInstance().createContent(readerPanel, "", false);
        content.setDisposer(this); // 设置资源释放器
        toolWindow.getContentManager().addContent(content);

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
    
    @Override
    public void dispose() {
        if (readerPanel != null) {
            readerPanel.dispose();
            readerPanel = null;
        }
    }
} 