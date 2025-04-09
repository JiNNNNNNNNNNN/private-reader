package com.lv.tool.privatereader.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.config.PrivateReaderConfig;
import com.lv.tool.privatereader.exception.ExceptionHandler;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.settings.*;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.ui.dialog.AddBookDialog;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import com.lv.tool.privatereader.reader.ReactiveChapterPreloader;
import com.lv.tool.privatereader.ui.factory.UIComponentFactory;
import com.lv.tool.privatereader.ui.topics.BookshelfTopics;
import org.jetbrains.annotations.NotNull;
import com.lv.tool.privatereader.service.impl.BookServiceImpl;
import com.lv.tool.privatereader.service.impl.ChapterServiceImpl;
import com.lv.tool.privatereader.service.impl.NotificationServiceImpl;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import com.lv.tool.privatereader.repository.RepositoryModule;
import com.lv.tool.privatereader.util.StringUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.lv.tool.privatereader.service.*;
import com.intellij.openapi.Disposable;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.lv.tool.privatereader.cache.CacheManager;
import com.lv.tool.privatereader.monitor.PerformanceMonitor;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.EventListener;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import com.lv.tool.privatereader.ui.listener.ThemeAwareMouseListener;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JSeparator;
import javax.swing.table.DefaultTableModel;
import com.lv.tool.privatereader.parser.NovelParser.Chapter; // 添加导入

/**
 * 私人阅读器面板
 * <p>
 * 提供书籍阅读的主要界面，包含以下功能：
 * - 书籍列表管理（添加、删除、选择）
 * - 章节列表显示和导航
 * - 阅读进度追踪和显示
 * - 内容显示和格式化
 * - 快捷键支持
 */
public class PrivateReaderPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(PrivateReaderPanel.class);
    private static final SimpleDateFormat DATE_FORMAT;
    private static PrivateReaderPanel instance;
    private final Project project;
    private final CacheManager cacheManager;
    private final PerformanceMonitor performanceMonitor;
    private JBList<Book> bookList;
    private JBList<NovelParser.Chapter> chapterList;
    private JTextPane contentArea;
    private JBLabel progressLabel;
    private JBLabel lastReadLabel;
    
    // 服务类
    private BookService bookService;
    private ChapterService chapterService;
    private NotificationService notificationService;
    private PrivateReaderConfig config;
    private ReadingProgressManager progressManager;
    private RepositoryModule repositoryModule;
    private BookRepository bookRepository;
    private ChapterCacheRepository chapterCacheRepository;
    
    private JButton prevChapterBtn;
    private JButton nextChapterBtn;
    private String currentChapterId;
    private static final String NOTIFICATION_GROUP_ID = "Private Reader";
    private JSplitPane mainSplitPane;
    private JButton toggleLeftPanelButton;
    private int lastDividerLocation = 250;
    private boolean isNotificationMode;
    private Notification currentNotification;
    private String currentContent;
    private int currentPage = 1;
    private int totalPages = 1;
    private static final int MAX_NOTIFICATIONS = 5;
    private final List<Notification> activeNotifications = new ArrayList<>();
    private JButton toggleModeButton;
    private JButton refreshBtn;
    private ListSelectionListener bookListListener;
    private boolean isLoadingLastChapter = false;
    private ListSelectionListener chapterListListener;
    private boolean isLoadingChapter = false;
    private Book currentBook;
    private JBScrollPane contentScrollPane; // Checklist Item 1: Add contentScrollPane member variable
    // Checklist Item 1: Add state variables
    private volatile int currentChapterIndex = -1;
    private volatile int totalChaptersInList = 0;
    // 上次选中的书籍，用于在切换时保存进度
    private Book previouslySelectedBook = null;
    private volatile boolean initialLoadAttempted = false; // Add initial load flag

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 50;

    // --- Temporary Listener Definition ---
    // TODO: Move this interface and topic to src/main/java/com/lv/tool/privatereader/events/BookDataListener.java
    public static interface BookDataListener extends EventListener { // Made static
        Topic<BookDataListener> BOOK_DATA_TOPIC = Topic.create("Book Data Loaded", BookDataListener.class); // Topic is implicitly static
        void bookDataLoaded();
    }
    // --- End Temporary Definition ---

    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 构造函数
     */
    private PrivateReaderPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.cacheManager = new CacheManager();
        this.performanceMonitor = new PerformanceMonitor();
        instance = this;
        
        // 显示加载状态
        setContent("正在初始化...", 0);
        
        // 获取调度器
        ReactiveSchedulers schedulers = ReactiveSchedulers.getInstance();
        
        // 使用响应式流进行初始化
        Flux.just("services", "ui", "events", "books")
            .subscribeOn(schedulers.io())
            .concatMap(phase -> {
                switch (phase) {
                    case "services":
                        return initServicesReactive();
                    case "ui":
                        return setupUIComponentsReactive();
                    case "events":
                        return registerEventListenersReactive();
                    case "books":
                        return loadBooksReactive();
                    default:
                        return Mono.empty();
                }
            })
            .doOnNext(result -> {
                LOG.info("完成初始化阶段: " + result);
            })
            .doOnError(error -> {
                LOG.error("初始化过程中发生错误", error);
                setContent("初始化失败: " + error.getMessage(), 0);
            })
            .doFinally(signal -> {
                LOG.info("初始化流程结束，信号: " + signal);
            })
            .subscribe();
        
        LOG.info("PrivateReaderPanel 构造函数完成，等待异步初始化完成。");
    }
    
    /**
     * 注册所有事件监听器
     */
    private void registerEventListeners() {
        LOG.info("注册事件监听器...");
        // 注册设置变更监听
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(ReaderSettingsListener.TOPIC, () -> {
                    SwingUtilities.invokeLater(() -> applyFontSettings());
                });

        // 注册通知栏阅读设置变更监听
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(NotificationReaderSettingsListener.TOPIC, () -> {
                    if (isNotificationMode && currentContent != null) {
                        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
                        Book selectedBook = bookList.getSelectedValue();
                        if (currentChapter != null && selectedBook != null) {
                            SwingUtilities.invokeLater(() ->
                                    showChapterInNotification(selectedBook, currentChapter, currentContent, false));
                        }
                    }
                });
            
        // 注册阅读模式设置变更监听
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(ReaderModeSettings.TOPIC, (notificationMode) -> {
                    boolean newMode = notificationMode;
                    if (newMode != isNotificationMode) {
                        isNotificationMode = newMode;
                        updateToggleModeButton();
                        
                        // 如果有当前章节，重新加载以应用新模式
                        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
                        if (currentChapter != null) {
                            SwingUtilities.invokeLater(() -> loadChapter(currentChapter));
                        }
                    }
                });

        // Subscribe to BookDataListener event for initial load
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        try {
             messageBus.connect(this) // Connect requires Disposable
                .subscribe(BookDataListener.BOOK_DATA_TOPIC, new BookDataListener() { // Use static inner interface
                    @Override // This should now be valid
                    public void bookDataLoaded() {
                        LOG.info("接收到 bookDataLoaded 事件");
                        if (!initialLoadAttempted) {
                             LOG.info("首次加载触发: 调用 loadLastReadChapter via SwingUtilities");
                             SwingUtilities.invokeLater(() -> {
                                 try {
                                    loadLastReadChapter();
                                 } catch (Exception e) {
                                     LOG.error("在 bookDataLoaded 事件处理中调用 loadLastReadChapter 时出错", e);
                                 }
                             });
                        } else {
                             LOG.info("已经尝试过加载，忽略重复的 bookDataLoaded 事件");
                        }
                    }
                });
             LOG.info("成功订阅 BOOK_DATA_TOPIC");
        } catch (Exception e) {
            LOG.error("订阅 BOOK_DATA_TOPIC 时出错", e);
        }

        // Immediately check if data is already ready (in case event fired before subscription)
        RepositoryModule repoModule = RepositoryModule.getInstance();
        if (repoModule != null && repoModule.isDataReady() && !initialLoadAttempted) {
            LOG.info("数据已就绪 (订阅时检查)，尝试加载上次阅读章节...");
            SwingUtilities.invokeLater(() -> {
                 try {
                    loadLastReadChapter();
                 } catch (Exception e) {
                     LOG.error("在订阅时检查并调用 loadLastReadChapter 时出错", e);
                 }
            });
        } else {
             LOG.info(String.format("启动时数据未就绪或已尝试加载 (isDataReady=%b, initialLoadAttempted=%b)，等待事件...",
                      (repoModule != null && repoModule.isDataReady()), initialLoadAttempted));
        }

        // 添加书籍列表选择监听器
        bookListListener = e -> {
            if (!e.getValueIsAdjusting() && !isLoadingLastChapter) {
                // Checklist Item 3: Add logic to save previous book's progress
                Book newlySelectedBook = bookList.getSelectedValue();
                if (previouslySelectedBook != null && newlySelectedBook != null && !previouslySelectedBook.equals(newlySelectedBook)) {
                    if (bookService != null && previouslySelectedBook.getLastReadChapterId() != null) {
                        try {
                            // Checklist Item 2: Fix book switch save logic
                            String previousChapterId = previouslySelectedBook.getLastReadChapterId();
                            String previousChapterTitle = "";
                            int positionToSave = 0;
                            
                            if (previousChapterId != null && !previousChapterId.isEmpty()) {
                                // Find chapter title
                                List<NovelParser.Chapter> prevChapters = previouslySelectedBook.getCachedChapters();
                                if (prevChapters != null) {
                                    for (NovelParser.Chapter ch : prevChapters) {
                                        if (previousChapterId.equals(ch.url())) {
                                            previousChapterTitle = ch.title();
                                            break;
                                        }
                                    }
                                }

                                // Determine position
                                if (!isNotificationMode) {
                                    if (contentScrollPane != null) {
                                        try {
                                            positionToSave = contentScrollPane.getVerticalScrollBar().getValue();
                                        } catch (Exception scrollEx) {
                                            LOG.warn("获取滚动位置失败，将保存位置 0: " + scrollEx.getMessage());
                                        }
                                    } else {
                                         LOG.warn("阅读器模式下 contentScrollPane 未初始化，将保存位置 0");
                                    }
                                } else {
                                    if (notificationService != null) {
                                        try {
                                            int currentPage = notificationService.getCurrentPage();
                                            NotificationReaderSettings settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
                                            int pageSize = settings != null ? settings.getPageSize() : 70;
                                            positionToSave = Math.max(0, (currentPage - 1) * pageSize);
                                        } catch (Exception notifyEx) {
                                            LOG.warn("获取通知页面信息失败，将保存位置 0: " + notifyEx.getMessage());
                                        }
                                    } else {
                                        LOG.warn("通知模式下 notificationService 未初始化，将保存位置 0");
                                    }
                                }
                            }

                            // Fix Linter Error: Corrected logger arguments
                            // LOG.info(String.format("切换书籍，保存上一本书籍 '%s' 的进度，章节ID: %s", 
                            //          previouslySelectedBook.getTitle(), previouslySelectedBook.getLastReadChapterId()));
                            // Checklist Item 1 (Retry 2): Translate log message using concatenation
                            LOG.info("切换书籍，保存上一本书 '" + previouslySelectedBook.getTitle() + "' 进度：章节ID=" + previousChapterId + ", 标题=" + previousChapterTitle + ", 位置=" + positionToSave);
                            // 使用上次记录的章节ID和位置0来保存（主要目的是持久化章节ID）
                            // Using empty string placeholder for title
                            // bookService.saveReadingProgress(previouslySelectedBook, previouslySelectedBook.getLastReadChapterId(), "", 0); 
                            bookService.saveReadingProgress(previouslySelectedBook, previousChapterId, previousChapterTitle, positionToSave);
                        } catch (Exception ex) {
                            // Fix Linter Error: Corrected logger arguments
                            LOG.error(String.format("保存上一本书籍 '%s' 的进度时出错: %s", previouslySelectedBook.getTitle(), ex.getMessage()), ex);
                        }
                    }
                }
                // 更新上次选中的书籍记录 (无论是否保存成功，都要更新)
                previouslySelectedBook = newlySelectedBook; 

                updateChapterList();
            }
        };
        bookList.addListSelectionListener(bookListListener);
        
        // Setup chapter list listener (Ensure this listener remains)
        setupChapterListListener();
        LOG.info("事件监听器注册完成。");
    }
    
    /**
     * 初始化UI组件
     */
    private void setupUIComponents() {
        LOG.info("开始设置UI组件");
        
        // 初始化必要的组件为空值，避免NPE
        bookList = new JBList<>();
        chapterList = new JBList<>();
        contentArea = new JTextPane();
        progressLabel = new JBLabel();
        lastReadLabel = new JBLabel();
        
        // 检查服务是否正确初始化
        if (this.bookService == null || this.chapterService == null || 
            this.notificationService == null || this.config == null || 
            this.progressManager == null) {
            LOG.error("服务初始化失败，使用备用方式");
            // 使用备用方式初始化UI
            setLayout(new BorderLayout());
            JLabel errorLabel = new JLabel("服务初始化失败，请重启IDE", SwingConstants.CENTER);
            add(errorLabel, BorderLayout.CENTER);
            return;
        }

        // 初始化必要的组件
        bookList = UIComponentFactory.createBookList(project);
        chapterList = UIComponentFactory.createChapterList();
        contentArea = UIComponentFactory.createContentArea();
        progressLabel = new JBLabel();
        lastReadLabel = new JBLabel();

        // 从配置中获取阅读模式
        isNotificationMode = config.getReaderMode() == PrivateReaderConfig.ReaderMode.NOTIFICATION;
        LOG.info("初始化阅读模式: " + (isNotificationMode ? "通知栏" : "阅读器"));

        // 检查插件是否启用
        if (!config.isPluginEnabled()) {
            LOG.info("插件已禁用，不初始化阅读器面板");
            setLayout(new BorderLayout());
            JLabel disabledLabel = new JLabel("插件已禁用，请在设置中启用插件", SwingConstants.CENTER);
            add(disabledLabel, BorderLayout.CENTER);
            return;
        }

        // 设置布局
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(0));
        
        // 创建工具栏
        JPanel toolBar = createToolBar();
        add(toolBar, BorderLayout.NORTH);

        // 创建主内容面板（左右分割）
        JSplitPane mainSplitPane = createMainContentPane();
        add(mainSplitPane, BorderLayout.CENTER);
        
        // 添加状态栏
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);
        
        // 设置键盘快捷键
        setupKeyboardShortcuts();
        
        LOG.info("UI组件设置完成");
    }
    
    /**
     * 创建工具栏
     */
    private JPanel createToolBar() {
        // 创建工具栏面板
        JPanel toolBar = new JPanel();
        toolBar.setLayout(new BorderLayout());
        toolBar.setBorder(JBUI.Borders.empty(0, 0, 1, 0));
        toolBar.setBackground(UIManager.getColor("ToolBar.background"));
        
        // 左侧按钮区域
        JPanel leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        leftButtonsPanel.setOpaque(false);
        
        // 视图控制
        toggleLeftPanelButton = createActionButton(
            AllIcons.Actions.ArrowCollapse, "隐藏侧栏", 
            e -> toggleLeftPanel()
        );
        leftButtonsPanel.add(toggleLeftPanelButton);
        
        leftButtonsPanel.add(createVerticalSeparator());
        
        // 阅读模式切换按钮
        toggleModeButton = createActionButton(
            isNotificationMode ? AllIcons.Actions.ShowReadAccess : AllIcons.Actions.PreviewDetails, 
            isNotificationMode ? "当前：通知栏模式" : "当前：阅读器模式", 
            e -> toggleReadingMode() // Changed from e -> {}
            // Removed TODO comment
        );
        leftButtonsPanel.add(toggleModeButton);
        
        // 主题切换按钮
        JButton toggleThemeButton = createActionButton(
            AllIcons.Actions.InlayGear, "切换阅读主题", 
            e -> toggleTheme()
        );
        leftButtonsPanel.add(toggleThemeButton);
        
        leftButtonsPanel.add(createVerticalSeparator());
        
        // 书籍管理
        JButton addButton = createActionButton(
            AllIcons.General.Add, "添加书籍", 
            e -> {
                AddBookDialog dialog = new AddBookDialog(project);
                dialog.show();
            }
        );
        leftButtonsPanel.add(addButton);
        
        JButton removeButton = createActionButton(
            AllIcons.General.Remove, "移除书籍", 
            e -> {
                Book selectedBook = bookList.getSelectedValue();
                if (selectedBook != null) {
                    int result = Messages.showYesNoDialog(
                        project,
                        String.format("确定要移除《%s》吗？", selectedBook.getTitle()),
                        "移除书籍",
                        Messages.getQuestionIcon()
                    );
                    if (result == Messages.YES) {
                        removeBook();
                        refresh();
                    }
                }
            }
        );
        leftButtonsPanel.add(removeButton);
        
        leftButtonsPanel.add(createVerticalSeparator());
        
        // 章节管理
        JButton refreshChaptersButton = createActionButton(
            AllIcons.Actions.Refresh, "刷新章节列表", 
            e -> refreshChapterList()
        );
        leftButtonsPanel.add(refreshChaptersButton);
        
        refreshBtn = createTextButton("刷新内容", AllIcons.Actions.Refresh, e -> reloadCurrentChapter());
        refreshBtn.setEnabled(false);
        leftButtonsPanel.add(refreshBtn);
        
        JButton resetButton = createActionButton(
            AllIcons.Actions.Rollback, "重置进度", 
            e -> {
                Book selectedBook = bookList.getSelectedValue();
                if (selectedBook != null) {
                    int result = Messages.showYesNoDialog(
                        project,
                        String.format("确定要重置《%s》的阅读进度吗？这将清除所有阅读记录。", selectedBook.getTitle()),
                        "重置进度",
                        Messages.getQuestionIcon()
                    );
                    if (result == Messages.YES) {
                        progressManager.resetProgress(selectedBook);
                        refresh();
                    }
                }
            }
        );
        leftButtonsPanel.add(resetButton);
        
        // 导航按钮 - 放在右侧
        JPanel rightButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        rightButtonsPanel.setOpaque(false);
        
        prevChapterBtn = createTextButton("上一章", AllIcons.Actions.Back, e -> navigateChapter(-1));
        nextChapterBtn = createTextButton("下一章", AllIcons.Actions.Forward, e -> navigateChapter(1));
        
        prevChapterBtn.setEnabled(false);
        nextChapterBtn.setEnabled(false);
        
        rightButtonsPanel.add(prevChapterBtn);
        rightButtonsPanel.add(nextChapterBtn);
        
        // 添加到工具栏
        toolBar.add(leftButtonsPanel, BorderLayout.WEST);
        toolBar.add(rightButtonsPanel, BorderLayout.EAST);
        
        return toolBar;
    }
    
    /**
     * 创建主内容区
     */
    private JSplitPane createMainContentPane() {
        // 创建左侧面板(书架和章节)
        JPanel leftPanel = createLeftPanel();
        
        // 创建右侧阅读区
        JPanel readingPanel = createReadingPanel();

        // 创建主分割面板
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, readingPanel);
        // mainSplitPane.setResizeWeight(0.3); // 调整左右面板比例为3:7 - 注释掉，改用 setDividerLocation
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setBorder(null);
        mainSplitPane.setBackground(UIManager.getColor("Tree.background"));
        mainSplitPane.setDividerSize(3);
        
        updateSplitPaneStyle(mainSplitPane);
        mainSplitPane.setDividerLocation(300); // 直接设置初始分隔线位置
        
        // 监听分隔面板位置变化
        mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            int location = mainSplitPane.getDividerLocation();
            if (location <= 1) {
                toggleLeftPanelButton.setIcon(AllIcons.Actions.MoveToRightBottom);
                toggleLeftPanelButton.setToolTipText("显示侧栏");
            } else {
                toggleLeftPanelButton.setIcon(AllIcons.Actions.MoveToLeftBottom);
                toggleLeftPanelButton.setToolTipText("隐藏侧栏");
            }
        });
        
        return mainSplitPane;
    }
    
    /**
     * 创建左侧面板
     */
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UIManager.getColor("Tree.background"));
        
        // 创建书架面板
        JPanel bookshelfPanel = createBookshelfPanel();
        
        // 创建章节面板
        JPanel chaptersPanel = createChaptersPanel();
        
        // 创建左侧分割面板
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bookshelfPanel, chaptersPanel);
        leftSplitPane.setResizeWeight(0.3);
        leftSplitPane.setBorder(null);
        leftSplitPane.setDividerSize(3);
        leftSplitPane.setContinuousLayout(true);
        
        // 美化分隔条
        leftSplitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(UIManager.getColor("Separator.foreground"));
                        g.fillRect(0, 0, getSize().width, getSize().height);
                    }
                };
            }
        });
        
        leftPanel.add(leftSplitPane, BorderLayout.CENTER);
        
        return leftPanel;
    }
    
    /**
     * 创建书架面板
     */
    private JPanel createBookshelfPanel() {
        JPanel booksPanel = new JPanel(new BorderLayout());
        booksPanel.setBackground(UIManager.getColor("Tree.background"));
        
        // 标题栏
        JPanel bookTitlePanel = new JPanel(new BorderLayout());
        bookTitlePanel.setBackground(UIManager.getColor("ToolBar.background"));
        bookTitlePanel.setBorder(JBUI.Borders.empty(4, 8));
        
        JLabel booksLabel = new JLabel("书架");
        booksLabel.setFont(booksLabel.getFont().deriveFont(Font.BOLD));
        bookTitlePanel.add(booksLabel, BorderLayout.WEST);
        
        // 搜索框
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);
        searchPanel.setBorder(JBUI.Borders.empty(0, 8, 0, 0));
        
        JTextField bookSearchField = new JTextField(15);
        bookSearchField.putClientProperty("JTextField.placeholderText", "搜索书籍...");
        bookSearchField.setBorder(JBUI.Borders.customLine(UIManager.getColor("Component.borderColor"), 1));
        
        // 添加实时搜索功能
        bookSearchField.getDocument().addDocumentListener(new DocumentListener() {
            private Timer searchTimer;
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSearch();
            }
            
            private void scheduleSearch() {
                if (searchTimer != null) {
                    searchTimer.stop();
                }
                
                searchTimer = new Timer(300, e -> {
            String searchText = bookSearchField.getText().trim().toLowerCase();
            if (searchText.isEmpty()) {
                        loadBooks();
                return;
            }
            
            List<Book> books = new ArrayList<>();
            try {
            if (bookRepository != null) {
                books = bookRepository.getAllBooks();
                        } else if (bookService != null) {
                     LOG.warn("BookRepository not initialized in search, using BookService as fallback.");
                     books = bookService.getAllBooks().collectList().block();
            } else {
                    LOG.error("Both BookRepository and BookService are uninitialized in search. Cannot filter books.");
                 }
            } catch (Exception ex) {
                 LOG.error("Error getting books for search filtering", ex);
                 ExceptionHandler.handle(project, ex, "搜索时获取书籍列表失败");
            }
            
            List<Book> filteredBooks = books.stream()
                .filter(book -> 
                    book.getTitle().toLowerCase().contains(searchText) || 
                    (book.getAuthor() != null && book.getAuthor().toLowerCase().contains(searchText))
                )
                .collect(Collectors.toList());
            
            bookList.setListData(filteredBooks.toArray(new Book[0]));
                });
                
                searchTimer.setRepeats(false);
                searchTimer.start();
            }
        });
        
        // 添加搜索框焦点效果
        bookSearchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                bookSearchField.setBorder(JBUI.Borders.customLine(new Color(0, 120, 215), 1));
            }
            
            @Override
            public void focusLost(FocusEvent e) {
                bookSearchField.setBorder(JBUI.Borders.customLine(UIManager.getColor("Component.borderColor"), 1));
            }
        });
        
        searchPanel.add(bookSearchField, BorderLayout.CENTER);
        bookTitlePanel.add(searchPanel, BorderLayout.EAST);
        
        booksPanel.add(bookTitlePanel, BorderLayout.NORTH);
        
        // 样式化书籍列表
        bookList.setBorder(JBUI.Borders.empty());
        bookList.setCellRenderer(new ModernBookListCellRenderer());

        // 添加书籍列表选择监听器
        bookListListener = e -> {
            if (!e.getValueIsAdjusting() && !isLoadingLastChapter) {
                // Checklist Item 3: Add logic to save previous book's progress
                Book newlySelectedBook = bookList.getSelectedValue();
                if (previouslySelectedBook != null && newlySelectedBook != null && !previouslySelectedBook.equals(newlySelectedBook)) {
                    if (bookService != null && previouslySelectedBook.getLastReadChapterId() != null) {
                        try {
                            // Checklist Item 2: Fix book switch save logic
                            String previousChapterId = previouslySelectedBook.getLastReadChapterId();
                            String previousChapterTitle = "";
                            int positionToSave = 0;
                            
                            if (previousChapterId != null && !previousChapterId.isEmpty()) {
                                // Find chapter title
                                List<NovelParser.Chapter> prevChapters = previouslySelectedBook.getCachedChapters();
                                if (prevChapters != null) {
                                    for (NovelParser.Chapter ch : prevChapters) {
                                        if (previousChapterId.equals(ch.url())) {
                                            previousChapterTitle = ch.title();
                                            break;
                                        }
                                    }
                                }

                                // Determine position
                                if (!isNotificationMode) {
                                    if (contentScrollPane != null) {
                                        try {
                                            positionToSave = contentScrollPane.getVerticalScrollBar().getValue();
                                        } catch (Exception scrollEx) {
                                            LOG.warn("获取滚动位置失败，将保存位置 0: " + scrollEx.getMessage());
                                        }
                                    } else {
                                         LOG.warn("阅读器模式下 contentScrollPane 未初始化，将保存位置 0");
                                    }
                                } else {
                                    if (notificationService != null) {
                                        try {
                                            int currentPage = notificationService.getCurrentPage();
                                            NotificationReaderSettings settings = ApplicationManager.getApplication().getService(NotificationReaderSettings.class);
                                            int pageSize = settings != null ? settings.getPageSize() : 70;
                                            positionToSave = Math.max(0, (currentPage - 1) * pageSize);
                                        } catch (Exception notifyEx) {
                                            LOG.warn("获取通知页面信息失败，将保存位置 0: " + notifyEx.getMessage());
                                        }
                                    } else {
                                        LOG.warn("通知模式下 notificationService 未初始化，将保存位置 0");
                                    }
                                }
                            }

                            // Fix Linter Error: Corrected logger arguments
                            // LOG.info(String.format("切换书籍，保存上一本书籍 '%s' 的进度，章节ID: %s", 
                            //          previouslySelectedBook.getTitle(), previouslySelectedBook.getLastReadChapterId()));
                            // Checklist Item 1 (Retry 2): Translate log message using concatenation
                            LOG.info("切换书籍，保存上一本书 '" + previouslySelectedBook.getTitle() + "' 进度：章节ID=" + previousChapterId + ", 标题=" + previousChapterTitle + ", 位置=" + positionToSave);
                            // 使用上次记录的章节ID和位置0来保存（主要目的是持久化章节ID）
                            // Using empty string placeholder for title
                            // bookService.saveReadingProgress(previouslySelectedBook, previouslySelectedBook.getLastReadChapterId(), "", 0); 
                            bookService.saveReadingProgress(previouslySelectedBook, previousChapterId, previousChapterTitle, positionToSave);
                        } catch (Exception ex) {
                            // Fix Linter Error: Corrected logger arguments
                            LOG.error(String.format("保存上一本书籍 '%s' 的进度时出错: %s", previouslySelectedBook.getTitle(), ex.getMessage()), ex);
                        }
                    }
                }
                // 更新上次选中的书籍记录 (无论是否保存成功，都要更新)
                previouslySelectedBook = newlySelectedBook; 

                updateChapterList();
            }
        };
        bookList.addListSelectionListener(bookListListener);
        
        // 添加右键菜单
        bookList.addMouseListener(new BookListPopupMenu(project, bookList));
        
        // 添加双击打开功能
        bookList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Book selectedBook = bookList.getSelectedValue();
                    if (selectedBook != null) {
                        // 禁用书籍列表监听器，避免重复通知
                        disableBookListListener();
                        try {
                            // 如果双击的是当前书籍，且已经有章节被选中，则不重新加载
                            if (currentChapterId != null && selectedBook.getLastReadChapterId() != null 
                                && selectedBook.getLastReadChapterId().equals(currentChapterId)) {
                                return;
                            }
                            loadLastReadChapter();
                        } finally {
                            // 重新启用书籍列表监听器
                            enableBookListListener();
                        }
                    }
                }
            }
        });
        
        JBScrollPane bookScrollPane = new JBScrollPane(bookList);
        bookScrollPane.setBorder(JBUI.Borders.empty());
        booksPanel.add(bookScrollPane, BorderLayout.CENTER);
        
        return booksPanel;
    }
    
    /**
     * 创建章节面板
     */
    private JPanel createChaptersPanel() {
        JPanel chaptersPanel = new JPanel(new BorderLayout());
        chaptersPanel.setBackground(UIManager.getColor("Tree.background"));
        
        // 标题栏
        JPanel chapterTitlePanel = new JPanel(new BorderLayout());
        chapterTitlePanel.setBackground(UIManager.getColor("ToolBar.background"));
        chapterTitlePanel.setBorder(JBUI.Borders.empty(4, 8));
        
        JLabel chaptersLabel = new JLabel("章节");
        chaptersLabel.setFont(chaptersLabel.getFont().deriveFont(Font.BOLD));
        chapterTitlePanel.add(chaptersLabel, BorderLayout.WEST);
        
        // 添加章节搜索
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);
        searchPanel.setBorder(JBUI.Borders.empty(0, 8, 0, 0));
        
        JTextField chapterSearchField = new JTextField(15);
        chapterSearchField.putClientProperty("JTextField.placeholderText", "搜索章节...");
        chapterSearchField.setBorder(JBUI.Borders.customLine(UIManager.getColor("Component.borderColor"), 1));
        
        // 添加实时搜索功能
        chapterSearchField.getDocument().addDocumentListener(new DocumentListener() {
            private Timer searchTimer;
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSearch();
            }
            
            private void scheduleSearch() {
                if (searchTimer != null) {
                    searchTimer.stop();
                }
                
                searchTimer = new Timer(300, e -> {
            String searchText = chapterSearchField.getText().trim().toLowerCase();
            if (searchText.isEmpty()) {
                updateChapterList();
                return;
            }
            
            Book book = bookList.getSelectedValue();
            if (book == null || book.getCachedChapters() == null) {
                return;
            }
            
            List<NovelParser.Chapter> allChapters = book.getCachedChapters();
            List<NovelParser.Chapter> filteredChapters = allChapters.stream()
                .filter(chapter -> chapter.title().toLowerCase().contains(searchText))
                .collect(Collectors.toList());
            
            DefaultListModel<NovelParser.Chapter> listModel = (DefaultListModel<NovelParser.Chapter>) chapterList.getModel();
            listModel.clear();
            for (NovelParser.Chapter chapter : filteredChapters) {
                listModel.addElement(chapter);
            }
            
            if (!filteredChapters.isEmpty()) {
                chapterList.setSelectedIndex(0);
                    }
                });
                
                searchTimer.setRepeats(false);
                searchTimer.start();
            }
        });
        
        // 添加搜索框焦点效果
        chapterSearchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                chapterSearchField.setBorder(JBUI.Borders.customLine(new Color(0, 120, 215), 1));
            }
            
            @Override
            public void focusLost(FocusEvent e) {
                chapterSearchField.setBorder(JBUI.Borders.customLine(UIManager.getColor("Component.borderColor"), 1));
            }
        });
        
        searchPanel.add(chapterSearchField, BorderLayout.CENTER);
        chapterTitlePanel.add(searchPanel, BorderLayout.EAST);
        
        chaptersPanel.add(chapterTitlePanel, BorderLayout.NORTH);
        
        // 样式化章节列表
        chapterList.setBorder(JBUI.Borders.empty());
        chapterList.setCellRenderer(new ModernChapterListCellRenderer());

        // 添加章节列表选择监听器
        chapterListListener = e -> {
            if (!e.getValueIsAdjusting() && !isLoadingLastChapter && !isLoadingChapter) {
                NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();
                if (selectedChapter != null) {
                    loadChapter(selectedChapter);
                }
            }
        };
        chapterList.addListSelectionListener(chapterListListener);

        JBScrollPane chapterScrollPane = new JBScrollPane(chapterList);
        chapterScrollPane.setBorder(JBUI.Borders.empty());
        chaptersPanel.add(chapterScrollPane, BorderLayout.CENTER);
        
        return chaptersPanel;
    }
    
    /**
     * 创建阅读面板
     */
    private JPanel createReadingPanel() {
        JPanel readingPanel = new JPanel(new BorderLayout());
        readingPanel.setBackground(UIManager.getColor("EditorPane.background"));
        
        // 设置阅读器样式
        contentArea.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        contentArea.setMargin(new Insets(12, 16, 12, 16));
        contentArea.setBackground(new Color(250, 250, 250));
        
        // 创建内容滚动面板
        this.contentScrollPane = new JBScrollPane(contentArea);
        this.contentScrollPane.setBorder(JBUI.Borders.empty());
        this.contentScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // 添加平滑滚动
        this.contentScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (e.getValueIsAdjusting()) {
                int target = e.getValue();
                int[] current = {contentScrollPane.getVerticalScrollBar().getValue()};
                int step = (target - current[0]) / 8;
                
                Timer timer = new Timer(16, null);
                timer.addActionListener(evt -> {
                    current[0] += step;
                    if (Math.abs(target - current[0]) <= Math.abs(step)) {
                        current[0] = target;
                        timer.stop();
                    }
                    contentScrollPane.getVerticalScrollBar().setValue(current[0]);
                });
                timer.start();
            }
        });
        
        // 添加阴影效果
        JPanel shadowPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                g2d.setColor(new Color(0, 0, 0, 20));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
        };
        shadowPanel.setOpaque(false);
        shadowPanel.add(contentScrollPane, BorderLayout.CENTER);
        
        readingPanel.add(shadowPanel, BorderLayout.CENTER);
        
        return readingPanel;
    }
    
    /**
     * 创建状态栏
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(JBUI.Borders.customLine(UIManager.getColor("Separator.foreground"), 1, 0, 0, 0));
        statusBar.setBackground(UIManager.getColor("StatusBar.background"));
        
        // 左侧-进度信息
        JPanel leftStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        leftStatusPanel.setOpaque(false);
        
        // 样式化进度标签
        progressLabel = new JBLabel("阅读进度：-");
        progressLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 12));
        progressLabel.setForeground(new Color(60, 60, 60));
        leftStatusPanel.add(progressLabel);
        
        lastReadLabel = new JBLabel("上次阅读：-");
        lastReadLabel.setForeground(new Color(120, 120, 120));
        lastReadLabel.setFont(lastReadLabel.getFont().deriveFont(12f));
        leftStatusPanel.add(lastReadLabel);
        
        // 右侧-分页控制
        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        rightStatusPanel.setOpaque(false);
        
        JButton prevPageButton = createActionButton(
            AllIcons.Actions.Play_back, "上一页", 
            e -> prevPage()
        );
        prevPageButton.setEnabled(isNotificationMode);
        
        JLabel pageInfoLabel = new JLabel("第 1/1 页");
        pageInfoLabel.setFont(pageInfoLabel.getFont().deriveFont(12f));
        pageInfoLabel.setForeground(new Color(60, 60, 60));
        
        JButton nextPageButton = createActionButton(
            AllIcons.Actions.Play_forward, "下一页", 
            e -> nextPage()
        );
        nextPageButton.setEnabled(isNotificationMode);
        
        rightStatusPanel.add(prevPageButton);
        rightStatusPanel.add(pageInfoLabel);
        rightStatusPanel.add(nextPageButton);
        
        // 中间-进度条
        JProgressBar readingProgressBar = new JProgressBar(0, 100);
        readingProgressBar.setStringPainted(true);
        readingProgressBar.setString("0%");
        readingProgressBar.setForeground(new Color(0, 120, 215));
        readingProgressBar.setBackground(new Color(240, 240, 240));
        readingProgressBar.setBorder(JBUI.Borders.empty());
        readingProgressBar.setPreferredSize(new Dimension(100, 6));
        readingProgressBar.setAlignmentY(CENTER_ALIGNMENT);
        
        // 添加进度条动画效果
        readingProgressBar.addChangeListener(e -> {
            if (readingProgressBar.getValue() > 0) {
                Timer timer = new Timer(16, null);
                timer.addActionListener(evt -> {
                    int current = readingProgressBar.getValue();
                    int target = readingProgressBar.getMaximum();
                    int step = (target - current) / 8;
                    
                    if (Math.abs(target - current) <= Math.abs(step)) {
                        current = target;
                        timer.stop();
                    } else {
                        current += step;
                    }
                    
                    readingProgressBar.setValue(current);
                });
                timer.start();
            }
        });
        
        JPanel progressBarPanel = new JPanel(new GridBagLayout());
        progressBarPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(2, 20);
        progressBarPanel.add(readingProgressBar, gbc);
        
        // 组装状态栏
        statusBar.add(leftStatusPanel, BorderLayout.WEST);
        statusBar.add(progressBarPanel, BorderLayout.CENTER);
        statusBar.add(rightStatusPanel, BorderLayout.EAST);
        
        return statusBar;
    }
    
    /**
     * 创建操作按钮
     */
    private JButton createActionButton(Icon icon, String tooltip, ActionListener action) {
        JButton button = new JButton(icon);
        updateButtonStyle(button);
        button.setToolTipText(tooltip);
        button.addActionListener(action);
        return button;
    }
    
    /**
     * 创建带文本的按钮
     */
    private JButton createTextButton(String text, Icon icon, ActionListener action) {
        JButton button = new JButton(text, icon);
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(false);
        button.setBorder(JBUI.Borders.customLine(UIManager.getColor("Button.borderColor"), 1, 8, 1, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(action);
        
        // 添加鼠标悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setContentAreaFilled(true);
                    button.setBackground(UIManager.getColor("Button.hoverBackground"));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });
        
        return button;
    }

    /**
     * 获取PrivateReaderPanel实例，如果不存在则创建
     * 
     * @param project 项目
     * @return PrivateReaderPanel实例
     */
    public static PrivateReaderPanel getInstance(Project project) {
        if (instance == null) {
            return createInstance(project);
        }
        return instance;
    }
    
    /**
     * 创建一个新的PrivateReaderPanel实例
     * 
     * @param project 项目
     * @return 新创建的PrivateReaderPanel实例
     */
    public static PrivateReaderPanel createInstance(Project project) {
        LOG.info("创建PrivateReaderPanel实例 [project: " + (project != null ? project.getName() : "null") + "]");
        if (instance == null) {
            LOG.info("首次创建实例");
            instance = new PrivateReaderPanel(project);
        } else if (instance.project != project) {
            LOG.info("检测到项目变更，创建新实例");
            instance = new PrivateReaderPanel(project);
        } else {
            LOG.info("使用现有实例");
        }
        return instance;
    }

    /**
     * 刷新面板内容
     * 重新加载书籍列表并更新进度信息
     */
    public void refresh() {
        LOG.debug("刷新阅读器面板");
        
        // 清理通知
        if (notificationService != null) {
            notificationService.closeAllNotifications();
        }
        
        // 重新加载书籍列表
        loadBooks();
        
        // 更新章节列表
        updateChapterList();
        
        // 尝试加载上次阅读的章节
        try {
            LOG.info("刷新后尝试加载上次阅读章节");
            // 延迟一点加载，确保UI已经更新
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    loadLastReadChapter();
                } catch (Exception e) {
                    LOG.error("延迟加载上次阅读章节失败", e);
                }
            });
        } catch (Exception e) {
            LOG.error("刷新后加载上次阅读章节失败", e);
        }
    }

    /**
     * 更新阅读进度信息
     * 显示当前选中书籍的阅读进度、章节信息和最后阅读时间
     */
    private void updateProgressInfo() {
        Book selectedBook = bookList.getSelectedValue();
        NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();

        if (selectedBook != null && selectedChapter != null) {
            // Simplify progress text to avoid calling missing method
            String progressText = String.format("章节: %s (%d/%d)",
                    selectedChapter.title(),
                    chapterList.getSelectedIndex() + 1,
                    chapterList.getModel().getSize()
                    //calculateReadingProgress(selectedBook, selectedChapter) // Removed call to missing method
            );
            // progressInfoLabel.setText(progressText); // - Incorrect variable
            progressLabel.setText(progressText); // + Correct variable
        } else {
            // progressInfoLabel.setText(" "); // - Incorrect variable
            progressLabel.setText(" "); // + Correct variable
        }
    }

    /**
     * 获取书籍列表组件
     * @return 书籍列表组件
     */
    public JList<Book> getBookList() {
        return bookList;
    }

    /**
     * 获取章节列表组件
     * @return 章节列表组件
     */
    public JList<NovelParser.Chapter> getChapterList() {
        return chapterList;
    }

    /**
     * 设置内容区域的文本，并尝试恢复滚动位置
     *
     * @param content             要显示的文本内容
     * @param initialScrollPosition 初始滚动位置，< 0 表示不尝试恢复，滚动到顶部
     */
    public void setContent(String content, int initialScrollPosition) {
        // Checklist Item 1: Ensure services are initialized before UI updates
        if (chapterService == null) {
            initServices(); // Attempt re-initialization if needed
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (contentArea != null) {
                try {
                    // 清空现有内容和样式
                    contentArea.getDocument().remove(0, contentArea.getDocument().getLength());

                    // 设置新内容
                    // 使用 Document API 插入以避免潜在的 UI 冻结
                    Document doc = contentArea.getDocument();
                    doc.insertString(0, content != null ? content : "", null);

                    // 应用字体和样式设置
                    applyFontSettings();

                    // 尝试恢复滚动位置或滚动到顶部
                    if (contentScrollPane != null && initialScrollPosition >= 0) {
                        // 延迟滚动操作以确保UI已更新
                        SwingUtilities.invokeLater(() -> {
                             JScrollBar verticalScrollBar = contentScrollPane.getVerticalScrollBar();
                            if (verticalScrollBar != null) {
                                // 稍微延迟以等待布局完成
                                Timer timer = new Timer(50, event -> {
                                    int maxScroll = verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount();
                                    // 确保 maxScroll 不为负
                                    maxScroll = Math.max(0, maxScroll);
                                    int targetScroll = Math.min(Math.max(0, initialScrollPosition), maxScroll);
                                    LOG.info(String.format("尝试滚动到: %d (请求: %d, 最大: %d, 可见: %d, 总高: %d)",
                                            targetScroll, initialScrollPosition, maxScroll, verticalScrollBar.getVisibleAmount(), verticalScrollBar.getMaximum()));
                                    verticalScrollBar.setValue(targetScroll);
                                    // 使用安全的方式设置光标位置
                                    if (!safeSetCaretPosition(contentArea, targetScroll, MAX_RETRIES)) {
                                        LOG.warn("设置光标位置失败，将滚动到文档开头");
                                        contentArea.setCaretPosition(0);
                                    }
                                });
                                timer.setRepeats(false);
                                timer.start();
                            } else {
                                LOG.warn("无法获取垂直滚动条，无法恢复滚动位置，滚动到顶部");
                                contentArea.setCaretPosition(0);
                            }
                        });
                    } else {
                        // 如果不恢复位置或滚动面板无效，则滚动到顶部
                        LOG.info("滚动到文档开头 (initialScrollPosition=" + initialScrollPosition + ", contentScrollPane=" + (contentScrollPane != null) + ")");
                        contentArea.setCaretPosition(0);
                    }
                } catch (BadLocationException e) {
                    LOG.error("设置内容时发生错误", e);
                    // 尝试设置错误消息
                    try {
                         contentArea.getDocument().remove(0, contentArea.getDocument().getLength());
                         contentArea.getDocument().insertString(0, "设置内容时发生错误: " + e.getMessage(), null);
                         contentArea.setCaretPosition(0);
                    } catch (BadLocationException ex) {
                         // Ignore secondary error
                    }
                }
            } else {
                LOG.warn("contentArea 未初始化，无法设置内容");
            }
        });
    }

    public void updateReadingProgress(String chapterId, String chapterTitle, int position) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            LOG.info(String.format("更新阅读进度 - 书籍: %s, 章节: %s, 位置: %d",
                    selectedBook.getTitle(), chapterTitle, position));
            
            // 只更新进度，不触发事件
            progressManager.updateProgress(selectedBook, chapterId, chapterTitle, position);
            
            // 更新进度显示
            updateProgressInfo();
        } else {
            LOG.warn("无法更新阅读进度：未选择书籍");
        }
    }

    /**
     * 导航到上一章或下一章
     * 
     * @param direction 导航方向，-1表示上一章，1表示下一章
     */
    public void navigateChapter(int direction) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null || currentChapterId == null) return;

        LOG.info("Navigating chapter, direction: " + direction);

        chapterService.getChapterList(selectedBook)
            .flatMap(chapters -> {
                if (chapters == null || chapters.isEmpty()) {
                    LOG.warn("Cannot navigate chapter, chapter list is empty or null.");
                    return Mono.empty();
                }
                int currentIndex = -1;
                for (int i = 0; i < chapters.size(); i++) {
                    if (chapters.get(i).url().equals(currentChapterId)) {
                        currentIndex = i;
                        break;
                    }
                }

                if (currentIndex == -1) {
                    LOG.warn("Current chapter ID not found in list: " + currentChapterId);
                    return Mono.empty();
                }

                int targetIndex = currentIndex + direction;

                if (targetIndex >= 0 && targetIndex < chapters.size()) {
                    NovelParser.Chapter targetChapter = chapters.get(targetIndex);
                    LOG.info("Navigating to chapter: " + targetChapter.title());
                    return Mono.just(targetChapter);
                } else {
                    LOG.info("Already at first/last chapter.");
                    showNotification(direction < 0 ? "已经是第一章了" : "已经是最后一章了", NotificationType.INFORMATION);
                    return Mono.empty();
                }
            })
            .publishOn(ReactiveSchedulers.getInstance().ui()) // Ensure UI update is on EDT
            .subscribe(targetChapter -> {
                // Select and load the target chapter
                selectAndLoadChapter(targetChapter);
            }, error -> {
                LOG.error("Error during chapter navigation: " + error.getMessage(), error);
                showNotification("章节导航失败: " + error.getMessage(), NotificationType.ERROR);
            });
    }

    // 更新当前章节ID
    public void updateCurrentChapter(String chapterId) {
        this.currentChapterId = chapterId;
    }

    /**
     * 加载上次阅读的章节
     */
    public void loadLastReadChapter() {
        // Check data readiness and if load already attempted
        RepositoryModule repoModule = RepositoryModule.getInstance();
        boolean dataReady = (repoModule != null && repoModule.isDataReady());

        if (!dataReady) {
            LOG.warn(String.format("跳过 loadLastReadChapter: 数据未就绪 (dataReady=%b)", dataReady));
            if (!initialLoadAttempted) {
                 setContent("正在初始化书籍数据...", 0);
            }
            return; // Exit if data not ready
        }

        if (initialLoadAttempted) {
            LOG.info("跳过 loadLastReadChapter: 已尝试过首次加载 (initialLoadAttempted=true)");
            return; // Exit if already attempted
        }

        initialLoadAttempted = true;
        LOG.info("首次尝试加载上次阅读章节 [loadLastReadChapter]...");

        // Ensure bookService is available
        if (bookService == null) {
             LOG.error("BookService 为 null，无法加载上次阅读章节。");
             setContent("服务错误，无法加载阅读记录", 0);
                                 return;
        }

        // --- Original logic starts here ---
        LOG.debug("执行 loadLastReadChapter 核心逻辑..."); 
        try {
            List<Book> books = ReadAction.compute(() -> bookService.getAllBooks().collectList().block());
            if (books.isEmpty()) {
                LOG.info("书架为空，无需加载上次阅读章节。");
                setContent("", 0); // Replaced clearContentPanel()
                return;
            }

            Book lastReadBook = ReadAction.compute(() -> bookService.getLastReadBook().block());
            if (lastReadBook == null) {
                LOG.info("没有找到上次阅读的书籍记录。");
                setContent("", 0); // Replaced clearContentPanel()
            return;
        }
             LOG.info("找到上次阅读书籍: " + lastReadBook.getTitle());

            // ... (logic to select book in list, load its chapters, find last chapter, select chapter, display content) ...
            // Make sure the logic below uses the 'lastReadBook' variable defined above
            // The duplicate definition around line 1391 should have been removed by the logic above.
            // Example continuation:
            final Book bookToLoad = lastReadBook;
            final String chapterIdToLoad = lastReadBook.getLastReadChapterId();

            if (chapterIdToLoad == null || chapterIdToLoad.isEmpty()) {
                 LOG.info("上次阅读书籍 " + bookToLoad.getTitle() + " 没有记录章节ID，无需加载。");
                 // Optional: Select the book in the list anyway?
                bookList.setSelectedValue(bookToLoad, true);
                 setContent("", 0); // Clear content if no chapter to load
            return;
        }
        
            LOG.info("准备为书籍 '" + bookToLoad.getTitle() + "' 加载章节 ID: " + chapterIdToLoad);
            // TODO: Implement the logic to select the book in the list, 
            // load its chapters (if needed), find the chapter by ID, 
            // select the chapter in the chapter list, and load its content.
            // This involves UI updates and potentially service calls.
            // Placeholder comment for the complex UI/logic part.


            } catch (Exception e) {
            LOG.error("加载上次阅读章节时发生异常", e);
                // LOG.warn("更新Action状态失败: " + actionId, e); // Already commented out
            }
        // Remove this closing brace -> }
    }

    private void updateToggleModeButton() {
        if (isNotificationMode) {
            toggleModeButton.setIcon(AllIcons.Actions.ShowReadAccess);
            toggleModeButton.setToolTipText("当前：通知栏模式 (点击切换到阅读器模式)");
        } else {
            toggleModeButton.setIcon(AllIcons.Actions.PreviewDetails);
            toggleModeButton.setToolTipText("当前：阅读器模式 (点击切换到通知栏模式)");
        }
    }

    /**
     * 显示上一页内容
     */
    public void prevPage() {
        if (!canGoToPrevPage()) return;

        Book selectedBook = bookList.getSelectedValue();
        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
        if (selectedBook == null || currentChapter == null || currentContent == null) return;

        // - boolean success = notificationService.showPreviousPage(); // Removed
        // Instead, decrement local page and update display
        if (this.currentPage > 1) {
            this.currentPage--;
            LOG.info("Navigating to previous page: " + this.currentPage);
            // Need to re-display content for the new page
            displayCurrentPageContent(selectedBook, currentChapter, currentContent);
        } else {
            LOG.warn("Cannot go to previous page, already on page 1.");
        }
        // Update buttons if necessary
        // updateNavigationButtons(); // Might need adjustment based on local page state
    }

    /**
     * 显示下一页内容
     */
    public void nextPage() {
        if (!canGoToNextPage()) return;

        Book selectedBook = bookList.getSelectedValue();
        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
        if (selectedBook == null || currentChapter == null || currentContent == null) return;

        // Increment local page and update display
        if (this.currentPage < this.totalPages) {
            this.currentPage++;
            LOG.info("Navigating to next page: " + this.currentPage);
            displayCurrentPageContent(selectedBook, currentChapter, currentContent);
        } else {
            LOG.warn("Cannot go to next page, already on last page.");
        }
        // updateNavigationButtons(); // Might need adjustment
    }

    public void reloadCurrentChapter() {
        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
        if (currentChapter != null) {
            Book selectedBook = bookList.getSelectedValue();
            if (selectedBook != null) {
                try {
                    selectedBook.setProject(project);
                    NovelParser parser = selectedBook.getParser();
                    if (parser == null) {
                        LOG.error("解析器为空，尝试重新创建");
                        parser = ParserFactory.createParser(selectedBook.getUrl());
                        selectedBook.setParser(parser);
                    }

                    if (parser == null) {
                        String error = "无法创建解析器";
                        showNotification(error, NotificationType.ERROR);
                        throw new IllegalStateException(error);
                    }

                    // 直接调用解析方法，绕过缓存
                    String content = parser.parseChapterContent(currentChapter.url());
                    
                    // 更新缓存
                    cacheChapterContent(content);
                    
                    // 更新显示
                    if (isNotificationMode) {
                        showChapterInNotification(selectedBook, currentChapter, content, true);
                    } else {
                        setContent(content, 0);
                    }
                    
                    showNotification("章节内容已刷新", NotificationType.INFORMATION);
                } catch (Exception e) {
                    String error = "刷新内容失败: " + e.getMessage();
                    LOG.error(error, e);
                    showNotification(error, NotificationType.ERROR);
                }
            }
        }
    }

    /**
     * 释放资源
     * 在面板销毁时调用，关闭预加载服务等
     */
    public void dispose() {
        LOG.info("Disposing PrivateReaderPanel for project: " + (project != null ? project.getName() : "null"));
        // Unsubscribe listeners or clean up resources if necessary
        // MessageBus connections made with connect(this) are automatically handled.
        instance = null; // Help GC
        bookService = null; // Release service references
        chapterService = null;
        notificationService = null;
        // REMOVED: settingService = null;
        config = null; // Also nullify config if initialized
        progressManager = null; // Also nullify progressManager
        repositoryModule = null;
        bookRepository = null;
        chapterCacheRepository = null;
        LOG.info("PrivateReaderPanel disposed.");
    }

    /**
     * 禁用书籍列表选择监听器
     */
    public void disableBookListListener() {
        if (bookListListener != null) {
            bookList.removeListSelectionListener(bookListListener);
        }
    }

    /**
     * 启用书籍列表选择监听器
     */
    public void enableBookListListener() {
        if (bookListListener != null) {
            bookList.addListSelectionListener(bookListListener);
        }
    }

    private void setupChapterListListener() {
        // 移除所有现有监听器
        for (ListSelectionListener listener : chapterList.getListSelectionListeners()) {
            chapterList.removeListSelectionListener(listener);
        }
        
        // 如果监听器不存在，创建新的
        if (chapterListListener == null) {
            chapterListListener = e -> {
                if (!e.getValueIsAdjusting()) {
                    NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();
                    if (selectedChapter != null) {
                        loadChapter(selectedChapter);
                    }
                }
            };
        }
        
        // 添加监听器
        chapterList.addListSelectionListener(chapterListListener);
    }

    /**
     * 设置按钮样式
     */
    private void styleButton(JButton button) {
        button.setFocusable(false);
        button.setBorder(JBUI.Borders.empty(4, 8));
        button.setBackground(UIManager.getColor("Tree.background"));
        button.setForeground(UIManager.getColor("Button.foreground"));
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(UIManager.getColor("Button.hoverBackground"));
                    button.setBorder(JBUI.Borders.customLine(UIManager.getColor("Button.focusedBorderColor"), 1));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(UIManager.getColor("Tree.background"));
                button.setBorder(JBUI.Borders.empty(4, 8));
            }
        });
    }

    /**
     * 选择并加载指定章节
     * 供外部类调用
     * 
     * @param chapter 要加载的章节
     */
    public void selectAndLoadChapter(NovelParser.Chapter chapter) {
        if (chapter == null || currentBook == null) return;
        
        // 更新章节列表选中状态
        List<NovelParser.Chapter> chapters = currentBook.getCachedChapters();
        if (chapters != null) {
            int index = chapters.indexOf(chapter);
            if (index >= 0) {
                chapterList.setSelectedIndex(index);
                chapterList.ensureIndexIsVisible(index);
            }
        }
        
        // 加载章节内容
        loadChapterContent(chapter);
    }

    /**
     * 更新导航按钮状态
     * 根据当前章节位置启用或禁用上一章/下一章按钮
     */
    private void updateNavigationButtons() {
        if (prevChapterBtn == null || nextChapterBtn == null) {
            return;
        }
        
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null || currentChapterId == null) {
            prevChapterBtn.setEnabled(false);
            nextChapterBtn.setEnabled(false);
            return;
        }
        
        int currentIndex = chapterList.getSelectedIndex();
        int totalChapters = chapterList.getModel().getSize();
        
        // 更新上一章按钮状态
        prevChapterBtn.setEnabled(currentIndex > 0);
        
        // 更新下一章按钮状态
        nextChapterBtn.setEnabled(currentIndex < totalChapters - 1);
    }

    /**
     * 安全地设置章节列表选择，避免触发监听器导致循环加载
     * 
     * @param index 要选择的索引
     */
    private void safelySetChapterSelection(int index) {
        if (chapterListListener != null) {
            chapterList.removeListSelectionListener(chapterListListener);
        }
        
        try {
            chapterList.setSelectedIndex(index);
        } finally {
            if (chapterListListener != null) {
                chapterList.addListSelectionListener(chapterListListener);
            }
        }
    }

    private void loadBooks() {
        performanceMonitor.startOperation("loadBooks");
        
        // 尝试从缓存获取书籍列表
        List<Book> cachedBooks = cacheManager.getBooks("all_books");
        if (cachedBooks != null) {
            LOG.info("从缓存加载书籍列表");
            updateBookList(cachedBooks);
            performanceMonitor.endOperation("loadBooks");
            return;
        }
        
        // 缓存未命中，从服务加载
        LOG.info("从服务加载书籍列表");
        if (bookService != null) {
            List<Book> books = ReadAction.compute(() -> bookService.getAllBooks().collectList().block());
            if (books != null) {
                // 更新缓存
                cacheManager.putBooks("all_books", books);
                updateBookList(books);
            }
        } else {
            LOG.error("BookService 未初始化，无法获取书籍列表");
        }
        
        performanceMonitor.endOperation("loadBooks");
    }

    private void removeBook() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            int result = Messages.showYesNoDialog(
                    project,
                    String.format("确定要移除《%s》吗？", selectedBook.getTitle()),
                    "移除书籍",
                    Messages.getQuestionIcon()
            );

            if (result == Messages.YES) {
                boolean success = false; // Track success
                try {
                if (bookRepository != null) {
                         bookRepository.removeBook(selectedBook); // Assuming void or boolean return
                         // If boolean, capture: success = bookRepository.removeBook(selectedBook);
                         success = true; // Assume success if no exception
                    } else if (bookService != null) { // Use bookService as fallback
                         LOG.warn("BookRepository not initialized, using BookService as fallback for removal.");
                         bookService.removeBook(selectedBook); // removeBook is void
                         success = true; // Assume success if no exception
                } else {
                        LOG.error("Both BookRepository and BookService are uninitialized. Cannot remove book.");
                    }
                } catch (Exception e) {
                    LOG.error("Error removing book", e);
                     ExceptionHandler.handle(project, e, "移除书籍失败");
                     success = false;
                }
                
                if (success) {
                    refresh(); // Refresh the panel if removal was successful
                    Messages.showInfoMessage(project, "书籍已移除", "移除成功");
                } else {
                    Messages.showErrorDialog(project, "移除书籍失败", "移除失败");
                }
            }
        }
    }

    private void updateBookInfo() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            boolean success = false; // Track success
            try {
            if (bookRepository != null) {
                    bookRepository.updateBook(selectedBook); // Assuming void or boolean return
                    // If boolean, capture: success = bookRepository.updateBook(selectedBook);
                    success = true; // Assume success if no exception
                } else if (bookService != null) { // Use bookService as fallback
                    LOG.warn("BookRepository not initialized, using BookService as fallback for update.");
                    bookService.updateBook(selectedBook); // updateBook is void
                    success = true; // Assume success if no exception
            } else {
                    LOG.error("Both BookRepository and BookService are uninitialized. Cannot update book.");
                }
            } catch (Exception e) {
                LOG.error("Error updating book info", e);
                ExceptionHandler.handle(project, e, "更新书籍信息失败");
                success = false;
            }
            if (!success) {
                 LOG.warn("Book update operation failed or threw an exception.");
                 // Optionally show a message to the user
                 // Messages.showWarningDialog(project, "未能成功更新书籍信息", "更新失败");
            }
        }
    }

    private void cacheChapterContent(String content) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            if (chapterCacheRepository != null) {
                chapterCacheRepository.cacheContent(selectedBook.getId(), currentChapterId, content);
            } else {
                ChapterCacheManager cacheManager = project.getService(ChapterCacheManager.class);
                cacheManager.cacheContent(selectedBook.getId(), currentChapterId, content);
            }
        }
    }

    private void loadChapterContent(NovelParser.Chapter chapter) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null || chapter == null) return;

        String chapterUrl = chapter.url();
        LOG.info("Loading content for chapter: " + chapter.title() + " URL: " + chapterUrl);
        setContent("正在加载章节内容...", 0);

        // Get chapter content using ChapterService
        chapterService.getChapter(selectedBook, chapterUrl) // Use getChapter which returns Mono<Chapter>
            .flatMap(loadedChapter -> { // Chapter object might contain content or need parsing
                // Assuming Chapter object itself doesn't directly hold content
                // We might need to use the parser associated with the book
                NovelParser parser = selectedBook.getParser();
                if (parser == null) {
                    return Mono.error(new IllegalStateException("找不到书籍的解析器: " + selectedBook.getTitle()));
                }
                // Use the parser to get content - assume parser method is blocking
                return Mono.fromCallable(() -> parser.parseChapterContent(chapterUrl))
                           .subscribeOn(ReactiveSchedulers.getInstance().io());
            })
            .publishOn(ReactiveSchedulers.getInstance().ui()) // Switch to UI thread for UI updates
            .subscribe(
                content -> {
                    LOG.info("成功加载章节内容 for " + chapter.title());
                    currentContent = content;
                    currentPage = 1; // Reset to first page
                    displayCurrentPageContent(selectedBook, chapter, content); // Use helper to display page 1
                    cacheChapterContent(content);
                    saveReadingProgress(); // Save progress after content is loaded
                    updateProgressInfo();
                    updateNavigationButtons();
                },
                error -> {
                    LOG.error("加载章节内容失败 for " + chapter.title(), error);
                    setContent("加载章节内容失败: " + error.getMessage(), 0);
                    showNotification("加载章节内容失败: " + error.getMessage(), NotificationType.ERROR);
                }
            );
    }

    private void saveReadingProgress() {
        if (currentBook != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // BookStorage bookStorage = ApplicationManager.getApplication().getService(BookStorage.class); // Old code
                    // bookStorage.updateBook(currentBook); // Old code
                    if (bookService != null) { // Use BookService
                        bookService.updateBook(currentBook);
                        LOG.debug("Reading progress saved via BookService for book: " + currentBook.getTitle());
                    } else {
                        LOG.error("BookService is uninitialized. Cannot save reading progress.");
                        Messages.showErrorDialog("Service not initialized, cannot save reading progress.", "Error");
                    }
                } catch (Exception e) {
                    LOG.error("Error saving reading progress", e);
                    Messages.showErrorDialog("保存阅读进度失败：" + e.getMessage(), "错误");
                }
            });
        }
    }

    private void initServices() {
        try {
            // 从应用级别获取服务
            bookService = ReadAction.compute(() -> 
                ApplicationManager.getApplication().getService(BookService.class));
            if (bookService == null) {
                LOG.warn("通过接口获取BookService失败，尝试直接获取实现类");
                BookServiceImpl impl = ApplicationManager.getApplication().getService(BookServiceImpl.class);
                if (impl != null) {
                    bookService = impl;
                    LOG.info("成功获取BookServiceImpl");
                }
            }
            
            chapterService = ReadAction.compute(() -> 
                ApplicationManager.getApplication().getService(ChapterService.class));
            if (chapterService == null) {
                LOG.warn("通过接口获取ChapterService失败，尝试直接获取实现类");
                ChapterServiceImpl impl = ApplicationManager.getApplication().getService(ChapterServiceImpl.class);
                if (impl != null) {
                    chapterService = impl;
                    LOG.info("成功获取ChapterServiceImpl");
                }
            }
            
            notificationService = ReadAction.compute(() -> 
                ApplicationManager.getApplication().getService(NotificationService.class));
            if (notificationService == null) {
                LOG.warn("通过接口获取NotificationService失败，尝试直接获取实现类");
                NotificationServiceImpl impl = ApplicationManager.getApplication().getService(NotificationServiceImpl.class);
                if (impl != null) {
                    notificationService = impl;
                    LOG.info("成功获取NotificationServiceImpl");
                }
            }
            
            // 获取其他服务
            config = ApplicationManager.getApplication().getService(PrivateReaderConfig.class);
            progressManager = ApplicationManager.getApplication().getService(ReadingProgressManager.class);
            
            // 获取RepositoryModule
            repositoryModule = RepositoryModule.getInstance();
            if (repositoryModule != null) {
                bookRepository = repositoryModule.getBookRepository();
                chapterCacheRepository = repositoryModule.getChapterCacheRepository();
                LOG.info("RepositoryModule: 已初始化");
                LOG.info("BookRepository: " + (bookRepository != null ? "已初始化" : "未初始化"));
                LOG.info("ChapterCacheRepository: " + (chapterCacheRepository != null ? "已初始化" : "未初始化"));
            } else {
                LOG.warn("RepositoryModule未初始化，将使用旧的存储服务");
            }
            
            // 记录服务初始化状态
            LOG.info("BookService: " + (bookService != null ? "已初始化" : "未初始化"));
            LOG.info("ChapterService: " + (chapterService != null ? "已初始化" : "未初始化"));
            LOG.info("NotificationService: " + (notificationService != null ? "已初始化" : "未初始化"));
            LOG.info("PrivateReaderConfig: " + (config != null ? "已初始化" : "未初始化"));
            LOG.info("ReadingProgressManager: " + (progressManager != null ? "已初始化" : "未初始化"));
        } catch (Exception e) {
            LOG.error("获取服务实例时发生异常", e);
            bookService = null;
            chapterService = null;
            notificationService = null;
            config = null;
            progressManager = null;
            repositoryModule = null;
            bookRepository = null;
            chapterCacheRepository = null;
        }
    }

    // 现代化书籍列表单元格渲染器
    private static class ModernBookListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof Book book) {
                StringBuilder displayText = new StringBuilder("<html>");
                
                // 书名 - 加粗显示
                displayText.append("<b>").append(book.getTitle()).append("</b>");
                
                // 如果有作者，显示作者信息
                if (book.getAuthor() != null && !book.getAuthor().isEmpty()) {
                    displayText.append("<br><font color='#666666' size='2'>").append(book.getAuthor()).append("</font>");
                }
                
                // 显示阅读进度
                displayText.append("<br><font color='#888888' size='2'>进度: ");
                displayText.append(String.format("%.1f%%", book.getReadingProgress() * 100));
                displayText.append("</font>");
                
                displayText.append("</html>");
                
                label.setText(displayText.toString());
                label.setBorder(JBUI.Borders.empty(8, 5, 8, 5));
            }
            
            if (isSelected) {
                label.setBackground(new Color(210, 230, 250));
                label.setForeground(UIManager.getColor("List.foreground"));
                label.setBorder(JBUI.Borders.customLine(new Color(180, 200, 220), 0, 0, 0, 3));
            }
            
            return label;
        }
    }

    // 现代化章节列表单元格渲染器
    private static class ModernChapterListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof NovelParser.Chapter chapter) {
                label.setText(chapter.title());
                label.setBorder(JBUI.Borders.empty(6, 10));
            }
            
            if (isSelected) {
                label.setBackground(new Color(210, 230, 250));
                label.setForeground(UIManager.getColor("List.foreground"));
                label.setBorder(JBUI.Borders.customLine(new Color(180, 200, 220), 0, 0, 0, 3));
            }
            
            return label;
        }
    }

    // 添加主题切换方法
    private void toggleTheme() {
            ReaderSettings settings = ApplicationManager.getApplication().getService(ReaderSettings.class);
        if (settings == null) {
            LOG.error("无法获取 ReaderSettings 服务");
            return;
        }
        
        // 获取当前和新主题
        Theme oldTheme = settings.getCurrentTheme();
        settings.toggleTheme();
        Theme newTheme = settings.getCurrentTheme();
        
        LOG.info("切换主题: " + oldTheme.getName() + " -> " + newTheme.getName());
        
        // 如果启用了动画，使用动画效果
        if (settings.isUseAnimation()) {
            animateThemeChange(oldTheme, newTheme);
            } else {
            applyTheme(newTheme);
        }
    }

    private void animateThemeChange(Theme oldTheme, Theme newTheme) {
        // 创建动画计时器
        Timer timer = new Timer(16, null); // 约60fps
        final int steps = 30;
        final AtomicInteger currentStep = new AtomicInteger(0);
        
        timer.addActionListener(e -> {
            float progress = (float) currentStep.get() / steps;
            
            // 计算过渡颜色
            Color currentBg = interpolateColor(oldTheme.getBackgroundColor(), 
                newTheme.getBackgroundColor(), progress);
            Color currentText = interpolateColor(oldTheme.getTextColor(), 
                newTheme.getTextColor(), progress);
            Color currentAccent = interpolateColor(oldTheme.getAccentColor(), 
                newTheme.getAccentColor(), progress);
            
            // 应用过渡颜色
            applyTransitionColors(currentBg, currentText, currentAccent);
            
            // 检查动画是否完成
            if (currentStep.incrementAndGet() >= steps) {
                timer.stop();
                applyTheme(newTheme); // 确保最终状态正确
            }
        });
        
        timer.start();
    }

    private void applyTheme(Theme theme) {
        // 更新面板背景
        setBackground(theme.getBackgroundColor());
        
        // 更新内容区域
        contentArea.setBackground(theme.getBackgroundColor());
        contentArea.setForeground(theme.getTextColor());
        
        // 更新列表
        updateListTheme(bookList, theme);
        updateListTheme(chapterList, theme);
        
        // 更新按钮
        updateButtonsTheme(theme);
        
        // 更新分割面板
        updateSplitPaneTheme(mainSplitPane, theme);
        
        // 更新滚动条
        updateScrollBarTheme(contentScrollPane, theme);
        
        // 更新状态栏
        updateStatusBarTheme(theme);
        
        LOG.info("主题应用完成: " + theme.getName());
    }

    private void applyTransitionColors(Color bg, Color text, Color accent) {
        // 更新面板背景
        setBackground(bg);
        
        // 更新内容区域
        contentArea.setBackground(bg);
        contentArea.setForeground(text);
        
        // 更新列表
        bookList.setBackground(bg);
        bookList.setForeground(text);
        chapterList.setBackground(bg);
        chapterList.setForeground(text);
        
        // 更新按钮背景
        for (Component comp : getComponents()) {
            if (comp instanceof JButton) {
                comp.setBackground(bg);
                comp.setForeground(text);
            }
        }
    }

    private void updateListTheme(JList<?> list, Theme theme) {
        list.setBackground(theme.getBackgroundColor());
        list.setForeground(theme.getTextColor());
        list.setBorder(BorderFactory.createLineBorder(theme.getAccentColor()));
    }

    private void updateButtonsTheme(Theme theme) {
        for (Component comp : getComponents()) {
            if (comp instanceof JButton) {
                JButton button = (JButton) comp;
                button.setBackground(theme.getBackgroundColor());
                button.setForeground(theme.getTextColor());
                updateButtonHoverEffect(button, theme);
            }
        }
    }

    private void updateButtonHoverEffect(JButton button, Theme theme) {
        // 移除现有的鼠标监听器
        for (MouseListener listener : button.getMouseListeners()) {
            if (listener instanceof ThemeAwareMouseListener) {
                button.removeMouseListener(listener);
            }
        }
        
        // 添加新的主题感知鼠标监听器
        button.addMouseListener(new ThemeAwareMouseListener(theme));
    }

    private void updateSplitPaneTheme(JSplitPane splitPane, Theme theme) {
        splitPane.setBackground(theme.getBackgroundColor());
        splitPane.setForeground(theme.getTextColor());
        
        // 更新分隔条样式
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        Graphics2D g2d = (Graphics2D) g;
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // 创建渐变效果
                        GradientPaint gradient = new GradientPaint(
                            0, 0, theme.getBackgroundColor(),
                            getWidth(), 0, theme.getAccentColor()
                        );
                        g2d.setPaint(gradient);
                        g2d.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
    }

    private void updateScrollBarTheme(JScrollPane scrollPane, Theme theme) {
        if (scrollPane != null) {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            JScrollBar horizontal = scrollPane.getHorizontalScrollBar();
            
            // 更新垂直滚动条
            if (vertical != null) {
                vertical.setBackground(theme.getBackgroundColor());
                vertical.setForeground(theme.getAccentColor());
            }
            
            // 更新水平滚动条
            if (horizontal != null) {
                horizontal.setBackground(theme.getBackgroundColor());
                horizontal.setForeground(theme.getAccentColor());
            }
        }
    }

    private void updateStatusBarTheme(Theme theme) {
        if (progressLabel != null) {
            progressLabel.setForeground(theme.getTextColor());
        }
        if (lastReadLabel != null) {
            lastReadLabel.setForeground(theme.getTextColor());
        }
    }

    private Color interpolateColor(Color c1, Color c2, float ratio) {
        float[] c1Comps = c1.getRGBComponents(null);
        float[] c2Comps = c2.getRGBComponents(null);
        float[] result = new float[4];
        
        for (int i = 0; i < 4; i++) {
            result[i] = c1Comps[i] + (c2Comps[i] - c1Comps[i]) * ratio;
        }
        
        return new Color(result[0], result[1], result[2], result[3]);
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        InputMap inputMap = contentArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = contentArea.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevChapter");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextChapter");

        actionMap.put("prevChapter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateChapter(-1);
            }
        });

        actionMap.put("nextChapter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateChapter(1);
            }
        });
    }

    /**
     * 切换左侧面板的显示/隐藏
     */
    private void toggleLeftPanel() {
        if (mainSplitPane.getDividerLocation() > 10) {
            // 保存当前分隔线位置
            lastDividerLocation = mainSplitPane.getDividerLocation();
            // 隐藏左侧面板
            mainSplitPane.setDividerLocation(1);
            toggleLeftPanelButton.setIcon(AllIcons.Actions.MoveToRightBottom);
            toggleLeftPanelButton.setToolTipText("显示侧栏");
        } else {
            // 恢复之前的分隔线位置
            mainSplitPane.setDividerLocation(lastDividerLocation > 10 ? lastDividerLocation : 250);
            toggleLeftPanelButton.setIcon(AllIcons.Actions.MoveToLeftBottom);
            toggleLeftPanelButton.setToolTipText("隐藏侧栏");
        }

        // 重新设置分隔线大小以确保可拖动
        mainSplitPane.setDividerSize(3);
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }

    /**
     * 应用字体设置
     */
    private void applyFontSettings() {
        LOG.info("应用字体设置");
        try {
            ReaderSettings readerSettings = ApplicationManager.getApplication().getService(ReaderSettings.class);
            
            // 设置内容字体
            Font contentFont = new Font(readerSettings.getFontFamily(), Font.PLAIN, readerSettings.getFontSize());
            contentArea.setFont(contentFont);
            
            // 设置内容区域背景和前景色
            contentArea.setBackground(UIManager.getColor("EditorPane.background"));
            contentArea.setForeground(readerSettings.getCurrentTheme().getTextColor());
            
            // 设置行间距
            MutableAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(attrs, 0.3f);
            contentArea.setParagraphAttributes(attrs, false);
            
            // 应用段落缩进
            Document doc = contentArea.getDocument();
            if (doc instanceof StyledDocument) {
                StyledDocument styledDoc = (StyledDocument) doc;
                MutableAttributeSet paragraphAttrs = new SimpleAttributeSet();
                StyleConstants.setFirstLineIndent(paragraphAttrs, 20f);
                styledDoc.setParagraphAttributes(0, doc.getLength(), paragraphAttrs, false);
            }
            
            LOG.info("字体设置应用完成");
        } catch (Exception e) {
            LOG.error("应用字体设置失败", e);
        }
    }

    /**
     * 刷新章节列表
     */
    public void refreshChapterList() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            LOG.info("手动刷新章节列表: " + selectedBook.getTitle());
            try {
                selectedBook.setProject(project);
                NovelParser parser = selectedBook.getParser();
                if (parser != null) {
                    List<NovelParser.Chapter> chapters = parser.getChapterList(selectedBook);
                    selectedBook.setCachedChapters(chapters);
                    // 更新存储
                    updateBookInfo();
                    LOG.info(String.format("更新章节列表成功 - 书籍: %s, 章节数: %d",
                            selectedBook.getTitle(), chapters.size()));
                    updateChapterList();
                    Messages.showInfoMessage(
                            project,
                            String.format("成功刷新章节列表，共 %d 章", chapters.size()),
                            "刷新成功"
                    );
                }
            } catch (Exception ex) {
                LOG.error("刷新章节列表失败: " + ex.getMessage(), ex);
                Messages.showErrorDialog(
                        project,
                        "刷新章节列表失败：" + ex.getMessage(),
                        "错误"
                );
            }
        }
    }

    /**
     * 更新章节列表
     * 根据当前选中的书籍加载章节列表
     */
    private void updateChapterList() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null) {
            chapterList.setModel(new DefaultListModel<>());
            updateNavigationButtons();
            return;
        }

        LOG.info("Updating chapter list for book: " + selectedBook.getTitle());
        // List<NovelParser.Chapter> chapters = chapterService.getChapters(selectedBook); // Removed old call
        chapterService.getChapterList(selectedBook) // Use new reactive method
            .publishOn(ReactiveSchedulers.getInstance().ui()) // Ensure UI update is on EDT
            .subscribe(
                chapters -> {
                    if (chapters == null) {
                        chapters = new ArrayList<>(); // Ensure list is not null
                    }
                    DefaultListModel<NovelParser.Chapter> model = new DefaultListModel<>();
                    chapters.forEach(model::addElement);
                    chapterList.setModel(model);
                    LOG.info("Chapter list updated with " + chapters.size() + " chapters.");

                    // Try to restore selection
                    if (currentChapterId != null) {
                        for (int i = 0; i < model.getSize(); i++) {
                            if (model.getElementAt(i).url().equals(currentChapterId)) {
                                chapterList.setSelectedIndex(i);
                                chapterList.ensureIndexIsVisible(i);
                                break;
                            }
                        }
                    }
                    updateNavigationButtons();
                },
                error -> {
                    LOG.error("Failed to update chapter list for book: " + selectedBook.getTitle(), error);
                    chapterList.setModel(new DefaultListModel<>()); // Clear list on error
                    showNotification("获取章节列表失败: " + error.getMessage(), NotificationType.ERROR);
                    updateNavigationButtons();
                }
            );
    }

    /**
     * 加载章节内容
     * 
     * @param chapter 要加载的章节
     */
    private void loadChapter(NovelParser.Chapter chapter) {
        // 如果正在加载章节，跳过
        if (isLoadingChapter) {
            LOG.info("已经在加载章节，跳过重复加载: " + (chapter != null ? chapter.title() : "null"));
            return;
        }
        
        if (chapter == null) {
            LOG.warn("章节为null，无法加载");
            return;
        }
        
        try {
            isLoadingChapter = true;
            LOG.info("======================================");
            LOG.info("开始加载章节: " + chapter.title() + ", URL: " + chapter.url() + " [Thread: " + Thread.currentThread().getName() + "]");
            
            Book selectedBook = bookList.getSelectedValue();
            if (selectedBook == null) {
                LOG.warn("未选择书籍，无法加载章节");
                // 显示错误提示
                setContent("请先选择书籍", 0);
                isLoadingChapter = false;
                LOG.info("======================================");
                return;
            }
            
            // 更新当前章节信息
            currentBook = selectedBook;
            currentChapterId = chapter.url();
            
            LOG.info("当前书籍: " + selectedBook.getTitle() + ", 当前章节: " + chapter.title());
            
            // 确保Book对象的project属性被设置
            if (selectedBook.getProject() == null) {
                LOG.info("设置书籍的project属性");
                selectedBook.setProject(project);
            }
            
            try {
                // 获取章节内容
                LOG.info("从服务获取章节内容 [chapterService=" + (chapterService != null ? "已初始化" : "未初始化") + "]");
                // String content = ReadAction.compute(() -> chapterService != null ? chapterService.getChapter(selectedBook, currentChapterId).block() : null);
                String content = ReadAction.compute(() -> {
                    if (chapterService == null || selectedBook == null || selectedBook.getParser() == null) {
                        LOG.warn("必要服务或对象未初始化，无法获取章节内容");
                        return null;
                    }
                    try {
                        // 使用与书籍关联的解析器获取内容
                        return selectedBook.getParser().parseChapterContent(currentChapterId);
                    } catch (Exception parseEx) {
                        LOG.error("使用解析器获取章节内容失败: " + currentChapterId, parseEx);
                        return null;
                    }
                });
                
                if (content == null || content.isEmpty()) {
                    LOG.warn("获取章节内容失败或内容为空");
                    setContent("无法获取章节内容", 0);
                    isLoadingChapter = false;
                    LOG.info("======================================");
                    return;
                }
                
                LOG.info("获取章节内容成功，长度: " + content.length());
                
                // 根据阅读模式显示内容
                if (isNotificationMode) {
                    LOG.info("使用通知栏模式显示章节");
                    // 判断是否需要更新存储
                    // 如果是加载上次阅读的章节，且章节ID匹配，则不更新存储
                    boolean shouldUpdateStorage = !(isLoadingLastChapter && 
                        selectedBook.getLastReadChapterId() != null && 
                        selectedBook.getLastReadChapterId().equals(chapter.url()));
                    
                    LOG.info("是否更新存储: " + shouldUpdateStorage + 
                            ", 是否正在加载上次章节: " + isLoadingLastChapter + 
                            ", 上次章节ID: " + selectedBook.getLastReadChapterId());
                    
                    // 显示章节内容（页码恢复逻辑已移至showChapterInNotification方法）
                    showChapterInNotification(selectedBook, chapter, content, shouldUpdateStorage);
                } else {
                    LOG.info("使用阅读器模式显示章节");
                    // 在面板中显示内容
                    // Checklist Item 3: Pass 0 or restored position to setContent
                    int initialPos = 0; // Default to top
                    if (isLoadingLastChapter && selectedBook.getLastReadChapterId() != null && 
                        selectedBook.getLastReadChapterId().equals(chapter.url())) {
                        initialPos = selectedBook.getLastReadPosition();
                        LOG.info("恢复上次阅读位置: " + initialPos);
                    }
                    setContent(content, initialPos);
                    
                    // 更新进度信息 (移到保存之后?)
                    // updateProgressInfo(); 
                    
                    // Checklist Item 3: Always save progress in reader mode and get scroll position
                    // 移除原来的 if 条件判断
                    LOG.info("保存阅读进度 (阅读器模式)");
                    int scrollPosition = 0; // 默认保存位置0，除非是恢复上次阅读
                    if (this.contentScrollPane != null) {
                         try {
                             // 如果是恢复上次阅读，应该保存恢复的位置；否则保存0表示从头开始
                             scrollPosition = initialPos;
                             // 如果不是恢复上次阅读，则获取当前实际滚动位置 (可能不是0，但通常逻辑是保存0)
                             // 如果确实需要保存实际滚动位置，取消注释下一行并调整逻辑
                             // scrollPosition = this.contentScrollPane.getVerticalScrollBar().getValue(); 
                         } catch (Exception scrollEx) {
                             LOG.warn("获取滚动位置失败，将保存位置 0: " + scrollEx.getMessage());
                             scrollPosition = 0; // 异常时确保为0
                         }
                    } else {
                         LOG.warn("contentScrollPane 未初始化，将保存位置 0");
                         scrollPosition = 0; // 未初始化时确保为0
                    }
                    
                    // Checklist Item 4: Add progressManager call before saving
                    // 更新内存中的进度管理器状态
                    if (progressManager != null) {
                        progressManager.updateProgress(selectedBook, currentChapterId, chapter.title(), scrollPosition); // Use the determined scrollPosition
                        LOG.info(String.format("通过 progressManager 更新内存进度 - 章节: %s, 位置: %d", chapter.title(), scrollPosition));
                    } else {
                        LOG.warn("progressManager 为 null，无法更新内存进度");
                    }
                    
                    // 保存包含滚动位置的进度
                    if (bookService != null) {
                        bookService.saveReadingProgress(selectedBook, currentChapterId, chapter.title(), scrollPosition); // Use the same scrollPosition
                        // Fix Linter Error: Use String.format for logger
                        LOG.info(String.format("已通过 bookService 保存持久化进度 - 章节: %s, 位置: %d", chapter.title(), scrollPosition));
                    } else {
                        LOG.warn("bookService 为 null，无法保存持久化进度");
                    }
                    
                    // 在保存进度后再更新UI
                    updateProgressInfo();
                    
                    // 原本的 else 块 (跳过保存进度) 已被移除
                    
                    // 预加载后续章节
                    int currentIndex = chapterList.getSelectedIndex();
                    if (currentIndex >= 0) {
                        ReactiveChapterPreloader preloader = ApplicationManager.getApplication().getService(ReactiveChapterPreloader.class);
                        if (preloader != null) {
                            try {
                                // 异步预加载，不等待结果
                                LOG.info("启动章节预加载");
                                preloader.preloadChapters(selectedBook, currentIndex);
                            } catch (Exception e) {
                                LOG.warn("章节预加载失败", e);
                            }
                        }
                    }
                }
                
                // 缓存当前内容，以便模式切换时使用
                currentContent = content;
                
                // 更新导航按钮状态
                updateNavigationButtons();
            } catch (Exception e) {
                // 处理异常
                LOG.error("加载章节内容失败", e);
                if (ExceptionHandler.class != null) {
                    ExceptionHandler.handle(project, e, "加载章节内容失败: " + e.getMessage());
                } else {
                    setContent("加载章节内容失败: " + e.getMessage(), 0);
                }
            }
        } finally {
            // 重置标志位
            isLoadingChapter = false;
            LOG.info("完成加载章节: " + chapter.title());
            LOG.info("======================================");
        }
    }

    /**
     * 在通知栏中显示章节内容
     * 
     * @param book 书籍
     * @param chapter 章节
     * @param content 内容
     * @param updateStorage 是否更新存储
     */
    private void showChapterInNotification(Book book, NovelParser.Chapter chapter, String content, boolean updateStorage) {
        LOG.info("开始在通知栏中显示章节 - 书籍: " + (book != null ? book.getTitle() : "null") + 
                ", 章节: " + (chapter != null ? chapter.title() : "null") + 
                ", 内容长度: " + (content != null ? content.length() : 0) +
                ", 是否更新存储: " + updateStorage);
                
        if (book == null || chapter == null || content == null || content.isEmpty()) {
            LOG.warn("无法显示章节，参数无效");
            return;
        }
        
        // 检查服务是否初始化
        if (notificationService != null && bookService != null) {
            // Call interface method, subscribe to trigger it
            ReadAction.run(() -> {
                if (notificationService != null) {
                    notificationService.showChapterContent(book, chapter.url(), content).subscribe();
                }
            });
        } else {
            LOG.error("服务未初始化 - notificationService: " + (notificationService != null ? "已初始化" : "未初始化") + 
                    ", bookService: " + (bookService != null ? "已初始化" : "未初始化"));
            // Maybe show an error notification?
            // Ensure notificationService is checked before use
            if(notificationService != null){
                 notificationService.showError("服务错误", "通知或书籍服务未初始化").subscribe();
            }
        }
    }

    /**
     * 显示通知
     * 
     * @param content 通知内容
     * @param type 通知类型
     */
    private void showNotification(String content, NotificationType type) {
        if (type == NotificationType.ERROR) {
            ExceptionHandler.handle(project, new RuntimeException(content), content);
        } else {
            // 使用通知服务显示普通通知
            notificationService.showInfo(type.toString(), content);
        }
    }

    /**
     * 面板创建完成后调用，尝试立即加载上次阅读章节
     */
    private void onPanelCreated() {
        LOG.info("面板创建完成，开始执行初始化后操作... [Thread: " + Thread.currentThread().getName() + "]");
        
        // 记录所有服务和组件状态
        LOG.info("服务状态检查:");
        LOG.info("  - bookService: " + (bookService != null ? "已初始化" : "未初始化"));
        LOG.info("  - chapterService: " + (chapterService != null ? "已初始化" : "未初始化"));
        LOG.info("  - notificationService: " + (notificationService != null ? "已初始化" : "未初始化"));
        LOG.info("  - bookList: " + (bookList != null ? "已创建" : "未创建"));
        LOG.info("  - chapterList: " + (chapterList != null ? "已创建" : "未创建"));
        LOG.info("  - contentArea: " + (contentArea != null ? "已创建" : "未创建"));
        
        // 检查服务是否已初始化
        boolean servicesReady = bookService != null && chapterService != null;
        // 检查UI是否已准备好
        boolean uiReady = bookList != null && chapterList != null && contentArea != null;
        
        if (servicesReady && uiReady) {
            LOG.info("所有服务和UI组件已就绪，加载上次阅读的章节");
            // 如果服务和UI都已准备就绪，立即加载上次阅读章节
            try {
                loadLastReadChapter();
            } catch (Exception e) {
                LOG.error("加载上次阅读章节时出错", e);
                setContent("加载上次阅读章节时出错: " + e.getMessage(), 0);
            }
        } else {
            // 如果服务或UI组件未就绪，创建重试机制
            LOG.warn("部分服务或UI组件未就绪，设置重试加载机制");
            retryLoadLastReadChapter(1, 10, 500); // 从500ms开始，最多重试10次
        }
    }
    
    /**
     * 重试加载上次阅读章节
     * @param retryCount 当前重试次数
     * @param maxRetries 最大重试次数
     * @param delay 延迟时间（毫秒）
     */
    private void retryLoadLastReadChapter(int retryCount, int maxRetries, int delay) {
        LOG.info("计划重试加载上次阅读章节 [第" + retryCount + "次，延迟" + delay + "ms]");
        
        Timer timer = new Timer(delay, e -> {
            LOG.info("开始第" + retryCount + "次重试加载上次阅读章节...");
            
            try {
                // 检查服务是否已初始化
                boolean servicesReady = bookService != null && chapterService != null;
                // 检查UI是否已准备好
                boolean uiReady = bookList != null && chapterList != null && contentArea != null;
                
                LOG.info("服务状态检查 [重试#" + retryCount + "]:");
                LOG.info("  - bookService: " + (bookService != null ? "已初始化" : "未初始化"));
                LOG.info("  - chapterService: " + (chapterService != null ? "已初始化" : "未初始化"));
                LOG.info("  - notificationService: " + (notificationService != null ? "已初始化" : "未初始化"));
                LOG.info("  - bookList: " + (bookList != null ? "已创建" : "未创建"));
                LOG.info("  - chapterList: " + (chapterList != null ? "已创建" : "未创建"));
                LOG.info("  - contentArea: " + (contentArea != null ? "已创建" : "未创建"));
                
                if (servicesReady && uiReady) {
                    LOG.info("服务和UI已就绪，加载上次阅读的章节 [重试#" + retryCount + "]");
                    try {
                        loadLastReadChapter();
                    } catch (Exception ex) {
                        LOG.error("加载上次阅读章节时出错", ex);
                        setContent("加载上次阅读章节时出错: " + ex.getMessage(), 0);
                    }
                } else if (retryCount < maxRetries) {
                    LOG.warn("服务或UI组件仍未就绪，继续重试 [" + retryCount + "/" + maxRetries + "]");
                    // 如果未就绪但未达到最大重试次数，继续重试
                    
                    // 计算下一次延迟时间 (指数退避策略，但最大不超过2000ms)
                    int nextDelay = Math.min(delay * 2, 2000);
                    retryLoadLastReadChapter(retryCount + 1, maxRetries, nextDelay);
                } else {
                    // 达到最大重试次数，放弃尝试
                    LOG.error("达到最大重试次数（" + maxRetries + "），但服务或UI组件仍未就绪，放弃加载上次阅读章节");
                    if (contentArea != null) {
                        setContent("无法加载上次阅读记录: 服务初始化超时，请重启应用后重试", 0);
                    }
                }
            } catch (Exception ex) {
                // 捕获所有可能的异常，确保重试逻辑不会中断
                LOG.error("重试过程中发生异常", ex);
                if (retryCount < maxRetries) {
                    // 出现错误但未达到最大重试次数，继续重试
                    int nextDelay = Math.min(delay * 2, 2000);
                    LOG.warn("继续重试... [" + retryCount + "/" + maxRetries + "]");
                    retryLoadLastReadChapter(retryCount + 1, maxRetries, nextDelay);
                } else {
                    LOG.error("重试期间发生异常，且已达到最大重试次数，放弃");
                    if (contentArea != null) {
                        setContent("加载上次阅读章节时发生错误: " + ex.getMessage(), 0);
                    }
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    // Checklist Item 6: Modify canNavigateToPrevChapter
    /**
     * 检查是否可以导航到上一章
     *
     * @return 如果可以导航到上一章则返回true，否则返回false
     */
    public boolean canNavigateToPrevChapter() {
        // 直接读取 volatile 状态变量，无需检查线程
        return currentChapterIndex > 0;
    }
    
    // Checklist Item 7: Modify canNavigateToNextChapter
    /**
     * 检查是否可以导航到下一章
     *
     * @return 如果可以导航到下一章则返回true，否则返回false
     */
    public boolean canNavigateToNextChapter() {
        // 直接读取 volatile 状态变量，无需检查线程
        return currentChapterIndex >= 0 && currentChapterIndex < totalChaptersInList - 1;
    }

    // Checklist Item 2: Add canGoToPrevPage method
    /**
     * 检查是否可以导航到上一页（仅限通知模式）
     *
     * @return 如果可以导航到上一页则返回true，否则返回false
     */
    public boolean canGoToPrevPage() {
        // 检查是否处于通知模式，并且 notificationService 已初始化
        if (!isNotificationMode || notificationService == null) {
            return false;
        }
        // 检查当前页是否大于1
        return notificationService.getCurrentPage() > 1;
    }

    // Checklist Item 3: Add canGoToNextPage method
    /**
     * 检查是否可以导航到下一页（仅限通知模式）
     *
     * @return 如果可以导航到下一页则返回true，否则返回false
     */
    public boolean canGoToNextPage() {
        // 检查是否处于通知模式，并且 notificationService 已初始化
        if (!isNotificationMode || notificationService == null) {
            return false;
        }
        // 检查当前页是否小于总页数
        return notificationService.getCurrentPage() < notificationService.getTotalPages();
    }

    // Method to handle mode toggling (stub)
    public void toggleReadingMode() {
        // 获取设置服务
        ReaderModeSettings settings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        if (settings == null) {
            LOG.error("无法获取 ReaderModeSettings 服务，无法切换模式。");
            return;
        }
        
        // 获取当前模式并切换
        boolean currentMode = settings.isNotificationMode();
        boolean newMode = !currentMode;
        
        LOG.info("通过按钮切换阅读模式：从 " + (currentMode ? "通知栏" : "阅读器") + " 模式切换到 " + (newMode ? "通知栏" : "阅读器") + " 模式");
        
        // 应用新模式（这将保存设置并触发事件监听器）
        settings.setNotificationMode(newMode);
        
        // （可选）立即更新按钮状态，提供更快的视觉反馈
        // updateToggleModeButton(); 
        // 注意：事件监听器也会调用 updateToggleModeButton 和 loadChapter
    }

    // Method to load progress for a specific book (stub)
    public void loadSpecificBookProgress(Book bookToLoad) {
        if (bookToLoad == null) {
            LOG.warn("尝试加载指定书籍进度，但书籍为 null");
            return;
        }
        LOG.info("loadSpecificBookProgress() 被调用 for book: " + bookToLoad.getTitle() + ". 需要实现具体加载逻辑。");
        // TODO: Implement the logic to:
        // 1. Select the book in the bookList.
        // 2. Update the chapterList for this book.
        // 3. Find and select the last read chapter for this book.
        // 4. Load the content for that chapter.
        // 5. Set the scroll position.
        // This might involve calling existing methods like bookList.setSelectedValue, updateChapterList,
        // selectLastReadChapterForCurrentBook, loadChapter, setContent etc. Ensure proper EDT handling.
        
        // Example (very basic, needs refinement and EDT handling):
        // ApplicationManager.getApplication().invokeLater(() -> {
        //     bookList.setSelectedValue(bookToLoad, true);
        //     // Triggering selection listener might be enough if it handles the rest
        // });

        chapterService.getChapterList(bookToLoad)
            .publishOn(ReactiveSchedulers.getInstance().ui()) // Ensure UI update is on EDT
            .subscribe(
                chapters -> {
                    // Existing logic using chapters...
                    if (chapters != null && !chapters.isEmpty()) {
                        DefaultListModel<NovelParser.Chapter> model = new DefaultListModel<>();
                        chapters.forEach(model::addElement);
                        chapterList.setModel(model);
                        totalChaptersInList = chapters.size();
                        LOG.info("Chapter list model updated with " + chapters.size() + " chapters for book: " + bookToLoad.getTitle());

                        String lastReadChapterId = bookToLoad.getLastReadChapterId();
                        int targetIndex = -1;
                        if (lastReadChapterId != null) {
                            for (int i = 0; i < chapters.size(); i++) {
                                if (lastReadChapterId.equals(chapters.get(i).url())) {
                                    targetIndex = i;
                                    break;
                                }
                            }
                        }

                        if (targetIndex != -1) {
                            LOG.info("Found last read chapter index: " + targetIndex);
                            safelySetChapterSelection(targetIndex);
                        } else {
                            LOG.warn("Last read chapter ID not found or invalid, selecting first chapter.");
                            safelySetChapterSelection(0); // Select first chapter if last read not found
                        }
                    } else {
                        LOG.warn("Chapter list is null or empty for book: " + bookToLoad.getTitle());
                        chapterList.setModel(new DefaultListModel<>()); // Clear list
                    }
                },
                error -> {
                     LOG.error("Failed to load chapter list for specific book progress: " + bookToLoad.getTitle(), error);
                     chapterList.setModel(new DefaultListModel<>()); // Clear list on error
                     showNotification("加载书籍章节失败: " + error.getMessage(), NotificationType.ERROR);
                }
            );
    }

    /**
     * 更新章节列表模型（内部辅助方法）
     */
    private void updateChapterListModel() {
        // 检查服务是否初始化
        if (chapterService == null) {
            LOG.error("章节服务未初始化");
            return;
        }
        
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null) {
            // 清理章节列表
            chapterList.setListData(new NovelParser.Chapter[0]);
            
            // 清理内容区域
            setContent("", 0);
            
            // 重置当前章节ID
            currentChapterId = null;
            
            // 重置页码
            currentPage = 1;
            totalPages = 1;
            
            // 禁用刷新按钮
            if (refreshBtn != null) {
                refreshBtn.setEnabled(false);
            }
            
            // 更新进度信息
            updateProgressInfo();
            return;
        }
        
        // 确保Book对象的project属性被设置
        if (selectedBook.getProject() == null) {
            selectedBook.setProject(project);
        }
        
        try {
            // 暂时移除章节列表选择监听器，防止循环触发
            if (chapterListListener != null) {
                chapterList.removeListSelectionListener(chapterListListener);
            }
            
            // 获取章节列表
            List<NovelParser.Chapter> chapters = ReadAction.compute(() -> {
                if (chapterService == null || selectedBook == null) {
                    LOG.warn("章节服务或选中书籍未初始化，无法获取章节列表");
                    return java.util.Collections.emptyList(); // 返回空列表
                }
                try {
                    //直接调用 block 获取 Mono 的结果
                    List<NovelParser.Chapter> result = chapterService.getChapterList(selectedBook).block();
                    // block() 可能返回 null，需要处理
                    return result != null ? result : java.util.Collections.emptyList(); 
                } catch (Exception blockEx) {
                    LOG.error("阻塞获取章节列表失败: " + selectedBook.getTitle(), blockEx);
                    return java.util.Collections.emptyList(); // 异常时返回空列表
                }
            });
            
            // 更新章节列表
            chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));

            // 更新进度信息
            updateProgressInfo();
            
            // 如果有上次阅读的章节，选中它
            if (selectedBook.getLastReadChapterId() != null && !selectedBook.getLastReadChapterId().isEmpty()) {
                for (int i = 0; i < chapters.size(); i++) {
                    if (chapters.get(i).url().equals(selectedBook.getLastReadChapterId())) {
                        chapterList.setSelectedIndex(i);
                        break;
                    }
                }
            }
            
            // 重新添加章节列表选择监听器
            if (chapterListListener != null) {
                chapterList.addListSelectionListener(chapterListListener);
            }
            
            // Checklist Item 4: Update state variables in updateChapterList
            final List<NovelParser.Chapter> finalChapters = chapters; // effectively final for lambda
            SwingUtilities.invokeLater(() -> { // 确保在EDT更新状态
                totalChaptersInList = finalChapters.size();
                int selectedIndex = chapterList.getSelectedIndex(); // 获取当前的选中索引
                currentChapterIndex = selectedIndex;
                LOG.info("状态更新(updateChapterList): totalChapters=" + totalChaptersInList + ", currentIndex=" + currentChapterIndex);
                updateNavigationButtons(); // 确保导航按钮状态基于新状态更新

                // Checklist Item 1: Trigger chapter loading after update
                NovelParser.Chapter chapterToLoad = chapterList.getSelectedValue(); // 获取当前实际选中的章节
                if (chapterToLoad != null) {
                    LOG.info("章节列表更新完毕，主动加载选中章节: " + chapterToLoad.title());
                    // 在EDT上触发加载，因为loadChapter内部可能访问UI或需要特定线程上下文
                     loadChapter(chapterToLoad); 
                } else {
                     LOG.info("章节列表更新完毕，但没有选中章节可加载。");
                     // 如果没有选中章节（例如，新书或上次阅读的章节无效），清空内容区
                     setContent("", 0); 
                     currentChapterId = null; // 重置当前章节ID
                     currentContent = null; // 重置当前内容
                     updateNavigationButtons(); // 再次更新按钮状态，因为可能变为不可用
                }
            });
        } catch (Exception e) {
            // 处理异常
            LOG.error("加载章节列表失败", e);
            if (ExceptionHandler.class != null) {
                ExceptionHandler.handle(project, e, "加载章节列表失败: " + e.getMessage());
            } else {
                setContent("加载章节列表失败: " + e.getMessage(), 0);
            }
            
            // 确保监听器被重新添加
            if (chapterListListener != null) {
                chapterList.addListSelectionListener(chapterListListener);
            }
        }
    }

    /**
     * 安全地设置光标位置，包含重试机制
     * @param contentArea 文本区域组件
     * @param targetScroll 目标滚动位置
     * @param maxRetries 最大重试次数
     * @return 是否成功设置光标位置
     */
    private boolean safeSetCaretPosition(JTextPane contentArea, int targetScroll, int maxRetries) {
        if (contentArea == null || contentArea.getDocument() == null) {
            LOG.warn("文本区域或文档为空，无法设置光标位置");
            return false;
        }

        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                // 检查组件是否已经完成布局
                if (contentArea.getSize().height > 0) {
                    int modelPosition = contentArea.viewToModel(new Point(0, targetScroll));
                    if (modelPosition >= 0) {
                        int safePosition = Math.min(contentArea.getDocument().getLength(), modelPosition);
                        contentArea.setCaretPosition(safePosition);
                        LOG.info(String.format("成功设置光标位置: %d (目标滚动: %d, 重试次数: %d)",
                                safePosition, targetScroll, retryCount));
                        return true;
                    }
                }

                // 如果布局未完成或位置无效，等待后重试
                if (retryCount < maxRetries - 1) {
                    LOG.debug(String.format("布局未完成或位置无效，等待后重试 (重试次数: %d)", retryCount));
                    Thread.sleep(RETRY_DELAY_MS);
                }
            } catch (Exception e) {
                LOG.warn(String.format("设置光标位置时发生错误 (重试次数: %d): %s", retryCount, e.getMessage()));
            }
            retryCount++;
        }

        // 如果所有重试都失败，回退到安全位置
        try {
            contentArea.setCaretPosition(0);
            LOG.info("所有重试失败，回退到文档开头");
            return true;
        } catch (Exception e) {
            LOG.error("设置安全光标位置失败: " + e.getMessage());
            return false;
        }
    }

    private void updateBookList(List<Book> books) {
        bookList.setListData(books.toArray(new Book[0]));
        LOG.info("更新了 " + books.size() + " 本书到列表");

        // 尝试选择上次阅读的书籍
        try {
            Book lastRead = ReadAction.compute(() -> (bookService != null) ? bookService.getLastReadBook().block() : null);
            if (lastRead != null) {
                bookList.setSelectedValue(lastRead, true);
                LOG.info("自动选中上次阅读书籍: " + lastRead.getTitle());
            } else if (!books.isEmpty()) {
                bookList.setSelectedIndex(0); // 默认选中第一本书
                LOG.info("没有上次阅读记录，默认选中第一本书");
            }
        } catch (Exception e) {
            LOG.error("选择上次阅读书籍时出错", e);
        }

        updateProgressInfo(); // 确保加载后更新进度信息
    }

    /**
     * 响应式服务初始化
     */
    private Mono<String> initServicesReactive() {
        return Mono.fromCallable(() -> {
            performanceMonitor.startOperation("initServices");
            try {
                // 从应用级别获取服务
                bookService = ReadAction.compute(() -> 
                    ApplicationManager.getApplication().getService(BookService.class));
                if (bookService == null) {
                    LOG.warn("通过接口获取BookService失败，尝试直接获取实现类");
                    BookServiceImpl impl = ApplicationManager.getApplication().getService(BookServiceImpl.class);
                    if (impl != null) {
                        bookService = impl;
                        LOG.info("成功获取BookServiceImpl");
                    }
                }
                
                chapterService = ReadAction.compute(() -> 
                    ApplicationManager.getApplication().getService(ChapterService.class));
                if (chapterService == null) {
                    LOG.warn("通过接口获取ChapterService失败，尝试直接获取实现类");
                    ChapterServiceImpl impl = ApplicationManager.getApplication().getService(ChapterServiceImpl.class);
                    if (impl != null) {
                        chapterService = impl;
                        LOG.info("成功获取ChapterServiceImpl");
                    }
                }
                
                notificationService = ReadAction.compute(() -> 
                    ApplicationManager.getApplication().getService(NotificationService.class));
                if (notificationService == null) {
                    LOG.warn("通过接口获取NotificationService失败，尝试直接获取实现类");
                    NotificationServiceImpl impl = ApplicationManager.getApplication().getService(NotificationServiceImpl.class);
                    if (impl != null) {
                        notificationService = impl;
                        LOG.info("成功获取NotificationServiceImpl");
                    }
                }
                
                // 获取其他服务
                config = ApplicationManager.getApplication().getService(PrivateReaderConfig.class);
                progressManager = ApplicationManager.getApplication().getService(ReadingProgressManager.class);
                
                // 获取RepositoryModule
                repositoryModule = RepositoryModule.getInstance();
                if (repositoryModule != null) {
                    bookRepository = repositoryModule.getBookRepository();
                    chapterCacheRepository = repositoryModule.getChapterCacheRepository();
                    LOG.info("RepositoryModule: 已初始化");
                    LOG.info("BookRepository: " + (bookRepository != null ? "已初始化" : "未初始化"));
                    LOG.info("ChapterCacheRepository: " + (chapterCacheRepository != null ? "已初始化" : "未初始化"));
                } else {
                    LOG.warn("RepositoryModule未初始化，将使用旧的存储服务");
                }
                
                return "services";
            } finally {
                performanceMonitor.endOperation("initServices");
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().io());
    }
    
    /**
     * 响应式UI组件初始化
     */
    private Mono<String> setupUIComponentsReactive() {
        return Mono.fromCallable(() -> {
            performanceMonitor.startOperation("setupUIComponents");
            try {
                // 在EDT线程中执行UI初始化
                SwingUtilities.invokeAndWait(() -> {
                    setupUIComponents();
                });
                return "ui";
            } finally {
                performanceMonitor.endOperation("setupUIComponents");
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().compute());
    }
    
    /**
     * 响应式事件监听器注册
     */
    private Mono<String> registerEventListenersReactive() {
        return Mono.fromCallable(() -> {
            performanceMonitor.startOperation("registerEventListeners");
            try {
                registerEventListeners();
                return "events";
            } finally {
                performanceMonitor.endOperation("registerEventListeners");
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().ui());
    }
    
    /**
     * 响应式书籍加载
     */
    private Mono<String> loadBooksReactive() {
        return Mono.fromCallable(() -> {
            performanceMonitor.startOperation("loadBooks");
            try {
                // 检查缓存是否过期
                if (cacheManager.isCacheExpired("all_books")) {
                    LOG.info("缓存已过期，重新加载");
                    cacheManager.invalidateCache("all_books");
                }
                
                // 尝试从缓存获取书籍列表
                performanceMonitor.startOperation("loadFromCache");
                List<Book> cachedBooks = cacheManager.getBooks("all_books");
                performanceMonitor.endOperation("loadFromCache");
                
                if (cachedBooks != null) {
                    LOG.info("从缓存加载书籍列表，数量: " + cachedBooks.size());
                    performanceMonitor.startOperation("updateUIFromCache");
                    SwingUtilities.invokeLater(() -> {
                        updateBookList(cachedBooks);
                    });
                    performanceMonitor.endOperation("updateUIFromCache");
                    
                    // 异步预热缓存
                    preheatCache();
                    return "books_from_cache";
                }
                
                // 从服务加载
                if (bookService != null) {
                    return Mono.fromCallable(() -> {
                        try {
                            performanceMonitor.startOperation("loadFromService");
                            List<Book> books = ReadAction.compute(() -> bookService.getAllBooks().collectList().block());
                            performanceMonitor.endOperation("loadFromService");
                            
                            if (books != null) {
                                LOG.info("从服务加载书籍列表，数量: " + books.size());
                                // 更新缓存
                                performanceMonitor.startOperation("updateCache");
                                cacheManager.putBooks("all_books", books);
                                // 同时更新备用缓存
                                cacheManager.putBooks("fallback_books", books);
                                performanceMonitor.endOperation("updateCache");
                                
                                // 异步更新UI
                                performanceMonitor.startOperation("updateUIFromService");
                                SwingUtilities.invokeLater(() -> {
                                    updateBookList(books);
                                });
                                performanceMonitor.endOperation("updateUIFromService");
                                return "books";
                            }
                            return "no_books";
                        } catch (Exception e) {
                            LOG.error("加载书籍列表失败", e);
                            throw e;
                        }
                    })
                    .retry(3)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorResume(e -> {
                        LOG.error("加载书籍列表失败，尝试使用缓存", e);
                        performanceMonitor.startOperation("loadFromFallbackCache");
                        List<Book> fallbackBooks = cacheManager.getBooks("fallback_books");
                        performanceMonitor.endOperation("loadFromFallbackCache");
                        
                        if (fallbackBooks != null) {
                            performanceMonitor.startOperation("updateUIFromFallback");
                            SwingUtilities.invokeLater(() -> {
                                updateBookList(fallbackBooks);
                            });
                            performanceMonitor.endOperation("updateUIFromFallback");
                            return Mono.just("books_from_fallback_cache");
                        }
                        return Mono.error(e);
                    })
                    .block();
                }
                return "no_service";
            } finally {
                performanceMonitor.endOperation("loadBooks");
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().io());
    }
    
    private void preheatCache() {
        Mono.fromCallable(() -> {
            try {
                if (bookService != null) {
                    List<Book> books = ReadAction.compute(() -> bookService.getAllBooks().collectList().block());
                    if (books != null) {
                        cacheManager.putBooks("all_books", books);
                        cacheManager.putBooks("fallback_books", books);
                    }
                }
            } catch (Exception e) {
                LOG.error("缓存预热失败", e);
            }
            return null;
        }).subscribeOn(ReactiveSchedulers.getInstance().io())
          .subscribe();
    }

    private void updateButtonStyle(JButton button) {
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(32, 32));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setContentAreaFilled(true);
                    button.setBackground(new Color(240, 240, 240));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });
    }

    private void updateSplitPaneStyle(JSplitPane splitPane) {
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        Graphics2D g2d = (Graphics2D) g;
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        GradientPaint gradient = new GradientPaint(
                            0, 0, new Color(240, 240, 240),
                            getWidth(), 0, new Color(220, 220, 220)
                        );
                        g2d.setPaint(gradient);
                        g2d.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
    }

    // 创建现代风格的垂直分隔符
    private Component createVerticalSeparator() {
        JSeparator separator = new JSeparator(JSeparator.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        separator.setForeground(UIManager.getColor("Separator.foreground"));
        return separator;
    }

    // + Add helper method to display content for current page
    private void displayCurrentPageContent(Book book, NovelParser.Chapter chapter, String fullContent) {
        if (fullContent == null || fullContent.isEmpty()) {
            setContent("内容为空", 0);
            this.totalPages = 1;
            this.currentPage = 1;
            return;
        }
        
        int contentLen = fullContent.length();
        // int pageSize = PrivateReaderConfig.getInstance().getPageSize(); // - Incorrect access
        PrivateReaderConfig config = ApplicationManager.getApplication().getService(PrivateReaderConfig.class);
        int pageSize = (config != null) ? config.getNotificationPageSize() : 70; // + Use service instance
        this.totalPages = (int) Math.ceil((double) contentLen / pageSize);
        if (this.totalPages == 0) this.totalPages = 1;
        
        // Clamp currentPage
        if (this.currentPage < 1) this.currentPage = 1;
        if (this.currentPage > this.totalPages) this.currentPage = this.totalPages;
        
        int start = (this.currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, contentLen);
        String pageContent = fullContent.substring(start, end);
        
        LOG.info(String.format("Displaying page %d/%d (chars %d-%d)", this.currentPage, this.totalPages, start, end));
        setContent(pageContent, 0); // Display content for the current page
        
        // Update progress label (potentially)
        updateProgressInfo(); // Re-evaluate if this needs page info
        
        // Update navigation buttons based on local state
        // updateNavigationButtons(); 
    }
} 