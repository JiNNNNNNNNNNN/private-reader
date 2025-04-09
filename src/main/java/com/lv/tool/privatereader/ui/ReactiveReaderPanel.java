package com.lv.tool.privatereader.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.service.BookService;
import com.intellij.util.messages.MessageBusConnection;
import com.lv.tool.privatereader.settings.ReaderSettings;
import com.lv.tool.privatereader.settings.ReaderSettingsListener;
import com.lv.tool.privatereader.settings.CacheSettings;
import com.lv.tool.privatereader.settings.CacheSettingsListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 响应式阅读器面板
 * 使用ReactiveUIAdapter加载和显示内容
 */
public class ReactiveReaderPanel extends SimpleToolWindowPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(ReactiveReaderPanel.class);
    
    // 高亮颜色常量
    private static final Color SELECTION_BACKGROUND = new Color(0, 120, 215);
    private static final Color SELECTION_FOREGROUND = Color.WHITE;
    
    private final Project project;
    private final ReactiveUIAdapter uiAdapter;
    private final BookService bookService;
    
    // 设置监听器
    private final MessageBusConnection messageBusConnection;
    private final ReaderSettingsListener readerSettingsListener;
    private final CacheSettingsListener cacheSettingsListener;
    
    // UI组件
    private final DefaultListModel<Book> booksListModel;
    private final JBList<Book> booksList;
    private final DefaultListModel<Chapter> chaptersListModel;
    private final JBList<Chapter> chaptersList;
    private final JTextArea contentTextArea;
    private final JBScrollPane contentScrollPane;
    private final JLabel loadingLabel;
    private final JButton refreshButton;
    private final JButton addBookButton;
    private final JButton deleteBookButton;
    private final JTextField searchField;
    
    // 当前选中的书籍和章节
    private Book selectedBook;
    private Chapter selectedChapter;
    
    public ReactiveReaderPanel(Project project) {
        super(true);
        this.project = project;
        this.uiAdapter = new ReactiveUIAdapter(project);
        
        // Initialize BookService
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        if (this.bookService == null) {
            LOG.error("Failed to get BookService instance. Reading progress saving will be disabled.");
        }
        
        // 初始化设置监听器
        this.messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        this.readerSettingsListener = this::handleReaderSettingsChanged;
        this.cacheSettingsListener = this::handleCacheSettingsChanged;
        
        // 注册设置监听器
        messageBusConnection.subscribe(ReaderSettingsListener.TOPIC, readerSettingsListener);
        messageBusConnection.subscribe(CacheSettingsListener.TOPIC, cacheSettingsListener);
        
        // 设置章节加载完成回调
        uiAdapter.setOnChaptersLoaded(this::selectLastReadChapter);
        // 设置书籍加载完成回调
        uiAdapter.setOnBooksLoaded(this::selectAndHighlightLastReadBook);
        
        // 初始化UI组件
        booksListModel = new DefaultListModel<>();
        booksList = new JBList<>(booksListModel);
        booksList.setCellRenderer(new BookListCellRenderer());
        
        chaptersListModel = new DefaultListModel<>();
        chaptersList = new JBList<>(chaptersListModel);
        chaptersList.setCellRenderer(new ChapterListCellRenderer());
        
        contentTextArea = new JTextArea();
        contentTextArea.setEditable(false);
        contentTextArea.setLineWrap(true);
        contentTextArea.setWrapStyleWord(true);
        contentTextArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        contentTextArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        contentScrollPane = new JBScrollPane(contentTextArea);
        
        loadingLabel = new JLabel("加载中...");
        loadingLabel.setVisible(false);
        
        refreshButton = new JButton("刷新");
        addBookButton = new JButton("添加书籍");
        deleteBookButton = new JButton("删除书籍");
        searchField = new JTextField(20);
        
        // 设置布局
        setupLayout();
        
        // 添加事件监听器
        setupEventListeners();
        
        // 加载书籍列表 (选择和高亮将通过回调处理)
        loadBooks();
        
        LOG.info("初始化ReactiveReaderPanel");
    }
    
    /**
     * 设置布局
     */
    private void setupLayout() {
        // 创建左侧面板（书籍和章节列表）
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // 书籍列表面板
        JPanel booksPanel = new JPanel(new BorderLayout());
        JPanel booksToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        booksToolbar.add(addBookButton);
        booksToolbar.add(deleteBookButton);
        booksToolbar.add(new JLabel("搜索:"));
        booksToolbar.add(searchField);
        booksPanel.add(booksToolbar, BorderLayout.NORTH);
        booksPanel.add(new JBScrollPane(booksList), BorderLayout.CENTER);
        
        // 章节列表面板
        JPanel chaptersPanel = new JPanel(new BorderLayout());
        JPanel chaptersToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chaptersToolbar.add(refreshButton);
        chaptersPanel.add(chaptersToolbar, BorderLayout.NORTH);
        chaptersPanel.add(new JBScrollPane(chaptersList), BorderLayout.CENTER);
        
        // 将书籍和章节列表添加到左侧面板
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, booksPanel, chaptersPanel);
        leftSplitPane.setDividerLocation(200);
        leftPanel.add(leftSplitPane, BorderLayout.CENTER);
        
        // 创建右侧面板（内容显示）
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(contentScrollPane, BorderLayout.CENTER);
        
        // 加载状态面板
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(loadingLabel);
        rightPanel.add(statusPanel, BorderLayout.SOUTH);
        
        // 创建主分割面板
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplitPane.setDividerLocation(300);
        
        // 设置主面板
        setContent(mainSplitPane);
    }
    
    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        // 书籍列表选择事件
        booksList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && booksList.getSelectedValue() != null) {
                saveCurrentProgress();
                selectedBook = booksList.getSelectedValue();
                
                // 获取完整的书籍信息
                if (bookService != null) {
                    try {
                        Book completeBook = bookService.getBookById(selectedBook.getId()).block();
                        if (completeBook != null) {
                            LOG.info("Loaded complete book info for: " + completeBook.getTitle() + 
                                    " (ID: " + completeBook.getId() + 
                                    ", Last Chapter: " + completeBook.getLastReadChapterId() + 
                                    ", Position: " + completeBook.getLastReadPosition() + ")");
                            selectedBook = completeBook;
                        } else {
                            LOG.warn("Failed to load complete book info (null returned) for: " + selectedBook.getTitle() + " (ID: " + selectedBook.getId() + ")");
                        }
                    } catch (Exception ex) {
                        LOG.error("Error blocking/getting complete book info for: " + selectedBook.getTitle(), ex);
                    }
                } else {
                    LOG.error("BookService is not initialized. Cannot load complete book info.");
                }
                
                chaptersListModel.clear();
                contentTextArea.setText("");
                loadChapters(selectedBook);
                selectLastReadChapter();
            }
        });
        
        // 章节列表选择事件
        chaptersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && chaptersList.getSelectedValue() != null && selectedBook != null) {
                selectedChapter = chaptersList.getSelectedValue();
                loadChapterContent(selectedBook, selectedChapter);
            }
        });
        
        // 章节列表单击/双击事件 (Combined logic)
        chaptersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = chaptersList.locationToIndex(e.getPoint());
                if (index >= 0 && selectedBook != null) {
                    Chapter clickedChapter = chaptersListModel.getElementAt(index);
                    boolean selectionChanged = !clickedChapter.equals(selectedChapter);
                    
                    if (selectionChanged || e.getClickCount() == 2) {
                        saveCurrentProgress();
                        selectedChapter = clickedChapter;
                        chaptersList.setSelectedIndex(index);
                        loadChapterContent(selectedBook, selectedChapter);
                    }
                }
            }
        });
        
        // 刷新按钮点击事件
        refreshButton.addActionListener(e -> {
            if (selectedBook != null) {
                loadChapters(selectedBook);
            }
        });
        
        // 添加书籍按钮点击事件
        addBookButton.addActionListener(e -> {
            String url = JOptionPane.showInputDialog(this, "请输入小说网址:", "添加书籍", JOptionPane.PLAIN_MESSAGE);
            if (url != null && !url.trim().isEmpty()) {
                uiAdapter.addNewBook(url.trim(), booksListModel, loadingLabel, null);
            }
        });
        
        // 删除书籍按钮点击事件
        deleteBookButton.addActionListener(e -> {
            if (selectedBook != null) {
                int result = JOptionPane.showConfirmDialog(
                    this,
                    "确定要删除书籍 \"" + selectedBook.getTitle() + "\" 吗?",
                    "删除书籍",
                    JOptionPane.YES_NO_OPTION
                );
                
                if (result == JOptionPane.YES_OPTION) {
                    uiAdapter.deleteBook(selectedBook, booksListModel);
                    selectedBook = null;
                    chaptersListModel.clear();
                    contentTextArea.setText("");
                }
            }
        });
        
        // 搜索框事件
        searchField.addActionListener(e -> {
            String keyword = searchField.getText().trim();
            uiAdapter.searchBooks(keyword, booksListModel, loadingLabel);
        });
    }
    
    /**
     * 加载书籍列表 (仅启动加载)
     */
    private void loadBooks() {
        LOG.info("Initiating books loading...");
        uiAdapter.loadAllBooks(booksListModel, loadingLabel);
    }
    
    /**
     * 在书籍加载完成后选择并高亮最后阅读的书籍
     */
    private void selectAndHighlightLastReadBook() {
        LOG.info("Selecting and highlighting last read book...");
        // 获取最后阅读的书籍并自动选择
        if (bookService != null) {
            bookService.getLastReadBook()
                    .publishOn(ReactiveSchedulers.getInstance().ui())
                    .subscribe(
                            lastReadBook -> {
                                if (lastReadBook != null) {
                                    LOG.info("Found last read book: " + lastReadBook.getTitle());
                                    // 查找书籍在模型中的索引
                                    int indexToSelect = -1;
                                    for (int i = 0; i < booksListModel.getSize(); i++) {
                                        if (booksListModel.getElementAt(i).equals(lastReadBook)) {
                                            indexToSelect = i;
                                            break;
                                        }
                                    }
                                    
                                    if (indexToSelect != -1) {
                                        // 先设置 selectedBook，这样在触发选择事件时就能使用完整的书籍信息
                                        selectedBook = booksListModel.getElementAt(indexToSelect); // 使用模型中的实例
                                        // 触发选择事件并确保高亮显示
                                        booksList.setSelectedIndex(indexToSelect);
                                        booksList.ensureIndexIsVisible(indexToSelect);
                                        // 设置选中背景色
                                        booksList.setSelectionBackground(SELECTION_BACKGROUND);
                                        booksList.setSelectionForeground(SELECTION_FOREGROUND);
                                        // 直接加载章节
                                        loadChapters(selectedBook);
                                    } else {
                                        LOG.warn("Last read book found but not present in the loaded list model. Selecting first book instead.");
                                        selectAndHighlightFirstBook();
                                    }
                                } else {
                                    LOG.info("No last read book found, selecting first book if available");
                                    selectAndHighlightFirstBook();
                                }
                            },
                            error -> {
                                LOG.error("Error loading last read book: " + error.getMessage(), error);
                                Messages.showErrorDialog(project, "加载上次阅读书籍失败: " + error.getMessage(), "加载错误");
                                selectAndHighlightFirstBook();
                            }
                    );
        } else {
            LOG.error("BookService is not initialized. Cannot select last read book.");
        }
    }
    
    /**
     * 选择并高亮第一本书 (如果列表不为空)
     */
    private void selectAndHighlightFirstBook() {
        if (!booksListModel.isEmpty()) {
            selectedBook = booksListModel.getElementAt(0);
            booksList.setSelectedIndex(0);
            booksList.ensureIndexIsVisible(0);
            booksList.setSelectionBackground(SELECTION_BACKGROUND);
            booksList.setSelectionForeground(SELECTION_FOREGROUND);
            loadChapters(selectedBook);
        }
    }
    
    /**
     * 加载章节列表
     */
    private void loadChapters(Book book) {
        if (book != null) {
            LOG.info("Loading chapters for book: " + book.getTitle());
            // 显示加载状态
            loadingLabel.setVisible(true);
            // 使用响应式适配器加载章节
            uiAdapter.loadBookChapters(book, chaptersListModel, chaptersList, loadingLabel);
        }
    }
    
    /**
     * 加载章节内容
     */
    private void loadChapterContent(Book book, Chapter chapter) {
        uiAdapter.loadChapterContent(book, chapter, contentTextArea, loadingLabel);
        SwingUtilities.invokeLater(() -> {
            int targetPosition = 0;
            if (book != null && chapter != null && book.getLastReadChapterId() != null &&
                chapter.url() != null && chapter.url().equals(book.getLastReadChapterId())) {
                targetPosition = book.getLastReadPosition();
                LOG.debug("Restoring scroll position for " + book.getTitle() + " / " + chapter.title() + " to " + targetPosition);
            } else {
                LOG.debug("Setting scroll position to top for: " + (chapter != null ? chapter.title() : "null"));
            }
            JScrollBar verticalScrollBar = contentScrollPane.getVerticalScrollBar();
            targetPosition = Math.max(0, Math.min(targetPosition, verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount()));
            verticalScrollBar.setValue(targetPosition);
        });
    }
    
    /**
     * Helper method to select the last read chapter in the list.
     */
    private void selectLastReadChapter() {
        if (selectedBook == null || chaptersListModel.isEmpty()) {
            return;
        }
        String lastReadChapterId = selectedBook.getLastReadChapterId();
        if (lastReadChapterId != null && !lastReadChapterId.isEmpty()) {
            for (int i = 0; i < chaptersListModel.getSize(); i++) {
                Chapter chapter = chaptersListModel.getElementAt(i);
                if (lastReadChapterId.equals(chapter.url())) {
                    LOG.debug("Auto-selecting last read chapter: " + chapter.title());
                    chaptersList.setSelectedIndex(i);
                    chaptersList.ensureIndexIsVisible(i);
                    return;
                }
            }
            LOG.warn("Last read chapter ID [" + lastReadChapterId + "] not found in the loaded chapter list for " + selectedBook.getTitle());
        } else {
            LOG.debug("No last read chapter ID found for " + selectedBook.getTitle() + ", selecting first chapter.");
            if (!chaptersListModel.isEmpty()) {
                chaptersList.setSelectedIndex(0);
                chaptersList.ensureIndexIsVisible(0);
            }
        }
    }
    
    /**
     * Helper method to save current reading progress.
     */
    private void saveCurrentProgress() {
        if (bookService == null) {
            return;
        }
        if (selectedBook != null && selectedChapter != null) {
            int position = contentScrollPane.getVerticalScrollBar().getValue();
            LOG.debug("Attempting to save progress: Book=" + selectedBook.getTitle() 
                      + ", Chapter=" + selectedChapter.url() 
                      + ", ScrollPosition=" + position);
            try {
                bookService.saveReadingProgress(selectedBook, selectedChapter.url(), selectedChapter.title(), position);
            } catch (Exception e) {
                LOG.error("Error saving reading progress for book " + selectedBook.getId(), e);
            }
        } else {
            LOG.debug("Skipping save progress: No book or chapter selected.");
        }
    }
    
    /**
     * 书籍列表单元格渲染器
     */
    private static class BookListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof Book book) {
                setText(book.getTitle());
                setToolTipText(book.getAuthor() + " - " + book.getUrl());
            }
            
            return this;
        }
    }
    
    /**
     * 章节列表单元格渲染器
     */
    private static class ChapterListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof Chapter chapter) {
                setText(chapter.title());
                setToolTipText(chapter.url());
            }
            
            return this;
        }
    }
    
    /**
     * 处理阅读器设置变更
     */
    private void handleReaderSettingsChanged() {
        ReaderSettings settings = ApplicationManager.getApplication().getService(ReaderSettings.class);
        if (settings != null) {
            // 更新字体设置
            contentTextArea.setFont(new Font(
                settings.getFontFamily(),
                settings.isBold() ? Font.BOLD : Font.PLAIN,
                settings.getFontSize()
            ));
            
            // 更新主题设置
            if (settings.isDarkTheme()) {
                contentTextArea.setBackground(Color.DARK_GRAY);
                contentTextArea.setForeground(Color.WHITE);
            } else {
                contentTextArea.setBackground(Color.WHITE);
                contentTextArea.setForeground(Color.BLACK);
            }
            
            // 重新加载当前章节以应用新设置
            if (selectedChapter != null) {
                loadChapterContent(selectedBook, selectedChapter);
            }
        }
    }
    
    /**
     * 处理缓存设置变更
     */
    private void handleCacheSettingsChanged(CacheSettings settings) {
        if (settings != null) {
            // 更新缓存策略
            if (!settings.isEnableCache()) {
                // 如果禁用缓存，清理当前缓存
                clearChapterCache();
            }
            
            // 更新预加载策略
            if (!settings.isEnablePreload()) {
                // 如果禁用预加载，停止当前预加载任务
                stopPreloading();
            } else {
                // 如果启用预加载，重新开始预加载
                startPreloading();
            }
        }
    }
    
    /**
     * 清理章节缓存
     */
    private void clearChapterCache() {
        // TODO: 实现章节缓存清理逻辑
        LOG.info("清理章节缓存");
    }
    
    /**
     * 停止预加载
     */
    private void stopPreloading() {
        // TODO: 实现预加载停止逻辑
        LOG.info("停止预加载");
    }
    
    /**
     * 开始预加载
     */
    private void startPreloading() {
        // TODO: 实现预加载开始逻辑
        LOG.info("开始预加载");
    }
    
    /**
     * 释放资源
     */
    @Override
    public void dispose() {
        LOG.info("Disposing ReactiveReaderPanel and saving progress.");
        saveCurrentProgress();
        uiAdapter.dispose();
        
        // 取消注册设置监听器
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        
        LOG.info("释放ReactiveReaderPanel资源");
    }
} 