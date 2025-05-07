package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.lv.tool.privatereader.ui.ReaderPanel;
import com.lv.tool.privatereader.ui.ReaderToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 下一章操作
 * 用于跳转到当前书籍的下一章节
 */
public class NextChapterAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(NextChapterAction.class);
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            NotificationService notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
            ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);

            if (notificationService != null && modeSettings != null && modeSettings.isNotificationMode()) {
                LOG.info("[通知栏模式] 尝试导航到下一章");
                try {
                    // 使用同步方法，传递 project 参数和方向参数
                    notificationService.navigateChapter(project, 1);
                    LOG.info("[通知栏模式] 成功导航到下一章");
                } catch (Exception ex) {
                    LOG.error("[通知栏模式] 调用 navigateChapter 时发生异常: " + ex.getMessage(), ex);
                }
            } else {
                ReaderPanel panel = ReaderToolWindowFactory.findPanel(project);
                if (panel != null) {
                    panel.navigateChapter(1);
                }
            }
        }
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            NotificationService notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
            ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);

            if (notificationService != null && modeSettings != null && modeSettings.isNotificationMode()) {
                // 在通知栏模式下始终启用此操作
                e.getPresentation().setEnabled(true);
            } else {
                ReaderPanel panel = ReaderToolWindowFactory.findPanel(project);
                if (panel != null) {
                    boolean enabled = panel.getSelectedChapter() != null;
                    e.getPresentation().setEnabled(enabled);
                } else {
                    e.getPresentation().setEnabled(false);
                }
            }
        } else {
            e.getPresentation().setEnabled(false);
        }
    }
}