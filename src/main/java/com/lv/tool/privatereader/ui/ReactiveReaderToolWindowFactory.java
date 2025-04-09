package com.lv.tool.privatereader.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 阅读器工具窗口工厂
 * 用于注册和显示阅读器面板
 */
public class ReactiveReaderToolWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(ReactiveReaderToolWindowFactory.class);
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LOG.info("创建阅读器工具窗口");
        
        // 创建响应式阅读器面板
        ReactiveReaderPanel readerPanel = new ReactiveReaderPanel(project);
        
        // 创建内容
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(readerPanel, "阅读器", false);
        content.setDisposer(readerPanel);
        
        // 添加内容到工具窗口
        toolWindow.getContentManager().addContent(content);
    }
    
    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Private Reader");
        toolWindow.setStripeTitle("Private Reader");
    }
    
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
} 