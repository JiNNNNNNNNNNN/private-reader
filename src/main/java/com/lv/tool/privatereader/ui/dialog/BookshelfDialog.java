package com.lv.tool.privatereader.ui.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.reader.ReactiveChapterPreloader;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import com.lv.tool.privatereader.ui.topics.BookshelfTopics;
import com.intellij.notification.NotificationType;
import org.jetbrains.annotations.Nullable;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.Notification;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.RepositoryModule;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.lv.tool.privatereader.async.ReactiveTaskManager;
import com.lv.tool.privatereader.service.BookService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.time.Duration;

public class BookshelfDialog extends DialogWrapper {
    private final Project project;
    private final JBList<Book> bookList;
    private final BookService bookService;
    private final RepositoryModule repositoryModule;
    private final BookRepository bookRepository;
    private JPanel mainPanel;
    private JComboBox<String> sortComboBox;
    private static final String NOTIFICATION_GROUP_ID = "Private Reader";
    private static final Logger LOG = Logger.getInstance(BookshelfDialog.class);

    public BookshelfDialog(Project project) {
        super(project);
        this.project = project;
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        
        // 获取RepositoryModule
        this.repositoryModule = RepositoryModule.getInstance();
        if (this.repositoryModule != null) {
            this.bookRepository = repositoryModule.getBookRepository();
        } else {
            LOG.warn("获取 RepositoryModule 失败");
            this.bookRepository = null;
        }
        
        this.bookList = new JBList<>();
        
        // 订阅书籍更新事件
        project.getMessageBus().connect().subscribe(BookshelfTopics.BOOK_UPDATED, book -> {
            if (book != null) {
                refreshBookList();
            }
        });
        
        init();
        setTitle("书架");
        setSize(600, 400);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // 创建工具栏
        JPanel toolBar = createToolBar();
        mainPanel.add(toolBar, BorderLayout.NORTH);
        
        // 创建书籍列表
        bookList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedBook();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showPopupMenu(e);
                }
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(bookList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 加载书籍
        refreshBookList();
        
        return mainPanel;
    }

    private JPanel createToolBar() {
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // 排序下拉框
        sortComboBox = new JComboBox<>(new String[]{"按书名排序", "按进度排序", "按时间排序"});
        sortComboBox.addActionListener(e -> sortBooks());
        toolBar.add(sortComboBox);
        
        // 刷新按钮
        JButton refreshButton = new JButton("刷新章节");
        refreshButton.addActionListener(e -> {
            Book selectedBook = bookList.getSelectedValue();
            if (selectedBook != null) {
                try {
                    selectedBook.setProject(project);
                    NovelParser parser = selectedBook.getParser();
                    if (parser != null) {
                        List<NovelParser.Chapter> chapters = parser.getChapterList(selectedBook);
                        selectedBook.setCachedChapters(chapters);
                        // 更新存储
                        updateBookInfo(selectedBook);
                        refreshBookList();
                        showNotification(
                            String.format("成功刷新章节列表，共 %d 章", chapters.size()),
                            NotificationType.INFORMATION);
                    }
                } catch (Exception ex) {
                    showNotification(
                        "刷新章节列表失败：" + ex.getMessage(),
                        NotificationType.ERROR);
                }
            }
        });
        toolBar.add(refreshButton);
        
        // 重置进度按钮
        JButton resetButton = new JButton("重置进度");
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
                    project.getService(ReadingProgressManager.class).resetProgress(selectedBook);
                    refreshBookList();
                }
            }
        });
        toolBar.add(resetButton);
        
        // 移除书籍按钮
        JButton removeButton = new JButton("移除书籍");
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
                    try {
                        // 任务管理器将取消预加载任务
                        ReactiveTaskManager taskManager = ReactiveTaskManager.getInstance();
                        taskManager.cancelTasksByPrefix("preload-chapters-" + selectedBook.getId());
                        
                        // 删除书籍
                        removeBook(selectedBook);
                        // 刷新书架对话框
                        refreshBookList();
                        // 刷新阅读面板
                        PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
                        if (panel != null) {
                            panel.refresh();
                        }
                    } catch (Exception ex) {
                        LOG.error("移除书籍失败", ex);
                    }
                }
            }
        });
        toolBar.add(removeButton);
        
        return toolBar;
    }

    private void refreshBookList() {
        List<Book> books = getAllBooks();
        bookList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Book book = (Book) value;
                String text = String.format("<html><b>%s</b> - %s [%d%%]<br><font color='gray'>URL: %s<br>添加时间: %s</font></html>",
                    book.getTitle(),
                    book.getAuthor(),
                    (int) (book.getReadingProgress() * 100),
                    book.getUrl(),
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(book.getCreateTimeMillis()))
                );
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        bookList.setListData(books.toArray(new Book[0]));
        sortBooks(); // 应用当前选择的排序
    }

    private void sortBooks() {
        List<Book> books = getAllBooks();
        int selectedIndex = sortComboBox.getSelectedIndex();
        
        switch (selectedIndex) {
            case 0: // 按书名排序
                books = books.stream()
                    .sorted((b1, b2) -> b1.getTitle().compareTo(b2.getTitle()))
                    .collect(Collectors.toList());
                break;
            case 1: // 按进度排序
                books = books.stream()
                    .sorted((b1, b2) -> Double.compare(b2.getReadingProgress(), b1.getReadingProgress()))
                    .collect(Collectors.toList());
                break;
            case 2: // 按时间排序
                books = books.stream()
                    .sorted((b1, b2) -> Long.compare(b2.getLastReadTimeMillis(), b1.getLastReadTimeMillis()))
                    .collect(Collectors.toList());
                break;
        }
        
        bookList.setListData(books.toArray(new Book[0]));
    }

    private void showNotification(String content, NotificationType type) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(content, type)
                .setImportant(false);

        notification.hideBalloon();
        notification.notify(project);
    }

    private void openSelectedBook() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                showNotification(
                    String.format("正在打开《%s》...", selectedBook.getTitle()),
                    NotificationType.INFORMATION);
                
                try {
                    // 不再在此处更新书籍进度信息或获取章节，交给新的加载方法处理
                    
                    showNotification(
                        "准备加载书籍进度...",
                        NotificationType.INFORMATION);
                        
                    // 调用新方法加载指定书籍的进度
                    panel.loadSpecificBookProgress(selectedBook);
                    
                    // 加载完成的通知现在应由 loadSpecificBookProgress 内部或通过事件触发
                    // 或者保留一个通用的"正在打开"通知，但具体的完成状态需要异步处理
                    // showNotification(
                    //     String.format("《%s》加载完成", selectedBook.getTitle()),
                    //     NotificationType.INFORMATION);
                        
                    close(OK_EXIT_CODE);
                } catch (Exception e) {
                    showNotification(
                        String.format("打开《%s》时出错: %s", selectedBook.getTitle(), e.getMessage()),
                        NotificationType.ERROR);
                }
            }
        }
    }

    private void showPopupMenu(MouseEvent e) {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            
            JMenuItem copyUrlItem = new JMenuItem("复制URL");
            copyUrlItem.addActionListener(event -> {
                String url = selectedBook.getUrl();
                if (url != null && !url.isEmpty()) {
                    Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(url), null);
                    showNotification("URL已复制到剪贴板", NotificationType.INFORMATION);
                }
            });
            popupMenu.add(copyUrlItem);
            
            popupMenu.show(bookList, e.getX(), e.getY());
        }
    }

    @Override
    protected void doOKAction() {
        openSelectedBook();
    }

    private void updateBookInfo(Book book) {
        if (bookService != null) {
            WriteAction.run(() -> bookService.updateBook(book));
        } else {
            LOG.error("BookService 未初始化，无法更新书籍信息");
        }
    }

    private void removeBook(Book book) {
        if (bookService != null) {
            WriteAction.run(() -> bookService.removeBook(book));
        } else {
            LOG.error("BookService 未初始化，无法删除书籍");
        }
    }

    private List<Book> getAllBooks() {
        if (bookService != null) {
            return loadBooks();
        } else {
            LOG.error("BookService 未初始化，无法获取书籍列表");
            return new ArrayList<>();
        }
    }

    private List<Book> loadBooks() {
        BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
        if (bookService == null) {
            LOG.error("无法获取 BookService 实例");
            return new ArrayList<>();
        }
        try {
            // return ReadAction.compute(() -> bookService.getAllBooks()); // Old sync call
            // Use ReadAction for potential file access, block for sync result needed by dialog
            return ReadAction.compute(() -> bookService.getAllBooks().collectList().block(Duration.ofSeconds(10))); 
        } catch (Exception e) {
            LOG.error("加载书籍列表时出错", e);
            Messages.showErrorDialog(project, "加载书籍列表失败: " + e.getMessage(), "错误");
            return new ArrayList<>();
        }
    }
} 