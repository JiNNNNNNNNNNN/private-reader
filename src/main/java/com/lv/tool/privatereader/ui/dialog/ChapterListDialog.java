package com.lv.tool.privatereader.ui.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.Nullable;

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

    public ChapterListDialog(Project project, Book book) {
        super(project);
        this.project = project;
        this.book = book;
        this.chapterList = new JBList<>();
        
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
        List<NovelParser.Chapter> chapters = book.getCachedChapters();
        if (chapters != null && !chapters.isEmpty()) {
            chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
            // 选中当前阅读的章节
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
        }
    }

    private void refreshChapters() {
        try {
            // 保存当前选中的位置
            int selectedIndex = chapterList.getSelectedIndex();
            
            book.setProject(project);
            NovelParser parser = book.getParser();
            if (parser != null) {
                List<NovelParser.Chapter> chapters = parser.getChapterList(book);
                chapterList.setListData(chapters.toArray(new NovelParser.Chapter[0]));
                
                // 恢复选中位置
                if (selectedIndex >= 0 && selectedIndex < chapters.size()) {
                    chapterList.setSelectedIndex(selectedIndex);
                    chapterList.ensureIndexIsVisible(selectedIndex);
                }
            }
        } catch (Exception e) {
            String error = "刷新章节列表失败: " + e.getMessage();
            JOptionPane.showMessageDialog(mainPanel, error, "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelectedChapter() {
        NovelParser.Chapter selectedChapter = chapterList.getSelectedValue();
        if (selectedChapter != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                panel.getBookList().setSelectedValue(book, true);
                int index = chapterList.getSelectedIndex();
                panel.loadChapter(selectedChapter, index);
                close(OK_EXIT_CODE);
            }
        }
    }
} 