package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.NotNull;

public class ToggleReadingModeAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                boolean isNotificationMode = panel.isNotificationMode();
                e.getPresentation().setText(isNotificationMode ? 
                    "切换到阅读器模式" : "切换到通知栏模式");
                e.getPresentation().setDescription(isNotificationMode ? 
                    "切换到阅读器模式显示内容" : "切换到通知栏模式显示内容");
            }
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                panel.toggleReadingMode();
            }
        }
    }
} 