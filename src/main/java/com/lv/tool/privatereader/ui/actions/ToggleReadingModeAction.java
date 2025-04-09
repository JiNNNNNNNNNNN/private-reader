package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 切换阅读模式动作
 * 在通知栏模式和阅读器模式之间切换
 */
public class ToggleReadingModeAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            ReaderModeSettings settings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
            if (settings != null) {
                boolean isNotificationMode = settings.isNotificationMode();
                e.getPresentation().setText(isNotificationMode ? 
                    "切换到阅读器模式" : "切换到通知栏模式");
                e.getPresentation().setDescription(isNotificationMode ? 
                    "切换到阅读器模式显示内容" : "切换到通知栏模式显示内容");
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