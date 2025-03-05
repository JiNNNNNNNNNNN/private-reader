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
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.settings.*;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.ui.dialog.AddBookDialog;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import com.lv.tool.privatereader.storage.cache.ChapterPreloader;
import com.lv.tool.privatereader.ui.topics.BookshelfTopics;
import org.jetbrains.annotations.NotNull;

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
    private final JBList<Book> bookList;
    private final JBList<NovelParser.Chapter> chapterList;
    private final JTextPane contentArea;
    private final JBLabel progressLabel;
    private final JBLabel lastReadLabel;
    private final ReadingProgressManager progressManager;
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

    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    public PrivateReaderPanel(Project project) {
        LOG.info("初始化阅读器面板");
        this.project = project;
        this.progressManager = project.getService(ReadingProgressManager.class);
        instance = this;

        // 初始化必要的组件
        bookList = new JBList<>();
        chapterList = new JBList<>();
        contentArea = new JTextPane();
        progressLabel = new JBLabel();
        lastReadLabel = new JBLabel();
        
        // 从设置中获取阅读模式
        ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        isNotificationMode = modeSettings.isNotificationMode();

        // 检查插件是否启用
        PluginSettings pluginSettings = ApplicationManager.getApplication().getService(PluginSettings.class);
        if (!pluginSettings.isEnabled()) {
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
                    ReaderModeSettings settings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
                    boolean newMode = settings.isNotificationMode();
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
            if (!e.getValueIsAdjusting() && !isLoadingLastChapter) {
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
        BookStorage bookStorage = project.getService(BookStorage.class);
        List<Book> books = bookStorage.getAllBooks();
        bookList.setListData(books.toArray(new Book[0]));

        // 如果有书籍，立即加载章节列表
        if (!books.isEmpty()) {
            // 找到最后阅读的书籍
            Book lastBook = books.stream()
                    .filter(book -> book.getLastReadTimeMillis() > 0)
                    .max((b1, b2) -> Long.compare(b1.getLastReadTimeMillis(), b2.getLastReadTimeMillis()))
                    .orElse(books.get(0));  // 如果没有阅读记录，使用第一本书
            
            // 设置选中的书籍
            bookList.setSelectedValue(lastBook, true);
        }
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
        clearAllNotifications();
        
        // 重新加载书籍列表
        BookStorage bookStorage = project.getService(BookStorage.class);
        List<Book> books = bookStorage.getAllBooks();
        LOG.debug("获取到书籍数量: " + books.size());
        bookList.setListData(books.toArray(new Book[0]));
        
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
        refreshBtn.setEnabled(false);
        
        // 更新进度信息
        updateProgressInfo();
        
        // 如果有选中的书籍，更新章节列表
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            updateChapterList();
        }
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
        LOG.debug("设置阅读内容，长度: " + (content != null ? content.length() : 0));
        contentArea.setText(content);
        contentArea.setCaretPosition(0);

        // 应用字体设置
        applyFontSettings();
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

    private void navigateChapter(int offset) {
        // 切换章节前先保存当前章节ID
        String oldChapterId = currentChapterId;
        
        int currentIndex = chapterList.getSelectedIndex();
        if (currentIndex != -1) {
            int newIndex = currentIndex + offset;
            if (newIndex >= 0 && newIndex < chapterList.getModel().getSize()) {
                // 获取目标章节
                NovelParser.Chapter targetChapter = chapterList.getModel().getElementAt(newIndex);
                
                // 暂时移除监听器
                if (chapterListListener != null) {
                    chapterList.removeListSelectionListener(chapterListListener);
                }
                
                try {
                    // 设置选中索引
                    chapterList.setSelectedIndex(newIndex);
                    chapterList.ensureIndexIsVisible(newIndex);
                    
                    // 直接加载章节，避免触发监听器
                    loadChapter(targetChapter);
                } finally {
                    // 恢复监听器
                    if (chapterListListener != null) {
                        chapterList.addListSelectionListener(chapterListListener);
                    }
                }
            }
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
        if (isLoadingLastChapter) {
            LOG.debug("正在加载上次阅读章节，跳过重复加载");
            return;
        }
        
        try {
            isLoadingLastChapter = true;
            
            // 获取所有书籍（不加载详细信息）
            BookStorage bookStorage = project.getService(BookStorage.class);
            List<Book> books = bookStorage.getAllBooks(false);
            LOG.debug("获取所有书籍，数量: " + books.size());
            
            // 获取上次阅读的书籍
            Book lastBook = books.stream()
                    .filter(book -> book.getLastReadTimeMillis() > 0)
                    .max((b1, b2) -> Long.compare(b1.getLastReadTimeMillis(), b2.getLastReadTimeMillis()))
                    .orElse(null);
            
            if (lastBook != null) {
                // 加载详细信息
                lastBook = bookStorage.getBook(lastBook.getId());
                LOG.info("自动加载上次阅读的书籍: " + lastBook.getTitle());
                
                // 暂时移除所有监听器
                disableBookListListener();
                if (chapterListListener != null) {
                    chapterList.removeListSelectionListener(chapterListListener);
                }
                
                try {
                    // 设置书籍选择
                    bookList.setSelectedValue(lastBook, true);
                    
                    // 获取上次阅读的章节ID
                    String lastChapterId = lastBook.getLastReadChapterId();
                    if (lastChapterId != null && !lastChapterId.isEmpty()) {
                        LOG.info("加载上次阅读章节: " + lastBook.getLastReadChapter());
                        
                        // 先确保书籍有解析器
                        lastBook.setProject(project);
                        NovelParser parser = lastBook.getParser();
                        if (parser == null) {
                            parser = ParserFactory.createParser(lastBook.getUrl());
                            lastBook.setParser(parser);
                        }
                        
                        // 获取章节列表
                        List<NovelParser.Chapter> chapters = lastBook.getCachedChapters();
                        if (chapters == null || chapters.isEmpty()) {
                            LOG.info("章节缓存为空，获取章节列表");
                            chapters = parser.getChapterList(lastBook);
                            lastBook.setCachedChapters(chapters);
                            bookStorage.updateBook(lastBook);
                        }
                        
                        // 更新章节列表
                        if (chapters != null && !chapters.isEmpty()) {
                            chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                            
                            // 查找并选中上次阅读的章节
                            for (int i = 0; i < chapters.size(); i++) {
                                NovelParser.Chapter chapter = chapters.get(i);
                                if (chapter.url().equals(lastChapterId)) {
                                    LOG.info("找到上次阅读的章节，索引: " + i);
                                    chapterList.setSelectedIndex(i);
                                    chapterList.ensureIndexIsVisible(i);
                                    loadChapter(chapter);
                                    break;
                                }
                            }
                        }
                    }
                } finally {
                    // 恢复监听器
                    enableBookListListener();
                    setupChapterListListener();
                }
            }
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
        toggleModeButton = new JButton(AllIcons.Actions.Preview);
        styleIconButton(toggleModeButton, "当前：阅读器模式 (点击切换到通知栏模式)");
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
                    BookStorage bookStorage = project.getService(BookStorage.class);
                    bookStorage.removeBook(selectedBook);
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
                        project.getService(BookStorage.class).updateBook(selectedBook);
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
     * 加载并显示当前选中书籍的所有章节，
     * 如果有缓存则使用缓存，否则重新获取
     */
    private void updateChapterList() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            String message = String.format("更新章节列表 - 书籍: %s", selectedBook.getTitle());
            LOG.info(message);

            try {
                selectedBook.setProject(project);
                NovelParser parser = selectedBook.getParser();
                if (parser != null) {
                    List<NovelParser.Chapter> chapters = selectedBook.getCachedChapters();

                    if (chapters == null || chapters.isEmpty()) {
                        LOG.info("章节缓存不存在或为空，开始获取章节列表");
                        chapters = parser.getChapterList(selectedBook);
                        chapters = chapters.stream()
                                .filter(chapter -> chapter != null && !chapter.url().isEmpty())
                                .toList();
                        selectedBook.setCachedChapters(chapters);
                        // 只在获取新章节列表时更新存储
                        project.getService(BookStorage.class).updateBook(selectedBook);
                        message = String.format("成功获取章节列表 - 章节数: %d", chapters.size());
                        LOG.info(message);
                    } else {
                        message = String.format("使用缓存的章节列表 - 章节数: %d", chapters.size());
                        LOG.info(message);
                    }

                    // 移除监听器
                    if (chapterListListener != null) {
                        chapterList.removeListSelectionListener(chapterListListener);
                    }
                    
                    // 更新章节列表
                    chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));

                    // 选中当前阅读的章节
                    String lastChapterId = selectedBook.getLastReadChapterId();
                    if (lastChapterId != null && !lastChapterId.isEmpty()) {
                        LOG.info("尝试选中上次阅读的章节: " + selectedBook.getLastReadChapter());
                        for (int i = 0; i < chapters.size(); i++) {
                            NovelParser.Chapter chapter = chapters.get(i);
                            if (chapter != null && lastChapterId.equals(chapter.url())) {
                                LOG.info("找到上次阅读的章节，索引: " + i);
                                chapterList.setSelectedIndex(i);
                                chapterList.ensureIndexIsVisible(i);
                                
                                // 如果是在加载上次阅读章节的过程中，直接加载内容
                                if (isLoadingLastChapter) {
                                    loadChapter(chapter);
                                }
                                break;
                            }
                        }
                    } else {
                        if (!chapters.isEmpty()) {
                            chapterList.setSelectedIndex(0);
                            chapterList.ensureIndexIsVisible(0);
                        }
                    }
                    
                    // 只在非加载上次章节时添加监听器
                    if (!isLoadingLastChapter) {
                        setupChapterListListener();
                    }
                } else {
                    String error = "无法创建解析器，请检查书籍URL是否正确";
                    LOG.error(error);
                    showNotification(error, NotificationType.ERROR);
                    Messages.showErrorDialog(project, error, "错误");
                }
            } catch (Exception e) {
                String error = "获取章节列表失败: " + e.getMessage();
                LOG.error(error, e);
                showNotification(error, NotificationType.ERROR);
                Messages.showErrorDialog(project, error, "错误");
            }
        } else {
            chapterList.setListData(new NovelParser.Chapter[0]);
            setContent("");
            currentChapterId = null;
            refreshBtn.setEnabled(false);
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
     * 加载指定章节的内容
     * 包括更新进度、显示内容等操作
     */
    public void loadChapter(NovelParser.Chapter chapter) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook == null || chapter == null) {
            if (selectedBook == null) {
                LOG.error("未选择书籍");
                showNotification("未选择书籍", NotificationType.ERROR);
            }
            if (chapter == null) {
                LOG.error("章节为空");
                showNotification("章节为空", NotificationType.ERROR);
            }
            return;
        }

        // 如果是同一章节，不重复加载
        if (currentChapterId != null && currentChapterId.equals(chapter.url())) {
            LOG.debug("跳过加载相同章节: " + chapter.title());
            return;
        }

        String message = String.format("加载章节 - 书籍: %s, 章节: %s",
                selectedBook.getTitle(), chapter.title());
        LOG.info(message);

        String oldChapterId = currentChapterId;
        try {
            selectedBook.setProject(project);
            NovelParser parser = selectedBook.getParser();
            if (parser == null) {
                LOG.error("解析器为空，尝试重新创建");
                parser = ParserFactory.createParser(selectedBook.getUrl());
                selectedBook.setParser(parser);
            }

            if (parser == null) {
                showNotification("无法创建解析器", NotificationType.ERROR);
                return;
            }

            // 先尝试从缓存获取内容
            ChapterCacheManager cacheManager = project.getService(ChapterCacheManager.class);
            String content = cacheManager.getCachedContent(selectedBook.getId(), chapter.url());
            boolean isFromCache = content != null;

            // 如果缓存不存在或已过期，再从网络获取
            if (!isFromCache) {
                content = parser.getChapterContent(chapter.url(), selectedBook);
            }

            if (content == null || content.isEmpty()) {
                showNotification("章节内容为空", NotificationType.ERROR);
                return;
            }

            // 获取当前章节索引
            int currentIndex = chapterList.getSelectedIndex();
            
            // 先更新当前章节ID，避免重复通知
            currentChapterId = chapter.url();
            
            // 只在非缓存内容且非加载上次阅读章节时更新进度
            if (!isFromCache && !isLoadingLastChapter) {
                updateBookProgress(selectedBook, chapter, currentIndex, true);
            }

            if (isNotificationMode) {
                // 只有在章节ID变化时才清理通知
                if (oldChapterId == null || !oldChapterId.equals(chapter.url())) {
                    clearAllNotifications();
                    NotificationReaderSettings settings = ApplicationManager.getApplication()
                        .getService(NotificationReaderSettings.class);
                    int newTotalPages = (int) Math.ceil((double) content.length() / settings.getPageSize());
                    
                    // 如果是同一章节，保持当前页码；否则使用保存的页码或第一页
                    if (chapter.url().equals(selectedBook.getLastReadChapterId())) {
                        currentPage = Math.min(Math.max(selectedBook.getLastReadPage(), 1), newTotalPages);
                    } else {
                        currentPage = 1;
                    }
                    totalPages = newTotalPages;
                }
                showChapterInNotification(selectedBook, chapter, content, !isFromCache && !isLoadingLastChapter);
                setContent("已切换到通知栏阅读模式，请在左下角查看内容");
            } else {
                setContent(content);
            }

            // 启用刷新按钮
            refreshBtn.setEnabled(true);
            
            // 只在非缓存内容时启动后台预加载
            if (!isFromCache) {
                project.getService(ChapterPreloader.class).preloadChapters(selectedBook, currentIndex);
            }
        } catch (Exception e) {
            LOG.error("加载章节失败", e);
            showNotification("加载章节失败: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    private void showNotification(String content, NotificationType type) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(content, type)
                .setImportant(false);

        notification.hideBalloon();
        notification.notify(project);
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
                    project.getService(BookStorage.class).updateBook(selectedBook);
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

    private void showChapterInNotification(Book book, NovelParser.Chapter chapter, String content, boolean updateStorage) {
        // 先清理旧通知
        clearAllNotifications();

        // 如果通知数量超过限制，只保留最近的几条
        if (activeNotifications.size() >= MAX_NOTIFICATIONS) {
            // 只保留最近的几条通知
            while (activeNotifications.size() > MAX_NOTIFICATIONS / 2) {
                Notification oldNotification = activeNotifications.remove(0);
                oldNotification.expire();
            }
        }

        // 检查内容是否真的变化了
        boolean contentChanged = !content.equals(currentContent);
        currentContent = content;

        // 获取通知栏阅读设置
        NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
        int pageSize = settings.getPageSize();

        // 计算总页数
        int newTotalPages = (int) Math.ceil((double) content.length() / pageSize);
        boolean pagesChanged = newTotalPages != totalPages;
        totalPages = newTotalPages;

        // 确保页码在有效范围内
        int oldPage = currentPage;
        currentPage = Math.min(Math.max(currentPage, 1), totalPages);
        boolean pageChanged = oldPage != currentPage;

        // 计算当前页的内容
        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, content.length());
        String pageContent = content.substring(startIndex, endIndex);

        // 创建标题（只在第一页显示）
        String title = "";
        if (currentPage == 1) {
            title = String.format("%s - %s", book.getTitle(), chapter.title());
            if (settings.isShowPageNumber() && totalPages > 1) {
                title += String.format(" (%d/%d)", currentPage, totalPages);
            }
        } else if (settings.isShowPageNumber() && totalPages > 1) {
            title = String.format("(%d/%d)", currentPage, totalPages);
        }

        currentNotification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, pageContent, NotificationType.INFORMATION)
                .setDisplayId("private-reader-" + System.currentTimeMillis())
                .setImportant(false);

        // 添加到活跃通知列表
        activeNotifications.add(currentNotification);

        currentNotification.hideBalloon();
        currentNotification.notify(project);
        
        // 只在内容或页码变化时更新进度
        if ((contentChanged || pageChanged || pagesChanged) && updateStorage) {
            // 更新阅读进度，包括当前页数和位置
            progressManager.updateProgress(book, chapter.url(), chapter.title(), startIndex, currentPage);
            
            // 立即保存到存储
            project.getService(BookStorage.class).updateBook(book);
        }
    }

    private void clearAllNotifications() {
        // 清理所有活跃通知
        for (Notification notification : activeNotifications) {
            notification.expire();
            notification.hideBalloon();
        }
        activeNotifications.clear();
        if (currentNotification != null) {
            currentNotification.expire();
            currentNotification.hideBalloon();
            currentNotification = null;
        }
    }

    public void toggleReadingMode() {
        isNotificationMode = !isNotificationMode;
        
        // 更新设置
        ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        modeSettings.setNotificationMode(isNotificationMode);
        
        // 通知设置变更
        ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(ReaderModeSettings.TOPIC)
                .readerModeSettingsChanged();
        
        NovelParser.Chapter currentChapter = chapterList.getSelectedValue();
        Book selectedBook = bookList.getSelectedValue();

        // 更新按钮状态
        updateToggleModeButton();
        
        // 显示模式切换通知
        String modeMessage = isNotificationMode ? "已切换到通知栏模式" : "已切换到阅读器模式";
        showNotification(modeMessage, NotificationType.INFORMATION);

        if (isNotificationMode) {
            // 不再重置页码
            clearAllNotifications();
            if (currentChapter != null && selectedBook != null) {
                loadChapter(currentChapter);
            }
            contentArea.setText("已切换到通知栏阅读模式，请在左下角查看内容");
        } else {
            clearAllNotifications();
            if (currentChapter != null && selectedBook != null && currentContent != null) {
                setContent(currentContent);
            } else if (currentChapter != null && selectedBook != null) {
                loadChapter(currentChapter);
            }
        }
    }

    private void updateToggleModeButton() {
        if (isNotificationMode) {
            toggleModeButton.setIcon(AllIcons.Actions.PreviewDetails);
            toggleModeButton.setToolTipText("当前：通知栏模式 (点击切换到阅读器模式)");
        } else {
            toggleModeButton.setIcon(AllIcons.Actions.Preview);
            toggleModeButton.setToolTipText("当前：阅读器模式 (点击切换到通知栏模式)");
        }
    }

    public void prevPage() {
        if (!isNotificationMode) return;
        
        Book book = bookList.getSelectedValue();
        NovelParser.Chapter chapter = chapterList.getSelectedValue();
        int currentChapterIndex = chapterList.getSelectedIndex();
        
        if (book != null && chapter != null) {
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
                
            if (currentPage > 1) {
                currentPage--;
                // 保存当前页码
                progressManager.updateProgress(book, chapter.url(), chapter.title(), 
                    (currentPage - 1) * settings.getPageSize(), currentPage);
                showChapterInNotification(book, chapter, currentContent, true);
            } else if (currentChapterIndex > 0) {
                // 跳转到上一章最后一页
                NovelParser.Chapter prevChapter = chapterList.getModel().getElementAt(currentChapterIndex - 1);
                try {
                    String prevContent = book.getParser().getChapterContent(prevChapter.url(), book);
                    
                    // 计算总页数
                    totalPages = (int) Math.ceil((double) prevContent.length() / settings.getPageSize());
                    currentPage = totalPages; // 设置为最后一页
                    
                    // 更新状态
                    currentContent = prevContent;
                    currentChapterId = prevChapter.url();
                    
                    // 暂时移除监听器
                    if (chapterListListener != null) {
                        chapterList.removeListSelectionListener(chapterListListener);
                    }
                    
                    try {
                        // 清理旧通知并更新显示
                        clearAllNotifications();
                        chapterList.setSelectedIndex(currentChapterIndex - 1);
                        showChapterInNotification(book, prevChapter, prevContent, true);
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
            }
        }
    }
    
    public void nextPage() {
        if (!isNotificationMode) return;
        
        Book book = bookList.getSelectedValue();
        NovelParser.Chapter chapter = chapterList.getSelectedValue();
        int currentChapterIndex = chapterList.getSelectedIndex();
        
        if (book != null && chapter != null) {
            NotificationReaderSettings settings = ApplicationManager.getApplication()
                .getService(NotificationReaderSettings.class);
                
            if (currentPage < totalPages) {
                currentPage++;
                // 保存当前页码
                progressManager.updateProgress(book, chapter.url(), chapter.title(), 
                    (currentPage - 1) * settings.getPageSize(), currentPage);
                showChapterInNotification(book, chapter, currentContent, true);
            } else if (currentChapterIndex < chapterList.getModel().getSize() - 1) {
                // 跳转到下一章第一页
                NovelParser.Chapter nextChapter = chapterList.getModel().getElementAt(currentChapterIndex + 1);
                try {
                    String nextContent = book.getParser().getChapterContent(nextChapter.url(), book);
                    
                    // 计算总页数
                    totalPages = (int) Math.ceil((double) nextContent.length() / settings.getPageSize());
                    currentPage = 1; // 设置为第一页
                    
                    // 更新状态
                    currentContent = nextContent;
                    currentChapterId = nextChapter.url();
                    
                    // 暂时移除监听器
                    if (chapterListListener != null) {
                        chapterList.removeListSelectionListener(chapterListListener);
                    }
                    
                    try {
                        // 清理旧通知并更新显示
                        clearAllNotifications();
                        chapterList.setSelectedIndex(currentChapterIndex + 1);
                        showChapterInNotification(book, nextChapter, nextContent, true);
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
            }
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
                    ChapterCacheManager cacheManager = project.getService(ChapterCacheManager.class);
                    cacheManager.cacheContent(selectedBook.getId(), currentChapter.url(), content);
                    
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
} 