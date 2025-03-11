package com.lv.tool.privatereader.service.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * NotificationService接口的实现类
 */
public final class NotificationServiceImpl implements NotificationService {
    private static final Logger LOG = Logger.getInstance(NotificationServiceImpl.class);
    private static final String NOTIFICATION_GROUP_ID = "Private Reader";
    private static final int MAX_NOTIFICATIONS = 5;
    
    private final List<Notification> activeNotifications = new ArrayList<>();
    private Notification currentNotification;
    private String currentContent;
    private int currentPage = 1;
    private int totalPages = 1;

    public NotificationServiceImpl(Project project) {
        LOG.info("初始化NotificationServiceImpl");
    }

    @Override
    public Notification showChapterNotification(Project project, String title, String content) {
        // 关闭当前通知
        closeCurrentNotification();
        
        try {
            // 分页处理
            NotificationReaderSettings settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
            int maxLength = settings != null ? settings.getPageSize() : 70;
            
            if (content.length() > maxLength) {
                // 需要分页
                totalPages = (int) Math.ceil((double) content.length() / maxLength);
                currentContent = content;
                currentPage = 1;
                
                // 获取第一页内容
                String pageContent = getPageContent(currentPage, maxLength);
                String pageInfo = String.format(" [%d/%d]", currentPage, totalPages);
                
                // 创建通知
                currentNotification = createNotification(title + pageInfo, pageContent);
            } else {
                // 不需要分页
                totalPages = 1;
                currentPage = 1;
                currentContent = content;
                
                // 创建通知
                currentNotification = createNotification(title, content);
            }
            
            // 显示通知
            currentNotification.notify(project);
            
            // 管理活动通知列表
            activeNotifications.add(currentNotification);
            if (activeNotifications.size() > MAX_NOTIFICATIONS) {
                Notification oldNotification = activeNotifications.remove(0);
                oldNotification.expire();
            }
            
            return currentNotification;
        } catch (Exception e) {
            LOG.error("显示通知失败", e);
            return null;
        }
    }

    @Override
    public void closeCurrentNotification() {
        if (currentNotification != null && !currentNotification.isExpired()) {
            currentNotification.expire();
            currentNotification = null;
        }
    }

    @Override
    public void closeAllNotifications() {
        for (Notification notification : activeNotifications) {
            if (!notification.isExpired()) {
                notification.expire();
            }
        }
        activeNotifications.clear();
        currentNotification = null;
    }

    @Override
    public boolean showNextPage() {
        if (currentContent == null || currentPage >= totalPages) {
            return false;
        }
        
        try {
            currentPage++;
            
            // 获取设置
            NotificationReaderSettings settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
            int maxLength = settings != null ? settings.getPageSize() : 70;
            
            String pageContent = getPageContent(currentPage, maxLength);
            String pageInfo = String.format(" [%d/%d]", currentPage, totalPages);
            
            // 关闭当前通知并创建新通知
            closeCurrentNotification();
            currentNotification = createNotification(pageInfo, pageContent);
            currentNotification.notify(null);
            
            // 管理活动通知列表
            activeNotifications.add(currentNotification);
            if (activeNotifications.size() > MAX_NOTIFICATIONS) {
                Notification oldNotification = activeNotifications.remove(0);
                oldNotification.expire();
            }
            
            return true;
        } catch (Exception e) {
            LOG.error("显示下一页失败", e);
            return false;
        }
    }

    @Override
    public boolean showPreviousPage() {
        if (currentContent == null || currentPage <= 1) {
            return false;
        }
        
        try {
            currentPage--;
            
            // 获取设置
            NotificationReaderSettings settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
            int maxLength = settings != null ? settings.getPageSize() : 70;
            
            String pageContent = getPageContent(currentPage, maxLength);
            String pageInfo = String.format(" [%d/%d]", currentPage, totalPages);
            
            // 关闭当前通知并创建新通知
            closeCurrentNotification();
            currentNotification = createNotification(pageInfo, pageContent);
            currentNotification.notify(null);
            
            // 管理活动通知列表
            activeNotifications.add(currentNotification);
            if (activeNotifications.size() > MAX_NOTIFICATIONS) {
                Notification oldNotification = activeNotifications.remove(0);
                oldNotification.expire();
            }
            
            return true;
        } catch (Exception e) {
            LOG.error("显示上一页失败", e);
            return false;
        }
    }

    @Override
    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    public int getTotalPages() {
        return totalPages;
    }
    
    @Override
    public boolean jumpToPage(int pageNumber) {
        if (currentContent == null || pageNumber < 1 || pageNumber > totalPages) {
            return false;
        }
        
        try {
            currentPage = pageNumber;
            
            // 获取设置
            NotificationReaderSettings settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
            int maxLength = settings != null ? settings.getPageSize() : 70;
            
            String pageContent = getPageContent(currentPage, maxLength);
            String pageInfo = String.format(" [%d/%d]", currentPage, totalPages);
            
            // 关闭当前通知并创建新通知
            closeCurrentNotification();
            currentNotification = createNotification(pageInfo, pageContent);
            currentNotification.notify(null);
            
            // 管理活动通知列表
            activeNotifications.add(currentNotification);
            if (activeNotifications.size() > MAX_NOTIFICATIONS) {
                Notification oldNotification = activeNotifications.remove(0);
                oldNotification.expire();
            }
            
            return true;
        } catch (Exception e) {
            LOG.error("跳转到指定页失败", e);
            return false;
        }
    }
    
    /**
     * 创建通知
     */
    @NotNull
    private Notification createNotification(String title, String content) {
        try {
            return NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(content, NotificationType.INFORMATION)
                    .setTitle(title)
                    .setSubtitle(null);
        } catch (Exception e) {
            LOG.error("创建通知失败", e);
            throw e;
        }
    }
    
    /**
     * 获取指定页的内容
     */
    @NotNull
    private String getPageContent(int page, int maxLength) {
        if (currentContent == null) {
            return "";
        }
        
        int start = (page - 1) * maxLength;
        int end = Math.min(start + maxLength, currentContent.length());
        
        if (start >= currentContent.length()) {
            return "";
        }
        
        return currentContent.substring(start, end);
    }

    /**
     * 设置当前内容
     * @param content 内容
     */
    public void setCurrentContent(String content) {
        this.currentContent = content;
    }
    
    /**
     * 设置当前页码
     * @param page 页码
     */
    public void setCurrentPage(int page) {
        this.currentPage = page;
    }
    
    /**
     * 设置总页数
     * @param pages 总页数
     */
    public void setTotalPages(int pages) {
        this.totalPages = pages;
    }
    
    /**
     * 设置当前通知
     * @param notification 通知
     */
    public void setCurrentNotification(Notification notification) {
        // 关闭当前通知
        closeCurrentNotification();
        // 设置新通知
        this.currentNotification = notification;
        // 添加到活动通知列表
        activeNotifications.add(notification);
        // 管理活动通知列表大小
        if (activeNotifications.size() > MAX_NOTIFICATIONS) {
            Notification oldNotification = activeNotifications.remove(0);
            if (!oldNotification.isExpired()) {
                oldNotification.expire();
            }
        }
    }
} 