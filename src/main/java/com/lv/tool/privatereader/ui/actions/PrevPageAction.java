package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import org.jetbrains.annotations.NotNull;

public class PrevPageAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(PrevPageAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            NotificationService notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
            ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);

            if (notificationService != null && modeSettings != null && modeSettings.isNotificationMode()) {
                LOG.info("[通知栏模式] 尝试显示上一页，当前页码: " + notificationService.getCurrentPage() + ", 总页数: " + notificationService.getTotalPages());
                try {
                    // 使用同步方法，传递 project 参数
                    notificationService.showPrevPage(project);
                    LOG.info("[通知栏模式] 成功显示上一页");
                } catch (Exception ex) {
                    LOG.error("[通知栏模式] 调用 showPrevPage 时发生异常: " + ex.getMessage(), ex);
                }
            } else {
                LOG.warn("Prev Page action is not applicable to ReactiveReaderPanel (uses scrolling). Action performed but had no effect.");
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
        ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);

        // 只在通知栏模式下启用此操作
        e.getPresentation().setEnabled(project != null && modeSettings != null && modeSettings.isNotificationMode());
    }
}