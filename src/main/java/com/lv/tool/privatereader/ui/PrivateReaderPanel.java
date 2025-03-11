package com.lv.tool.privatereader.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
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
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.ui.dialog.AddBookDialog;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import com.lv.tool.privatereader.storage.cache.ChapterPreloader;
import com.lv.tool.privatereader.ui.factory.UIComponentFactory;
import com.lv.tool.privatereader.ui.topics.BookshelfTopics;
import org.jetbrains.annotations.NotNull;
import com.lv.tool.privatereader.service.impl.BookServiceImpl;
import com.lv.tool.privatereader.service.impl.ChapterServiceImpl;
import com.lv.tool.privatereader.service.impl.NotificationServiceImpl;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import com.lv.tool.privatereader.repository.RepositoryModule;

import javax.swing.*;
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
public class PrivateReaderPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(PrivateReaderPanel.class);
    private static final SimpleDateFormat DATE_FORMAT;
    private static PrivateReaderPanel instance;
    private final Project project;
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

    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    public PrivateReaderPanel(Project project) {
        LOG.info("初始化阅读器面板");
        this.project = project;
        instance = this;

        // 初始化必要的组件为空值，避免NPE
        bookList = new JBList<>();
        chapterList = new JBList<>();
        contentArea = new JTextPane();
        progressLabel = new JBLabel();
        lastReadLabel = new JBLabel();
        
        // 获取服务实例
        try {
            // 尝试通过接口获取服务
            this.bookService = project.getService(BookService.class);
            this.chapterService = project.getService(ChapterService.class);
            this.notificationService = project.getService(NotificationService.class);
            
            // 如果通过接口获取失败，尝试直接获取实现类
            if (this.bookService == null) {
                LOG.warn("通过接口获取BookService失败，尝试直接获取实现类");
                BookServiceImpl impl = project.getService(BookServiceImpl.class);
                if (impl != null) {
                    this.bookService = impl;
                    LOG.info("成功获取BookServiceImpl");
                }
            }
            
            if (this.chapterService == null) {
                LOG.warn("通过接口获取ChapterService失败，尝试直接获取实现类");
                ChapterServiceImpl impl = project.getService(ChapterServiceImpl.class);
                if (impl != null) {
                    this.chapterService = impl;
                    LOG.info("成功获取ChapterServiceImpl");
                }
            }
            
            if (this.notificationService == null) {
                LOG.warn("通过接口获取NotificationService失败，尝试直接获取实现类");
                NotificationServiceImpl impl = project.getService(NotificationServiceImpl.class);
                if (impl != null) {
                    this.notificationService = impl;
                    LOG.info("成功获取NotificationServiceImpl");
                }
            }
            
            // 获取其他服务
            this.config = project.getService(PrivateReaderConfig.class);
            this.progressManager = project.getService(ReadingProgressManager.class);
            
            // 获取RepositoryModule
            this.repositoryModule = project.getService(RepositoryModule.class);
            if (this.repositoryModule != null) {
                this.bookRepository = repositoryModule.getBookRepository();
                this.chapterCacheRepository = repositoryModule.getChapterCacheRepository();
                LOG.info("RepositoryModule: 已初始化");
                LOG.info("BookRepository: " + (this.bookRepository != null ? "已初始化" : "未初始化"));
                LOG.info("ChapterCacheRepository: " + (this.chapterCacheRepository != null ? "已初始化" : "未初始化"));
            } else {
                LOG.warn("RepositoryModule未初始化，将使用旧的存储服务");
            }
            
            // 记录服务初始化状态
            LOG.info("BookService: " + (this.bookService != null ? "已初始化" : "未初始化"));
            LOG.info("ChapterService: " + (this.chapterService != null ? "已初始化" : "未初始化"));
            LOG.info("NotificationService: " + (this.notificationService != null ? "已初始化" : "未初始化"));
            LOG.info("PrivateReaderConfig: " + (this.config != null ? "已初始化" : "未初始化"));
            LOG.info("ReadingProgressManager: " + (this.progressManager != null ? "已初始化" : "未初始化"));
        } catch (Exception e) {
            LOG.error("获取服务实例时发生异常", e);
            this.bookService = null;
            this.chapterService = null;
            this.notificationService = null;
            this.config = null;
            this.progressManager = null;
            this.repositoryModule = null;
            this.bookRepository = null;
            this.chapterCacheRepository = null;
        }
        
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
        setBorder(JBUI.Borders.empty(10));
        
        // 注册设置变更监听
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(ReaderSettingsListener.TOPIC, () -> {
                    SwingUtilities.invokeLater(this::applyFontSettings);
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
                .subscribe(ReaderModeSettings.TOPIC, () -> {
                    boolean newMode = config.getReaderMode() == PrivateReaderConfig.ReaderMode.NOTIFICATION;
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

        // 注册书架变更监听，自动加载上次阅读的章节
        project.getMessageBus()
                .connect()
                .subscribe(BookshelfTopics.BOOK_UPDATED, book -> {
                    if (book != null) {
                        SwingUtilities.invokeLater(this::loadLastReadChapter);
                    }
                });

        // 创建工具栏
        JPanel toolBar = createToolBar();
        add(toolBar, BorderLayout.NORTH);

        // 创建导航按钮
        prevChapterBtn = new JButton("上一章", AllIcons.Actions.Back);
        nextChapterBtn = new JButton("下一章", AllIcons.Actions.Forward);
        
        // 设置按钮样式
        styleButton(prevChapterBtn);
        styleButton(nextChapterBtn);
        
        // 添加按钮事件监听
        prevChapterBtn.addActionListener(e -> navigateChapter(-1));
        nextChapterBtn.addActionListener(e -> navigateChapter(1));
        
        // 初始时禁用导航按钮
        prevChapterBtn.setEnabled(false);
        nextChapterBtn.setEnabled(false);

        // 创建进度面板
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        progressPanel.setBackground(UIManager.getColor("Tree.background"));
        progressPanel.add(prevChapterBtn);
        progressPanel.add(nextChapterBtn);
        progressPanel.add(progressLabel);
        progressPanel.add(lastReadLabel);

        // 创建书籍列表滚动面板
        JBScrollPane bookScrollPane = new JBScrollPane(bookList);
        bookScrollPane.setBorder(BorderFactory.createTitledBorder("书架"));
        bookScrollPane.setBackground(UIManager.getColor("Tree.background"));

        // 创建章节列表滚动面板
        JBScrollPane chapterScrollPane = new JBScrollPane(chapterList);
        chapterScrollPane.setBorder(BorderFactory.createTitledBorder("章节"));
        chapterScrollPane.setBackground(UIManager.getColor("Tree.background"));

        // 创建内容区域滚动面板
        JBScrollPane contentScrollPane = new JBScrollPane(contentArea);
        contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
        contentScrollPane.setBackground(UIManager.getColor("Tree.background"));

        // 创建右侧面板
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(progressPanel, BorderLayout.NORTH);
        rightPanel.add(contentScrollPane, BorderLayout.CENTER);
        rightPanel.setBackground(UIManager.getColor("Tree.background"));

        // 创建左侧面板（包含书籍列表和章节列表）
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                bookScrollPane, chapterScrollPane);
        leftSplitPane.setDividerLocation(200);
        leftSplitPane.setBorder(null);
        leftSplitPane.setBackground(UIManager.getColor("Tree.background"));
        leftSplitPane.setDividerSize(1);
        leftSplitPane.setContinuousLayout(true);

        // 添加分隔面板
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftSplitPane, rightPanel);
        mainSplitPane.setResizeWeight(0.3);
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setBorder(null);
        mainSplitPane.setBackground(UIManager.getColor("Tree.background"));
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setDividerSize(1);

        // 在组件显示后设置初始分隔位置
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int width = getWidth();
                if (width > 0) {
                    int dividerLocation = (int) (width * 0.3);
                    mainSplitPane.setDividerLocation(dividerLocation);
                    lastDividerLocation = dividerLocation;
                }
            }
        });

        // 设置最小尺寸
        leftSplitPane.setMinimumSize(new Dimension(0, 0));
        rightPanel.setMinimumSize(new Dimension(0, 0));

        // 设置左侧面板的首选大小
        leftSplitPane.setPreferredSize(new Dimension(250, 0));

        // 添加主分隔面板
        add(mainSplitPane, BorderLayout.CENTER);

        // 设置字体
        applyFontSettings();

        // 添加书籍列表选择监听器
        bookListListener = e -> {
            if (!e.getValueIsAdjusting() && !isLoadingLastChapter) {
                updateChapterList();
            }
        };
        bookList.addListSelectionListener(bookListListener);

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

        // 立即加载书籍列表和上次阅读的书籍
        loadBooks();
    }

    public static PrivateReaderPanel getInstance(Project project) {
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
    }

    /**
     * 更新阅读进度信息
     * 显示当前选中书籍的阅读进度、章节信息和最后阅读时间
     */
    private void updateProgressInfo() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            LOG.debug("更新书籍进度信息: " + selectedBook.getTitle());
            progressLabel.setText(String.format("阅读进度：%d/%d 章 (%.1f%%)",
                    selectedBook.getCurrentChapterIndex(),
                    selectedBook.getTotalChapters(),
                    selectedBook.getReadingProgress() * 100));

            if (selectedBook.getLastReadTimeMillis() > 0) {
                lastReadLabel.setText(String.format("上次阅读：%s - %s",
                        selectedBook.getLastReadChapter(),
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(selectedBook.getLastReadTimeMillis()))));
            } else {
                lastReadLabel.setText("尚未开始阅读");
            }
        } else {
            LOG.debug("未选择书籍");
            progressLabel.setText("阅读进度：-");
            lastReadLabel.setText("上次阅读：-");
        }
    }

    public JBList<Book> getBookList() {
        return bookList;
    }

    public JBList<NovelParser.Chapter> getChapterList() {
        return chapterList;
    }

    public void setContent(String content) {
        LOG.info("设置阅读内容 - 长度: " + (content != null ? content.length() : 0));
        if (content == null) {
            LOG.error("内容为null");
            return;
        }
        
        try {
            contentArea.setText(content);
            contentArea.setCaretPosition(0);
            LOG.info("内容设置成功，应用字体设置");
            applyFontSettings();
        } catch (Exception e) {
            LOG.error("设置内容失败", e);
            showNotification("设置内容失败: " + e.getMessage(), NotificationType.ERROR);
        }
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
    private void navigateChapter(int direction) {
        // 如果正在加载章节，跳过
        if (isLoadingChapter || isLoadingLastChapter) {
            LOG.info("正在加载章节，跳过导航");
            return;
        }
        
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null || currentChapterId == null) {
            return;
        }
        
        try {
            NovelParser.Chapter targetChapter = null;
            
            if (direction < 0) {
                // 获取上一章
                targetChapter = chapterService.getPreviousChapter(selectedBook, currentChapterId);
            } else {
                // 获取下一章
                targetChapter = chapterService.getNextChapter(selectedBook, currentChapterId);
            }
            
            if (targetChapter != null) {
                // 使用安全的方式设置选择
                if (chapterListListener != null) {
                    chapterList.removeListSelectionListener(chapterListListener);
                }
                
                try {
                    // 找到章节在列表中的索引
                    for (int i = 0; i < chapterList.getModel().getSize(); i++) {
                        if (chapterList.getModel().getElementAt(i).url().equals(targetChapter.url())) {
                            // 选中章节
                            chapterList.setSelectedIndex(i);
                            break;
                        }
                    }
                    
                    // 直接加载章节
                    loadChapter(targetChapter);
                } finally {
                    // 恢复监听器
                    if (chapterListListener != null) {
                        chapterList.addListSelectionListener(chapterListListener);
                    }
                }
            }
        } catch (Exception e) {
            // 处理异常
            ExceptionHandler.handle(project, e, "导航章节失败: " + e.getMessage());
            LOG.error("导航章节失败", e);
        }
    }

    // 更新当前章节ID
    public void updateCurrentChapter(String chapterId) {
        this.currentChapterId = chapterId;
    }

    /**
     * 加载上次阅读的章节
     */
    public void loadLastReadChapter() {
        // 如果已经在加载章节，跳过
        if (isLoadingLastChapter || isLoadingChapter) {
            LOG.info("已经在加载章节，跳过重复加载");
            return;
        }
        
        try {
            isLoadingLastChapter = true;
            
            // 获取最近阅读的书籍
            Book lastReadBook = bookService.getLastReadBook();
            if (lastReadBook == null) {
                isLoadingLastChapter = false;
                return;
            }
            
            // 确保Book对象的project属性被设置
            if (lastReadBook.getProject() == null) {
                lastReadBook.setProject(project);
            }
            
            // 暂时移除监听器
            if (bookListListener != null) {
                bookList.removeListSelectionListener(bookListListener);
            }
            if (chapterListListener != null) {
                chapterList.removeListSelectionListener(chapterListListener);
            }
            
            try {
                // 选中最近阅读的书籍
                bookList.setSelectedValue(lastReadBook, true);
                
                // 获取章节列表
                List<NovelParser.Chapter> chapters = chapterService.getChapters(lastReadBook);
                
                // 更新章节列表
                chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                
                // 查找上次阅读的章节
                String lastChapterId = lastReadBook.getLastReadChapterId();
                if (lastChapterId != null && !lastChapterId.isEmpty()) {
                    for (int i = 0; i < chapters.size(); i++) {
                        if (chapters.get(i).url().equals(lastChapterId)) {
                            // 选中章节
                            chapterList.setSelectedIndex(i);
                            
                            // 直接加载章节内容，避免触发监听器
                            loadChapter(chapters.get(i));
                            break;
                        }
                    }
                }
            } finally {
                // 恢复监听器
                if (bookListListener != null) {
                    bookList.addListSelectionListener(bookListListener);
                }
                if (chapterListListener != null) {
                    chapterList.addListSelectionListener(chapterListListener);
                }
            }
        } catch (Exception e) {
            // 处理异常
            ExceptionHandler.handle(project, e, "加载上次阅读章节失败: " + e.getMessage());
            LOG.error("加载上次阅读章节失败", e);
        } finally {
            isLoadingLastChapter = false;
        }
    }

    /**
     * 创建工具栏
     * 包含添加书籍、移除书籍、重置进度等功能按钮
     */
    private JPanel createToolBar() {
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolBar.setBorder(JBUI.Borders.empty(0, 0, 10, 0));
        toolBar.setBackground(UIManager.getColor("Tree.background"));

        // 隐藏/显示左侧面板按钮
        toggleLeftPanelButton = new JButton(AllIcons.Actions.ArrowCollapse);
        styleIconButton(toggleLeftPanelButton, "隐藏侧栏");
        toggleLeftPanelButton.addActionListener(e -> toggleLeftPanel());
        toolBar.add(toggleLeftPanelButton);

        // 添加阅读模式切换按钮
        toggleModeButton = new JButton(isNotificationMode ? AllIcons.Actions.ShowReadAccess : AllIcons.Actions.PreviewDetails);
        styleIconButton(toggleModeButton, isNotificationMode ? 
            "当前：通知栏模式 (点击切换到阅读器模式)" : 
            "当前：阅读器模式 (点击切换到通知栏模式)");
        toggleModeButton.addActionListener(e -> toggleReadingMode());
        toolBar.add(toggleModeButton);

        // 添加书籍按钮
        JButton addButton = new JButton(AllIcons.General.Add);
        styleIconButton(addButton, "添加书籍");
        addButton.addActionListener(e -> {
            AddBookDialog dialog = new AddBookDialog(project);
            dialog.show();
        });
        toolBar.add(addButton);

        // 移除书籍按钮
        JButton removeButton = new JButton(AllIcons.General.Remove);
        styleIconButton(removeButton, "移除书籍");
        removeButton.addActionListener(e -> {
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
        });
        toolBar.add(removeButton);

        // 重置进度按钮
        JButton resetButton = new JButton(AllIcons.Actions.Rollback);
        styleIconButton(resetButton, "重置进度");
        resetButton.addActionListener(e -> {
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
        });
        toolBar.add(resetButton);

        // 刷新章节列表按钮
        JButton refreshChaptersButton = new JButton(AllIcons.Actions.Refresh);
        styleIconButton(refreshChaptersButton, "刷新章节列表");
        refreshChaptersButton.addActionListener(e -> {
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
        });
        toolBar.add(refreshChaptersButton);

        // 添加刷新按钮
        refreshBtn = new JButton("刷新章节内容", AllIcons.Actions.Refresh);
        refreshBtn.setToolTipText("忽略缓存，重新获取当前章节的最新内容");
        refreshBtn.addActionListener(e -> reloadCurrentChapter());
        refreshBtn.setEnabled(false);
        toolBar.add(refreshBtn);

        return toolBar;
    }

    private void styleIconButton(JButton button, String tooltip) {
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setToolTipText(tooltip);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(28, 28));

        // 添加鼠标悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBackground(UIManager.getColor("Button.hoverBackground"));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });
    }

    private void setupBookSelectionListener() {
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
    }

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
     * 更新章节列表
     * 根据当前选中的书籍加载章节列表
     */
    private void updateChapterList() {
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
            setContent("");
            
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
            List<NovelParser.Chapter> chapters = chapterService.getChapters(selectedBook);
            
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
        } catch (Exception e) {
            // 处理异常
            LOG.error("加载章节列表失败", e);
            if (ExceptionHandler.class != null) {
                ExceptionHandler.handle(project, e, "加载章节列表失败: " + e.getMessage());
            } else {
                setContent("加载章节列表失败: " + e.getMessage());
            }
            
            // 确保监听器被重新添加
            if (chapterListListener != null) {
                chapterList.addListSelectionListener(chapterListListener);
            }
        }
    }

    /**
     * 统一更新书籍进度和状态
     */
    private void updateBookProgress(Book book, NovelParser.Chapter chapter, int chapterIndex, boolean updateStorage) {
        if (book == null || chapter == null) return;
        
        // 更新当前章节索引和总章节数
        book.setCurrentChapterIndex(chapterIndex + 1);
        book.setTotalChapters(chapterList.getModel().getSize());
        
        // 更新进度（只更新进度，不更新存储）
        progressManager.updateProgress(book, chapter.url(), chapter.title(), 0);
        
        // 更新进度显示
        updateProgressInfo();
        
        // 更新导航按钮状态
        prevChapterBtn.setEnabled(chapterIndex > 0);
        nextChapterBtn.setEnabled(chapterIndex < chapterList.getModel().getSize() - 1);
    }

    /**
     * 加载章节内容
     * 
     * @param chapter 要加载的章节
     */
    private void loadChapter(NovelParser.Chapter chapter) {
        // 检查是否正在加载章节，防止循环加载
        if (isLoadingChapter) {
            LOG.info("正在加载章节，跳过重复加载");
            return;
        }
        
        // 设置标志位
        isLoadingChapter = true;
        
        try {
            // 检查服务是否初始化
            if (chapterService == null || bookService == null) {
                LOG.error("服务未初始化");
                return;
            }
            
            if (chapter == null) {
                return;
            }
            
            Book selectedBook = bookList.getSelectedValue();
            if (selectedBook == null) {
                return;
            }
            
            // 如果是同一章节，不重复加载
            if (currentChapterId != null && currentChapterId.equals(chapter.url())) {
                LOG.info("跳过加载相同章节: " + chapter.title());
                return;
            }
            
            LOG.info("加载章节: " + chapter.title());
            
            // 确保Book对象的project属性被设置
            if (selectedBook.getProject() == null) {
                selectedBook.setProject(project);
            }
            
            // 更新当前章节ID
            currentChapterId = chapter.url();
            
            // 启用刷新按钮
            if (refreshBtn != null) {
                refreshBtn.setEnabled(true);
            }
            
            try {
                // 获取章节内容
                String content = chapterService.getChapterContent(selectedBook, currentChapterId);
                
                // 根据阅读模式显示内容
                if (isNotificationMode) {
                    // 判断是否需要更新存储
                    // 如果是加载上次阅读的章节，且章节ID匹配，则不更新存储
                    boolean shouldUpdateStorage = !(isLoadingLastChapter && 
                        selectedBook.getLastReadChapterId() != null && 
                        selectedBook.getLastReadChapterId().equals(chapter.url()));
                    
                    // 显示章节内容（页码恢复逻辑已移至showChapterInNotification方法）
                    showChapterInNotification(selectedBook, chapter, content, shouldUpdateStorage);
                } else {
                    // 在面板中显示内容
                    setContent(content);
                    
                    // 更新进度信息
                    updateProgressInfo();
                    
                    // 如果不是加载上次阅读的章节，才保存阅读进度
                    if (!isLoadingLastChapter || !chapter.url().equals(selectedBook.getLastReadChapterId())) {
                        bookService.saveReadingProgress(selectedBook, currentChapterId, 0);
                    }
                    
                    // 预加载后续章节
                    int currentIndex = chapterList.getSelectedIndex();
                    if (currentIndex >= 0) {
                        ChapterPreloader preloader = project.getService(ChapterPreloader.class);
                        if (preloader != null) {
                            preloader.preloadChapters(selectedBook, currentIndex);
                        }
                    }
                }
                
                // 更新导航按钮状态
                updateNavigationButtons();
            } catch (Exception e) {
                // 处理异常
                LOG.error("加载章节内容失败", e);
                if (ExceptionHandler.class != null) {
                    ExceptionHandler.handle(project, e, "加载章节内容失败: " + e.getMessage());
                } else {
                    setContent("加载章节内容失败: " + e.getMessage());
                }
            }
        } finally {
            // 重置标志位
            isLoadingChapter = false;
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
            notificationService.showChapterNotification(project, type.toString(), content);
        }
    }

    private void applyFontSettings() {
        ReaderSettings settings = ApplicationManager.getApplication().getService(ReaderSettings.class);

        // 创建字体样式
        Style style = contentArea.addStyle("customStyle", null);
        StyleConstants.setFontFamily(style, settings.getFontFamily());
        StyleConstants.setFontSize(style, settings.getFontSize());
        StyleConstants.setBold(style, settings.isBold());
        StyleConstants.setForeground(style, settings.getTextColor());

        // 应用样式到所有文本
        StyledDocument doc = contentArea.getStyledDocument();
        doc.setCharacterAttributes(0, doc.getLength(), style, true);

        // 设置默认样式
        contentArea.setCharacterAttributes(style, true);

        // 更新段落属性
        MutableAttributeSet paragraphAttrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragraphAttrs, 0.5f);  // 增加行距
        StyleConstants.setSpaceAbove(paragraphAttrs, 10.0f);  // 段落上间距
        StyleConstants.setSpaceBelow(paragraphAttrs, 10.0f);  // 段落下间距
        doc.setParagraphAttributes(0, doc.getLength(), paragraphAttrs, true);
    }

    public void refreshContent() {
        refresh();
        applyFontSettings();
    }

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
        mainSplitPane.setDividerSize(1);
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
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
        if (book == null || chapter == null || content == null || content.isEmpty()) {
            return;
        }
        
        // 检查服务是否初始化
        if (notificationService == null || bookService == null) {
            LOG.error("服务未初始化");
            setContent("通知服务未初始化，无法显示内容");
            return;
        }
        
        // 保存当前内容，供翻页使用
        currentContent = content;
        
        // 设置通知标题
        String title = book.getTitle() + " - " + chapter.title();
        
        // 如果是加载上次阅读的章节，先设置页码
        if (isLoadingLastChapter && book.getLastReadChapterId() != null && 
            book.getLastReadChapterId().equals(chapter.url()) && 
            book.getLastReadPage() > 1) {
            // 获取上次阅读的页码
            int lastPage = book.getLastReadPage();
            // 计算总页数
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
            int pageSize = settings != null ? settings.getPageSize() : 70;
            int totalPages = (int) Math.ceil((double) content.length() / pageSize);
            
            // 确保页码有效
            if (lastPage > 0 && lastPage <= totalPages) {
                // 计算目标页的内容
                int startPos = (lastPage - 1) * pageSize;
                int endPos = Math.min(startPos + pageSize, content.length());
                String pageContent = content.substring(startPos, endPos);
                
                // 创建带有页码信息的标题
                if (settings != null && settings.isShowPageNumber() && totalPages > 1) {
                    title += String.format(" [%d/%d]", lastPage, totalPages);
                }
                
                // 显示指定页的通知
                Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(pageContent, NotificationType.INFORMATION)
                    .setTitle(title);
                notification.notify(project);
                
                // 更新notificationService的状态
                if (notificationService instanceof NotificationServiceImpl) {
                    NotificationServiceImpl impl = (NotificationServiceImpl) notificationService;
                    impl.setCurrentContent(content);
                    impl.setCurrentPage(lastPage);
                    impl.setTotalPages(totalPages);
                    impl.setCurrentNotification(notification);
                }
                
                LOG.info("直接恢复到上次阅读的页码: " + lastPage + "/" + totalPages);
            } else {
                // 如果页码无效，显示第一页
                notificationService.showChapterNotification(project, title, content);
                LOG.warn("上次阅读的页码无效: " + lastPage + ", 总页数: " + totalPages + ", 显示第一页");
            }
        } else {
            // 正常显示第一页
            notificationService.showChapterNotification(project, title, content);
        }
        
        // 更新内容区域提示
        setContent("当前正在使用通知栏模式，内容已显示在通知栏中");
        
        // 更新进度信息
        updateProgressInfo();
        
        // 保存阅读进度
        if (updateStorage) {
            // 获取当前页码和总页数
            int currentPage = notificationService.getCurrentPage();
            int totalPages = notificationService.getTotalPages();
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
            int pageSize = settings != null ? settings.getPageSize() : 70;
            
            // 计算当前位置
            int position = (currentPage - 1) * pageSize;
            
            // 保存当前页码和位置到进度管理器
            progressManager.updateProgress(book, chapter.url(), chapter.title(), position, currentPage);
                
            // 保存阅读进度到书籍服务
            bookService.saveReadingProgress(book, chapter.url(), position);
            
            // 更新书籍的最后阅读页码
            book.setLastReadPage(currentPage);
            
            // 记录日志
            LOG.info("保存阅读进度 - 章节: " + chapter.title() + ", 页码: " + currentPage + "/" + totalPages + 
                    ", 位置: " + position);
        }
    }

    /**
     * 清除所有通知
     */
    private void clearAllNotifications() {
        if (notificationService != null) {
            notificationService.closeAllNotifications();
        }
    }

    public void toggleReadingMode() {
        isNotificationMode = !isNotificationMode;
        
        // 更新设置
        ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        modeSettings.setNotificationMode(isNotificationMode);
        
        // 更新按钮状态
        updateToggleModeButton();
        
        // 获取当前状态
        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
        Book selectedBook = bookList.getSelectedValue();
        
        // 清理所有通知
        clearAllNotifications();
        
        // 如果没有选中的章节或书籍，显示提示信息
        if (currentChapter == null || selectedBook == null) {
            setContent("请选择左侧书籍和章节开始阅读");
            return;
        }
        
        // 如果没有当前内容，重新加载章节
        if (currentContent == null) {
            loadChapter(currentChapter);
            return;
        }
        
        if (isNotificationMode) {
            // 切换到通知栏模式
            showChapterInNotification(selectedBook, currentChapter, currentContent, true);
            setContent("当前正在使用通知栏模式");
        } else {
            // 切换到阅读器模式
            setContent(currentContent);
        }
        
        // 通知设置变更
        ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(ReaderModeSettings.TOPIC)
                .readerModeSettingsChanged();
                
        // 显示模式切换通知
        String modeMessage = isNotificationMode ? "已切换到通知栏模式" : "已切换到阅读器模式";
        showNotification(modeMessage, NotificationType.INFORMATION);
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
        if (!isNotificationMode) return;
        
        // 检查服务是否初始化
        if (notificationService == null) {
            LOG.error("通知服务未初始化");
            return;
        }
        
        Book book = bookList.getSelectedValue();
        NovelParser.Chapter chapter = chapterList.getSelectedValue();
        int currentChapterIndex = chapterList.getSelectedIndex();
        
        if (book == null || chapter == null) {
            return;
        }
        
        // 尝试显示上一页
        boolean success = notificationService.showPreviousPage();
        
        // 如果当前已经是第一页，且不是第一章，则跳转到上一章
        if (!success && currentChapterIndex > 0) {
                // 跳转到上一章最后一页
                NovelParser.Chapter prevChapter = chapterList.getModel().getElementAt(currentChapterIndex - 1);
                try {
                // 确保Book对象的project属性被设置
                if (book.getProject() == null) {
                    book.setProject(project);
                }
                
                // 获取上一章内容
                    String prevContent = book.getParser().getChapterContent(prevChapter.url(), book);
                    
                    // 暂时移除监听器
                    if (chapterListListener != null) {
                        chapterList.removeListSelectionListener(chapterListListener);
                    }
                    
                    try {
                    // 清理旧通知
                        clearAllNotifications();
                    
                    // 选中上一章
                        chapterList.setSelectedIndex(currentChapterIndex - 1);
                    
                    // 显示上一章内容（最后一页）
                    currentContent = prevContent;
                    currentChapterId = prevChapter.url();
                    
                    // 计算总页数和最后一页的起始位置
                    NotificationReaderSettings settings = ApplicationManager.getApplication()
                        .getService(NotificationReaderSettings.class);
                    int pageSize = settings != null ? settings.getPageSize() : 70;
                    int totalPages = (int) Math.ceil((double) prevContent.length() / pageSize);
                    int lastPageStart = (totalPages - 1) * pageSize;
                    
                    // 获取最后一页的内容
                    String lastPageContent = lastPageStart < prevContent.length() ? 
                        prevContent.substring(lastPageStart) : prevContent;
                    
                    // 创建带有页码信息的标题
                    String title = book.getTitle() + " - " + prevChapter.title();
                    if (settings != null && settings.isShowPageNumber() && totalPages > 1) {
                        title += String.format(" [%d/%d]", totalPages, totalPages);
                    }
                    
                    // 关闭所有通知
                    clearAllNotifications();
                    
                    // 直接创建并显示最后一页的通知
                    Notification notification = NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification(lastPageContent, NotificationType.INFORMATION)
                        .setTitle(title);
                    notification.notify(project);
                    
                    // 更新notificationService的状态
                    if (notificationService instanceof NotificationServiceImpl) {
                        NotificationServiceImpl impl = (NotificationServiceImpl) notificationService;
                        impl.setCurrentContent(prevContent);
                        impl.setCurrentPage(totalPages);
                        impl.setTotalPages(totalPages);
                        impl.setCurrentNotification(notification);
                    }
                    
                    // 更新进度信息
                    updateProgressInfo();
                    
                    // 保存阅读进度
                    progressManager.updateProgress(book, prevChapter.url(), prevChapter.title(), 
                        lastPageStart, totalPages);
                    bookService.saveReadingProgress(book, prevChapter.url(), lastPageStart);
                    } finally {
                        // 恢复监听器
                        if (chapterListListener != null) {
                            chapterList.addListSelectionListener(chapterListListener);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("获取上一章内容失败", ex);
                    showNotification("获取上一章内容失败: " + ex.getMessage(), NotificationType.ERROR);
                }
        } else if (success && book != null && chapter != null) {
            // 更新阅读进度
            int currentPage = notificationService.getCurrentPage();
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
            int pageSize = settings != null ? settings.getPageSize() : 70;
            
            // 保存当前页码和位置
            progressManager.updateProgress(book, chapter.url(), chapter.title(), 
                (currentPage - 1) * pageSize, currentPage);
        }
    }
    
    /**
     * 显示下一页内容
     */
    public void nextPage() {
        if (!isNotificationMode) return;
        
        // 检查服务是否初始化
        if (notificationService == null) {
            LOG.error("通知服务未初始化");
            return;
        }
        
        Book book = bookList.getSelectedValue();
        NovelParser.Chapter chapter = chapterList.getSelectedValue();
        int currentChapterIndex = chapterList.getSelectedIndex();
        
        if (book == null || chapter == null) {
            return;
        }
        
        // 尝试显示下一页
        boolean success = notificationService.showNextPage();
        
        // 如果当前已经是最后一页，且不是最后一章，则跳转到下一章
        if (!success && currentChapterIndex < chapterList.getModel().getSize() - 1) {
                // 跳转到下一章第一页
                NovelParser.Chapter nextChapter = chapterList.getModel().getElementAt(currentChapterIndex + 1);
                try {
                // 确保Book对象的project属性被设置
                if (book.getProject() == null) {
                    book.setProject(project);
                }
                
                // 获取下一章内容
                    String nextContent = book.getParser().getChapterContent(nextChapter.url(), book);
                    
                    // 暂时移除监听器
                    if (chapterListListener != null) {
                        chapterList.removeListSelectionListener(chapterListListener);
                    }
                    
                    try {
                    // 清理旧通知
                        clearAllNotifications();
                    
                    // 选中下一章
                        chapterList.setSelectedIndex(currentChapterIndex + 1);
                    
                    // 更新状态
                    currentContent = nextContent;
                    currentChapterId = nextChapter.url();
                    
                    // 计算总页数
                    NotificationReaderSettings settings = ApplicationManager.getApplication()
                        .getService(NotificationReaderSettings.class);
                    int pageSize = settings != null ? settings.getPageSize() : 70;
                    int totalPages = (int) Math.ceil((double) nextContent.length() / pageSize);
                    
                    // 获取第一页的内容
                    String firstPageContent = nextContent.length() > pageSize ? 
                        nextContent.substring(0, pageSize) : nextContent;
                    
                    // 创建带有页码信息的标题
                    String title = book.getTitle() + " - " + nextChapter.title();
                    if (settings != null && settings.isShowPageNumber() && totalPages > 1) {
                        title += String.format(" [%d/%d]", 1, totalPages);
                    }
                    
                    // 关闭所有通知
                    clearAllNotifications();
                    
                    // 直接创建并显示第一页的通知
                    Notification notification = NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification(firstPageContent, NotificationType.INFORMATION)
                        .setTitle(title);
                    notification.notify(project);
                    
                    // 更新notificationService的状态
                    if (notificationService instanceof NotificationServiceImpl) {
                        NotificationServiceImpl impl = (NotificationServiceImpl) notificationService;
                        impl.setCurrentContent(nextContent);
                        impl.setCurrentPage(1);
                        impl.setTotalPages(totalPages);
                        impl.setCurrentNotification(notification);
                    }
                    
                    // 更新进度信息
                    updateProgressInfo();
                    
                    // 保存阅读进度
                    progressManager.updateProgress(book, nextChapter.url(), nextChapter.title(), 0, 1);
                    bookService.saveReadingProgress(book, nextChapter.url(), 0);
                    } finally {
                        // 恢复监听器
                        if (chapterListListener != null) {
                            chapterList.addListSelectionListener(chapterListListener);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("获取下一章内容失败", ex);
                    showNotification("获取下一章内容失败: " + ex.getMessage(), NotificationType.ERROR);
                }
        } else if (success && book != null && chapter != null) {
            // 更新阅读进度
            int currentPage = notificationService.getCurrentPage();
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
            int pageSize = settings != null ? settings.getPageSize() : 70;
            
            // 保存当前页码和位置
            progressManager.updateProgress(book, chapter.url(), chapter.title(), 
                (currentPage - 1) * pageSize, currentPage);
        }
    }

    public boolean isNotificationMode() {
        return isNotificationMode;
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
                        setContent(content);
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
        try {
            // 关闭预加载服务
            ChapterPreloader preloader = project.getService(ChapterPreloader.class);
            if (preloader != null) {
                preloader.shutdown();
            }
            
            // 清理通知
            clearAllNotifications();
            
            LOG.info("阅读器面板资源已释放");
        } catch (Exception e) {
            LOG.error("释放资源时发生错误: " + e.getMessage(), e);
        }
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
    }

    /**
     * 选择并加载指定章节
     * 供外部类调用
     * 
     * @param chapter 要加载的章节
     */
    public void selectAndLoadChapter(NovelParser.Chapter chapter) {
        if (chapter == null) {
            return;
        }
        
        // 查找章节在列表中的索引
        for (int i = 0; i < chapterList.getModel().getSize(); i++) {
            if (chapterList.getModel().getElementAt(i).url().equals(chapter.url())) {
                // 选中章节
                    chapterList.setSelectedIndex(i);
                    break;
                }
            }
            
            // 加载章节内容
            loadChapter(chapter);
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
        List<Book> books;
        if (bookRepository != null) {
            books = bookRepository.getAllBooks();
        } else {
            BookStorage bookStorage = project.getService(BookStorage.class);
            books = bookStorage.getAllBooks();
        }
        bookList.setListData(books.toArray(new Book[0]));
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
                if (bookRepository != null) {
                    bookRepository.removeBook(selectedBook);
                } else {
                    BookStorage bookStorage = project.getService(BookStorage.class);
                    bookStorage.removeBook(selectedBook);
                }
                refresh();
            }
        }
    }

    private void updateBookInfo() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            if (bookRepository != null) {
                bookRepository.updateBook(selectedBook);
            } else {
                project.getService(BookStorage.class).updateBook(selectedBook);
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
} 