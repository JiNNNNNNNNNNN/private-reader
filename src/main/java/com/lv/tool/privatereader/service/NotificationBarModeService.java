package com.lv.tool.privatereader.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookProgressData;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.lv.tool.privatereader.storage.SettingsStorage;
import com.lv.tool.privatereader.ui.actions.NextChapterAction;
import com.lv.tool.privatereader.ui.actions.NextPageAction;
import com.lv.tool.privatereader.ui.actions.PrevChapterAction;
import com.lv.tool.privatereader.ui.actions.PrevPageAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.Alarm;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 通知栏模式服务，负责管理通知栏阅读模式的状态和行为。
 * 实现为应用级服务，确保在整个IDE中共享同一个实例。
 *
 * 注意：在保存阅读进度时，使用 readingProgressRepository.updateProgress(book, chapterId, chapterTitle, position, page)
 * 方法的重载版本，其中 position 设为 0，page 参数直接使用 currentPageNumber。
 * 这是因为 currentPageNumber 是 1 基索引的页码，而不是滚动位置。
 * 如果使用 updateProgress(book, chapterId, chapterTitle, position) 方法，它会将 position 参数作为滚动位置，
 * 而使用 book.getLastReadPageOrDefault(1) 作为页码，这会导致页码始终是 1 或者是 book 对象中已有的页码。
 */
@Service(Service.Level.APP)
@Singleton
public class NotificationBarModeService implements Disposable {

    private static final Logger LOG = Logger.getInstance(NotificationBarModeService.class);

    private final ReaderModeSettings readerModeSettings;
    private final NotificationService notificationService;
    private final ChapterService chapterService;
    private final ReadingProgressRepository readingProgressRepository;
    private final NotificationReaderSettings notificationReaderSettings;
    private final Project project; // Assuming project context is needed for actions/services

    private Alarm refreshAlarm;
    private String currentBookId;
    private String currentChapterId;
    private int currentPageNumber;

    /**
     * 获取 NotificationBarModeService 实例
     * @return NotificationBarModeService 实例
     */
    public static NotificationBarModeService getInstance() {
        return ApplicationManager.getApplication().getService(NotificationBarModeService.class);
    }

    @Inject
    public NotificationBarModeService(
            Project project,
            ReaderModeSettings readerModeSettings,
            NotificationService notificationService,
            ChapterService chapterService,
            ReadingProgressRepository readingProgressRepository,
            NotificationReaderSettings notificationReaderSettings) {
        LOG.info("NotificationBarModeService 构造函数被调用");

        // 如果 project 为 null，尝试获取第一个打开的项目
        if (project == null) {
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                project = openProjects[0];
                LOG.info("使用第一个打开的项目: " + project.getName());
            } else {
                LOG.warn("没有打开的项目，NotificationBarModeService 可能无法正常工作");
            }
        }

        this.project = project;
        this.readerModeSettings = readerModeSettings;
        this.notificationService = notificationService;
        this.chapterService = chapterService;
        this.readingProgressRepository = readingProgressRepository;
        this.notificationReaderSettings = notificationReaderSettings;

        LOG.info("NotificationBarModeService 初始化完成");
    }

    /**
     * Activates the notification bar reading mode.
     * @param bookId The ID of the book to read.
     * @param chapterId The ID of the chapter to read.
     * @param pageNumber The page number to start reading from.
     */
    public void activateNotificationBarMode(String bookId, String chapterId, int pageNumber) {
        // 1. Update ReaderModeSettings to notification bar mode
        readerModeSettings.setCurrentMode(ReaderModeSettings.Mode.NOTIFICATION_BAR);

        this.currentBookId = bookId;
        this.currentChapterId = chapterId;
        this.currentPageNumber = pageNumber;

        // 2. Get current chapter content and title
        String chapterContent = chapterService.getChapterContent(bookId, chapterId);
        String chapterTitle = chapterService.getChapterTitle(bookId, chapterId);

        // 3. Calculate total pages and get current page content (NotificationService handles pagination)
        // NotificationService needs methods to handle pagination internally based on content
        notificationService.setCurrentChapterContent(chapterContent); // Assuming NotificationService can store content
        int totalPages = notificationService.calculateTotalPages(chapterContent);

        // 确保页码在有效范围内
        if (pageNumber <= 0) {
            pageNumber = 1; // 如果页码无效，默认为第一页
        } else if (pageNumber > totalPages) {
            pageNumber = totalPages; // 如果页码超出范围，使用最后一页
        }

        this.currentPageNumber = pageNumber; // 更新为有效的页码
        String currentPageContent = notificationService.getPageContent(chapterContent, pageNumber);

        // 4. Build notification content (including title, progress, content, and navigation controls)
        String notificationTitle = "正在阅读: " + (notificationReaderSettings.isShowChapterTitle() ? chapterTitle : "");
        String progressText = notificationReaderSettings.isShowReadingProgress() ?
                "进度: 第 " + pageNumber + " 页，共 " + totalPages + " 页" : "";
        String notificationContent = currentPageContent + "\n\n" + progressText;

        // 5. Use NotificationService to display notification with actions
        // NotificationService needs a method to show content with actions
        notificationService.showChapterContent(project, bookId, chapterId, pageNumber, notificationTitle, notificationContent);

        // 6. Start timer to refresh notification based on settings interval
        startRefreshTimer();

        // 7. 记录日志
        System.out.println("[通知栏模式] 激活通知栏模式: 书籍=" + bookId + ", 章节=" + chapterId + ", 页码=" + pageNumber);

        // Update Memory Bank
        updateActiveContext("Activated Notification Bar Mode for book: " + bookId + ", chapter: " + chapterId + ", page: " + pageNumber);
        updateProgress("Started implementing Notification Bar Mode activation.");
    }

    /**
     * Deactivates the notification bar reading mode.
     */
    public void deactivateNotificationBarMode() {
        // 1. Stop refresh timer
        stopRefreshTimer();

        // 2. Close all notifications
        notificationService.closeAllNotifications();

        // 3. Update ReaderModeSettings to default mode (or previous mode)
        readerModeSettings.setCurrentMode(ReaderModeSettings.Mode.DEFAULT); // Or handle previous mode

        // Clear current book/chapter/page
        this.currentBookId = null;
        this.currentChapterId = null;
        this.currentPageNumber = 0;

        // Update Memory Bank
        updateActiveContext("Deactivated Notification Bar Mode.");
        updateProgress("Started implementing Notification Bar Mode deactivation.");
    }

    /**
     * Handles the next page action triggered from the notification.
     */
    public void handleNextPageAction() {
        // 1. Call NotificationService's next page method
        // NotificationService needs a method to navigate to the next page and update the notification
        notificationService.showNextPage(project);

        // 2. Save current reading progress
        // NotificationService should update its internal state for current page
        this.currentPageNumber = notificationService.getCurrentPage();
        if (currentBookId != null && currentChapterId != null) {
            // 使用 BookService 获取书籍对象
            BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
            if (bookService != null) {
                Book book = bookService.getBookById(currentBookId).block(); // 阻塞操作
                if (book != null) {
                    // 使用 updateProgress 方法更新阅读进度
                    // 注意：使用带页码参数的重载方法，position设为0，直接使用currentPageNumber作为页码
                    readingProgressRepository.updateProgress(book, currentChapterId, null, 0, currentPageNumber);
                }
            }
        }

        // Update Memory Bank
        updateActiveContext("Handled Next Page action in Notification Bar Mode.");
        updateProgress("Started implementing Next Page action handling for Notification Bar Mode.");
    }

    /**
     * Handles the previous page action triggered from the notification.
     */
    public void handlePrevPageAction() {
        // 1. Call NotificationService's previous page method
        // NotificationService needs a method to navigate to the previous page and update the notification
        notificationService.showPrevPage(project);

        // 2. Save current reading progress
        this.currentPageNumber = notificationService.getCurrentPage();
        if (currentBookId != null && currentChapterId != null) {
            // 使用 BookService 获取书籍对象
            BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
            if (bookService != null) {
                Book book = bookService.getBookById(currentBookId).block(); // 阻塞操作
                if (book != null) {
                    // 使用 updateProgress 方法更新阅读进度
                    // 注意：使用带页码参数的重载方法，position设为0，直接使用currentPageNumber作为页码
                    readingProgressRepository.updateProgress(book, currentChapterId, null, 0, currentPageNumber);
                }
            }
        }

        // Update Memory Bank
        updateActiveContext("Handled Previous Page action in Notification Bar Mode.");
        updateProgress("Started implementing Previous Page action handling for Notification Bar Mode.");
    }

    /**
     * Handles the next chapter action triggered from the notification.
     */
    public void handleNextChapterAction() {
        // 1. Call NotificationService's navigate chapter method
        // NotificationService needs a method to navigate to the next chapter and update the notification
        notificationService.navigateChapter(project, 1); // 1 means next chapter

        // 2. Save current reading progress (start page of the new chapter)
        // NotificationService should update its internal state for current book/chapter/page
        this.currentBookId = notificationService.getCurrentBookId();
        this.currentChapterId = notificationService.getCurrentChapterId();
        this.currentPageNumber = notificationService.getCurrentPage(); // Usually the first page of the new chapter
        if (currentBookId != null && currentChapterId != null) {
            // 使用 BookService 获取书籍对象
            BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
            if (bookService != null) {
                Book book = bookService.getBookById(currentBookId).block(); // 阻塞操作
                if (book != null) {
                    // 使用 updateProgress 方法更新阅读进度
                    // 注意：使用带页码参数的重载方法，position设为0，直接使用currentPageNumber作为页码
                    readingProgressRepository.updateProgress(book, currentChapterId, null, 0, currentPageNumber);
                }
            }
        }

        // Update Memory Bank
        updateActiveContext("Handled Next Chapter action in Notification Bar Mode.");
        updateProgress("Started implementing Next Chapter action handling for Notification Bar Mode.");
    }

    /**
     * Handles the previous chapter action triggered from the notification.
     */
    public void handlePrevChapterAction() {
        // 1. Call NotificationService's navigate chapter method
        // NotificationService needs a method to navigate to the previous chapter and update the notification
        notificationService.navigateChapter(project, -1); // -1 means previous chapter

        // 2. Save current reading progress (start page of the new chapter)
        // NotificationService should update its internal state for current book/chapter/page
        this.currentBookId = notificationService.getCurrentBookId();
        this.currentChapterId = notificationService.getCurrentChapterId();
        this.currentPageNumber = notificationService.getCurrentPage(); // Usually the first page of the new chapter
        if (currentBookId != null && currentChapterId != null) {
            // 使用 BookService 获取书籍对象
            BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
            if (bookService != null) {
                Book book = bookService.getBookById(currentBookId).block(); // 阻塞操作
                if (book != null) {
                    // 使用 updateProgress 方法更新阅读进度
                    // 注意：使用带页码参数的重载方法，position设为0，直接使用currentPageNumber作为页码
                    readingProgressRepository.updateProgress(book, currentChapterId, null, 0, currentPageNumber);
                }
            }
        }

        // Update Memory Bank
        updateActiveContext("Handled Previous Chapter action in Notification Bar Mode.");
        updateProgress("Started implementing Previous Chapter action handling for Notification Bar Mode.");
    }

    /**
     * Initializes notification bar mode settings and potentially activates the mode on startup.
     */
    public void initializeNotificationBarModeSettings() {
        LOG.info("初始化通知栏模式设置");

        // Settings are typically loaded by the settings system itself,
        // but we can check if the mode should be active on startup.

        // If settings enable notification bar mode, try to restore reading state and activate mode
        if (notificationReaderSettings.isEnabled()) {
            LOG.info("通知栏模式已启用，尝试恢复上次的阅读位置");

            // 使用 getLastReadProgressData 方法获取最近阅读的书籍进度
            Optional<BookProgressData> lastReadBookOpt = readingProgressRepository.getLastReadProgressData();
            if (lastReadBookOpt.isPresent()) {
                BookProgressData lastReadBook = lastReadBookOpt.get();
                LOG.info("找到上次阅读的书籍: " + lastReadBook.bookId() +
                         ", 章节: " + lastReadBook.lastReadChapterId() +
                         ", 页码: " + lastReadBook.lastReadPage());

                // Need to ensure book and chapter still exist before activating
                // For now, assuming they do. Add checks if necessary.
                activateNotificationBarMode(
                    lastReadBook.bookId(),
                    lastReadBook.lastReadChapterId(),
                    lastReadBook.lastReadPage()
                );
            } else {
                LOG.info("没有找到上次阅读的书籍记录");
            }
        } else {
            LOG.info("通知栏模式未启用，跳过恢复阅读位置");
        }

        // Update Memory Bank
        updateActiveContext("Initialized Notification Bar Mode settings and checked for startup activation.");
        updateProgress("Started implementing Notification Bar Mode initialization.");

        LOG.info("通知栏模式设置初始化完成");
    }

    private void startRefreshTimer() {
        stopRefreshTimer(); // Stop any existing timer

        long intervalMillis = TimeUnit.SECONDS.toMillis(notificationReaderSettings.getUpdateInterval());
        if (intervalMillis > 0) {
            refreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
            refreshAlarm.addRequest(() -> {
                if (readerModeSettings.getCurrentMode() == ReaderModeSettings.Mode.NOTIFICATION_BAR &&
                    currentBookId != null && currentChapterId != null) {
                    // Refresh current page content and progress
                    String chapterContent = chapterService.getChapterContent(currentBookId, currentChapterId);
                    // NotificationService needs to know the current page internally or take it as a parameter
                    int totalPages = notificationService.calculateTotalPages(chapterContent);
                    String currentPageContent = notificationService.getPageContent(chapterContent, currentPageNumber);

                    String progressText = notificationReaderSettings.isShowReadingProgress() ?
                            "进度: 第 " + currentPageNumber + " 页，共 " + totalPages + " 页" : "";
                    String notificationContent = currentPageContent + "\n\n" + progressText;

                    // NotificationService needs a method to update the existing notification
                    notificationService.updateNotificationContent(project, notificationContent);
                }
                startRefreshTimer(); // Schedule the next refresh
            }, intervalMillis);
        }

        // Update Memory Bank
        updateActiveContext("Started notification refresh timer with interval: " + notificationReaderSettings.getUpdateInterval() + " seconds.");
        updateProgress("Started implementing notification refresh timer.");
    }

    private void stopRefreshTimer() {
        if (refreshAlarm != null) {
            refreshAlarm.cancelAllRequests();
            refreshAlarm = null;
        }

        // Update Memory Bank
        updateActiveContext("Stopped notification refresh timer.");
        updateProgress("Started implementing stopping notification refresh timer.");
    }

    @Override
    public void dispose() {
        stopRefreshTimer();
        // Any other cleanup
    }

    // Helper methods to update Memory Bank - these will be replaced by actual tool calls
    private void updateActiveContext(String message) {
        System.out.println("[MEMORY BANK UPDATE] activeContext.md: " + message);
        // TODO: Replace with actual append_to_file tool call
    }

    private void updateProgress(String message) {
        System.out.println("[MEMORY BANK UPDATE] progress.md: " + message);
        // TODO: Replace with actual append_to_file tool call
    }

    private void updateDecisionLog(String decision, String rationale, String details) {
        System.out.println("[MEMORY BANK UPDATE] decisionLog.md: " + decision);
        // TODO: Replace with actual append_to_file tool call
    }
}