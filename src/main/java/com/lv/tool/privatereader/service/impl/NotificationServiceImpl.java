package com.lv.tool.privatereader.service.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.lv.tool.privatereader.model.BookProgressData;
import com.lv.tool.privatereader.repository.impl.SqliteReadingProgressRepository;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;

/**
 * NotificationService 实现类
 *
 * 注意：在保存阅读进度时，我们需要特别注意页码的处理。
 * 1. 从数据库中恢复的页码是1基索引，我们将其转换为0基索引的 currentPageIndex
 * 2. 在保存页码时，我们不能使用 bookService.saveReadingProgress 方法，因为它会将 currentPageIndex 加1
 * 3. 如果我们使用 bookService.saveReadingProgress 方法，它会再次将 currentPageIndex 加1，导致页码始终是1
 * 4. 我们应该直接使用 SqliteReadingProgressRepository 类的 updateProgress 方法的重载版本，直接传递页码参数
 */
@Service
public final class NotificationServiceImpl implements NotificationService {
    private static final Logger LOG = Logger.getInstance(NotificationServiceImpl.class);
    private static final String NOTIFICATION_GROUP_ID = "PrivateReader";
    // 通知相关字段
    private final AtomicReference<Notification> currentNotificationRef = new AtomicReference<>(null);

    // 服务相关字段
    private BookService bookService;
    private ChapterService chapterService;
    private NotificationReaderSettings notificationSettings;
    private final ReactiveSchedulers reactiveSchedulers;

    // 分页相关字段
    private List<String> currentPages = new ArrayList<>();
    private int currentPageIndex = 0;
    private Book currentBook;
    private String currentChapterId;
    private String currentChapterTitle;

    // 自动翻页相关字段
    private ScheduledExecutorService autoReadExecutor;
    private ScheduledFuture<?> autoReadTask;

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

        if (notificationSettings == null) {
            notificationSettings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
        }
    }

    @Override
    public void setCurrentChapterContent(@NotNull String content) {
        ensureServicesInitialized();
        int pageSize = notificationSettings != null ? notificationSettings.getPageSize() : 70; // Use setting for page size
        LOG.info("NotificationServiceImpl: 设置当前章节内容，使用页面大小: " + pageSize +
                 ", notificationSettings 是否为 null: " + (notificationSettings == null));

        // 记录当前页码索引
        int oldPageIndex = this.currentPageIndex;
        LOG.info(String.format("[页码调试] setCurrentChapterContent 调用前的页码索引: %d", oldPageIndex));

        this.currentPages = paginateContent(content, pageSize);
        this.currentPageIndex = 0; // Reset to first page

        LOG.info(String.format("[页码调试] setCurrentChapterContent 调用后的页码索引: %d (重置为0)", this.currentPageIndex));
        LOG.info("NotificationServiceImpl: 分页完成，总页数: " + this.currentPages.size());
    }

    @Override
    public int calculateTotalPages(@NotNull String content) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 计算总页数");
        int pageSize = notificationSettings != null ? notificationSettings.getPageSize() : 70; // Use setting for page size
        return paginateContent(content, pageSize).size();
    }

    @Override
    public String getPageContent(@NotNull String content, int pageNumber) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 获取页码 " + pageNumber + " 的内容");
        int pageSize = notificationSettings != null ? notificationSettings.getPageSize() : 70; // Use setting for page size
        List<String> pages = paginateContent(content, pageSize);
        if (pageNumber > 0 && pageNumber <= pages.size()) {
            return pages.get(pageNumber - 1); // pageNumber is 1-based, list index is 0-based
        }
        LOG.warn("NotificationServiceImpl: 无效的页码: " + pageNumber);
        return "Invalid page number.";
    }

    @Override
    public void showChapterContent(@NotNull Project project, @NotNull String bookId, @NotNull String chapterId, int pageNumber, @NotNull String title, @NotNull String content) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 显示章节内容通知: " + title);

        closeCurrentNotificationInternal();

        if (content == null || content.isEmpty()) {
            LOG.warn("章节内容为空，无法显示通知: " + chapterId);
            showError("显示章节失败", "章节内容为空");
            return;
        }

        // Save current book, chapter, and page information
        // Need to get the Book object from bookId
        // Block on the Mono to get the Book object. Use with caution.
        Book book = bookService.getBookById(bookId).block(); // Assuming BookService.getBookById returns Mono<Book>
        if (book == null) {
             LOG.warn("未找到书籍，无法显示通知: " + bookId);
             showError("显示章节失败", "未找到书籍");
             return;
        }
        this.currentBook = book;
        this.currentChapterId = chapterId;
        // currentChapterTitle is passed in as title, but might need to be fetched separately if title format changes
        this.currentChapterTitle = chapterService.getChapterTitle(bookId, chapterId); // Fetch title using ChapterService

        // 检查是否有保存的页码信息
        int savedPageNumber = pageNumber;

        // 如果传入的页码是1（默认值），尝试从数据库中获取保存的页码
        if (pageNumber == 1) {
            try {
                // 获取 SqliteReadingProgressRepository 实例
                SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
                if (readingProgressRepository != null) {
                    // 获取书籍的阅读进度
                    Optional<BookProgressData> progressDataOpt = readingProgressRepository.getProgress(bookId);
                    if (progressDataOpt.isPresent()) {
                        BookProgressData progressData = progressDataOpt.get();
                        // 检查章节ID是否匹配
                        if (chapterId.equals(progressData.lastReadChapterId())) {
                            // 使用保存的页码
                            savedPageNumber = progressData.lastReadPage();
                            LOG.info(String.format("[页码调试] 从数据库恢复页码: %d", savedPageNumber));
                        } else {
                            LOG.info("[页码调试] 章节ID不匹配，无法恢复页码");
                        }
                    } else {
                        LOG.info("[页码调试] 未找到书籍的阅读进度记录");
                    }
                } else {
                    LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例");
                }
            } catch (Exception e) {
                LOG.error("[页码调试] 恢复页码时出错", e);
            }
        }

        // 先记录恢复的页码
        LOG.info(String.format("[页码调试] 恢复的页码: %d", savedPageNumber));

        // 然后进行分页，这会重置 currentPageIndex = 0
        setCurrentChapterContent(content); // Paginate and set current pages

        // 记录详细日志，用于调试
        LOG.info(String.format("[页码调试] 显示章节内容: 书籍=%s, 章节=%s, 传入页码=%d, 总页数=%d",
                bookId, chapterId, pageNumber, currentPages.size()));

        // 在分页后，再次设置恢复的页码
        this.currentPageIndex = savedPageNumber - 1; // pageNumber is 1-based, index is 0-based
        LOG.info(String.format("[页码调试] 重新设置页码索引: %d (对应页码: %d)",
                this.currentPageIndex, this.currentPageIndex + 1));

        // 确保页码索引在有效范围内
        if (this.currentPageIndex < 0) {
            LOG.info("[页码调试] 页码索引小于0，重置为0");
            this.currentPageIndex = 0;
        } else if (this.currentPageIndex >= currentPages.size()) {
            LOG.info("[页码调试] 页码索引超出范围，重置为最后一页");
            this.currentPageIndex = Math.max(0, currentPages.size() - 1);
        }

        LOG.info(String.format("[页码调试] 设置页码索引: %d (对应页码: %d)",
                this.currentPageIndex, this.currentPageIndex + 1));

        if (currentPages.isEmpty()) {
            LOG.warn("分页后内容为空，无法显示通知: " + chapterId);
            showError("显示章节失败", "分页后内容为空");
            return;
        }

        // Show the specified page content
        if (currentPageIndex >= 0 && currentPageIndex < currentPages.size()) {
            String pageContent = currentPages.get(currentPageIndex);
            String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                    "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
            String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);
            showCurrentPageInternal(project, title, notificationContent);
        } else {
            LOG.warn("无效的页码索引: " + currentPageIndex);
            showError("显示章节失败", "无效的页码索引");
        }

        // 记录日志
        LOG.info("[通知栏模式] 显示章节内容通知: 书籍=" + bookId + ", 章节=" + chapterId + ", 页码=" + pageNumber);
    }

    /**
     * 内部方法，用于在通知中显示当前页面内容
     * 增强版本支持更多的交互选项和更好的格式化
     *
     * @param project 当前项目
     * @param title 通知标题
     * @param content 页面内容
     */
    private void showCurrentPageInternal(@NotNull Project project, @NotNull String title, @NotNull String content) {
        // 验证当前状态
        if (currentPages.isEmpty() || currentPageIndex < 0 || currentPageIndex >= currentPages.size()) {
            LOG.warn("当前页索引无效: " + currentPageIndex);
            showError("显示页面失败", "当前页索引无效");
            return;
        }

        // 检查内容长度
        LOG.info("[通知栏模式] 原始通知内容长度: " + content.length());
        if (content.length() > 1000) {
            LOG.warn("[通知栏模式] 内容长度超过 1000 个字符，可能会被截断: " + content.length());
        }

        // 构建通知标题
        String notificationTitle;

        // 只有第一页显示书名和章节名，其他页只显示页码
        if (currentPageIndex == 0) {
            notificationTitle = title;
        } else {
            notificationTitle = "阅读中";
        }

        // 如果设置启用，添加页码信息
        boolean showPageNumber = notificationSettings != null && notificationSettings.isShowPageNumbers();
        if (showPageNumber) {
            notificationTitle += String.format(" (第%d页/共%d页)", currentPageIndex + 1, currentPages.size());
        }

        // 清理内容中的HTML标签，保留换行符
        String cleanContent = cleanHtmlTags(content);
        LOG.info("[通知栏模式] 清理后的通知内容长度: " + cleanContent.length());

        // 创建通知
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(cleanContent, NotificationType.INFORMATION); // 使用 INFORMATION 类型

        notification.setTitle(notificationTitle);

        // 检查是否显示按钮
        boolean showButtons = notificationSettings != null && notificationSettings.isShowButtons();
        if (showButtons) {
            // 添加导航按钮
            // 1. 页面导航按钮
            if (currentPageIndex > 0) {
                notification.addAction(NotificationAction.createSimple("上一页", () -> {
                    LOG.info("[通知栏模式] 点击了上一页按钮，当前页索引: " + currentPageIndex);
                    showPrevPage(project);
                }));
            }

            if (currentPageIndex < currentPages.size() - 1) {
                notification.addAction(NotificationAction.createSimple("下一页", () -> {
                    LOG.info("[通知栏模式] 点击了下一页按钮，当前页索引: " + currentPageIndex);
                    showNextPage(project);
                }));
            }

            // 2. 章节导航按钮
            notification.addAction(NotificationAction.createSimple("上一章", () -> {
                LOG.info("[通知栏模式] 点击了上一章按钮");
                navigateChapter(project, -1);
            }));

            notification.addAction(NotificationAction.createSimple("下一章", () -> {
                LOG.info("[通知栏模式] 点击了下一章按钮");
                navigateChapter(project, 1);
            }));

            // 3. 添加自动阅读按钮（如果设置允许）
            if (notificationSettings != null && notificationSettings.isAutoRead()) {
                boolean isAutoReadActive = autoReadTask != null && !autoReadTask.isDone();

                if (isAutoReadActive) {
                    notification.addAction(NotificationAction.createSimple("暂停自动阅读", () -> {
                        LOG.info("[通知栏模式] 点击了暂停自动阅读按钮");
                        stopAutoRead();
                        // 刷新通知以更新按钮状态
                        showCurrentPageInternal(project, title, content);
                    }));
                } else {
                    notification.addAction(NotificationAction.createSimple("开始自动阅读", () -> {
                        LOG.info("[通知栏模式] 点击了开始自动阅读按钮");
                        int interval = notificationSettings.getReadIntervalSeconds();
                        startAutoRead(interval);
                        // 刷新通知以更新按钮状态
                        showCurrentPageInternal(project, title, content);
                    }));
                }
            }

            // 4. 添加返回阅读器模式按钮
            notification.addAction(NotificationAction.createSimple("返回阅读器", () -> {
                LOG.info("[通知栏模式] 点击了返回阅读器按钮");
                // 获取ReaderModeSettings服务并切换模式
                try {
                    com.lv.tool.privatereader.settings.ReaderModeSettings settings =
                        ApplicationManager.getApplication().getService(com.lv.tool.privatereader.settings.ReaderModeSettings.class);
                    if (settings != null) {
                        settings.setNotificationMode(false);
                        // 模式切换会通过监听器自动处理UI变化
                    }
                } catch (Exception e) {
                    LOG.error("[通知栏模式] 切换到阅读器模式时出错: " + e.getMessage(), e);
                }
            }));
        } else {
            LOG.info("[通知栏模式] 按照设置不显示导航按钮");
        }

        // 保存当前通知引用并显示通知
        currentNotificationRef.set(notification);
        // notification.notify(project); // 原来的显示方式
        com.intellij.notification.Notifications.Bus.notify(notification, project); // 新的显示方式

        // 记录日志
        LOG.info("[通知栏模式] 显示通知: " + notificationTitle + ", 当前页: " + (currentPageIndex + 1) + "/" + currentPages.size());
    }

    @Override
    public void updateNotificationContent(@NotNull Project project, @NotNull String content) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 更新通知内容");

        Notification existingNotification = currentNotificationRef.get();
        if (existingNotification != null && !existingNotification.isExpired()) {
            // Assuming the content parameter here is the *full* content to display for the current page
            // This method is called by the refresh timer, which rebuilds the content string
            existingNotification.setContent(content);
            // Re-notify to update the display
            existingNotification.notify(project);

            // 记录日志
            LOG.info("[通知栏模式] 更新通知内容成功");
        } else {
            LOG.warn("没有现有通知可更新或通知已过期");
        }
    }

    @Override
    public Mono<Notification> showError(@NotNull String title, @NotNull String message) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 显示错误: " + title + " - " + message);

        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.ERROR);

        notification.setTitle(title);
        notification.notify(null); // Notify without project context for general errors

        // 记录日志
        LOG.info("[通知栏模式] 显示错误通知: " + title);
        return Mono.just(notification); // Return Mono for compatibility
    }

    @Override
    public Mono<Notification> showInfo(@NotNull String title, @NotNull String message) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 显示信息: " + title + " - " + message);

        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.INFORMATION);

        notification.setTitle(title);
        notification.notify(null); // Notify without project context for general info

        // 记录日志
        LOG.info("[通知栏模式] 显示信息通知: " + title);
        return Mono.just(notification); // Return Mono for compatibility
    }

    @Override
    public Mono<Void> closeAllNotificationsReactive() {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl (Reactive): 关闭所有通知");
        // This reactive method might be used by other parts of the application
        // It should not interfere with the state managed by the notification bar mode
        // It will just expire the current notification if it exists
        Notification existingNotification = currentNotificationRef.getAndSet(null);
        if (existingNotification != null && !existingNotification.isExpired()) {
            existingNotification.expire();
        }
        return Mono.empty().then();
    }

    @Override
    public void closeAllNotifications() {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 关闭所有通知");
        closeCurrentNotificationInternal();

        // 记录日志
        LOG.info("[通知栏模式] 已关闭所有通知");
    }

    @Override
    public int getCurrentPage() {
        if (currentPages.isEmpty()) {
            return 0;
        }
        // 返回1基索引的页码（currentPageIndex是0基索引）
        return currentPageIndex + 1;
    }

    @Override
    public int getTotalPages() {
        return currentPages.size();
    }

    @Override
    public String getCurrentBookId() {
        return currentBook != null ? currentBook.getId() : null;
    }

    @Override
    public String getCurrentChapterId() {
        return currentChapterId;
    }

    @Override
    public void showPrevPage(@NotNull Project project) {
        ensureServicesInitialized();
        LOG.info("[通知栏模式] 显示上一页，当前页索引: " + currentPageIndex + ", 总页数: " + currentPages.size());

        // 验证当前状态
        if (currentBook == null || currentChapterId == null || currentChapterTitle == null) {
            LOG.warn("[通知栏模式] 当前没有正在阅读的内容");
            showInfo("导航", "当前没有正在阅读的内容");
            return;
        }

        // 检查是否已经是第一页
        if (currentPageIndex <= 0) {
            // 如果是第一页，尝试跳转到上一章的最后一页
            LOG.info("[通知栏模式] 当前是第一页，尝试跳转到上一章的最后一页");
            navigateChapterToLastPage(project, -1);
            return;
        }

        // 更新页面索引
        currentPageIndex--;
        LOG.info("[通知栏模式] 页面索引递减为: " + currentPageIndex);

        // 构建通知内容
        String title = currentBook.getTitle() + " - " + currentChapterTitle;
        String pageContent = currentPages.get(currentPageIndex);
        String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
        String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

        // 使用内部方法显示通知，确保一致的格式和行为
        showCurrentPageInternal(project, title, notificationContent);

        // 保存阅读进度
        // 注意：不使用 bookService.saveReadingProgress 方法，因为它会将 currentPageIndex 加1
        // 而我们已经从数据库中恢复的页码是1基索引，转换为 currentPageIndex 时减了1
        // 如果再使用 bookService.saveReadingProgress 方法，它会再次将 currentPageIndex 加1，导致页码始终是1
        SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
        if (readingProgressRepository != null) {
            // 使用带页码参数的重载方法，position设为0，直接使用currentPageIndex + 1作为页码
            readingProgressRepository.updateProgress(currentBook, currentChapterId, currentChapterTitle, 0, currentPageIndex + 1);
            LOG.info(String.format("[页码调试] 直接保存页码: %d", currentPageIndex + 1));
        } else {
            LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
            bookService.saveReadingProgress(currentBook, currentChapterId, currentChapterTitle, currentPageIndex);
        }

        LOG.info("[通知栏模式] 成功显示上一页，当前页索引: " + currentPageIndex);
    }

    @Override
    public void showNextPage(@NotNull Project project) {
        ensureServicesInitialized();
        LOG.info("[通知栏模式] 显示下一页，当前页索引: " + currentPageIndex + ", 总页数: " + currentPages.size());

        // 验证当前状态
        if (currentBook == null || currentChapterId == null || currentChapterTitle == null) {
            LOG.warn("[通知栏模式] 当前没有正在阅读的内容");
            showInfo("导航", "当前没有正在阅读的内容");
            return;
        }

        // 检查是否已经是最后一页
        if (currentPageIndex >= currentPages.size() - 1) {
            // 如果是最后一页，尝试跳转到下一章的第一页
            LOG.info("[通知栏模式] 当前是最后一页，尝试跳转到下一章的第一页");
            navigateChapter(project, 1);
            return;
        }

        // 更新页面索引
        currentPageIndex++;
        LOG.info("[通知栏模式] 页面索引递增为: " + currentPageIndex);

        // 构建通知内容
        String title = currentBook.getTitle() + " - " + currentChapterTitle;
        String pageContent = currentPages.get(currentPageIndex);
        String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
        String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

        // 使用内部方法显示通知，确保一致的格式和行为
        showCurrentPageInternal(project, title, notificationContent);

        // 保存阅读进度
        // 注意：不使用 bookService.saveReadingProgress 方法，因为它会将 currentPageIndex 加1
        // 而我们已经从数据库中恢复的页码是1基索引，转换为 currentPageIndex 时减了1
        // 如果再使用 bookService.saveReadingProgress 方法，它会再次将 currentPageIndex 加1，导致页码始终是1
        SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
        if (readingProgressRepository != null) {
            // 使用带页码参数的重载方法，position设为0，直接使用currentPageIndex + 1作为页码
            readingProgressRepository.updateProgress(currentBook, currentChapterId, currentChapterTitle, 0, currentPageIndex + 1);
            LOG.info(String.format("[页码调试] 直接保存页码: %d", currentPageIndex + 1));
        } else {
            LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
            bookService.saveReadingProgress(currentBook, currentChapterId, currentChapterTitle, currentPageIndex);
        }

        LOG.info("[通知栏模式] 成功显示下一页，当前页索引: " + currentPageIndex);
    }

    @Override
    public void navigateChapter(@NotNull Project project, int direction) {
        ensureServicesInitialized();
        LOG.info("NotificationServiceImpl: 导航章节，方向: " + direction);

        if (currentBook == null || currentChapterId == null) {
            LOG.warn("当前没有正在阅读的内容");
            showInfo("导航", "当前没有正在阅读的内容");
            return;
        }

        // 显示加载状态通知
        showLoadingNotification(project, "正在加载章节...");

        // 首先尝试使用Book中的cachedChapters
        List<NovelParser.Chapter> cachedChapters = currentBook.getCachedChapters();
        if (cachedChapters != null && !cachedChapters.isEmpty()) {
            LOG.info("使用Book中的cachedChapters进行导航，章节数量: " + cachedChapters.size());
            // 在UI线程上处理导航逻辑
            reactiveSchedulers.runOnUI(() -> processChapterNavigationWithCachedChapters(project, cachedChapters, direction));
            return;
        }

        // 如果cachedChapters为空，则使用异步方式获取章节列表
        LOG.info("Book中的cachedChapters为空，使用bookService.getChaptersSync获取章节列表");
        Mono.fromCallable(() -> bookService.getChaptersSync(currentBook.getId()))
            .subscribeOn(reactiveSchedulers.io()) // 在IO线程上执行
            .timeout(java.time.Duration.ofSeconds(30)) // 设置超时
            .doOnError(e -> {
                LOG.error("获取章节列表时出错: " + e.getMessage(), e);
                reactiveSchedulers.runOnUI(() -> showError("导航失败", "获取章节列表时出错: " + e.getMessage()));
            })
            .subscribe(chapters -> {
                // 在获取到章节列表后，在UI线程上处理导航逻辑
                reactiveSchedulers.runOnUI(() -> processChapterNavigation(project, chapters, direction));
            });
    }

    /**
     * 显示加载状态通知
     * @param project 当前项目
     * @param message 加载消息
     */
    private void showLoadingNotification(@NotNull Project project, @NotNull String message) {
        // 关闭当前通知
        closeCurrentNotificationInternal();

        // 创建加载状态通知
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, NotificationType.INFORMATION);

        notification.setTitle("加载中");
        currentNotificationRef.set(notification);
        com.intellij.notification.Notifications.Bus.notify(notification, project);

        LOG.info("[通知栏模式] 显示加载状态通知: " + message);
    }

    /**
     * 处理章节导航逻辑
     * 在UI线程上执行
     *
     * @param project 当前项目
     * @param chapters 章节列表
     * @param direction 导航方向
     */
    private void processChapterNavigation(@NotNull Project project,
                                         @Nullable List<ChapterService.EnhancedChapter> chapters,
                                         int direction) {
        if (chapters == null || chapters.isEmpty()) {
            LOG.warn("章节列表为空");
            showInfo("导航", "章节列表为空");
            return;
        }

        int currentIndex = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).url().equals(currentChapterId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            LOG.warn("当前章节在列表中未找到: " + currentChapterId);
            showError("导航失败", "当前章节在列表中未找到");
            return;
        }

        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= chapters.size()) {
            String message = direction < 0 ? "已经是第一章了" : "已经是最后一章了";
            LOG.warn(message);
            showInfo("导航", message);
            return;
        }

        // Get the target chapter and its content
        ChapterService.EnhancedChapter targetChapter = chapters.get(targetIndex);
        String targetChapterId = targetChapter.url();
        String targetChapterTitle = targetChapter.title();
        String targetChapterContent = targetChapter.getContent(); // Get content from EnhancedChapter

        if (targetChapterContent == null || targetChapterContent.isEmpty()) {
             LOG.warn("目标章节内容为空: " + targetChapterId);
             showError("导航失败", "目标章节内容为空");
             return;
        }

        // Update current book, chapter, and page (reset to first page of new chapter)
        this.currentChapterId = targetChapterId;
        this.currentChapterTitle = targetChapterTitle;
        setCurrentChapterContent(targetChapterContent); // Paginate and set current pages for the new chapter
        this.currentPageIndex = 0; // Start from the first page of the new chapter

        // Show the first page of the new chapter
        if (currentPages.isEmpty()) {
            LOG.warn("分页后内容为空，无法显示通知: " + targetChapterId);
            showError("显示章节失败", "分页后内容为空");
            return;
        }

        // 构建通知标题和内容
        String title = currentBook.getTitle() + " - " + targetChapterTitle;
        String pageContent = currentPages.get(currentPageIndex);
        String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
        String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

        // 使用内部方法显示通知，确保一致的格式和行为
        showCurrentPageInternal(project, title, notificationContent);

        // Save reading progress for the new chapter (first page)
        // 注意：不使用 bookService.saveReadingProgress 方法，因为它会将 currentPageIndex 加1
        // 而我们已经从数据库中恢复的页码是1基索引，转换为 currentPageIndex 时减了1
        // 如果再使用 bookService.saveReadingProgress 方法，它会再次将 currentPageIndex 加1，导致页码始终是1
        SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
        if (readingProgressRepository != null) {
            // 使用带页码参数的重载方法，position设为0，直接使用currentPageIndex + 1作为页码
            readingProgressRepository.updateProgress(currentBook, targetChapterId, targetChapterTitle, 0, currentPageIndex + 1);
            LOG.info(String.format("[页码调试] 直接保存页码: %d", currentPageIndex + 1));
        } else {
            LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
            bookService.saveReadingProgress(currentBook, targetChapterId, targetChapterTitle, currentPageIndex);
        }

        // 记录日志
        LOG.info("[通知栏模式] 导航到章节: " + targetChapterId);
    }

    /**
     * 使用Book中的cachedChapters处理章节导航逻辑
     * 在UI线程上执行
     *
     * @param project 当前项目
     * @param cachedChapters 缓存的章节列表
     * @param direction 导航方向
     */
    private void processChapterNavigationWithCachedChapters(@NotNull Project project,
                                                          @Nullable List<Chapter> cachedChapters,
                                                          int direction) {
        if (cachedChapters == null || cachedChapters.isEmpty()) {
            LOG.warn("缓存的章节列表为空");
            showInfo("导航", "章节列表为空");
            return;
        }

        int currentIndex = -1;
        for (int i = 0; i < cachedChapters.size(); i++) {
            if (cachedChapters.get(i).url().equals(currentChapterId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            LOG.warn("当前章节在缓存列表中未找到: " + currentChapterId);
            showError("导航失败", "当前章节在列表中未找到");
            return;
        }

        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= cachedChapters.size()) {
            String message = direction < 0 ? "已经是第一章了" : "已经是最后一章了";
            LOG.warn(message);
            showInfo("导航", message);
            return;
        }

        // 获取目标章节信息
        Chapter targetChapter = cachedChapters.get(targetIndex);
        String targetChapterId = targetChapter.url();
        String targetChapterTitle = targetChapter.title();

        // 显示加载状态通知
        showLoadingNotification(project, "正在加载章节内容...");

        // 使用异步方式获取章节内容，避免阻塞UI线程
        Mono.fromCallable(() -> {
            // 使用NovelParser获取章节内容
            if (currentBook.getParser() != null) {
                return currentBook.getParser().getChapterContent(targetChapterId, currentBook);
            }
            return null;
        })
        .subscribeOn(reactiveSchedulers.io()) // 在IO线程上执行
        .timeout(java.time.Duration.ofSeconds(30)) // 设置超时
        .doOnError(e -> {
            LOG.error("获取章节内容时出错: " + e.getMessage(), e);
            reactiveSchedulers.runOnUI(() -> showError("导航失败", "获取章节内容时出错: " + e.getMessage()));
        })
        .subscribe(content -> {
            // 在获取到章节内容后，在UI线程上处理显示逻辑
            reactiveSchedulers.runOnUI(() -> {
                if (content == null || content.isEmpty()) {
                    LOG.warn("目标章节内容为空: " + targetChapterId);
                    showError("导航失败", "目标章节内容为空");
                    return;
                }

                // 更新当前章节信息
                currentChapterId = targetChapterId;
                currentChapterTitle = targetChapterTitle;
                setCurrentChapterContent(content); // 分页并设置当前页面
                currentPageIndex = 0; // 从第一页开始

                // 显示第一页内容
                if (currentPages.isEmpty()) {
                    LOG.warn("分页后内容为空，无法显示通知: " + targetChapterId);
                    showError("显示章节失败", "分页后内容为空");
                    return;
                }

                // 构建通知标题和内容
                String title = currentBook.getTitle() + " - " + targetChapterTitle;
                String pageContent = currentPages.get(currentPageIndex);
                String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                        "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
                String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

                // 显示通知
                showCurrentPageInternal(project, title, notificationContent);

                // 保存阅读进度
                SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
                if (readingProgressRepository != null) {
                    readingProgressRepository.updateProgress(currentBook, targetChapterId, targetChapterTitle, 0, currentPageIndex + 1);
                    LOG.info(String.format("[页码调试] 直接保存页码: %d", currentPageIndex + 1));
                } else {
                    LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
                    bookService.saveReadingProgress(currentBook, targetChapterId, targetChapterTitle, currentPageIndex);
                }

                // 记录日志
                LOG.info("[通知栏模式] 使用cachedChapters导航到章节: " + targetChapterId);
            });
        });
    }

    // Existing reactive methods (kept for compatibility if still used elsewhere)
    @Override
    public Mono<Notification> showChapterContent(@NotNull Book book, @NotNull String chapterId, @NotNull String content) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("[通知栏模式] (Reactive) 显示章节内容: " + book.getTitle() + " - " + chapterId + ", 内容长度: " + content.length());

            // 关闭当前通知
            closeCurrentNotificationInternal();

            if (content == null || content.isEmpty()) {
                LOG.warn("[通知栏模式] (Reactive) 章节内容为空，无法显示通知: " + chapterId);
                return showError("显示章节失败", "章节内容为空");
            }

            // 保存当前书籍、章节和内容信息
            this.currentBook = book;
            this.currentChapterId = chapterId;
            this.currentChapterTitle = chapterService.getChapterTitle(book.getId(), chapterId);

            // 检查是否有保存的页码信息
            int savedPageNumber = 1; // 默认从第1页开始（对应索引0）

            // 尝试从数据库中获取保存的页码
            try {
                // 获取 SqliteReadingProgressRepository 实例
                SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
                if (readingProgressRepository != null) {
                    // 获取书籍的阅读进度
                    Optional<BookProgressData> progressDataOpt = readingProgressRepository.getProgress(book.getId());
                    if (progressDataOpt.isPresent()) {
                        BookProgressData progressData = progressDataOpt.get();
                        // 检查章节ID是否匹配
                        if (chapterId.equals(progressData.lastReadChapterId())) {
                            // 使用保存的页码
                            savedPageNumber = progressData.lastReadPage();
                            LOG.info(String.format("[页码调试] (Reactive) 从数据库恢复页码: %d", savedPageNumber));
                        } else {
                            LOG.info("[页码调试] (Reactive) 章节ID不匹配，无法恢复页码");
                        }
                    } else {
                        LOG.info("[页码调试] (Reactive) 未找到书籍的阅读进度记录");
                    }
                } else {
                    LOG.warn("[页码调试] (Reactive) 无法获取 SqliteReadingProgressRepository 实例");
                }
            } catch (Exception e) {
                LOG.error("[页码调试] (Reactive) 恢复页码时出错", e);
            }

            // 记录恢复的页码
            LOG.info(String.format("[页码调试] (Reactive) 恢复的页码: %d", savedPageNumber));

            // 使用 setCurrentChapterContent 方法进行分页
            setCurrentChapterContent(content);

            // 分页后，设置恢复的页码索引
            this.currentPageIndex = savedPageNumber - 1; // 页码是1基索引，转换为0基索引
            LOG.info(String.format("[页码调试] (Reactive) 重新设置页码索引: %d (对应页码: %d)",
                    this.currentPageIndex, this.currentPageIndex + 1));

            // 确保页码索引在有效范围内
            if (this.currentPageIndex < 0) {
                LOG.info("[页码调试] (Reactive) 页码索引小于0，重置为0");
                this.currentPageIndex = 0;
            } else if (this.currentPageIndex >= currentPages.size()) {
                LOG.info("[页码调试] (Reactive) 页码索引超出范围，重置为最后一页");
                this.currentPageIndex = Math.max(0, currentPages.size() - 1);
            }

            if (currentPages.isEmpty()) {
                LOG.warn("[通知栏模式] (Reactive) 分页后内容为空，无法显示通知: " + chapterId);
                return showError("显示章节失败", "分页后内容为空");
            }

            // 获取当前页内容
            String pageContent = currentPages.get(currentPageIndex);
            String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                    "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
            String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

            // 构建通知标题
            String title = book.getTitle() + " - " + currentChapterTitle;

            // 使用 Project 对象，如果可用
            Project project = null;
            try {
                project = ProjectManager.getInstance().getOpenProjects()[0]; // 获取第一个打开的项目
            } catch (Exception e) {
                LOG.warn("[通知栏模式] (Reactive) 无法获取 Project 对象: " + e.getMessage());
            }

            // 使用 showCurrentPageInternal 方法显示通知
            if (project != null) {
                showCurrentPageInternal(project, title, notificationContent);
                // 保存阅读进度
                // 注意：不使用 bookService.saveReadingProgress 方法，因为它会将 currentPageIndex 加1
                // 而我们已经从数据库中恢复的页码是1基索引，转换为 currentPageIndex 时减了1
                // 如果再使用 bookService.saveReadingProgress 方法，它会再次将 currentPageIndex 加1，导致页码始终是1
                SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
                if (readingProgressRepository != null) {
                    // 使用带页码参数的重载方法，position设为0，直接使用currentPageIndex + 1作为页码
                    readingProgressRepository.updateProgress(book, chapterId, currentChapterTitle, 0, currentPageIndex + 1);
                    LOG.info(String.format("[页码调试] (Reactive) 直接保存页码: %d", currentPageIndex + 1));
                } else {
                    LOG.warn("[页码调试] (Reactive) 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
                    bookService.saveReadingProgress(book, chapterId, currentChapterTitle, currentPageIndex);
                }
                LOG.info("[通知栏模式] (Reactive) 显示章节内容成功，使用 Project 对象");
                return Mono.just(currentNotificationRef.get());
            } else {
                // 如果无法获取 Project 对象，使用简单的通知
                LOG.warn("[通知栏模式] (Reactive) 无法获取 Project 对象，使用简单通知");
                // 清理内容中的HTML标签
                String cleanContent = cleanHtmlTags(notificationContent);
                Notification notification = NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification(cleanContent, NotificationType.INFORMATION);

                notification.setTitle(title);
                notification.notify(null);
                currentNotificationRef.set(notification);
                return Mono.just(notification);
            }
        }).subscribeOn(reactiveSchedulers.ui());
    }

    @Override
    public Mono<Notification> showPrevPageReactive() {
        // This reactive method is likely for the main reader panel and might need different logic
        // For now, it will just log a warning
        LOG.warn("showPrevPageReactive called - Implementation needed for main reader panel.");
        return Mono.empty(); // Or implement reactive page navigation for the main panel
    }

    @Override
    public Mono<Notification> showNextPageReactive() {
         // This reactive method is likely for the main reader panel and might need different logic
        // For now, it will just log a warning
        LOG.warn("showNextPageReactive called - Implementation needed for main reader panel.");
        return Mono.empty(); // Or implement reactive page navigation for the main panel
    }

    @Override
    public Mono<Notification> navigateChapterReactive(int direction) {
         // This reactive method is likely for the main reader panel and might need different logic
        // For now, it will just log a warning
        LOG.warn("navigateChapterReactive called - Implementation needed for main reader panel.");
        return Mono.empty(); // Or implement reactive chapter navigation for the main panel
    }

    @Override
    public void startAutoRead(int intervalSeconds) {
        // 先停止现有的自动阅读任务
        stopAutoRead();

        // 验证参数和状态
        if (intervalSeconds <= 0) {
            LOG.warn("[通知栏模式] 自动阅读间隔必须大于0秒，使用默认值5秒");
            intervalSeconds = 5; // 使用默认值
        }

        if (currentBook == null || currentChapterId == null || currentPages.isEmpty()) {
            LOG.warn("[通知栏模式] 当前没有正在阅读的内容，无法启动自动阅读");
            return;
        }

        LOG.info("[通知栏模式] 启动自动翻页，间隔: " + intervalSeconds + " 秒");

        // 创建守护线程执行器
        autoReadExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "AutoReadThread");
            thread.setDaemon(true); // 设置为守护线程，不会阻止 JVM 退出
            return thread;
        });

        // 获取当前项目，用于通知显示
        final Project currentProject = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects().length > 0 ?
                com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0] : null;

        // 调度定期任务
        autoReadTask = autoReadExecutor.scheduleAtFixedRate(() -> {
            try {
                if (currentBook != null && currentChapterId != null && currentChapterTitle != null) {
                    if (currentPageIndex < currentPages.size() - 1) {
                        // 如果还有下一页，显示下一页
                        LOG.info("[通知栏模式] 自动阅读：显示下一页");
                        showNextPage(currentProject);
                    } else {
                        // 如果已经是最后一页，尝试加载下一章
                        LOG.info("[通知栏模式] 自动阅读：显示下一章");
                        navigateChapter(currentProject, 1);
                    }
                } else {
                    LOG.warn("[通知栏模式] 自动阅读：当前没有正在阅读的内容，停止自动阅读");
                    stopAutoRead();
                }
            } catch (Exception e) {
                LOG.error("[通知栏模式] 自动翻页时出错: " + e.getMessage(), e);
                // 出错时不停止自动阅读，让它继续尝试
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        LOG.info("[通知栏模式] 自动阅读已启动，间隔: " + intervalSeconds + " 秒");
    }

    @Override
    public void stopAutoRead() {
        LOG.info("[通知栏模式] 停止自动翻页");

        // 取消定时任务
        if (autoReadTask != null && !autoReadTask.isDone()) {
            try {
                autoReadTask.cancel(false); // 尝试优雅地取消，不中断正在执行的任务
                LOG.info("[通知栏模式] 自动阅读任务已取消");
            } catch (Exception e) {
                LOG.error("[通知栏模式] 取消自动阅读任务时出错: " + e.getMessage(), e);
            } finally {
                autoReadTask = null;
            }
        }

        // 关闭执行器
        if (autoReadExecutor != null && !autoReadExecutor.isShutdown()) {
            try {
                autoReadExecutor.shutdown(); // 优雅关闭，允许已提交的任务完成
                // 等待一小段时间让任务完成
                if (!autoReadExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 如果超时，强制关闭
                    autoReadExecutor.shutdownNow();
                    LOG.info("[通知栏模式] 自动阅读执行器已强制关闭");
                } else {
                    LOG.info("[通知栏模式] 自动阅读执行器已优雅关闭");
                }
            } catch (InterruptedException e) {
                // 如果等待被中断，强制关闭
                autoReadExecutor.shutdownNow();
                LOG.warn("[通知栏模式] 关闭自动阅读执行器时被中断，已强制关闭");
                // 恢复中断状态
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.error("[通知栏模式] 关闭自动阅读执行器时出错: " + e.getMessage(), e);
                // 确保执行器被关闭
                try {
                    autoReadExecutor.shutdownNow();
                } catch (Exception ignored) {
                    // 忽略进一步的异常
                }
            } finally {
                autoReadExecutor = null;
            }
        }

        LOG.info("[通知栏模式] 自动阅读已停止");
    }

    /**
     * 将内容分页
     * 严格按照pageSize进行分页，同时尽量在段落或句子结束处分页，提供更好的阅读体验
     * 每页字符数严格等于pageSize（除了最后一页可能少于pageSize）
     *
     * @param content 内容
     * @param pageSize 每页字符数
     * @return 分页后的内容列表
     */
    private List<String> paginateContent(String content, int pageSize) {
        List<String> pages = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return pages;
        }

        // 确保 pageSize 大于 0
        int effectivePageSize = Math.max(pageSize, 10);

        LOG.info("[分页] 开始分页，内容长度: " + content.length() + ", 目标页面大小: " + effectivePageSize);

        // 首先按段落分割内容
        String[] paragraphs = content.split("\\n+");
        LOG.info("[分页] 分割后的段落数量: " + paragraphs.length);
        StringBuilder currentPage = new StringBuilder();

        for (String paragraph : paragraphs) {
            // 如果段落本身就超过页面大小，需要进一步分割
            if (paragraph.length() > effectivePageSize) {
                // 如果当前页已有内容，先添加当前页
                if (currentPage.length() > 0) {
                    String pageContent = currentPage.toString();
                    pages.add(pageContent);
                    LOG.info("[分页] 添加页面 #" + pages.size() + ", 字符数: " + pageContent.length());
                    currentPage = new StringBuilder();
                }

                // 分割长段落
                int startIndex = 0;
                int contentLength = paragraph.length();

                while (startIndex < contentLength) {
                    int endIndex = Math.min(startIndex + effectivePageSize, contentLength);

                    // 尝试在句子结束处分页，但不超过pageSize
                    if (endIndex < contentLength) {
                        // 中文句号、问号、感叹号、英文句号、问号、感叹号
                        char[] breakChars = {'\u3002', '\uff1f', '\uff01', '.', '?', '!'};
                        boolean foundBreak = false;

                        // 向前查找句子结束符号，但不超过pageSize
                        for (int i = endIndex - 1; i >= startIndex; i--) {
                            char currentChar = paragraph.charAt(i);
                            for (char breakChar : breakChars) {
                                if (currentChar == breakChar) {
                                    endIndex = i + 1; // 包含断句符号
                                    foundBreak = true;
                                    break;
                                }
                            }
                            if (foundBreak) break;

                            // 如果搜索了超过10个字符还没找到断句点，就放弃，使用严格的pageSize
                            if (endIndex - i > 10) {
                                break;
                            }
                        }
                    }

                    // 提取当前页的内容
                    String pageContent = paragraph.substring(startIndex, endIndex);
                    pages.add(pageContent);
                    LOG.info("[分页] 添加长段落页面 #" + pages.size() + ", 字符数: " + pageContent.length());

                    // 移动到下一页的起始位置
                    startIndex = endIndex;
                }
            }
            // 如果添加当前段落会超过页面大小，先添加当前页，然后开始新页
            else if (currentPage.length() + paragraph.length() + (currentPage.length() > 0 ? 1 : 0) > effectivePageSize) {
                String pageContent = currentPage.toString();
                pages.add(pageContent);
                LOG.info("[分页] 添加页面 #" + pages.size() + ", 字符数: " + pageContent.length());
                currentPage = new StringBuilder(paragraph);
            }
            // 否则，将段落添加到当前页
            else {
                // 如果当前页不为空，添加换行符
                if (currentPage.length() > 0) {
                    currentPage.append("\n");
                }
                currentPage.append(paragraph);
            }
        }

        // 添加最后一页（如果有内容）
        if (currentPage.length() > 0) {
            String pageContent = currentPage.toString();
            pages.add(pageContent);
            LOG.info("[分页] 添加最后一页 #" + pages.size() + ", 字符数: " + pageContent.length());
        }

        // 特殊情况处理：如果只有一页且内容长度超过页面大小，强制分页
        if (pages.size() == 1 && pages.get(0).length() > effectivePageSize) {
            LOG.info("[分页] 检测到只有一页但内容长度超过页面大小，强制分页");
            String singlePage = pages.get(0);
            pages.clear();

            // 强制按照目标页面大小分页
            for (int i = 0; i < singlePage.length(); i += effectivePageSize) {
                int endIndex = Math.min(i + effectivePageSize, singlePage.length());
                String pageContent = singlePage.substring(i, endIndex);
                pages.add(pageContent);
                LOG.info("[分页] 强制分页后添加页面 #" + pages.size() + ", 字符数: " + pageContent.length());
            }
        }

        // 最后一次检查：确保所有页面（除了最后一页）都严格等于pageSize
        List<String> finalPages = new ArrayList<>();
        StringBuilder tempPage = new StringBuilder();

        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);

            // 如果是最后一页且长度小于pageSize，直接添加
            if (i == pages.size() - 1 && page.length() <= effectivePageSize) {
                finalPages.add(page);
                continue;
            }

            // 处理每个字符
            for (int j = 0; j < page.length(); j++) {
                tempPage.append(page.charAt(j));

                // 当达到pageSize或是最后一页的最后一个字符时，添加页面
                if (tempPage.length() == effectivePageSize || (i == pages.size() - 1 && j == page.length() - 1)) {
                    finalPages.add(tempPage.toString());
                    tempPage = new StringBuilder();
                }
            }
        }

        // 如果tempPage还有内容（不足pageSize的最后一页），添加它
        if (tempPage.length() > 0) {
            finalPages.add(tempPage.toString());
        }

        // 记录每页的字符数，用于调试
        if (!finalPages.isEmpty()) {
            StringBuilder pageSizeInfo = new StringBuilder("[分页] 各页字符数: ");
            for (int i = 0; i < finalPages.size(); i++) {
                if (i > 0) pageSizeInfo.append(", ");
                pageSizeInfo.append("#").append(i + 1).append(": ").append(finalPages.get(i).length());
            }
            LOG.info(pageSizeInfo.toString());
        }

        LOG.info("[分页] 分页完成，总页数: " + finalPages.size());
        return finalPages;
    }

    /**
     * 导航到指定章节的最后一页
     * 类似于 navigateChapter，但是跳转到目标章节的最后一页，而不是第一页
     *
     * @param project 当前项目
     * @param direction 方向，-1 表示上一章，1 表示下一章
     */
    private void navigateChapterToLastPage(@NotNull Project project, int direction) {
        ensureServicesInitialized();
        LOG.info("[通知栏模式] 导航到章节的最后一页，方向: " + direction);

        if (currentBook == null || currentChapterId == null) {
            LOG.warn("[通知栏模式] 当前没有正在阅读的内容");
            showInfo("导航", "当前没有正在阅读的内容");
            return;
        }

        // 显示加载状态通知
        showLoadingNotification(project, "正在加载章节...");

        // 首先尝试使用Book中的cachedChapters
        List<NovelParser.Chapter> cachedChapters = currentBook.getCachedChapters();
        if (cachedChapters != null && !cachedChapters.isEmpty()) {
            LOG.info("使用Book中的cachedChapters导航到最后一页，章节数量: " + cachedChapters.size());
            // 在UI线程上处理导航逻辑
            reactiveSchedulers.runOnUI(() -> processChapterNavigationToLastPageWithCachedChapters(project, cachedChapters, direction));
            return;
        }

        // 如果cachedChapters为空，则使用异步方式获取章节列表
        LOG.info("Book中的cachedChapters为空，使用bookService.getChaptersSync获取章节列表");
        Mono.fromCallable(() -> bookService.getChaptersSync(currentBook.getId()))
            .subscribeOn(reactiveSchedulers.io()) // 在IO线程上执行
            .timeout(java.time.Duration.ofSeconds(30)) // 设置超时
            .doOnError(e -> {
                LOG.error("[通知栏模式] 获取章节列表时出错: " + e.getMessage(), e);
                reactiveSchedulers.runOnUI(() -> showError("导航失败", "获取章节列表时出错: " + e.getMessage()));
            })
            .subscribe(chapters -> {
                // 在获取到章节列表后，在UI线程上处理导航逻辑
                reactiveSchedulers.runOnUI(() -> processChapterNavigationToLastPage(project, chapters, direction));
            });
    }

    /**
     * 处理章节导航到最后一页的逻辑
     * 在UI线程上执行
     *
     * @param project 当前项目
     * @param chapters 章节列表
     * @param direction 导航方向
     */
    private void processChapterNavigationToLastPage(@NotNull Project project,
                                                  @Nullable List<ChapterService.EnhancedChapter> chapters,
                                                  int direction) {
        if (chapters == null || chapters.isEmpty()) {
            LOG.warn("[通知栏模式] 章节列表为空");
            showInfo("导航", "章节列表为空");
            return;
        }

        // 查找当前章节的索引
        int currentIndex = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).url().equals(currentChapterId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            LOG.warn("[通知栏模式] 当前章节在列表中未找到: " + currentChapterId);
            showError("导航失败", "当前章节在列表中未找到");
            return;
        }

        // 计算目标章节的索引
        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= chapters.size()) {
            String message = direction < 0 ? "已经是第一章了" : "已经是最后一章了";
            LOG.warn("[通知栏模式] " + message);
            showInfo("导航", message);
            return;
        }

        // 获取目标章节的信息
        ChapterService.EnhancedChapter targetChapter = chapters.get(targetIndex);
        String targetChapterId = targetChapter.url();
        String targetChapterTitle = targetChapter.title();
        String targetChapterContent = targetChapter.getContent();

        if (targetChapterContent == null || targetChapterContent.isEmpty()) {
            LOG.warn("[通知栏模式] 目标章节内容为空: " + targetChapterId);
            showError("导航失败", "目标章节内容为空");
            return;
        }

        // 更新当前书籍、章节信息
        this.currentChapterId = targetChapterId;
        this.currentChapterTitle = targetChapterTitle;

        // 分页并设置为最后一页
        setCurrentChapterContent(targetChapterContent);
        if (currentPages.isEmpty()) {
            LOG.warn("[通知栏模式] 分页后内容为空，无法显示通知: " + targetChapterId);
            showError("显示章节失败", "分页后内容为空");
            return;
        }

        // 设置为最后一页
        this.currentPageIndex = currentPages.size() - 1;

        // 构建通知标题和内容
        String title = currentBook.getTitle() + " - " + targetChapterTitle;
        String pageContent = currentPages.get(currentPageIndex);
        String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
        String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

        // 显示通知
        showCurrentPageInternal(project, title, notificationContent);

        // 保存阅读进度
        // 注意：不使用 bookService.saveReadingProgress 方法，因为它会将 currentPageIndex 加1
        // 而我们已经从数据库中恢复的页码是1基索引，转换为 currentPageIndex 时减了1
        // 如果再使用 bookService.saveReadingProgress 方法，它会再次将 currentPageIndex 加1，导致页码始终是1
        SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
        if (readingProgressRepository != null) {
            // 使用带页码参数的重载方法，position设为0，直接使用currentPageIndex + 1作为页码
            readingProgressRepository.updateProgress(currentBook, targetChapterId, targetChapterTitle, 0, currentPageIndex + 1);
            LOG.info(String.format("[页码调试] 直接保存页码: %d", currentPageIndex + 1));
        } else {
            LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
            bookService.saveReadingProgress(currentBook, targetChapterId, targetChapterTitle, currentPageIndex);
        }

        // 记录日志
        LOG.info("[通知栏模式] 导航到章节的最后一页: " + targetChapterId);
    }

    /**
     * 使用Book中的cachedChapters处理章节导航到最后一页的逻辑
     * 在UI线程上执行
     *
     * @param project 当前项目
     * @param cachedChapters 缓存的章节列表
     * @param direction 导航方向
     */
    private void processChapterNavigationToLastPageWithCachedChapters(@NotNull Project project,
                                                                    @Nullable List<Chapter> cachedChapters,
                                                                    int direction) {
        if (cachedChapters == null || cachedChapters.isEmpty()) {
            LOG.warn("[通知栏模式] 缓存的章节列表为空");
            showInfo("导航", "章节列表为空");
            return;
        }

        // 查找当前章节的索引
        int currentIndex = -1;
        for (int i = 0; i < cachedChapters.size(); i++) {
            if (cachedChapters.get(i).url().equals(currentChapterId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            LOG.warn("[通知栏模式] 当前章节在缓存列表中未找到: " + currentChapterId);
            showError("导航失败", "当前章节在列表中未找到");
            return;
        }

        // 计算目标章节的索引
        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= cachedChapters.size()) {
            String message = direction < 0 ? "已经是第一章了" : "已经是最后一章了";
            LOG.warn("[通知栏模式] " + message);
            showInfo("导航", message);
            return;
        }

        // 获取目标章节信息
        Chapter targetChapter = cachedChapters.get(targetIndex);
        String targetChapterId = targetChapter.url();
        String targetChapterTitle = targetChapter.title();

        // 显示加载状态通知
        showLoadingNotification(project, "正在加载章节内容...");

        // 使用异步方式获取章节内容，避免阻塞UI线程
        Mono.fromCallable(() -> {
            // 使用NovelParser获取章节内容
            if (currentBook.getParser() != null) {
                return currentBook.getParser().getChapterContent(targetChapterId, currentBook);
            }
            return null;
        })
        .subscribeOn(reactiveSchedulers.io()) // 在IO线程上执行
        .timeout(java.time.Duration.ofSeconds(30)) // 设置超时
        .doOnError(e -> {
            LOG.error("[通知栏模式] 获取章节内容时出错: " + e.getMessage(), e);
            reactiveSchedulers.runOnUI(() -> showError("导航失败", "获取章节内容时出错: " + e.getMessage()));
        })
        .subscribe(content -> {
            // 在获取到章节内容后，在UI线程上处理显示逻辑
            reactiveSchedulers.runOnUI(() -> {
                if (content == null || content.isEmpty()) {
                    LOG.warn("[通知栏模式] 目标章节内容为空: " + targetChapterId);
                    showError("导航失败", "目标章节内容为空");
                    return;
                }

                // 更新当前章节信息
                currentChapterId = targetChapterId;
                currentChapterTitle = targetChapterTitle;

                // 分页并设置为最后一页
                setCurrentChapterContent(content);
                if (currentPages.isEmpty()) {
                    LOG.warn("[通知栏模式] 分页后内容为空，无法显示通知: " + targetChapterId);
                    showError("显示章节失败", "分页后内容为空");
                    return;
                }

                // 设置为最后一页
                currentPageIndex = currentPages.size() - 1;

                // 构建通知标题和内容
                String title = currentBook.getTitle() + " - " + targetChapterTitle;
                String pageContent = currentPages.get(currentPageIndex);
                String progressText = notificationSettings != null && notificationSettings.isShowReadingProgress() ?
                        "进度: 第 " + (currentPageIndex + 1) + " 页，共 " + currentPages.size() + " 页" : "";
                String notificationContent = pageContent + (progressText.isEmpty() ? "" : "\n\n" + progressText);

                // 显示通知
                showCurrentPageInternal(project, title, notificationContent);

                // 保存阅读进度
                SqliteReadingProgressRepository readingProgressRepository = ApplicationManager.getApplication().getService(SqliteReadingProgressRepository.class);
                if (readingProgressRepository != null) {
                    readingProgressRepository.updateProgress(currentBook, targetChapterId, targetChapterTitle, 0, currentPageIndex + 1);
                    LOG.info(String.format("[页码调试] 直接保存页码: %d", currentPageIndex + 1));
                } else {
                    LOG.warn("[页码调试] 无法获取 SqliteReadingProgressRepository 实例，使用 bookService.saveReadingProgress 方法");
                    bookService.saveReadingProgress(currentBook, targetChapterId, targetChapterTitle, currentPageIndex);
                }

                // 记录日志
                LOG.info("[通知栏模式] 使用cachedChapters导航到章节的最后一页: " + targetChapterId);
            });
        });
    }

    /**
     * 清理HTML标签，保留换行符和基本格式
     * @param content 包含HTML标签的内容
     * @return 清理后的纯文本内容
     */
    private String cleanHtmlTags(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // 记录原始内容长度
        int originalLength = content.length();
        LOG.debug("[通知栏模式] 清理HTML标签前内容长度: " + originalLength);

        // 1. 替换常见的HTML实体
        String result = content
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");

        // 2. 使用正则表达式移除所有HTML标签
        result = result.replaceAll("<[^>]*>", "");

        // 3. 确保段落之间有适当的换行
        result = result.replaceAll("\\n{3,}", "\n\n"); // 将3个或更多换行符替换为2个

        // 记录清理后内容长度
        int newLength = result.length();
        LOG.debug("[通知栏模式] 清理HTML标签后内容长度: " + newLength + ", 减少了: " + (originalLength - newLength) + " 个字符");

        return result;
    }

    /**
     * 关闭当前通知
     * 注意：只关闭通知，不重置其他状态（如 currentBook、currentChapterId 等）
     */
    private void closeCurrentNotificationInternal() {
        LOG.info("[通知栏模式] 关闭当前通知，当前页索引: " + currentPageIndex + ", 总页数: " + currentPages.size());

        Notification oldNotification = currentNotificationRef.getAndSet(null);
        if (oldNotification != null && !oldNotification.isExpired()) {
            try {
                oldNotification.expire();
                LOG.info("[通知栏模式] 成功关闭通知");
            } catch (Exception e) {
                LOG.error("[通知栏模式] 关闭通知时出错: " + e.getMessage(), e);
            }
        } else {
            LOG.info("[通知栏模式] 没有通知需要关闭或通知已过期");
        }

        // 不重置其他状态，以便在显示新通知时保持阅读进度
        // currentBook = null;
        // currentChapterId = null;
        // currentChapterTitle = null;
        // currentPages.clear();
        // currentPageIndex = 0;
    }
}