package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.parser.NovelParser;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.ui.dialog.AddBookDialog;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.settings.ReaderSettings;
import com.lv.tool.privatereader.settings.ReaderSettingsListener;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notification;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.text.StyleContext;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import com.intellij.icons.AllIcons;
import com.lv.tool.privatereader.settings.PluginSettings;

/**
 * 私人阅读器面板
 * 
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

        // 创建工具栏
        JPanel toolBar = createToolBar();
        add(toolBar, BorderLayout.NORTH);

        // 创建导航按钮
        prevChapterBtn = new JButton("上一章");
        nextChapterBtn = new JButton("下一章");
        prevChapterBtn.setFocusPainted(false);
        nextChapterBtn.setFocusPainted(false);
        JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        navigationPanel.setBorder(JBUI.Borders.empty(5));
        navigationPanel.add(prevChapterBtn);
        navigationPanel.add(nextChapterBtn);
        
        // 添加导航面板到底部
        add(navigationPanel, BorderLayout.SOUTH);
        
        // 添加导航按钮事件
        prevChapterBtn.addActionListener(e -> navigateChapter(-1));
        nextChapterBtn.addActionListener(e -> navigateChapter(1));

        // 创建书籍列表
        bookList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookList.setBorder(JBUI.Borders.empty(5));
        bookList.setBackground(UIManager.getColor("Tree.background"));
        bookList.addListSelectionListener(e -> {
            updateProgressInfo();
            updateChapterList();
        });
        JBScrollPane bookScrollPane = new JBScrollPane(bookList);
        bookScrollPane.setBorder(JBUI.Borders.customLine(UIManager.getColor("Separator.foreground"), 0, 0, 0, 0));

        // 创建章节列表
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.setBorder(JBUI.Borders.empty(5));
        chapterList.setBackground(UIManager.getColor("Tree.background"));
        chapterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();
                if (selectedChapter != null) {
                    loadChapter(selectedChapter);
                }
            }
        });
        JBScrollPane chapterScrollPane = new JBScrollPane(chapterList);
        chapterScrollPane.setBorder(JBUI.Borders.customLine(UIManager.getColor("Separator.foreground"), 1, 0, 0, 0));

        // 创建进度信息面板
        JPanel progressPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        progressPanel.add(progressLabel);
        progressPanel.add(lastReadLabel);
        progressPanel.setBorder(JBUI.Borders.empty(10));
        progressPanel.setBackground(UIManager.getColor("Tree.background"));

        // 创建内容区域
        contentArea.setEditable(false);
        contentArea.setBorder(JBUI.Borders.empty(20));
        contentArea.setBackground(UIManager.getColor("Tree.background"));
        applyFontSettings();
        JBScrollPane contentScrollPane = new JBScrollPane(contentArea);
        contentScrollPane.setBorder(JBUI.Borders.empty());
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
                    int dividerLocation = (int)(width * 0.3);
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
        
        // 自定义分隔线UI
        mainSplitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public int getDividerSize() {
                        return 1;
                    }

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(UIManager.getColor("Separator.foreground"));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });

        leftSplitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public int getDividerSize() {
                        return 1;
                    }

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(UIManager.getColor("Separator.foreground"));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        
        add(mainSplitPane, BorderLayout.CENTER);

        // 添加键盘快捷键
        setupKeyboardShortcuts();

        // 初始化数据
        refresh();
        setupBookSelectionListener();

        // 添加分隔线位置监听器
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
        BookStorage bookStorage = project.getService(BookStorage.class);
        List<Book> books = bookStorage.getAllBooks();
        LOG.debug("获取到书籍数量: " + books.size());
        bookList.setListData(books.toArray(new Book[0]));
        updateProgressInfo();
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
            progressManager.updateProgress(selectedBook, chapterId, chapterTitle, position);
            updateProgressInfo();
        } else {
            LOG.warn("无法更新阅读进度：未选择书籍");
        }
    }

    private void navigateChapter(int offset) {
        int currentIndex = chapterList.getSelectedIndex();
        if (currentIndex != -1) {
            int newIndex = currentIndex + offset;
            if (newIndex >= 0 && newIndex < chapterList.getModel().getSize()) {
                chapterList.setSelectedIndex(newIndex);
                chapterList.ensureIndexIsVisible(newIndex);
            }
        }
    }

    // 更新当前章节ID
    public void updateCurrentChapter(String chapterId) {
        this.currentChapterId = chapterId;
    }

    /**
     * 加载上次阅读的章节
     * 恢复书籍的阅读位置，包括章节内容和导航按钮状态
     */
    public void loadLastReadChapter() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            LOG.info("加载上次阅读章节: " + selectedBook.getTitle());
            try {
                // 设置project
                selectedBook.setProject(project);
                
                String lastChapterId = selectedBook.getLastReadChapterId();
                if (lastChapterId != null) {
                    currentChapterId = lastChapterId;
                    NovelParser parser = selectedBook.getParser();
                    String content = parser.getChapterContent(lastChapterId, selectedBook);
                    setContent(content);
                    
                    // 更新导航按钮状态
                    List<NovelParser.Chapter> chapters = parser.getChapterList(selectedBook);
                    int currentIndex = -1;
                    for (int i = 0; i < chapters.size(); i++) {
                        if (chapters.get(i).url().equals(currentChapterId)) {
                            currentIndex = i;
                            break;
                        }
                    }
                    
                    if (currentIndex != -1) {
                        prevChapterBtn.setEnabled(currentIndex > 0);
                        nextChapterBtn.setEnabled(currentIndex < chapters.size() - 1);
                    }
                }
            } catch (Exception e) {
                LOG.error("加载上次阅读章节失败: " + e.getMessage(), e);
                Messages.showErrorDialog(
                    project,
                    "加载上次阅读章节失败：" + e.getMessage(),
                    "错误"
                );
            }
        } else {
            LOG.warn("无法加载上次阅读章节：未选择书籍");
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
                        loadLastReadChapter();
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
            showNotification(message, NotificationType.INFORMATION);
            
            try {
                selectedBook.setProject(project);
                NovelParser parser = selectedBook.getParser();
                if (parser != null) {
                    List<NovelParser.Chapter> chapters = selectedBook.getCachedChapters();
                    
                    if (chapters == null || chapters.isEmpty()) {
                        LOG.info("章节缓存不存在或为空，开始获取章节列表");
                        chapters = parser.getChapterList(selectedBook);
                        // 过滤掉无效的章节
                        chapters = chapters.stream()
                            .filter(chapter -> chapter != null && !chapter.url().isEmpty())
                            .toList();
                        selectedBook.setCachedChapters(chapters);
                        // 更新存储
                        project.getService(BookStorage.class).updateBook(selectedBook);
                        message = String.format("成功获取章节列表 - 章节数: %d", chapters.size());
                        LOG.info(message);
                        showNotification(message, NotificationType.INFORMATION);
                    } else {
                        message = String.format("使用缓存的章节列表 - 章节数: %d", chapters.size());
                        LOG.info(message);
                        showNotification(message, NotificationType.INFORMATION);
                    }
                    
                    chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                    
                    // 选中当前阅读的章节
                    String lastChapterId = selectedBook.getLastReadChapterId();
                    if (lastChapterId != null && !lastChapterId.isEmpty()) {
                        for (int i = 0; i < chapters.size(); i++) {
                            NovelParser.Chapter chapter = chapters.get(i);
                            if (chapter != null && lastChapterId.equals(chapter.url())) {
                                chapterList.setSelectedIndex(i);
                                chapterList.ensureIndexIsVisible(i);
                                break;
                            }
                        }
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
        }
    }

    /**
     * 加载指定章节
     * 获取章节内容并更新阅读进度
     * @param chapter 要加载的章节信息
     */
    private void loadChapter(NovelParser.Chapter chapter) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null && chapter != null) {
            String message = String.format("加载章节 - 书籍: %s, 章节: %s", 
                selectedBook.getTitle(), chapter.title());
            LOG.info(message);
            showNotification(message, NotificationType.INFORMATION);
            
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
                
                String content = parser.getChapterContent(chapter.url(), selectedBook);
                if (content == null || content.isEmpty()) {
                    String error = "获取到的章节内容为空";
                    showNotification(error, NotificationType.ERROR);
                    throw new IllegalStateException(error);
                }
                
                setContent(content);
                currentChapterId = chapter.url();
                
                // 更新当前章节索引和总章节数
                int currentIndex = chapterList.getSelectedIndex();
                selectedBook.setCurrentChapterIndex(currentIndex + 1); // 设置为1-based索引
                selectedBook.setTotalChapters(chapterList.getModel().getSize());
                
                // 更新阅读进度
                progressManager.updateCurrentChapter(selectedBook, currentIndex);
                updateReadingProgress(currentChapterId, chapter.title(), 0);
                
                // 更新存储
                project.getService(BookStorage.class).updateBook(selectedBook);
                
                // 更新导航按钮状态
                prevChapterBtn.setEnabled(currentIndex > 0);
                nextChapterBtn.setEnabled(currentIndex < chapterList.getModel().getSize() - 1);
                
                // 更新进度显示
                updateProgressInfo();
            } catch (Exception e) {
                String error = "加载章节失败: " + e.getMessage();
                LOG.error(error, e);
                showNotification(error, NotificationType.ERROR);
                Messages.showErrorDialog(
                    project,
                    error,
                    "错误"
                );
            }
        } else {
            if (selectedBook == null) {
                LOG.error("未选择书籍");
                showNotification("未选择书籍", NotificationType.ERROR);
            }
            if (chapter == null) {
                LOG.error("章节为空");
                showNotification("章节为空", NotificationType.ERROR);
            }
        }
    }

    public void loadChapter(NovelParser.Chapter chapter, int chapterIndex) {
        if (chapter != null) {
            try {
                Book selectedBook = bookList.getSelectedValue();
                if (selectedBook != null) {
                    selectedBook.setProject(project);
                    NovelParser parser = selectedBook.getParser();
                    String content = parser.getChapterContent(chapter.url(), selectedBook);
                    setContent(content);
                    currentChapterId = chapter.url();
                    
                    // 更新阅读进度
                    progressManager.updateCurrentChapter(selectedBook, chapterIndex);
                    updateReadingProgress(currentChapterId, chapter.title(), 0);
                    
                    // 更新导航按钮状态
                    prevChapterBtn.setEnabled(chapterIndex > 0);
                    nextChapterBtn.setEnabled(chapterIndex < selectedBook.getTotalChapters() - 1);
                }
            } catch (Exception e) {
                LOG.error("加载章节失败: " + e.getMessage(), e);
                Messages.showErrorDialog(
                    project,
                    "加载章节失败：" + e.getMessage(),
                    "错误"
                );
            }
        }
    }

    private void showNotification(String content, NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(content, type)
            .notify(project);
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
        
        showNotification("字体设置已更新", NotificationType.INFORMATION);
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
} 