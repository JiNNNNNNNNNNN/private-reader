package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
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

    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    public PrivateReaderPanel(Project project) {
        LOG.info("初始化阅读器面板");
        this.project = project;
        this.progressManager = project.getService(ReadingProgressManager.class);
        instance = this;

        // 设置布局
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(5));

        // 创建工具栏
        JPanel toolBar = createToolBar();
        add(toolBar, BorderLayout.NORTH);

        // 创建导航按钮
        prevChapterBtn = new JButton("上一章");
        nextChapterBtn = new JButton("下一章");
        JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        navigationPanel.add(prevChapterBtn);
        navigationPanel.add(nextChapterBtn);
        
        // 添加导航面板到底部
        add(navigationPanel, BorderLayout.SOUTH);
        
        // 添加导航按钮事件
        prevChapterBtn.addActionListener(e -> navigateChapter(-1));
        nextChapterBtn.addActionListener(e -> navigateChapter(1));

        // 创建书籍列表
        bookList = new JBList<>();
        bookList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookList.addListSelectionListener(e -> {
            updateProgressInfo();
            updateChapterList();
        });
        JBScrollPane bookScrollPane = new JBScrollPane(bookList);
        bookScrollPane.setPreferredSize(new Dimension(200, -1));

        // 创建章节列表
        chapterList = new JBList<>();
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();
                if (selectedChapter != null) {
                    LOG.info(String.format("选中章节 - 标题: %s, URL: %s, 索引: %d/%d", 
                        selectedChapter.title(),
                        selectedChapter.url(),
                        chapterList.getSelectedIndex() + 1,
                        chapterList.getModel().getSize()));
                    loadChapter(selectedChapter);
                }
            }
        });
        JBScrollPane chapterScrollPane = new JBScrollPane(chapterList);
        chapterScrollPane.setPreferredSize(new Dimension(200, -1));

        // 创建进度信息面板
        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        progressLabel = new JBLabel();
        lastReadLabel = new JBLabel();
        progressPanel.add(progressLabel);
        progressPanel.add(lastReadLabel);
        progressPanel.setBorder(JBUI.Borders.empty(5));

        // 创建内容区域
        contentArea = new JTextPane();
        contentArea.setEditable(false);
        JBScrollPane contentScrollPane = new JBScrollPane(contentArea);

        // 创建右侧面板
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(progressPanel, BorderLayout.NORTH);
        rightPanel.add(contentScrollPane, BorderLayout.CENTER);

        // 创建左侧面板（包含书籍列表和章节列表）
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                bookScrollPane, chapterScrollPane);
        leftSplitPane.setDividerLocation(200);

        // 添加分隔面板
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftSplitPane, rightPanel);
        mainSplitPane.setDividerLocation(200);
        add(mainSplitPane, BorderLayout.CENTER);

        // 添加键盘快捷键
        setupKeyboardShortcuts();

        // 初始化数据
        refresh();
        setupBookSelectionListener();
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
        
        // 设置字体和样式
        contentArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        
        // 设置行距和段落属性
        StyledDocument doc = contentArea.getStyledDocument();
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(attrs, 0.3f);
        doc.setParagraphAttributes(0, doc.getLength(), attrs, true);
        
        // 设置边距
        contentArea.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
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
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // 添加书籍按钮
        JButton addButton = new JButton("添加书籍");
        addButton.addActionListener(e -> {
            AddBookDialog dialog = new AddBookDialog(project);
            dialog.show();
        });
        
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
                    BookStorage bookStorage = project.getService(BookStorage.class);
                    bookStorage.removeBook(selectedBook);
                    refresh();
                }
            }
        });
        
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
                    progressManager.resetProgress(selectedBook);
                    refresh();
                }
            }
        });

        // 刷新章节列表按钮
        JButton refreshChaptersButton = new JButton("刷新章节列表");
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
        
        toolBar.add(addButton);
        toolBar.add(removeButton);
        toolBar.add(resetButton);
        toolBar.add(refreshChaptersButton);
        
        return toolBar;
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
            LOG.debug("更新章节列表: " + selectedBook.getTitle());
            try {
                selectedBook.setProject(project);
                NovelParser parser = selectedBook.getParser();
                if (parser != null) {
                    List<NovelParser.Chapter> chapters = selectedBook.getCachedChapters();
                    
                    if (chapters == null) {
                        LOG.debug("章节缓存不存在，获取章节列表");
                        chapters = parser.getChapterList(selectedBook);
                        selectedBook.setCachedChapters(chapters);
                        // 更新存储
                        project.getService(BookStorage.class).updateBook(selectedBook);
                        LOG.info(String.format("缓存章节列表 - 书籍: %s, 章节数: %d", 
                            selectedBook.getTitle(), chapters.size()));
                    } else {
                        LOG.debug("使用缓存的章节列表");
                    }
                    
                    chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                    
                    // 选中当前阅读的章节
                    String lastChapterId = selectedBook.getLastReadChapterId();
                    if (lastChapterId != null) {
                        for (int i = 0; i < chapters.size(); i++) {
                            if (chapters.get(i).url().equals(lastChapterId)) {
                                chapterList.setSelectedIndex(i);
                                chapterList.ensureIndexIsVisible(i);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("获取章节列表失败: " + e.getMessage(), e);
                Messages.showErrorDialog(
                    project,
                    "获取章节列表失败：" + e.getMessage(),
                    "错误"
                );
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
            LOG.info(String.format("加载章节 - 书籍: %s, 章节: %s", 
                selectedBook.getTitle(), chapter.title()));
            try {
                selectedBook.setProject(project);
                NovelParser parser = selectedBook.getParser();
                if (parser == null) {
                    LOG.error("解析器为空，尝试重新创建");
                    parser = ParserFactory.createParser(selectedBook.getUrl());
                    selectedBook.setParser(parser);
                }
                
                if (parser == null) {
                    throw new IllegalStateException("无法创建解析器");
                }
                
                String content = parser.getChapterContent(chapter.url(), selectedBook);
                if (content == null || content.isEmpty()) {
                    throw new IllegalStateException("获取到的章节内容为空");
                }
                
                setContent(content);
                currentChapterId = chapter.url();
                // 更新当前章节索引
                int currentIndex = chapterList.getSelectedIndex();
                progressManager.updateCurrentChapter(selectedBook, currentIndex);
                updateReadingProgress(currentChapterId, chapter.title(), 0);
                
                // 更新导航按钮状态
                prevChapterBtn.setEnabled(currentIndex > 0);
                nextChapterBtn.setEnabled(currentIndex < chapterList.getModel().getSize() - 1);
            } catch (Exception e) {
                LOG.error("加载章节失败: " + e.getMessage(), e);
                Messages.showErrorDialog(
                    project,
                    "加载章节失败：" + e.getMessage(),
                    "错误"
                );
            }
        } else {
            if (selectedBook == null) {
                LOG.error("未选择书籍");
            }
            if (chapter == null) {
                LOG.error("章节为空");
            }
        }
    }
} 