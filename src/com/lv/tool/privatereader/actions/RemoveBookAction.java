package com.lv.tool.privatereader.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.persistence.BookStorage;
import com.lv.tool.privatereader.ui.BookShelfPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 移除书籍动作
 */
public class RemoveBookAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        BookShelfPanel bookShelfPanel = BookShelfPanel.getInstance(project);
        if (bookShelfPanel == null) return;

        Book selectedBook = bookShelfPanel.getBookList().getSelectedValue();
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
        bookShelfPanel.refresh();

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

        BookShelfPanel bookShelfPanel = BookShelfPanel.getInstance(project);
        if (bookShelfPanel == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        Book selectedBook = bookShelfPanel.getBookList().getSelectedValue();
        e.getPresentation().setEnabled(selectedBook != null);
    }
} 