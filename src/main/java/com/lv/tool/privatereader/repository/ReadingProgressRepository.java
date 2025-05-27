package com.lv.tool.privatereader.repository;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookProgressData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 阅读进度仓库接口
 * 
 * 定义阅读进度的存储和检索操作，提供统一的数据访问接口。
 * 实现类负责具体的存储实现细节。
 */
public interface ReadingProgressRepository {
    
    /**
     * 更新阅读进度 (使用默认页码 1)
     *
     * @param book 书籍对象 (包含 isFinished 状态)
     * @param chapterId 章节ID
     * @param chapterTitle 章节标题
     * @param position 阅读位置
     */
    void updateProgress(@NotNull Book book, @Nullable String chapterId, @Nullable String chapterTitle, int position);
    
    /**
     * 更新阅读进度 (包含页码)
     *
     * @param book 书籍对象 (包含 isFinished 状态)
     * @param chapterId 章节ID
     * @param chapterTitle 章节标题
     * @param position 阅读位置
     * @param page 页码
     */
    void updateProgress(@NotNull Book book, @Nullable String chapterId, @Nullable String chapterTitle, int position, int page);
    
    /**
     * 标记书籍为已读 (内部会调用 updateProgress 更新状态)
     *
     * @param book 书籍对象
     */
    void markAsFinished(@NotNull Book book);
    
    /**
     * 标记书籍为未读 (内部会调用 updateProgress 更新状态)
     *
     * @param book 书籍对象
     */
    void markAsUnfinished(@NotNull Book book);
    
    /**
     * 重置阅读进度 (删除进度记录)
     *
     * @param book 书籍对象
     */
    void resetProgress(@NotNull Book book);

    /**
     * Retrieves the latest reading progress for a specific book.
     *
     * @param bookId The ID of the book.
     * @return An Optional containing BookProgressData if found, otherwise empty.
     */
    @NotNull
    Optional<BookProgressData> getProgress(@NotNull String bookId);

    /**
     * Retrieves the progress data for the most recently read book across all books.
     *
     * @return An Optional containing BookProgressData for the last read book, otherwise empty.
     */
    @NotNull
    Optional<BookProgressData> getLastReadProgressData();
} 