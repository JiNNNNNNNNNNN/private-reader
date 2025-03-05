package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notification;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import org.jetbrains.annotations.NotNull;

public class ClearScreenAction extends AnAction {
    private static final int CLEAR_NOTIFICATIONS = 20;  // 生成的通知数量
    private static final String NOTIFICATION_GROUP_ID = "Private Reader";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            // 获取通知栏阅读设置
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                    .getService(NotificationReaderSettings.class);

            // 生成多条空白通知来"清屏"
            for (int i = 0; i < CLEAR_NOTIFICATIONS; i++) {
                Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification("", "　", NotificationType.INFORMATION)
                    .setImportant(false);

                notification.hideBalloon();
                notification.notify(project);
            }
        }
    }
} 