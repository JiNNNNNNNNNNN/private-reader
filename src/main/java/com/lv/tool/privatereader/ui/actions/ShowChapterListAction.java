package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import com.lv.tool.privatereader.ui.dialog.ChapterListDialog;
import com.lv.tool.privatereader.util.PluginUtil;
import org.jetbrains.annotations.NotNull;

public class ShowChapterListAction extends BaseAction {
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
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        // 告诉 IntelliJ 在后台线程而非 EDT 线程中执行 update 方法
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null && PluginUtil.isPluginEnabled()) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            e.getPresentation().setEnabled(panel != null && panel.getBookList() != null && panel.getBookList().getSelectedValue() != null);
        } else {
            e.getPresentation().setEnabled(false);
        }
    }
} 