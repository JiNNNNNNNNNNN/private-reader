package com.lv.tool.privatereader.repository;

import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;

/**
 * 阅读进度仓库接口
 * 
 * 定义阅读进度的存储和检索操作，提供统一的数据访问接口。
 * 实现类负责具体的存储实现细节。
 */
public interface ReadingProgressRepository {
    
    /**
     * 更新阅读进度
     * 
     * @param book 书籍对象
     * @param chapterId 章节ID
     * @param chapterTitle 章节标题
     * @param position 阅读位置
     */
    void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position);
    
    /**
     * 更新阅读进度
     * 
     * @param book 书籍对象
     * @param chapterId 章节ID
     * @param chapterTitle 章节标题
     * @param position 阅读位置
     * @param page 页码
     */
    void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position, int page);
    
    /**
     * 更新章节总数
     * 
     * @param book 书籍对象
     * @param totalChapters 章节总数
     */
    void updateTotalChapters(@NotNull Book book, int totalChapters);
    
    /**
     * 更新当前章节索引
     * 
     * @param book 书籍对象
     * @param currentChapterIndex 当前章节索引
     */
    void updateCurrentChapter(@NotNull Book book, int currentChapterIndex);
    
    /**
     * 标记书籍为已读
     * 
     * @param book 书籍对象
     */
    void markAsFinished(@NotNull Book book);
    
    /**
     * 标记书籍为未读
     * 
     * @param book 书籍对象
     */
    void markAsUnfinished(@NotNull Book book);
    
    /**
     * 重置阅读进度
     * 
     * @param book 书籍对象
     */
    void resetProgress(@NotNull Book book);
} 