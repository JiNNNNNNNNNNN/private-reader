package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
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
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                panel.reloadCurrentChapter();
            }
        }
    }
} 