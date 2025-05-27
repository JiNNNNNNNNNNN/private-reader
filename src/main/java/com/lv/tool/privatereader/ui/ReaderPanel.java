package com.lv.tool.privatereader.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.service.BookService;
import com.intellij.util.messages.MessageBusConnection;
import com.lv.tool.privatereader.settings.ReaderSettings;
import com.lv.tool.privatereader.settings.ReaderSettingsListener;
import com.lv.tool.privatereader.settings.CacheSettings;
import com.lv.tool.privatereader.settings.CacheSettingsListener;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.service.NotificationService;
import com.intellij.util.ui.JBUI;
import reactor.core.publisher.Mono;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.List;

/**
 * 阅读器面板
 * 使用ReactiveUIAdapter加载和显示内容
 */
public class ReaderPanel extends SimpleToolWindowPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(ReaderPanel.class);

    // 高亮颜色常量
    private static final Color SELECTION_BACKGROUND = new Color(0, 120, 215);
    private static final Color SELECTION_FOREGROUND = Color.WHITE;

    private final Project project;
    private final ReactiveUIAdapter uiAdapter;
    private final BookService bookService;
    private final NotificationService notificationService;

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
    private final JLabel currentChapterDisplayLabel;

    // 当前选中的书籍和章节
    private Book selectedBook;
    private Chapter selectedChapter;

    // 待选择的书籍（用于处理书籍列表尚未加载完成的情况）
    private Book pendingBookToSelect;
    private volatile boolean isLoadingState = false; // Flag to prevent listener chain reactions

    public ReaderPanel(Project project) {
        super(true);
        this.project = project;
        this.uiAdapter = new ReactiveUIAdapter(project);

        // Initialize BookService
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        if (this.bookService == null) {
            LOG.error("Failed to get BookService instance. Reading progress saving will be disabled.");
        }

        // Initialize NotificationService
        this.notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
        if (this.notificationService == null) {
            LOG.error("Failed to get NotificationService instance. Notifications will be disabled.");
        }

        // 初始化设置监听器
        this.messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        this.readerSettingsListener = this::handleReaderSettingsChanged;
        this.cacheSettingsListener = this::handleCacheSettingsChanged;

        // 注册设置监听器
        messageBusConnection.subscribe(ReaderSettingsListener.TOPIC, readerSettingsListener);
        messageBusConnection.subscribe(CacheSettingsListener.TOPIC, cacheSettingsListener);

        // 订阅章节变更事件 (由通知栏模式等发布)
        messageBusConnection.subscribe(com.lv.tool.privatereader.messaging.CurrentChapterNotifier.TOPIC, new com.lv.tool.privatereader.messaging.CurrentChapterNotifier() {
            @Override
            public void currentChapterChanged(Book changedBook, Chapter newChapterFromEvent) {
                SwingUtilities.invokeLater(() -> {
                    // Capture the state *before* this event handler potentially modifies selectedBook/selectedChapter
                    // These are effectively final for the logic within this invokeLater runnable.
                    final Book bookAsOfHandlerStart = ReaderPanel.this.selectedBook;
                    final Chapter chapterAsOfHandlerStart = ReaderPanel.this.selectedChapter;

                    LOG.debug("ReaderPanel (CurrentChapterNotifier) event received. Incoming Book: " + (changedBook != null ? changedBook.getTitle() : "null") +
                              ", Incoming Chapter: " + (newChapterFromEvent != null ? newChapterFromEvent.title() : "null") +
                              ". State at handler start: selectedBook=" + (bookAsOfHandlerStart != null ? bookAsOfHandlerStart.getTitle() : "null") +
                              ", selectedChapter=" + (chapterAsOfHandlerStart != null ? chapterAsOfHandlerStart.title() : "null"));
                    
                    isLoadingState = true; // Set flag at the beginning of the event handler logic
                    try {
                        if (changedBook == null || newChapterFromEvent == null) {
                            LOG.warn("ReaderPanel (CurrentChapterNotifier): Received null book or chapter in event. Ignoring.");
                            return;
                        }

                        if (bookAsOfHandlerStart == null || !bookAsOfHandlerStart.getId().equals(changedBook.getId())) {
                            // This logic path implies the book context is wrong for the current panel state or panel has no book selected.
                            // The original code had complex handling here which mostly resulted in returning.
                            // For robust handling, if the panel's selected book is not what the event is for,
                            // it should probably ignore the event or attempt to switch to the new book context if appropriate.
                            // Given the current repeated event issue, we'll keep it simple: if book context doesn't match, log and ignore.
                            LOG.warn("ReaderPanel (CurrentChapterNotifier): Event book '" + changedBook.getTitle() +
                                     "' does not match current selected book '" + (bookAsOfHandlerStart != null ? bookAsOfHandlerStart.getTitle() : "None") +
                                     "' or no book selected. Ignoring event to prevent context mismatch loading.");
                            return; 
                        }

                        // At this point, bookAsOfHandlerStart (which is this.selectedBook before modification in this handler) matches changedBook.
                        // Now check if the newChapterFromEvent is the same as chapterAsOfHandlerStart and content is already loaded.
                        if (chapterAsOfHandlerStart != null &&
                            chapterAsOfHandlerStart.url().equals(newChapterFromEvent.url()) &&
                            contentTextArea.getText() != null && !contentTextArea.getText().trim().isEmpty()) {
                            
                            LOG.info("ReaderPanel (CurrentChapterNotifier): Event for already selected chapter '" + newChapterFromEvent.title() +
                                     "' (URL: " + newChapterFromEvent.url() + ") with content present. Skipping redundant loadChapterContent.");

                            // Ensure internal state reflects the event's chapter, though it's the same.
                            ReaderPanel.this.selectedChapter = newChapterFromEvent;
                            
                            // Ensure chapter list model is up-to-date and the chapter is selected/visible.
                            // This might be useful if the chapter list was somehow altered externally, though unlikely.
                            List<Chapter> chaptersFromCache = bookAsOfHandlerStart.getCachedChapters(); // Use bookAsOfHandlerStart as it's the confirmed current book
                            if (chaptersFromCache != null) {
                                // Quick check: if model size differs, refresh. More sophisticated checks could compare elements.
                                if (chaptersListModel.getSize() != chaptersFromCache.size()) {
                                    chaptersListModel.clear();
                                    for (Chapter chap : chaptersFromCache) chaptersListModel.addElement(chap);
                                    LOG.debug("ReaderPanel (CurrentChapterNotifier): Chapter list model was refreshed during skip-load path for book " + bookAsOfHandlerStart.getTitle());
                                }
                                // Ensure selection
                                for (int i = 0; i < chaptersListModel.getSize(); i++) {
                                    if (chaptersListModel.getElementAt(i).url().equals(newChapterFromEvent.url())) {
                                        if (chaptersList.getSelectedIndex() != i) {
                                            chaptersList.setSelectedIndex(i); // isLoadingState=true protects listener
                                        }
                                        chaptersList.ensureIndexIsVisible(i);
                                        break;
                                    }
                                }
                            }
                            if (currentChapterDisplayLabel != null) {
                                currentChapterDisplayLabel.setText(newChapterFromEvent.title());
                            }
                            return; // Successfully skipped redundant load
                        }

                        // Proceed with normal update and load because it's a new chapter or content was empty.
                        LOG.debug("ReaderPanel (CurrentChapterNotifier): Processing as new/changed chapter event for '" + newChapterFromEvent.title() + "'.");
                        ReaderPanel.this.selectedBook = changedBook; // Should be same as bookAsOfHandlerStart here
                        ReaderPanel.this.selectedChapter = newChapterFromEvent; // Update internal state for the chapter

                        List<Chapter> chaptersFromCache = ReaderPanel.this.selectedBook.getCachedChapters();
                        if (chaptersFromCache != null) {
                            chaptersListModel.clear();
                            for (Chapter chap : chaptersFromCache) {
                                chaptersListModel.addElement(chap);
                            }
                            LOG.debug("ReaderPanel (CurrentChapterNotifier): chaptersListModel refreshed with " + chaptersListModel.getSize() + " chapters for book '" + ReaderPanel.this.selectedBook.getTitle() + "' from cache.");

                            boolean foundInRefreshedList = false;
                            for (int i = 0; i < chaptersListModel.getSize(); i++) {
                                if (chaptersListModel.getElementAt(i).url().equals(newChapterFromEvent.url())) {
                                    if (chaptersList.getSelectedIndex() != i) {
                                        chaptersList.setSelectedIndex(i); // isLoadingState=true protects listener
                                    }
                                    chaptersList.ensureIndexIsVisible(i);
                                    foundInRefreshedList = true;
                                    break;
                                }
                            }

                            if (foundInRefreshedList) {
                                LOG.debug("ReaderPanel (CurrentChapterNotifier): Successfully selected chapter '" + newChapterFromEvent.title() + "' in refreshed list. Calling loadChapterContent.");
                                loadChapterContent(ReaderPanel.this.selectedBook, ReaderPanel.this.selectedChapter);
                            } else {
                                LOG.warn("ReaderPanel (CurrentChapterNotifier): Chapter URL '" + newChapterFromEvent.url() + "' not found in refreshed chapters for book '" + ReaderPanel.this.selectedBook.getTitle() + "'.");
                                contentTextArea.setText(""); 
                                if (currentChapterDisplayLabel != null) {
                                    currentChapterDisplayLabel.setText("章节 ( " + newChapterFromEvent.title() + " ) 未在列表找到");
                                }
                            }
                        } else {
                            LOG.warn("ReaderPanel (CurrentChapterNotifier): Book's cachedChapters is null for '" + ReaderPanel.this.selectedBook.getTitle() + "'. Cannot update chapter list or select chapter.");
                            chaptersListModel.clear();
                            contentTextArea.setText("");
                            if (currentChapterDisplayLabel != null) {
                                currentChapterDisplayLabel.setText("章节列表为空");
                            }
                        }
                        // Update display label (it might have been set by loadChapterContent too)
                        if (ReaderPanel.this.selectedChapter != null && currentChapterDisplayLabel != null) {
                             currentChapterDisplayLabel.setText(ReaderPanel.this.selectedChapter.title());
                        }
                    } catch (Exception ex) {
                        LOG.error("ReaderPanel (CurrentChapterNotifier): Error processing chapter changed event: " + ex.getMessage(), ex);
                    } finally {
                        SwingUtilities.invokeLater(() -> isLoadingState = false); // Clear flag after all operations
                    }
                });
            }
        });

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

        // 初始化新增的章节标题标签
        currentChapterDisplayLabel = new JLabel(" "); // 初始为空白
        currentChapterDisplayLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14)); // 设置字体
        currentChapterDisplayLabel.setBorder(JBUI.Borders.empty(5, 10)); // 设置边距

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
        rightPanel.add(currentChapterDisplayLabel, BorderLayout.NORTH);
        rightPanel.add(contentScrollPane, BorderLayout.CENTER);

        // 加载状态面板
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(loadingLabel);
        rightPanel.add(statusPanel, BorderLayout.SOUTH);

        // 创建主分割面板
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplitPane.setDividerLocation(300);
        mainSplitPane.setResizeWeight(0.3); // 设置调整权重，使右侧面板获得更多空间

        // 确保内容区域可见
        contentTextArea.setVisible(true);
        contentScrollPane.setVisible(true);
        rightPanel.setVisible(true);

        // 设置最小大小
        rightPanel.setMinimumSize(new Dimension(400, 300));
        contentScrollPane.setMinimumSize(new Dimension(400, 300));

        // 设置主面板
        setContent(mainSplitPane);
    }

    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        // 书籍列表选择事件
        booksList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isLoadingState) { // Check isLoadingState
                Book newlySelectedBook = booksList.getSelectedValue();
                if (newlySelectedBook != null && !newlySelectedBook.equals(selectedBook)) {
                    saveCurrentProgress(); // Save progress before switching
                    selectedBook = newlySelectedBook;
                    LOG.debug("Book selection changed to: " + selectedBook.getTitle() + " (ID: " + selectedBook.getId() + ")");

                    chaptersListModel.clear();
                    contentTextArea.setText("");
                    if (currentChapterDisplayLabel != null) {
                        currentChapterDisplayLabel.setText(" ");
                    }
                    loadChapters(selectedBook);
                }
            }
        });

        // 章节列表选择事件
        chaptersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isLoadingState) { // Check isLoadingState
                 Chapter newlySelectedChapter = chaptersList.getSelectedValue();
                 if (newlySelectedChapter != null && !newlySelectedChapter.equals(selectedChapter) && selectedBook != null) {
                    // Don't save progress on simple selection change, only on explicit actions (like switching book/chapter via click)
                    // saveCurrentProgress();
                    selectedChapter = newlySelectedChapter;
                    LOG.debug("Chapter selection changed by user interaction to: " + selectedChapter.title());
                    loadChapterContent(selectedBook, selectedChapter); // Load content for newly selected chapter
                 }
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
    public void loadBooks() {
        LOG.info("Initiating books loading...");
        uiAdapter.loadAllBooks(booksListModel, loadingLabel);
    }

    /**
     * 在书籍加载完成后选择并高亮最后阅读的书籍
     */
    private void selectAndHighlightLastReadBook() {
        LOG.info("Selecting and highlighting last read book...");

        SwingUtilities.invokeLater(() -> { // Ensure all operations are on EDT
            isLoadingState = true;
            try {
        // 检查是否有待选择的书籍
        if (pendingBookToSelect != null) {
            LOG.info("发现待选择的书籍: " + pendingBookToSelect.getTitle());
            Book bookToSelect = pendingBookToSelect;
            pendingBookToSelect = null; // 清除待选择的书籍，防止重复处理

            // 在当前列表中查找书籍
            int indexToSelect = -1;
            for (int i = 0; i < booksListModel.getSize(); i++) {
                if (booksListModel.getElementAt(i).equals(bookToSelect)) {
                    indexToSelect = i;
                    break;
                }
            }

            if (indexToSelect != -1) {
                LOG.info("在列表中找到待选择的书籍，设置选中项");
                        selectedBook = booksListModel.getElementAt(indexToSelect);
                booksList.setSelectedIndex(indexToSelect);
                booksList.ensureIndexIsVisible(indexToSelect);
                booksList.setSelectionBackground(SELECTION_BACKGROUND);
                booksList.setSelectionForeground(SELECTION_FOREGROUND);
                        LOG.debug("[selectAndHighlightLastReadBook] Directly calling loadChapters for pending book.");
                loadChapters(selectedBook);
                        return; 
            } else {
                LOG.warn("在列表中未找到待选择的书籍，继续加载最后阅读的书籍");
            }
        }

        // 获取最后阅读的书籍并自动选择
        if (bookService != null) {
            bookService.getLastReadBook()
                            .publishOn(ReactiveSchedulers.getInstance().ui()) // Ensure subsequent operations are on UI thread
                    .subscribe(
                            lastReadBook -> {
                                        // This block is now on EDT
                                        try {
                                            isLoadingState = true; // Re-affirm as this is a new event loop from reactive stream
                                if (lastReadBook != null) {
                                    LOG.info("Found last read book: " + lastReadBook.getTitle());
                                    int indexToSelect = -1;
                                    for (int i = 0; i < booksListModel.getSize(); i++) {
                                        if (booksListModel.getElementAt(i).equals(lastReadBook)) {
                                            indexToSelect = i;
                                            break;
                                        }
                                    }
                                    if (indexToSelect != -1) {
                                                    selectedBook = booksListModel.getElementAt(indexToSelect);
                                        booksList.setSelectedIndex(indexToSelect);
                                        booksList.ensureIndexIsVisible(indexToSelect);
                                        booksList.setSelectionBackground(SELECTION_BACKGROUND);
                                        booksList.setSelectionForeground(SELECTION_FOREGROUND);
                                                    LOG.debug("[selectAndHighlightLastReadBook] Directly calling loadChapters for last read book.");
                                        loadChapters(selectedBook);
                                    } else {
                                        LOG.warn("Last read book found but not present in the loaded list model. Selecting first book instead.");
                                                    selectAndHighlightFirstBook(); // This will also manage isLoadingState
                                    }
                                } else {
                                    LOG.info("No last read book found, selecting first book if available");
                                                selectAndHighlightFirstBook(); // This will also manage isLoadingState
                                            }
                                        } finally {
                                            SwingUtilities.invokeLater(() -> isLoadingState = false);
                                }
                            },
                            error -> {
                                        try {
                                            isLoadingState = true; // Re-affirm
                                LOG.error("Error loading last read book: " + error.getMessage(), error);
                                Messages.showErrorDialog(project, "加载上次阅读书籍失败: " + error.getMessage(), "加载错误");
                                            selectAndHighlightFirstBook(); // This will also manage isLoadingState
                                        } finally {
                                            SwingUtilities.invokeLater(() -> isLoadingState = false);
                                        }
                            }
                    );
        } else {
            LOG.error("BookService is not initialized. Cannot select last read book.");
                    selectAndHighlightFirstBook(); // Fallback, manages its own isLoadingState
                }
            } finally {
                // This outer finally ensures isLoadingState is reset if the reactive call isn't made or fails synchronously.
                // The reactive callbacks above will also reset it for their specific paths.
                SwingUtilities.invokeLater(() -> isLoadingState = false);
            }
        });
    }

    /**
     * 选择并高亮第一本书 (如果列表不为空)
     */
    private void selectAndHighlightFirstBook() {
        SwingUtilities.invokeLater(() -> { // Ensure on EDT
            isLoadingState = true;
            try {
        if (!booksListModel.isEmpty()) {
            selectedBook = booksListModel.getElementAt(0);
            booksList.setSelectedIndex(0);
            booksList.ensureIndexIsVisible(0);
            booksList.setSelectionBackground(SELECTION_BACKGROUND);
            booksList.setSelectionForeground(SELECTION_FOREGROUND);
                    LOG.debug("[selectAndHighlightFirstBook] Directly calling loadChapters.");
            loadChapters(selectedBook);
                } else {
                    LOG.debug("Book list is empty, cannot select first book.");
        }
            } finally {
                SwingUtilities.invokeLater(() -> isLoadingState = false);
            }
        });
    }

    /**
     * 加载指定书籍的章节列表
     */
    private void loadChapters(Book book) {
        if (book == null) return;
        // uiAdapter handles the async loading and updates chaptersListModel
        // It will call selectLastReadChapter via the onChaptersLoaded callback
        // Pass the JList component as well
        uiAdapter.loadBookChapters(book, chaptersListModel, chaptersList, loadingLabel);
    }

    /**
     * 加载章节内容
     */
    private void loadChapterContent(Book book, Chapter chapter) {
        LOG.info("[LOAD_CONTENT_ENTRY] Called on thread: " + Thread.currentThread().getName() + ", isLoadingState: " + isLoadingState + ", Book: " + (book != null ? book.getTitle() : "null") + ", Chapter: " + (chapter != null ? chapter.title() : "null"), new Throwable("Call stack for loadChapterContent"));
        if (book == null || chapter == null) {
            contentTextArea.setText("请先选择书籍和章节。");
            if (currentChapterDisplayLabel != null) { 
                currentChapterDisplayLabel.setText(" "); 
            }
            this.selectedChapter = null; 
            return;
        }

        this.selectedBook = book;
        this.selectedChapter = chapter; 

        LOG.info("[调试] ReaderPanel: 即将调用 uiAdapter.loadChapterContent: 书籍=" + book.getTitle() + ", 章节=" + chapter.title());

        if (uiAdapter != null) {
             uiAdapter.loadChapterContent(book, chapter, contentTextArea, loadingLabel, currentChapterDisplayLabel);
            } else {
            LOG.error("uiAdapter is null in ReaderPanel.loadChapterContent. Cannot load chapter.");
            contentTextArea.setText("错误: UI适配器未初始化。");
            if (currentChapterDisplayLabel != null) {
                currentChapterDisplayLabel.setText("UI适配器错误");
            }
        }
    }

    /**
     * Restores scroll position based on the book's progress data.
     */
    private void restoreScrollPosition(Book book, Chapter chapter) {
         int targetPosition = 0;
         if (book != null && chapter != null && book.getLastReadChapterId() != null &&
             chapter.url() != null && chapter.url().equals(book.getLastReadChapterId())) {
             // Use the progress data already present in the Book object
             targetPosition = book.getLastReadPosition();
             LOG.debug("Restoring scroll position for " + book.getTitle() + " / " + chapter.title() + " to " + targetPosition);
         } else {
             LOG.debug("Setting scroll position to top for: " + (chapter != null ? chapter.title() : "null"));
         }

         JScrollBar verticalScrollBar = contentScrollPane.getVerticalScrollBar();
         // Ensure position is valid
         targetPosition = Math.max(0, Math.min(targetPosition, verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount()));
         verticalScrollBar.setValue(targetPosition);
    }

    /**
     * Saves the initial progress when a chapter is loaded and scrolled to position.
     */
    private void saveInitialProgressForChapter(Book book, Chapter chapter) {
        if (bookService != null && book != null && chapter != null) {
            final int positionToSave = contentScrollPane.getVerticalScrollBar().getValue();
            LOG.debug("[SAVE_TRACE] RRP.loadChapterContent: Saving initial progress - Book={}, Chapter={}, Pos={}",
                      book.getTitle(), chapter.title(), positionToSave);
            try {
                // Call bookService to save progress asynchronously
                bookService.saveReadingProgress(book, chapter.url(), chapter.title(), positionToSave)
                    .subscribe(
                        null, // onComplete (Void)
                        error -> LOG.error("[SAVE_TRACE] RRP.loadChapterContent: Error saving initial progress via BookService", error)
                    );
            } catch (Exception e) {
                LOG.error("[SAVE_TRACE] RRP.loadChapterContent: Exception calling BookService.saveReadingProgress", e);
            }
        } else {
            LOG.warn("[SAVE_TRACE] RRP.loadChapterContent: Skipping initial progress save - BookService, book, or chapter is null.");
        }
    }

    /**
     * Helper method to select the last read chapter in the list.
     * This is typically called AFTER chapters are loaded.
     */
    private void selectLastReadChapter() {
        SwingUtilities.invokeLater(() -> { // Ensure UI updates happen on EDT
            isLoadingState = true;
            try {
            if (selectedBook == null || chaptersListModel.isEmpty()) {
                LOG.debug("Cannot select last read chapter: No book selected or chapter list is empty.");
                    // If list is empty but book selected, maybe load its content if it's a single-chapter book (edge case)
                    // or clear content area. For now, just return.
                    if (selectedBook != null && chaptersListModel.isEmpty()) {
                        LOG.debug("Chapter list is empty for selected book. Clearing content.");
                        contentTextArea.setText("");
                        if (currentChapterDisplayLabel != null) currentChapterDisplayLabel.setText(" ");
                        this.selectedChapter = null;
                    }
                return;
            }

            String lastReadChapterId = selectedBook.getLastReadChapterId();
                Chapter chapterToLoad = null;
                int chapterIndexToSelect = -1;

            if (lastReadChapterId != null && !lastReadChapterId.isEmpty()) {
                for (int i = 0; i < chaptersListModel.getSize(); i++) {
                    Chapter chapter = chaptersListModel.getElementAt(i);
                    if (lastReadChapterId.equals(chapter.url())) {
                            chapterToLoad = chapter;
                            chapterIndexToSelect = i;
                        LOG.debug("Auto-selecting last read chapter: " + chapter.title());
                            break;
                        }
                    }
                    if (chapterToLoad == null) {
                        LOG.warn("Last read chapter ID [" + lastReadChapterId + "] not found in the loaded chapter list for " + selectedBook.getTitle() + ". Selecting first.");
                }
            } else {
                LOG.debug("No last read chapter ID found for " + selectedBook.getTitle() + ", selecting first chapter.");
            }

                // Fallback: Select the first chapter if no last read chapter or not found, and list is not empty
                if (chapterToLoad == null && !chaptersListModel.isEmpty()) {
                    chapterToLoad = chaptersListModel.getElementAt(0);
                    chapterIndexToSelect = 0;
                    LOG.debug("Selecting first chapter as fallback: " + chapterToLoad.title());
                }

                if (chapterToLoad != null) {
                    selectedChapter = chapterToLoad; // Update internal state
                    if (chaptersList.getSelectedIndex() != chapterIndexToSelect) {
                        chaptersList.setSelectedIndex(chapterIndexToSelect);
                    }
                    chaptersList.ensureIndexIsVisible(chapterIndexToSelect);
                    LOG.debug("[selectLastReadChapter] Directly calling loadChapterContent for: " + chapterToLoad.title());
                    loadChapterContent(selectedBook, selectedChapter); // Direct call
                } else {
                     LOG.debug("No chapter could be selected (e.g. chapter list model is empty or became empty).");
                     contentTextArea.setText(""); // Clear content if no chapter is selected
                     if (currentChapterDisplayLabel != null) currentChapterDisplayLabel.setText(" ");
                     this.selectedChapter = null;
                }
            } finally {
                SwingUtilities.invokeLater(() -> isLoadingState = false);
            }
        });
    }

    /**
     * 保存当前阅读进度
     * @return 返回一个 Mono<Void>，表示异步保存操作，如果不需要保存则返回 Mono.empty()
     */
    private Mono<Void> saveCurrentProgress() {
        if (bookService == null) {
            LOG.warn("BookService 未初始化，无法保存进度");
            return Mono.empty();
        }
        if (selectedBook != null && selectedChapter != null) {
            int position = contentScrollPane.getVerticalScrollBar().getValue();
            LOG.debug("Attempting to save progress via BookService: Book=" + selectedBook.getTitle()
                      + ", Chapter=" + selectedChapter.url()
                      + ", ScrollPosition=" + position);
            try {
                // Call the reactive BookService method
                return bookService.saveReadingProgress(selectedBook, selectedChapter.url(), selectedChapter.title(), position)
                       .doOnError(e -> LOG.error("Error saving reading progress via BookService for book " + selectedBook.getId(), e));
            } catch (Exception e) {
                LOG.error("Unexpected error initiating save progress via BookService for book " + selectedBook.getId(), e);
                return Mono.error(e);
            }
        } else {
            LOG.debug("Skipping save progress: No book or chapter selected.");
            return Mono.empty();
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
        LOG.info("Disposing ReactiveReaderPanel.");
        try {
            // 调用返回 Mono<Void> 的保存方法，改为异步执行
            LOG.info("Initiating asynchronous save of reading progress during dispose...");
            saveCurrentProgress()
                .subscribe(
                    null, // onComplete/onSuccess: Nothing needed for Mono<Void>
                    error -> LOG.error("Error during asynchronous save of reading progress in dispose:", error) // onError: Log error
                    // No need for onSubscribe callback here, log initiation before calling subscribe.
                );
            // Dispose continues immediately without blocking
            // LOG.info("Reading progress saved successfully during dispose."); // This log is no longer accurate here
        } catch (IllegalStateException e) {
            // This specific catch might no longer be relevant as block() is removed
            LOG.error("IllegalStateException initiating progress save (should not happen with async):", e);
        } catch (Exception e) {
             // Catch unexpected errors setting up the subscription
             LOG.error("Unexpected error initiating progress save in dispose:", e);
        }

        // Continue with other disposal tasks immediately
        LOG.info("Disposing UI adapter...");
        uiAdapter.dispose();

        // MessageBusConnection is handled by ReaderModeSwitcher's disposal if still active?
        // Or is this connection separate? Assuming it's separate based on code structure.
        if (messageBusConnection != null) {
            LOG.info("Disconnecting message bus connection for ReaderPanel...");
            messageBusConnection.disconnect();
        }

        // 从 PROJECT_PANELS 中移除自身
        // 确保 project 仍然有效并且当前实例确实是存储在映射中的那个实例
        // ReaderToolWindowFactory.PROJECT_PANELS.remove(project, this) 更安全，如果 ConcurrentMap 支持
        // 或者先 get 再比较，然后 remove
        if (project != null && !project.isDisposed()) { // 添加 !project.isDisposed() 检查
            ReaderPanel panelInMap = ReaderToolWindowFactory.PROJECT_PANELS.get(project);
            if (panelInMap == this) {
                ReaderToolWindowFactory.PROJECT_PANELS.remove(project);
                LOG.info("ReaderPanel removed from PROJECT_PANELS for project: " + project.getName());
            } else if (panelInMap != null) {
                LOG.warn("ReaderPanel in map for project " + project.getName() + " is different during dispose. Not removing.");
            }
        } else if (project != null && project.isDisposed()) {
             LOG.info("Project " + project.getName() + " is already disposed. Skipping removal from PROJECT_PANELS, map should be cleared elsewhere or panel already removed.");
             // At this point, if project is disposed, PROJECT_PANELS should ideally be empty for this project,
             // or the removal might have happened via another mechanism if ReaderToolWindowFactory itself is disposed with the project.
             // For safety, we can still attempt a remove if we are certain about the key's state.
             // However, accessing project.getName() on a disposed project can be problematic.
             // Let's assume that if project is disposed, other cleanup mechanisms should handle the map or it's already done.
        }

        LOG.info("Finished disposing ReactiveReaderPanel resources.");
    }

    // --- Public API for Actions and External Components ---

    /**
     * 获取当前选中的书籍
     * @return 当前选中的 Book 对象，如果未选择则返回 null
     */
    public Book getSelectedBook() {
        return selectedBook;
    }

    /**
     * 获取当前选中的章节
     * @return 当前选中的 Chapter 对象，如果未选择则返回 null
     */
    public Chapter getSelectedChapter() {
        return selectedChapter;
    }

    /**
     * 导航到上一章或下一章
     * @param direction -1 表示上一章, 1 表示下一章
     */
    public void navigateChapter(int direction) {
        if (chaptersListModel.isEmpty() || selectedChapter == null) {
            LOG.warn("无法导航章节：列表为空或未选择章节");
            return;
        }
        int currentIndex = chaptersList.getSelectedIndex();
        int targetIndex = currentIndex + direction;

        if (targetIndex >= 0 && targetIndex < chaptersListModel.getSize()) {
            LOG.debug("导航章节: 从索引 " + currentIndex + " 到 " + targetIndex);
            chaptersList.setSelectedIndex(targetIndex);
            chaptersList.ensureIndexIsVisible(targetIndex);
            // 列表选择监听器会自动调用 loadChapterContent
        } else {
            LOG.warn("无法导航章节：目标索引 " + targetIndex + " 超出范围 [0, " + (chaptersListModel.getSize() - 1) + "]");
            // Optionally provide user feedback (e.g., notification)
            if (notificationService != null) {
                if (direction < 0) {
                    notificationService.showInfo("导航", "已经是第一章了").subscribe();
                } else {
                    notificationService.showInfo("导航", "已经是最后一章了").subscribe();
                }
            }
        }
    }

    /**
     * 重新加载当前选中章节的内容
     */
    public void reloadCurrentChapter() {
        if (selectedBook != null && selectedChapter != null) {
            LOG.info("重新加载章节内容: " + selectedChapter.title());
            // Consider clearing cache if needed before reloading
            // ChapterCacheManager.getInstance().removeCache(...)
            loadChapterContent(selectedBook, selectedChapter);
            // Provide user feedback
            if (notificationService != null) {
                notificationService.showInfo("刷新", "已刷新当前章节内容").subscribe();
            }
        } else {
            LOG.warn("无法重新加载章节：未选择书籍或章节");
            if (notificationService != null) {
                notificationService.showInfo("警告", "请先选择书籍和章节").subscribe();
            }
        }
    }

    /**
     * 刷新当前选中书籍的章节列表
     */
    public void refreshChapterList() {
        if (selectedBook != null) {
            LOG.info("刷新章节列表: " + selectedBook.getTitle());
            // Clear current list model before loading
            chaptersListModel.clear();
            loadChapters(selectedBook);
             // Provide user feedback
            if (notificationService != null) {
                notificationService.showInfo("刷新", "已刷新章节列表").subscribe();
            }
        } else {
            LOG.warn("无法刷新章节列表：未选择书籍");
            if (notificationService != null) {
                notificationService.showInfo("警告", "请先选择书籍").subscribe();
            }
        }
    }

    /**
     * 触发加载上次阅读的状态（书籍和章节）
     * 由启动逻辑调用
     */
    public void triggerLoadLastReadState() {
        LOG.info("外部触发加载上次阅读状态...");
        // isLoadingState will be managed by the methods called below (selectAndHighlightLastReadBook or loadBooks -> selectAndHighlightLastReadBook)
        if (booksListModel.isEmpty()) {
            loadBooks(); 
        } else {
             selectAndHighlightLastReadBook();
        }
    }

    /**
     * 选择指定的书籍并触发其章节和进度的加载
     * @param bookToSelect 要选择的书籍
     */
    public void selectBookAndLoadProgress(Book bookToSelect) {
        if (bookToSelect == null) {
            LOG.warn("无法选择书籍：提供的书籍为 null");
            return;
        }
        LOG.info("外部请求选择书籍: " + bookToSelect.getTitle());

        SwingUtilities.invokeLater(() -> { // Ensure on EDT
            isLoadingState = true;
            try {
        if (booksListModel.isEmpty()) {
            LOG.info("书籍列表为空，将书籍 '" + bookToSelect.getTitle() + "' 保存到待选择列表");
            pendingBookToSelect = bookToSelect;
                    loadBooks(); // This will eventually call selectAndHighlightLastReadBook which manages isLoadingState
                    return; // isLoadingState will be reset by the chain initiated by loadBooks
        }

        for (int i = 0; i < booksListModel.getSize(); i++) {
            if (booksListModel.getElementAt(i).equals(bookToSelect)) {
                if (booksList.getSelectedIndex() == i) {
                    LOG.debug("书籍 '" + bookToSelect.getTitle() + "' 已被选中");
                            selectedBook = bookToSelect; // Ensure selectedBook is current instance
                            if (chaptersListModel.isEmpty()) { // If chapters not loaded, load them
                                LOG.debug("[selectBookAndLoadProgress] Selected book chapters empty, calling loadChapters.");
                                loadChapters(selectedBook); // loadChapters -> selectLastReadChapter (manages isLoadingState & loads content)
                            } else {
                                // Chapters are loaded. If the current selectedChapter isn't from this book or needs re-evaluation:
                                // This case implies the book is already selected, and chapters are present.
                                // We might need to ensure the correct chapter is selected/loaded if it's a specific deep link.
                                // For now, if book is already selected, assume chapter selection is handled or will be by other means.
                    }
                } else {
                    LOG.debug("在索引 " + i + " 找到书籍，设置选中项");
                            selectedBook = booksListModel.getElementAt(i); // Ensure current instance
                            booksList.setSelectedIndex(i); // Listener is skipped due to isLoadingState
                    booksList.ensureIndexIsVisible(i);
                            LOG.debug("[selectBookAndLoadProgress] Directly calling loadChapters after selecting book.");
                            loadChapters(selectedBook); // loadChapters -> selectLastReadChapter (manages isLoadingState & loads content)
                }
                        return; // Exit after handling found book
            }
        }

                LOG.warn("无法选择书籍：列表中未找到 '" + bookToSelect.getTitle() + "', setting as pending and reloading books.");
        pendingBookToSelect = bookToSelect;
                loadBooks(); // This will eventually call selectAndHighlightLastReadBook

            } finally {
                SwingUtilities.invokeLater(() -> isLoadingState = false);
            }
        });
    }

    /**
     * 加载指定的章节
     *
     * @param book 书籍
     * @param chapter 章节
     */
    public void loadChapter(Book book, Chapter chapter) {
        if (book == null || chapter == null) {
            LOG.warn("无法加载章节：书籍或章节为空");
            return;
        }

        LOG.info("加载指定章节：书籍=" + book.getTitle() + ", 章节=" + chapter.title());

        SwingUtilities.invokeLater(() -> { // Ensure on EDT
            isLoadingState = true;
            try {
                // If the book is not currently selected, select it first.
                // selectBookAndLoadProgress will handle setting selectedBook, isLoadingState,
                // and triggering chapter load (which includes selecting chapter and loading content).
                if (selectedBook == null || !selectedBook.getId().equals(book.getId())) {
                    LOG.debug("[loadChapter] Book is different or null. Calling selectBookAndLoadProgress for: " + book.getTitle());
                    // selectBookAndLoadProgress will handle the full selection and loading flow,
                    // including setting selectedChapter implicitly if it's the last read one.
                    // We need to ensure *this specific chapter* gets loaded.
                    // A bit tricky. selectBookAndLoadProgress might load the *lastReadChapter* for 'book'.
                    // We want to override that with 'chapter'.

                    // Let selectBookAndLoadProgress select the book and its default/last-read chapter.
                    // Then, we explicitly load the requested chapter if different.
                    // This is complex because selectBookAndLoadProgress is async and has its own isLoadingState.

                    // Simpler approach:
                    this.selectedBook = book; // Tentatively set
                    this.selectedChapter = chapter; // Tentatively set

                    // Find book in list and select it. This might trigger listeners if not for isLoadingState.
                    boolean bookFound = false;
                    for (int i = 0; i < booksListModel.getSize(); i++) {
                        if (booksListModel.getElementAt(i).getId().equals(book.getId())) {
                            if (booksList.getSelectedIndex() != i) {
                                booksList.setSelectedIndex(i); // isLoadingState should prevent listener
                            }
                            booksList.ensureIndexIsVisible(i);
                            bookFound = true;
                            break;
                        }
                    }
                    if (!bookFound) {
                        LOG.warn("[loadChapter] Target book " + book.getTitle() + " not found in booksListModel. Attempting to add/load it.");
                        // This scenario is complex: book not in list. Add it then select?
                        // For now, assume book is in list for loadChapter to work simply.
                        // Or rely on a mechanism that adds the book and then calls loadChapter.
                        // Simplification: if book not in list, this specific call might not fully work as intended without more logic.
                        // Let's proceed assuming it was found or will be handled by a prior add operation.
                    }
                     // Now that the book is selected (or we attempted to), refresh its chapter list and select the target chapter.
                    chaptersListModel.clear(); // Clear old chapters
                    List<Chapter> chaptersFromCache = book.getCachedChapters();
                    if(chaptersFromCache != null && !chaptersFromCache.isEmpty()){
                        for(Chapter chap : chaptersFromCache){
                            chaptersListModel.addElement(chap);
                        }
                    } else {
                        // If cache is empty, we might need to trigger a fetch for this book's chapters
                        // This indicates a potentially missing call to loadChapters for the book first.
                        // For simplicity, loadChapterContent will handle if chapter object is valid.
                        LOG.warn("[loadChapter] Book " + book.getTitle() + " has no cached chapters when trying to load specific chapter " + chapter.title());
                    }

                    // Select the specific chapter in the (potentially just refreshed) list
                    boolean chapterFoundInList = false;
            for (int i = 0; i < chaptersListModel.getSize(); i++) {
                        if (chaptersListModel.getElementAt(i).url().equals(chapter.url())) {
                            chaptersList.setSelectedIndex(i); // isLoadingState should prevent listener
                            chaptersList.ensureIndexIsVisible(i);
                            chapterFoundInList = true;
                            break;
                        }
                    }
                    if (!chapterFoundInList && chaptersListModel.isEmpty() && chaptersFromCache != null && chaptersFromCache.contains(chapter)) {
                        // If list was empty but cache had it (e.g. after clear/addAll above), and it should be there.
                        // This case might occur if addAll isn't synchronous with getSize() or model updates. Unlikely with DefaultListModel.
                        // More likely: Chapter object is valid, but list model isn't reflecting it yet.
                        // Add it if it's not in the model but should be.
                         LOG.debug("[loadChapter] Chapter "+chapter.title()+" not found in list model after refresh, but present in cache. Adding it.");
                         chaptersListModel.addElement(chapter); // Add it if it was missing
                         for (int i = 0; i < chaptersListModel.getSize(); i++) { // Try selecting again
                            if (chaptersListModel.getElementAt(i).url().equals(chapter.url())) {
                    chaptersList.setSelectedIndex(i);
                    chaptersList.ensureIndexIsVisible(i);
                                chapterFoundInList = true;
                    break;
                }
            }
                    }
                     if (!chapterFoundInList) {
                        LOG.warn("[loadChapter] Target chapter " + chapter.title() + " not found in chapterListModel for book " + book.getTitle());
                        // Content will still be loaded if chapter object is valid. List selection might be off.
                    }

                } else if (selectedChapter == null || !selectedChapter.url().equals(chapter.url())) {
                    // Book is the same, but chapter is different or null
                    this.selectedChapter = chapter;
                    boolean chapterFoundInList = false;
            for (int i = 0; i < chaptersListModel.getSize(); i++) {
                        if (chaptersListModel.getElementAt(i).url().equals(chapter.url())) {
                            if (chaptersList.getSelectedIndex() != i) {
                                chaptersList.setSelectedIndex(i); // isLoadingState should prevent listener
                            }
                    chaptersList.ensureIndexIsVisible(i);
                            chapterFoundInList = true;
                    break;
                }
            }
                     if (!chapterFoundInList) {
                        LOG.warn("[loadChapter] Target chapter " + chapter.title() + " not found in chapterListModel for current book " + book.getTitle());
                    }
                } else {
                    // Book and chapter are already selected, likely a forced reload.
                    LOG.debug("[loadChapter] Book and Chapter are already selected. Proceeding to load content for " + chapter.title());
                }
                
                // Now, selectedBook and selectedChapter should be correctly set. Load the content.
                LOG.debug("[loadChapter] Directly calling loadChapterContent for book: " + this.selectedBook.getTitle() + ", chapter: " + this.selectedChapter.title());
                loadChapterContent(this.selectedBook, this.selectedChapter);
                // Update book's progress to this chapter, position 0 as it's a new load.
                // Page 1 because lastReadPage means current page being read.
                this.selectedBook.updateReadingProgress(this.selectedChapter.url(), 0, 1);


            } finally {
                SwingUtilities.invokeLater(() -> isLoadingState = false);
            }
        });
    }

    // --- End Public API ---
}