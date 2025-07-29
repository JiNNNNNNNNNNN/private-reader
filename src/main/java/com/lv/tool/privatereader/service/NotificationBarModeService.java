package com.lv.tool.privatereader.service;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookProgressData;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import com.lv.tool.privatereader.settings.NotificationReaderSettingsListener;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import reactor.core.publisher.Mono;
import com.intellij.openapi.application.ModalityState;

import java.util.Optional;

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
public class NotificationBarModeService implements Disposable, NotificationReaderSettingsListener {

    private static final Logger LOG = Logger.getInstance(NotificationBarModeService.class);

    private final ReaderModeSettings readerModeSettings;
    private final NotificationService notificationService;
    private final ChapterService chapterService;
    private final BookService bookService;
    private final ReadingProgressRepository readingProgressRepository;
    private final NotificationReaderSettings notificationReaderSettings;
    private Project project; // Made non-final to allow initialization in constructor body

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

    public NotificationBarModeService() {
        LOG.info("NotificationBarModeService 构造函数被调用");

        this.readerModeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        this.notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
        this.chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        this.readingProgressRepository = ApplicationManager.getApplication().getService(ReadingProgressRepository.class);
        this.notificationReaderSettings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);

        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length > 0) {
            this.project = openProjects[0];
            LOG.info("使用第一个打开的项目: " + this.project.getName());
        } else {
            this.project = null;
            LOG.warn("没有打开的项目，NotificationBarModeService 可能无法在构造时确定默认项目");
        }

        // Subscribe to settings changes
        ApplicationManager.getApplication().getMessageBus().connect(this) // 'this' as Disposable
            .subscribe(NotificationReaderSettingsListener.TOPIC, this);
        LOG.info("NotificationBarModeService subscribed to NotificationReaderSettingsListener.");

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

        // 获取当前打开的项目
        Project currentProject = this.project;
        if (currentProject == null) {
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                currentProject = openProjects[0];
            }
        }
        
        if (currentProject == null) {
            LOG.error("无法激活通知栏模式：没有打开的项目");
            return;
        }
        
        // 显示加载状态通知
        notificationService.showLoadingNotification(currentProject, "正在加载章节内容...");
        
        // 异步获取章节内容和标题
        final Project finalProject = currentProject;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                // Since we are on a pooled thread, we can block to get the book object.
                Book book = bookService.getBookById(bookId).block();
                if (book == null) {
                    throw new IllegalStateException("Book not found: " + bookId);
                }

                // Get content and title in parallel
                Mono<String> contentMono = chapterService.getChapterContent(book, chapterId);
                Mono<String> titleMono = chapterService.getChapterTitle(bookId, chapterId);

                // Zip them together
                Mono.zip(contentMono, titleMono)
                    .subscribe(
                        tuple -> {
                            String chapterContent = tuple.getT1();
                            String chapterTitle = tuple.getT2();
                            
                            ApplicationManager.getApplication().invokeLater(() -> {
                                notificationService.setCurrentChapterContent(chapterContent);
                                int totalPages = notificationService.calculateTotalPages(chapterContent);
                                
                                int validPageNumber = Math.max(1, Math.min(pageNumber, totalPages > 0 ? totalPages : 1));
                                this.currentPageNumber = validPageNumber;
                                
                                notificationService.showChapterContent(finalProject, bookId, chapterId, validPageNumber, chapterTitle, chapterContent);
                            }, ModalityState.defaultModalityState());
                        },
                        error -> {
                            LOG.error("Failed to activate notification mode", error);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                notificationService.showError("Activation Failed", "Could not load chapter: " + error.getMessage());
                            }, ModalityState.defaultModalityState());
                        }
                    );
            } catch (Exception e) {
                LOG.error("Top-level error activating notification mode", e);
                ApplicationManager.getApplication().invokeLater(() -> {
                    notificationService.showError("Activation Error", "A critical error occurred: " + e.getMessage());
                }, ModalityState.defaultModalityState());
            }
        });
    }

    /**
     * Deactivates the notification bar reading mode.
     */
    public void deactivateNotificationBarMode() {
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

    @Override
    public void dispose() {
        // Connection to MessageBus is automatically disposed as 'this' was passed to connect()
        // Any other cleanup
        LOG.info("NotificationBarModeService disposed.");
    }

    // Listener method for settings changes
    @Override
    public void settingsChanged() {
        LOG.info("NotificationReaderSettings changed event received by NotificationBarModeService.");
        // Check if currently in notification bar mode and if essential data is present
        if (project != null && // Ensure project context is available
            readerModeSettings.getCurrentMode() == ReaderModeSettings.Mode.NOTIFICATION_BAR &&
            currentBookId != null && !currentBookId.isEmpty() &&
            currentChapterId != null && !currentChapterId.isEmpty()) {
            
            LOG.info("Currently in notification bar mode with an active book/chapter. Triggering refresh due to settings change.");
            refreshNotificationDisplay();
        } else {
            LOG.info("Not in notification bar mode, or no current book/chapter/project, or project is null. Skipping refresh on settings change.");
        }
    }

    private void refreshNotificationDisplay() {
        if (project == null) {
            LOG.warn("Cannot refresh notification display: project is null at the beginning of refreshNotificationDisplay.");
            // Attempt to re-acquire project context if it was lost (e.g. original project closed)
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                this.project = openProjects[0]; // Use the first available open project
                LOG.info("Re-acquired project context for refresh: " + this.project.getName());
            } else {
                LOG.error("No open projects found. Cannot refresh notification display.");
                // Optionally, show an error to the user or try to handle this state.
                return;
            }
        }

        if (currentBookId == null || currentChapterId == null) {
            LOG.warn("Cannot refresh notification display: currentBookId or currentChapterId is null.");
            return;
        }

        LOG.debug("Attempting to refresh notification display for book: " + currentBookId +
                  ", chapter: " + currentChapterId + ", page: " + currentPageNumber);
        
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Book book = bookService.getBookById(currentBookId).block();
                if (book == null) {
                    throw new IllegalStateException("Book not found for refresh: " + currentBookId);
                }

                Mono<String> contentMono = chapterService.getChapterContent(book, currentChapterId);
                Mono<String> titleMono = chapterService.getChapterTitle(currentBookId, currentChapterId);

                Mono.zip(contentMono, titleMono)
                    .subscribe(
                        tuple -> {
                            String chapterContent = tuple.getT1();
                            String chapterTitle = tuple.getT2();
                            
                            if (chapterContent == null || chapterContent.isEmpty()) {
                                LOG.error("Failed to refresh: content is null/empty for chapter " + currentChapterId);
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    notificationService.showError("Refresh Failed", "Could not retrieve chapter content.");
                                }, ModalityState.defaultModalityState());
                                return;
                            }
                            
                            ApplicationManager.getApplication().invokeLater(()-> {
                                notificationService.showChapterContent(project, currentBookId, currentChapterId, currentPageNumber, chapterTitle, chapterContent);
                            }, ModalityState.defaultModalityState());
                        },
                        error -> {
                            LOG.error("Failed to refresh notification", error);
                             ApplicationManager.getApplication().invokeLater(() -> {
                                notificationService.showError("Refresh Failed", "Error applying settings: " + error.getMessage());
                            }, ModalityState.defaultModalityState());
                        }
                    );
            } catch (Exception e) {
                 LOG.error("Top-level error refreshing notification", e);
                 ApplicationManager.getApplication().invokeLater(() -> {
                    notificationService.showError("Refresh Error", "A critical error occurred: " + e.getMessage());
                }, ModalityState.defaultModalityState());
            }
        });
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