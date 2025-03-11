package com.lv.tool.privatereader.exception;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/**
 * 异常处理器
 * 统一处理插件中的异常
 */
public class ExceptionHandler {
    private static final Logger LOG = Logger.getInstance(ExceptionHandler.class);
    private static final String NOTIFICATION_GROUP_ID = "Private Reader";
    
    /**
     * 处理异常
     * 
     * @param project 项目
     * @param exception 异常
     */
    public static void handle(Project project, PrivateReaderException exception) {
        // 记录日志
        LOG.error(exception);
        
        // 根据异常类型处理
        String title = getErrorTitle(exception.getType());
        String content = exception.getMessage();
        
        // 显示通知
        showErrorNotification(project, title, content);
    }
    
    /**
     * 处理异常
     * 
     * @param project 项目
     * @param exception 异常
     * @param message 自定义错误消息
     */
    public static void handle(Project project, Throwable exception, String message) {
        // 记录日志
        LOG.error(message, exception);
        
        try {
            // 显示通知
            showErrorNotification(project, "错误", message);
        } catch (Exception e) {
            // 如果通知显示失败，只记录日志
            LOG.error("无法显示错误通知", e);
        }
    }
    
    /**
     * 显示错误通知
     * 
     * @param project 项目
     * @param title 标题
     * @param content 内容
     */
    private static void showErrorNotification(Project project, String title, String content) {
        try {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(content, NotificationType.ERROR)
                    .setTitle(title);
            
            notification.notify(project);
        } catch (Exception e) {
            // 如果通知显示失败，只记录日志
            LOG.error("无法显示错误通知", e);
        }
    }
    
    /**
     * 根据异常类型获取错误标题
     * 
     * @param type 异常类型
     * @return 错误标题
     */
    private static String getErrorTitle(PrivateReaderException.ExceptionType type) {
        switch (type) {
            case NETWORK_ERROR:
                return "网络错误";
            case PARSE_ERROR:
                return "解析错误";
            case STORAGE_ERROR:
                return "存储错误";
            case CONFIG_ERROR:
                return "配置错误";
            case UNKNOWN_ERROR:
            default:
                return "未知错误";
        }
    }
} 