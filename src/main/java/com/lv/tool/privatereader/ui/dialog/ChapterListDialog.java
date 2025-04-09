package com.lv.tool.privatereader.ui.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ChapterListDialog extends DialogWrapper {
    private final Project project;
    private final Book book;
    private final JBList<NovelParser.Chapter> chapterList;
    private JPanel mainPanel;
    private JLabel infoLabel;
    private JProgressBar loadingProgress;
    private final ChapterService chapterService;
    private static final Logger LOG = LoggerFactory.getLogger(ChapterListDialog.class);

    public ChapterListDialog(Project project, Book book) {
        super(project);
        this.project = project;
        this.book = book;
        this.chapterList = new JBList<>();
        this.chapterService = project.getService(ChapterService.class);
        
        init();
        setTitle("章节列表 - " + book.getTitle());
        setSize(500, 600);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // 创建信息面板
        createInfoPanel();
        
        // 创建章节列表
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedChapter();
                }
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(chapterList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // 创建加载进度条
        loadingProgress = new JProgressBar();
        loadingProgress.setIndeterminate(true);
        loadingProgress.setVisible(false);
        mainPanel.add(loadingProgress, BorderLayout.SOUTH);
        
        // 加载章节
        loadChapters();
        
        return mainPanel;
    }

    private void createInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(JBUI.Borders.empty(0, 0, 10, 0));
        
        // 显示书籍信息
        infoLabel = new JLabel(String.format("<html>书名：%s<br>作者：%s<br>进度：%d/%d 章 (%.1f%%)</html>",
            book.getTitle(),
            book.getAuthor(),
            book.getCurrentChapterIndex(),
            book.getTotalChapters(),
            book.getReadingProgress() * 100));
        infoPanel.add(infoLabel, BorderLayout.CENTER);
        
        // 添加刷新按钮
        JButton refreshButton = new JButton("刷新章节");
        refreshButton.addActionListener(e -> refreshChapters());
        infoPanel.add(refreshButton, BorderLayout.EAST);
        
        mainPanel.add(infoPanel, BorderLayout.NORTH);
    }

    private void loadChapters() {
        if (book == null || chapterService == null) {
            chapterList.setListData(new NovelParser.Chapter[0]);
            return;
        }
        
        setLoading(true);
        chapterService.getChapterList(book)
            .publishOn(ReactiveSchedulers.getInstance().ui())
            .subscribe(
                chapters -> {
                    chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                    // Try to select current chapter if provided
                    String lastChapterId = book.getLastReadChapterId();
                    if (lastChapterId != null) {
                        for (int i = 0; i < chapters.size(); i++) {
                            if (lastChapterId.equals(chapters.get(i).url())) {
                                chapterList.setSelectedIndex(i);
                                chapterList.ensureIndexIsVisible(i);
                                break;
                            }
                        }
                    }
                    updateInfoLabel(chapters);
                    setLoading(false);
                },
                error -> {
                    LOG.error("加载章节列表失败 for book: " + book.getTitle(), error);
                    Messages.showErrorDialog(project, "加载章节列表失败: " + error.getMessage(), "错误");
                    chapterList.setListData(new NovelParser.Chapter[0]);
                    setLoading(false);
                }
            );
    }

    private void updateInfoLabel(List<NovelParser.Chapter> chapters) {
        if (chapters != null && !chapters.isEmpty()) {
            book.setTotalChapters(chapters.size());
            infoLabel.setText(String.format("<html>书名：%s<br>作者：%s<br>进度：%d/%d 章 (%.1f%%)</html>",
                book.getTitle(),
                book.getAuthor(),
                book.getCurrentChapterIndex(),
                book.getTotalChapters(),
                book.getReadingProgress() * 100));
        }
    }

    private void refreshChapters() {
        setLoading(true);
        chapterService.getChapterList(book)
            .publishOn(ReactiveSchedulers.getInstance().ui())
            .subscribe(
                chapters -> {
                    chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                    // Try to select current chapter if provided
                    String lastChapterId = book.getLastReadChapterId();
                    if (lastChapterId != null) {
                        for (int i = 0; i < chapters.size(); i++) {
                            if (lastChapterId.equals(chapters.get(i).url())) {
                                chapterList.setSelectedIndex(i);
                                chapterList.ensureIndexIsVisible(i);
                                break;
                            }
                        }
                    }
                    updateInfoLabel(chapters);
                    book.setCachedChapters(chapters);
                    JOptionPane.showMessageDialog(mainPanel,
                        String.format("成功刷新章节列表，共 %d 章", chapters.size()),
                        "刷新成功",
                        JOptionPane.INFORMATION_MESSAGE);
                    setLoading(false);
                },
                error -> {
                    LOG.error("刷新章节列表失败 for book: " + book.getTitle(), error);
                    Messages.showErrorDialog(project, "刷新章节列表失败: " + error.getMessage(), "错误");
                    chapterList.setListData(new NovelParser.Chapter[0]);
                    setLoading(false);
                }
            );
    }

    private void openSelectedChapter() {
        NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();
        if (selectedChapter != null) {
            close(OK_EXIT_CODE);
            // 通知阅读器加载选中的章节
            // 这里需要实现具体的加载逻辑
        }
    }

    private void setLoading(boolean loading) {
        loadingProgress.setIndeterminate(loading);
        loadingProgress.setVisible(loading);
    }
} 