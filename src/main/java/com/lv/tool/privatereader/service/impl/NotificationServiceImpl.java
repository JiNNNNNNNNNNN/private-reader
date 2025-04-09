package com.lv.tool.privatereader.service.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NotificationService 实现类
 */
@Service
public final class NotificationServiceImpl implements NotificationService {
    private static final Logger LOG = Logger.getInstance(NotificationServiceImpl.class);
    private static final String NOTIFICATION_GROUP_ID = "PrivateReader";
    private static final int PAGE_SIZE = 2000; // 每页显示的字符数
    
    private BookService bookService;
    private ChapterService chapterService;
    private final ReactiveSchedulers reactiveSchedulers;
    
    private final AtomicReference<Notification> currentNotificationRef = new AtomicReference<>(null);
    
    /**
     * 无参构造方法
     */
    public NotificationServiceImpl() {
        LOG.info("初始化 NotificationServiceImpl");
        this.reactiveSchedulers = ReactiveSchedulers.getInstance();
        // 其他服务会在首次使用时延迟初始化
    }
    
    /**
     * 确保服务已初始化
     */
    private void ensureServicesInitialized() {
        if (bookService == null) {
            bookService = ApplicationManager.getApplication().getService(BookService.class);
        }
        
        if (chapterService == null) {
            chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
        }
    }
    
    @Override
    public Mono<Notification> showChapterContent(@NotNull Book book, @NotNull String chapterId, @NotNull String content) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("NotificationServiceImpl: 显示章节内容: " + book.getTitle() + " - " + chapterId);
            
            closeCurrentNotificationInternal();

            if (content == null || content.isEmpty()) {
                 LOG.warn("章节内容为空，无法显示通知: " + chapterId);
                 return Mono.error(new IllegalArgumentException("章节内容为空")); 
            }
            
            return chapterService.getChapter(book, chapterId)
                .map(chapter -> chapter.title())
                .defaultIfEmpty("未知章节")
                .flatMap(chapterTitle -> {
                    String title = book.getTitle() + " - " + chapterTitle;
                    
                    Notification notification = NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification(content, NotificationType.INFORMATION);
                    
                    notification.setTitle(title);
                    
                    currentNotificationRef.set(notification);
                    
                    notification.notify(null);
                    
                    return bookService.saveReadingProgress(book, chapterId, chapterTitle, 0)
                        .thenReturn(notification);
                });
        }).subscribeOn(reactiveSchedulers.ui());
    }
    
    @Override
    public Mono<Notification> showError(@NotNull String title, @NotNull String message) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("NotificationServiceImpl: 显示错误: " + title + " - " + message);
            
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(message, NotificationType.ERROR);
            
            notification.setTitle(title);
            notification.notify(null);
            
            return Mono.just(notification);
        }).subscribeOn(reactiveSchedulers.ui());
    }

    @Override
    public Mono<Notification> showInfo(@NotNull String title, @NotNull String message) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
             LOG.info("NotificationServiceImpl: 显示信息: " + title + " - " + message);
             
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(message, NotificationType.INFORMATION);
            
            notification.setTitle(title);
            notification.notify(null);
            
            return Mono.just(notification);
        }).subscribeOn(reactiveSchedulers.ui());
    }
    
    @Override
    public Mono<Void> closeAllNotifications() {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("NotificationServiceImpl: 关闭所有通知");
            closeCurrentNotificationInternal();
            return Mono.empty().then();
        }).subscribeOn(reactiveSchedulers.ui());
    }

    private void closeCurrentNotificationInternal() {
        Notification oldNotification = currentNotificationRef.getAndSet(null);
        if (oldNotification != null && !oldNotification.isExpired()) {
            oldNotification.expire();
        }
    }

    // 添加 getCurrentPage 和 getTotalPages 的基本实现
    @Override
    public int getCurrentPage() {
        // TODO: 实现获取当前通知页码的逻辑
        LOG.warn("getCurrentPage() 未实现，暂时返回 1");
        return 1; // 临时返回值
    }

    @Override
    public int getTotalPages() {
        // TODO: 实现获取通知总页数的逻辑
        LOG.warn("getTotalPages() 未实现，暂时返回 1");
        return 1; // 临时返回值
    }
} 