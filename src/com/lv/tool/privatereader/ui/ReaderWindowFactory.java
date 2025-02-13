package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * 阅读器窗口工厂
 */
public class ReaderWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 这里不需要创建初始内容，内容会在打开书籍时动态添加
        JPanel emptyPanel = new JPanel();
        toolWindow.getContentManager().addContent(
            ContentFactory.getInstance().createContent(emptyPanel, "", false)
        );
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("阅读器");
        toolWindow.setStripeTitle("阅读器");
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
} 