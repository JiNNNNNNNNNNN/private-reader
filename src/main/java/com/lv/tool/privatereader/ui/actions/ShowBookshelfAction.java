package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.dialog.BookshelfDialog;
import org.jetbrains.annotations.NotNull;

public class ShowBookshelfAction extends BaseAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            BookshelfDialog dialog = new BookshelfDialog(project);
            dialog.show();
        }
    }
} 