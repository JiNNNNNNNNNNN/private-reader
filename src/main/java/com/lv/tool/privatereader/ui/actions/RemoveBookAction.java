package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 移除书籍动作
 */
public class RemoveBookAction extends AnAction implements DumbAware {
    public RemoveBookAction() {
        super("移除书籍", "从书架移除选中的书籍", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PrivateReaderPanel readerPanel = PrivateReaderPanel.getInstance(project);
        if (readerPanel == null) {
            Messages.showWarningDialog(project,
                "阅读器窗口未初始化",
                "提示");
            return;
        }

        Book selectedBook = readerPanel.getBookList().getSelectedValue();
        if (selectedBook == null) {
            Messages.showWarningDialog(project,
                "请先选择要移除的书籍",
                "提示");
            return;
        }

        int result = Messages.showYesNoDialog(project,
            String.format("确定要移除《%s》吗？", selectedBook.getTitle()),
            "移除书籍",
            Messages.getQuestionIcon());

        if (result != Messages.YES) return;

        BookStorage bookStorage = project.getService(BookStorage.class);
        bookStorage.removeBook(selectedBook);
        readerPanel.refresh();

        Messages.showInfoMessage(project,
            String.format("已移除《%s》", selectedBook.getTitle()),
            "移除成功");
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