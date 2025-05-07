package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.ReaderPanel;
import com.lv.tool.privatereader.ui.ReaderToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 刷新章节内容操作
 * 忽略缓存，重新获取当前章节的最新内容
 */
public class RefreshChapterContentAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            ReaderPanel panel = ReaderToolWindowFactory.findPanel(project);
            if (panel != null) {
                panel.reloadCurrentChapter();
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
        if (project != null) {
            ReaderPanel panel = ReaderToolWindowFactory.findPanel(project);
            // Enable only if panel exists and a chapter is selected
            e.getPresentation().setEnabled(panel != null && panel.getSelectedChapter() != null);
        } else {
            e.getPresentation().setEnabled(false);
        }
    }
} 