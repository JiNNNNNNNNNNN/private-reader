package com.lv.tool.privatereader.service;

import com.intellij.notification.Notification;
import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * 通知服务接口
 * 提供响应式API显示通知
 */
public interface NotificationService {
    /**
     * 显示章节内容
     *
     * @param book      书籍
     * @param chapterId 章节ID
     * @param content   章节内容
     * @return 通知对象
     */
    Mono<Notification> showChapterContent(@NotNull Book book, @NotNull String chapterId, @NotNull String content);

    /**
     * 显示错误信息
     *
     * @param title   标题
     * @param message 错误信息
     * @return 通知对象
     */
    Mono<Notification> showError(@NotNull String title, @NotNull String message);

    /**
     * 显示信息
     *
     * @param title   标题
     * @param message 信息内容
     * @return 通知对象
     */
    Mono<Notification> showInfo(@NotNull String title, @NotNull String message);

    /**
     * 关闭所有通知
     *
     * @return 完成信号
     */
    Mono<Void> closeAllNotifications();

    /**
     * 获取当前通知显示的页码
     *
     * @return 当前页码
     */
    int getCurrentPage();

    /**
     * 获取通知内容的总页数
     *
     * @return 总页数
     */
    int getTotalPages();
} 