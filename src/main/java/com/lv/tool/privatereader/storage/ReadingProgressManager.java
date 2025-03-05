package com.lv.tool.privatereader.storage;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.ui.topics.BookshelfTopics;
import org.jetbrains.annotations.NotNull;
import java.time.Instant;

/**
 * 阅读进度管理器
 */
@Service(Service.Level.PROJECT)
public final class ReadingProgressManager {
    private static final Logger LOG = Logger.getInstance(ReadingProgressManager.class);
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
        updateProgress(book, chapterId, chapterTitle, position, 1);
    }

    public void updateProgress(Book book, String chapterId, String chapterTitle, int position, int page) {
        if (book == null || chapterId == null) {
            LOG.warn("无法更新阅读进度：book 或 chapterId 为空");
            return;
        }
        
        // 更新书籍进度
        book.updateReadingProgress(chapterId, chapterTitle, position, page);
        
        // 更新存储
        BookStorage bookStorage = project.getService(BookStorage.class);
        bookStorage.updateBook(book);
        
        // 发布更新事件
        project.getMessageBus().syncPublisher(BookshelfTopics.BOOK_UPDATED).bookUpdated(book);
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
        if (book == null) {
            LOG.warn("无法更新当前章节：book 为空");
            return;
        }
        
        book.setCurrentChapterIndex(currentChapterIndex);
        
        // 保存更新
        project.getService(BookStorage.class).updateBook(book);
        
        // 发布更新事件
        project.getMessageBus().syncPublisher(BookshelfTopics.BOOK_UPDATED).bookUpdated(book);
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
        if (book == null) {
            LOG.warn("无法重置阅读进度：book 为空");
            return;
        }
        
        book.setCurrentChapterIndex(0);
        book.updateReadingProgress("", "", 0, 1);
        
        // 保存更新
        project.getService(BookStorage.class).updateBook(book);
        
        // 发布更新事件
        project.getMessageBus().syncPublisher(BookshelfTopics.BOOK_UPDATED).bookUpdated(book);
    }
} 