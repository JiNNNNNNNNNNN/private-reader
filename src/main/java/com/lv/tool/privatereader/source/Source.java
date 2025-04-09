package com.lv.tool.privatereader.source;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 源接口
 * 
 * 定义了获取书籍信息和章节内容的方法，每个源实现负责解析特定网站的内容。
 */
public interface Source {
    /**
     * 获取源ID
     *
     * @return 源ID
     */
    @NotNull
    String getId();
    
    /**
     * 获取源名称
     *
     * @return 源名称
     */
    @NotNull
    String getName();
    
    /**
     * 获取源描述
     *
     * @return 源描述
     */
    @NotNull
    String getDescription();
    
    /**
     * 检查URL是否由该源支持
     *
     * @param url 书籍URL
     * @return 是否支持
     */
    boolean supports(@NotNull String url);
    
    /**
     * 获取书籍信息
     *
     * @param url 书籍URL
     * @return 书籍信息
     */
    @NotNull
    Book getBookInfo(@NotNull String url);
    
    /**
     * 获取章节列表
     *
     * @param book 书籍
     * @return 章节列表
     */
    @NotNull
    List<Chapter> getChapterList(@NotNull Book book);
    
    /**
     * 获取章节内容
     *
     * @param book 书籍
     * @param chapterId 章节ID
     * @return 章节内容
     */
    @NotNull
    String getChapterContent(@NotNull Book book, @NotNull String chapterId);
    
    /**
     * 搜索书籍
     *
     * @param keyword 关键词
     * @return 书籍列表
     */
    @NotNull
    List<Book> searchBooks(@NotNull String keyword);
} 