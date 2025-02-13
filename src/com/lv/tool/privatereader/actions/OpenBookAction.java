package com.lv.tool.privatereader.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.ui.BookShelfPanel;
import com.lv.tool.privatereader.ui.ReaderPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 打开书籍动作
 */
public class OpenBookAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        BookShelfPanel bookShelfPanel = BookShelfPanel.getInstance(project);
        if (bookShelfPanel == null) return;

        Book selectedBook = bookShelfPanel.getBookList().getSelectedValue();
        if (selectedBook == null) {
            Messages.showWarningDialog(project,
                "请先选择要打开的书籍",
                "提示");
            return;
        }

        try {
            // 创建阅读面板
            ReaderPanel readerPanel = new ReaderPanel(project, selectedBook);
            String title = selectedBook.getTitle();

            // 获取或创建工具窗口
            ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("BookReader");

            if (toolWindow == null) {
                Messages.showErrorDialog(project,
                    "无法创建阅读窗口",
                    "错误");
                return;
            }

            // 创建新的内容标签
            Content content = ContentFactory.getInstance()
                .createContent(readerPanel, title, false);
            content.setCloseable(true);

            // 添加到工具窗口
            toolWindow.getContentManager().addContent(content);
            toolWindow.getContentManager().setSelectedContent(content);
            toolWindow.show();

        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                "打开书籍失败: " + ex.getMessage(),
                "错误");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        BookShelfPanel bookShelfPanel = BookShelfPanel.getInstance(project);
        if (bookShelfPanel == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        Book selectedBook = bookShelfPanel.getBookList().getSelectedValue();
        e.getPresentation().setEnabled(selectedBook != null);
    }
} 