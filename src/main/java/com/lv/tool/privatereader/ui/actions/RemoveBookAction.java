package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 移除书籍动作
 */
public class RemoveBookAction extends AnAction implements DumbAware {
    public RemoveBookAction() {
        super("删除书籍", "从书架中删除选中的书籍", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        // 获取阅读器面板
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("PrivateReader");
        if (toolWindow == null) {
            return;
        }
        
        // 确保工具窗口可见
        toolWindow.show(() -> {
            // 获取阅读器面板
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel == null) {
                return;
            }
            
            // 获取选中的书籍
            Book selectedBook = panel.getBookList().getSelectedValue();
            if (selectedBook == null) {
                Messages.showInfoMessage(project, "请先选择要删除的书籍", "提示");
                return;
            }
            
            // 确认删除
            int result = Messages.showYesNoDialog(
                    project,
                    "确定要删除书籍 \"" + selectedBook.getTitle() + "\" 吗？",
                    "删除确认",
                    Messages.getQuestionIcon()
            );
            
            if (result == Messages.YES) {
                // 删除书籍
                BookService bookService = project.getService(BookService.class);
                boolean success = bookService.removeBook(selectedBook);
                
                if (success) {
                    // 刷新面板
                    panel.refresh();
                } else {
                    Messages.showErrorDialog(project, "删除书籍失败", "错误");
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        PrivateReaderPanel readerPanel = PrivateReaderPanel.getInstance(project);
        if (readerPanel == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        Book selectedBook = readerPanel.getBookList().getSelectedValue();
        e.getPresentation().setEnabled(selectedBook != null);
    }
} 