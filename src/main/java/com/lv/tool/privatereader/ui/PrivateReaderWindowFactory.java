package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
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
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("私人阅读器");
        toolWindow.setStripeTitle("私人阅读器");
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
} 