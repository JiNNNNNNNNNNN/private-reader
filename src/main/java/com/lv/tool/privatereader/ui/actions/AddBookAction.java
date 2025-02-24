package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.dialog.AddBookDialog;
import org.jetbrains.annotations.NotNull;

public class AddBookAction extends BaseAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            AddBookDialog dialog = new AddBookDialog(project);
            dialog.show();
        }
    }
} 