package com.lv.tool.privatereader.service;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 章节服务接口
 * 处理章节相关的业务逻辑
 */
public interface ChapterService {
    /**
     * 获取书籍的所有章节
     * @param book 书籍对象
     * @return 章节列表
     */
    List<NovelParser.Chapter> getChapters(Book book);
    
    /**
     * 获取章节内容
     * @param book 书籍对象
     * @param chapterId 章节ID
     * @return 章节内容
     */
    String getChapterContent(Book book, String chapterId);
    
    /**
     * 异步获取章节内容
     * @param book 书籍对象
     * @param chapterId 章节ID
     * @return 包含章节内容的CompletableFuture
     */
    CompletableFuture<String> getChapterContentAsync(Book book, String chapterId);
    
    /**
     * 刷新章节内容（强制重新加载）
     * @param book 书籍对象
     * @param chapterId 章节ID
     * @return 刷新后的章节内容
     */
    String refreshChapterContent(Book book, String chapterId);
    
    /**
     * 获取上一章节
     * @param book 书籍对象
     * @param currentChapterId 当前章节ID
     * @return 上一章节，如果没有则返回null
     */
    NovelParser.Chapter getPreviousChapter(Book book, String currentChapterId);
    
    /**
     * 获取下一章节
     * @param book 书籍对象
     * @param currentChapterId 当前章节ID
     * @return 下一章节，如果没有则返回null
     */
    NovelParser.Chapter getNextChapter(Book book, String currentChapterId);
} 