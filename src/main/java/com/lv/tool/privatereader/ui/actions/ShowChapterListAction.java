package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import com.lv.tool.privatereader.ui.dialog.ChapterListDialog;
import org.jetbrains.annotations.NotNull;

public class ShowChapterListAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                Book selectedBook = panel.getBookList().getSelectedValue();
                if (selectedBook != null) {
                    ChapterListDialog dialog = new ChapterListDialog(project, selectedBook);
                    dialog.show();
                }
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            e.getPresentation().setEnabled(panel != null && panel.getBookList().getSelectedValue() != null);
        } else {
            e.getPresentation().setEnabled(false);
        }
    }
} 