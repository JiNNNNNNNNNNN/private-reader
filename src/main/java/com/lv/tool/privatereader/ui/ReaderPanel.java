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
import reactor.core.publisher.Mono;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;

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

    // 当前选中的书籍和章节
    private Book selectedBook;
    private Chapter selectedChapter;

    // 待选择的书籍（用于处理书籍列表尚未加载完成的情况）
    private Book pendingBookToSelect;

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
            if (!e.getValueIsAdjusting()) {
                Book newlySelectedBook = booksList.getSelectedValue();
                if (newlySelectedBook != null && !newlySelectedBook.equals(selectedBook)) {
                    saveCurrentProgress(); // Save progress before switching
                    selectedBook = newlySelectedBook;
                    LOG.debug("Book selection changed to: " + selectedBook.getTitle() + " (ID: " + selectedBook.getId() + ")");

                    // No need to manually call getBookById here.
                    // The Book object from the list *should* already contain the latest progress
                    // because BookService.getAllBooks now combines metadata and progress.
                    // We just need to ensure getAllBooks is called appropriately (e.g., on refresh/init).

                    // Update UI based on the selected book (which has progress info)
                    chaptersListModel.clear();
                    contentTextArea.setText("");
                    loadChapters(selectedBook); // This uses the selectedBook with progress
                    // selectLastReadChapter will be called by the uiAdapter callback if chapters load successfully
                }
            }
        });

        // 章节列表选择事件
        chaptersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                 Chapter newlySelectedChapter = chaptersList.getSelectedValue();
                 if (newlySelectedChapter != null && !newlySelectedChapter.equals(selectedChapter) && selectedBook != null) {
                    // Don't save progress on simple selection change, only on explicit actions (like switching book/chapter via click)
                    // saveCurrentProgress();
                    selectedChapter = newlySelectedChapter;
                    LOG.debug("Chapter selection changed to: " + selectedChapter.title());
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
                return; // 已处理待选择的书籍，不需要继续加载最后阅读的书籍
            } else {
                LOG.warn("在列表中未找到待选择的书籍，继续加载最后阅读的书籍");
                // 如果在列表中未找到待选择的书籍，继续加载最后阅读的书籍
            }
        }

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
        if (book == null || chapter == null) {
            contentTextArea.setText("请先选择书籍和章节。");
            return;
        }

        // 获取当前的阅读模式设置
        ReaderModeSettings modeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        boolean isNotificationMode = modeSettings != null && modeSettings.isNotificationMode();

        LOG.info("[调试] 加载章节内容: 书籍=" + book.getTitle() + ", 章节=" + chapter.title() + ", 通知栏模式=" + isNotificationMode);

        if (isNotificationMode) {
            // 如果是通知栏模式，则使用 NotificationService 显示章节内容
            if (notificationService != null) {
                LOG.info("[调试] 使用通知栏模式加载章节内容: 书籍=" + book.getTitle() + ", 章节=" + chapter.title());

                // 首先获取章节内容
                uiAdapter.getChapterContent(book, chapter.url())
                    .subscribe(
                        content -> {
                            LOG.info("[调试] 获取章节内容成功，准备显示到通知栏: 内容长度=" + (content != null ? content.length() : 0));
                            // 将内容显示到通知栏
                            notificationService.showChapterContent(book, chapter.url(), content)
                                .subscribe(
                                    notification -> LOG.info("[调试] 通知栏显示章节内容成功"),
                                    error -> LOG.error("[调试] 通知栏显示章节内容失败: " + error.getMessage(), error)
                                );

                            // 在通知栏模式下，不调用 saveInitialProgressForChapter 方法
                            // 因为 NotificationServiceImpl 的 showChapterContent 方法会自己保存页码
                            // 如果在这里调用 saveInitialProgressForChapter 方法，会导致页码被覆盖
                            // saveInitialProgressForChapter(book, chapter);
                        },
                        error -> {
                            LOG.error("[调试] 获取章节内容失败: " + error.getMessage(), error);
                            notificationService.showError("加载失败", "无法加载章节内容: " + error.getMessage()).subscribe();
                        }
                    );
            } else {
                LOG.error("[调试] NotificationService 为 null，无法显示通知");
            }
        } else {
            // 如果是阅读器模式，则使用原有的加载逻辑
            LOG.info("[调试] 使用阅读器模式加载章节内容: 书籍=" + book.getTitle() + ", 章节=" + chapter.title());

            // 确保内容区域可见
            contentTextArea.setVisible(true);
            contentScrollPane.setVisible(true);

            // 记录内容区域的大小和可见性状态
            LOG.info("[调试] 内容区域状态: contentTextArea.isVisible=" + contentTextArea.isVisible() +
                     ", contentScrollPane.isVisible=" + contentScrollPane.isVisible() +
                     ", contentTextArea.size=" + contentTextArea.getSize() +
                     ", contentScrollPane.size=" + contentScrollPane.getSize());

            // uiAdapter handles async loading and updates contentTextArea
            uiAdapter.loadChapterContent(book, chapter, contentTextArea, loadingLabel);

            // Restore scroll position AFTER content is loaded (might need callback or delay)
            // For simplicity, attempting immediate restore. A better approach would use a callback.
            SwingUtilities.invokeLater(() -> {
                // 确保内容区域可见并强制重绘UI
                contentTextArea.setVisible(true);
                contentScrollPane.setVisible(true);
                contentTextArea.revalidate();
                contentTextArea.repaint();
                contentScrollPane.revalidate();
                contentScrollPane.repaint();

                restoreScrollPosition(book, chapter);
                // Save progress after loading and scrolling
                saveInitialProgressForChapter(book, chapter);

                // 再次记录内容区域的大小和可见性状态
                LOG.info("[调试] 内容加载后区域状态: contentTextArea.isVisible=" + contentTextArea.isVisible() +
                         ", contentScrollPane.isVisible=" + contentScrollPane.isVisible() +
                         ", contentTextArea.size=" + contentTextArea.getSize() +
                         ", contentScrollPane.size=" + contentScrollPane.getSize() +
                         ", contentTextArea.text.length=" + contentTextArea.getText().length());
            });
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
            if (selectedBook == null || chaptersListModel.isEmpty()) {
                LOG.debug("Cannot select last read chapter: No book selected or chapter list is empty.");
                return;
            }
            // Use progress data directly from the selectedBook object
            String lastReadChapterId = selectedBook.getLastReadChapterId();
            if (lastReadChapterId != null && !lastReadChapterId.isEmpty()) {
                for (int i = 0; i < chaptersListModel.getSize(); i++) {
                    Chapter chapter = chaptersListModel.getElementAt(i);
                    if (lastReadChapterId.equals(chapter.url())) {
                        LOG.debug("Auto-selecting last read chapter: " + chapter.title());
                        if (chaptersList.getSelectedIndex() != i) { // Avoid redundant selection/event firing
                             chaptersList.setSelectedIndex(i);
                        }
                        chaptersList.ensureIndexIsVisible(i);
                        // Content loading should happen via the list selection listener or initial load logic
                        // loadChapterContent(selectedBook, chapter); // Avoid potentially redundant load
                        return;
                    }
                }
                LOG.warn("Last read chapter ID [" + lastReadChapterId + "] not found in the loaded chapter list for " + selectedBook.getTitle());
            } else {
                LOG.debug("No last read chapter ID found for " + selectedBook.getTitle() + ", selecting first chapter.");
            }

            // Fallback: Select the first chapter if no last read chapter or not found
            if (!chaptersListModel.isEmpty() && chaptersList.getSelectedIndex() == -1) { // Select only if nothing is selected
                chaptersList.setSelectedIndex(0);
                chaptersList.ensureIndexIsVisible(0);
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
        // The actual loading is handled by the callback uiAdapter.setOnBooksLoaded
        // which calls selectAndHighlightLastReadBook. We just need to ensure
        // loadBooks is called if it hasn't been already.
        if (booksListModel.isEmpty()) {
            loadBooks(); // Ensure books are loaded if the panel was initialized but hidden
        } else {
             // If books are already loaded, the callback might have already run.
             // Explicitly call selectAndHighlightLastReadBook again if needed,
             // but be careful about re-triggering logic unnecessarily.
             // For now, assume the initial loadBooks call and its callback handle this.
             LOG.debug("书籍列表非空，假设上次阅读状态已通过回调加载。");
             // We might still need to select the last read book if the initial selection failed
             // or if the window was closed and reopened without full reinitialization.
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

        // 如果书籍列表为空，将书籍保存到待选择列表，等待书籍列表加载完成后再选择
        if (booksListModel.isEmpty()) {
            LOG.info("书籍列表为空，将书籍 '" + bookToSelect.getTitle() + "' 保存到待选择列表");
            pendingBookToSelect = bookToSelect;
            // 如果书籍列表为空，尝试加载书籍列表
            loadBooks();
            return;
        }

        for (int i = 0; i < booksListModel.getSize(); i++) {
            if (booksListModel.getElementAt(i).equals(bookToSelect)) {
                if (booksList.getSelectedIndex() == i) {
                    LOG.debug("书籍 '" + bookToSelect.getTitle() + "' 已被选中");
                    // Even if selected, ensure chapters are loaded if needed
                    if (chaptersListModel.isEmpty()) {
                        loadChapters(bookToSelect);
                    }
                } else {
                    LOG.debug("在索引 " + i + " 找到书籍，设置选中项");
                    booksList.setSelectedIndex(i);
                    booksList.ensureIndexIsVisible(i);
                }
                // The list selection listener will handle loading chapters and progress.
                return;
            }
        }

        // 如果在当前列表中未找到书籍，将其保存到待选择列表
        LOG.warn("无法选择书籍：列表中未找到 '" + bookToSelect.getTitle() + "'");
        pendingBookToSelect = bookToSelect;

        // 尝试重新加载书籍列表，可能书籍列表尚未完全加载
        loadBooks();

        // 显示通知，但不要过早放弃，因为书籍可能在加载完成后出现
        if (notificationService != null) {
            notificationService.showInfo("提示", "正在尝试加载书籍：" + bookToSelect.getTitle()).subscribe();
        }
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

        // 如果当前选中的书籍不是指定的书籍，先选择书籍
        if (selectedBook == null || !selectedBook.equals(book)) {
            // 保存当前进度
            saveCurrentProgress().subscribe();

            // 选择书籍
            selectBookAndLoadProgress(book);

            // 由于选择书籍是异步的，我们需要等待章节列表加载完成后再选择章节
            // 这里我们使用一个简单的方法：直接加载章节内容
            selectedBook = book;
            selectedChapter = chapter;

            // 立即更新Book对象的lastReadChapterId，确保下次打开章节列表对话框时能够正确高亮显示当前选中的章节
            book.updateReadingProgress(chapter.url(), 0, 1);

            loadChapterContent(book, chapter);

            // 在章节列表中选择对应的章节
            for (int i = 0; i < chaptersListModel.getSize(); i++) {
                if (chaptersListModel.getElementAt(i).equals(chapter)) {
                    chaptersList.setSelectedIndex(i);
                    chaptersList.ensureIndexIsVisible(i);
                    break;
                }
            }
        } else {
            // 如果当前选中的书籍就是指定的书籍，直接选择章节
            selectedChapter = chapter;

            // 立即更新Book对象的lastReadChapterId，确保下次打开章节列表对话框时能够正确高亮显示当前选中的章节
            book.updateReadingProgress(chapter.url(), 0, 1);

            loadChapterContent(book, chapter);

            // 在章节列表中选择对应的章节
            for (int i = 0; i < chaptersListModel.getSize(); i++) {
                if (chaptersListModel.getElementAt(i).equals(chapter)) {
                    chaptersList.setSelectedIndex(i);
                    chaptersList.ensureIndexIsVisible(i);
                    break;
                }
            }
        }
    }

    // --- End Public API ---
}