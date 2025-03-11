package com.lv.tool.privatereader.service;

import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;

/**
 * 通知服务接口
 * 处理通知相关的业务逻辑
 */
public interface NotificationService {
    /**
     * 显示章节内容通知
     * @param project 项目对象
     * @param title 通知标题
     * @param content 通知内容
     * @return 创建的通知对象
     */
    Notification showChapterNotification(Project project, String title, String content);
    
    /**
     * 关闭当前通知
     */
    void closeCurrentNotification();
    
    /**
     * 关闭所有通知
     */
    void closeAllNotifications();
    
    /**
     * 显示下一页内容
     * @return 是否成功显示下一页
     */
    boolean showNextPage();
    
    /**
     * 显示上一页内容
     * @return 是否成功显示上一页
     */
    boolean showPreviousPage();
    
    /**
     * 获取当前页码
     * @return 当前页码
     */
    int getCurrentPage();
    
    /**
     * 获取总页数
     * @return 总页数
     */
    int getTotalPages();
    
    /**
     * 跳转到指定页
     * @param pageNumber 页码
     * @return 是否成功跳转
     */
    boolean jumpToPage(int pageNumber);
} 