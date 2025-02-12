package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 书架窗口工厂
 */
public final class BookShelfWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BookShelfPanel bookShelfPanel = new BookShelfPanel(project);
        Content content = ContentFactory.getInstance()
            .createContent(bookShelfPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("私人书库");
        toolWindow.setStripeTitle("私人书库");
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
} 