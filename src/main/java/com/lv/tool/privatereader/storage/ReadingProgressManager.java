package com.lv.tool.privatereader.storage;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;
import java.time.Instant;

/**
 * 阅读进度管理器
 */
@Service(Service.Level.PROJECT)
public final class ReadingProgressManager {
    private final Project project;
    private final BookStorage bookStorage;

    public ReadingProgressManager(Project project) {
        this.project = project;
        this.bookStorage = project.getService(BookStorage.class);
    }

    /**
     * 更新阅读进度
     */
    public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position) {
        book.setLastReadChapterId(chapterId);
        book.setLastReadChapter(chapterTitle);
        book.setLastReadPosition(position);
        book.setLastReadTimeMillis(System.currentTimeMillis());
        
        // 自动处理已读未读状态
        if (book.getCurrentChapterIndex() >= book.getTotalChapters() - 1 && position > 0.9) {
            book.setFinished(true);
        } else {
            book.setFinished(false);
        }
        bookStorage.updateBook(book);
    }

    /**
     * 更新章节总数
     */
    public void updateTotalChapters(@NotNull Book book, int totalChapters) {
        book.setTotalChapters(totalChapters);
        
        // 更新已读未读状态
        if (book.getCurrentChapterIndex() >= totalChapters - 1 && book.getLastReadPosition() > 0.9) {
            book.setFinished(true);
        } else {
            book.setFinished(false);
        }
        bookStorage.updateBook(book);
    }

    /**
     * 更新当前章节索引
     */
    public void updateCurrentChapter(@NotNull Book book, int currentChapterIndex) {
        book.setCurrentChapterIndex(currentChapterIndex);
        
        // 更新已读未读状态
        if (currentChapterIndex >= book.getTotalChapters() - 1 && book.getLastReadPosition() > 0.9) {
            book.setFinished(true);
        } else {
            book.setFinished(false);
        }
        bookStorage.updateBook(book);
    }

    /**
     * 标记书籍为已读
     */
    public void markAsFinished(@NotNull Book book) {
        book.setFinished(true);
        book.setCurrentChapterIndex(book.getTotalChapters());
        bookStorage.updateBook(book);
    }

    /**
     * 标记书籍为未读
     */
    public void markAsUnfinished(@NotNull Book book) {
        book.setFinished(false);
        bookStorage.updateBook(book);
    }

    /**
     * 重置阅读进度
     */
    public void resetProgress(@NotNull Book book) {
        book.setLastReadChapterId(null);
        book.setLastReadChapter(null);
        book.setLastReadPosition(0);
        book.setLastReadTimeMillis(0L);
        book.setCurrentChapterIndex(0);
        book.setFinished(false);
        bookStorage.updateBook(book);
    }
} 