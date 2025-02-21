package com.lv.tool.privatereader.ui.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.stream.Collectors;

public class BookshelfDialog extends DialogWrapper {
    private final Project project;
    private final JBList<Book> bookList;
    private final BookStorage bookStorage;
    private JPanel mainPanel;
    private JComboBox<String> sortComboBox;

    public BookshelfDialog(Project project) {
        super(project);
        this.project = project;
        this.bookStorage = project.getService(BookStorage.class);
        this.bookList = new JBList<>();
        
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
                        project.getService(BookStorage.class).updateBook(selectedBook);
                        refreshBookList();
                        Messages.showInfoMessage(
                            project,
                            String.format("成功刷新章节列表，共 %d 章", chapters.size()),
                            "刷新成功"
                        );
                    }
                } catch (Exception ex) {
                    Messages.showErrorDialog(
                        project,
                        "刷新章节列表失败：" + ex.getMessage(),
                        "错误"
                    );
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
                    bookStorage.removeBook(selectedBook);
                    refreshBookList();
                }
            }
        });
        toolBar.add(removeButton);
        
        return toolBar;
    }

    private void refreshBookList() {
        List<Book> books = bookStorage.getAllBooks();
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
        List<Book> books = bookStorage.getAllBooks();
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

    private void openSelectedBook() {
        Book selectedBook = bookList.getSelectedValue();
        if (selectedBook != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                // 更新书籍进度
                if (selectedBook.getLastReadChapterId() != null) {
                    selectedBook.setProject(project);
                    NovelParser parser = selectedBook.getParser();
                    if (parser != null) {
                        List<NovelParser.Chapter> chapters = selectedBook.getCachedChapters();
                        if (chapters != null) {
                            // 找到当前章节索引
                            for (int i = 0; i < chapters.size(); i++) {
                                if (selectedBook.getLastReadChapterId().equals(chapters.get(i).url())) {
                                    selectedBook.setCurrentChapterIndex(i + 1); // 设置为1-based索引
                                    selectedBook.setTotalChapters(chapters.size());
                                    // 更新存储
                                    bookStorage.updateBook(selectedBook);
                                    break;
                                }
                            }
                        }
                    }
                }
                
                panel.getBookList().setSelectedValue(selectedBook, true);
                panel.loadLastReadChapter();
                close(OK_EXIT_CODE);
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
                    Messages.showInfoMessage(project, "URL已复制到剪贴板", "复制成功");
                }
            });
            popupMenu.add(copyUrlItem);
            
            popupMenu.show(bookList, e.getX(), e.getY());
        }
    }
} 