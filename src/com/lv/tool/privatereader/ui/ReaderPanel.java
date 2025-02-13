package com.lv.tool.privatereader.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.persistence.BookStorage;
import com.lv.tool.privatereader.cache.ChapterCacheManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 阅读面板
 */
public class ReaderPanel extends JPanel {
    private final Project project;
    private final Book book;
    private final NovelParser parser;
    private final ChapterCacheManager cacheManager;
    private final JBTextArea contentArea;
    private final JComboBox<String> chapterComboBox;
    private List<NovelParser.Chapter> chapters;
    private int currentChapter;

    public ReaderPanel(@NotNull Project project, @NotNull Book book) {
        this.project = project;
        this.book = book;
        this.parser = ParserFactory.createParser(book.getUrl());
        this.cacheManager = project.getService(ChapterCacheManager.class);
        this.contentArea = new JBTextArea();
        this.chapterComboBox = new JComboBox<>();
        this.currentChapter = book.getLastChapter();

        setupUI();
        loadChapters();
        loadContent();
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        // 顶部工具栏
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton prevButton = new JButton("上一章");
        JButton nextButton = new JButton("下一章");
        toolbar.add(prevButton);
        toolbar.add(chapterComboBox);
        toolbar.add(nextButton);
        add(toolbar, BorderLayout.NORTH);

        // 内容区域
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        add(new JBScrollPane(contentArea), BorderLayout.CENTER);

        // 事件处理
        prevButton.addActionListener(e -> {
            if (currentChapter > 1) {
                currentChapter--;
                chapterComboBox.setSelectedIndex(currentChapter - 1);
                loadContent();
            }
        });

        nextButton.addActionListener(e -> {
            if (currentChapter < chapters.size()) {
                currentChapter++;
                chapterComboBox.setSelectedIndex(currentChapter - 1);
                loadContent();
            }
        });

        chapterComboBox.addActionListener(e -> {
            int selectedIndex = chapterComboBox.getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < chapters.size()) {
                currentChapter = selectedIndex + 1;
                loadContent();
            }
        });
    }

    private void loadChapters() {
        chapters = parser.getChapterList();
        chapterComboBox.removeAllItems();
        for (NovelParser.Chapter chapter : chapters) {
            chapterComboBox.addItem(chapter.title());
        }
        chapterComboBox.setSelectedIndex(currentChapter - 1);
    }

    private void loadContent() {
        if (currentChapter < 1 || currentChapter > chapters.size()) {
            return;
        }

        NovelParser.Chapter chapter = chapters.get(currentChapter - 1);
        String content = cacheManager.getCachedChapter(book.getUrl(), chapter.url());

        if (content == null) {
            content = parser.getChapterContent(chapter.url());
            cacheManager.cacheChapter(book.getUrl(), chapter.url(), content);
        }

        contentArea.setText(content);
        contentArea.setCaretPosition(0);

        // 更新最后阅读章节
        book.setLastChapter(currentChapter);
        BookStorage bookStorage = project.getService(BookStorage.class);
        bookStorage.addBook(book);  // 这里的 addBook 方法会更新现有记录
    }

    public void refresh() {
        loadChapters();
        loadContent();
    }
} 