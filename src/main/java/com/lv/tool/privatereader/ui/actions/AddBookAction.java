package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.lv.tool.privatereader.ui.dialog.AddBookDialog;
import org.jetbrains.annotations.NotNull;

/**
 * 添加书籍动作
 */
public class AddBookAction extends AnAction implements DumbAware {
    public AddBookAction() {
        super("添加书籍", "添加新书籍到书架", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        AddBookDialog dialog = new AddBookDialog(project);
        dialog.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只要有项目打开就可以添加书籍
        e.getPresentation().setEnabled(e.getProject() != null);
    }
} 