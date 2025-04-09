package com.lv.tool.privatereader.source.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.site.UniversalParser;
import com.lv.tool.privatereader.source.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 通用源实现
 * 
 * 支持通过通用解析器解析大多数网站的内容
 */
public class UniversalSource implements Source {
    private static final Logger LOG = Logger.getInstance(UniversalSource.class);
    private static final String ID = "universal";
    private static final String NAME = "通用解析器";
    private static final String DESCRIPTION = "通过智能分析网页结构解析大多数小说网站";

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @NotNull
    public String getName() {
        return NAME;
    }

    @Override
    @NotNull
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public boolean supports(@NotNull String url) {
        // 通用解析器尝试解析任何URL
        return url.startsWith("http");
    }
    
    /**
     * 支持URL的别名方法
     */
    public boolean supportsUrl(@NotNull String url) {
        return supports(url);
    }

    @Override
    @NotNull
    public Book getBookInfo(@NotNull String url) {
        LOG.info("通过通用解析器获取书籍信息: " + url);
        
        try {
            NovelParser parser = new UniversalParser(url);
            String title = parser.getTitle();
            String author = parser.getAuthor();
            
            // 创建新的Book对象
            Book book = new Book();
            book.setId(UUID.randomUUID().toString());
            book.setTitle(title);
            book.setAuthor(author);
            book.setUrl(url);
            book.setSourceId(ID);
            book.setCreateTimeMillis(System.currentTimeMillis());
            book.setLastReadTimeMillis(System.currentTimeMillis());
            
            return book;
        } catch (Exception e) {
            LOG.error("获取书籍信息失败: " + url, e);
            
            // 创建一个包含基本信息的Book对象
            Book book = new Book();
            book.setId(UUID.randomUUID().toString());
            book.setTitle(url.substring(url.lastIndexOf('/') + 1));
            book.setAuthor("未知");
            book.setUrl(url);
            book.setSourceId(ID);
            book.setCreateTimeMillis(System.currentTimeMillis());
            book.setLastReadTimeMillis(System.currentTimeMillis());
            
            return book;
        }
    }

    @Override
    @NotNull
    public List<NovelParser.Chapter> getChapterList(@NotNull Book book) {
        LOG.info("通过通用解析器获取章节列表: " + book.getTitle());
        
        try {
            NovelParser parser = new UniversalParser(book.getUrl());
            return parser.parseChapterList();
        } catch (Exception e) {
            LOG.error("获取章节列表失败: " + book.getUrl(), e);
            return new ArrayList<>();
        }
    }

    @Override
    @NotNull
    public String getChapterContent(@NotNull Book book, @NotNull String chapterId) {
        LOG.info("通过通用解析器获取章节内容: " + book.getTitle() + " - " + chapterId);
        
        try {
            NovelParser parser = new UniversalParser(book.getUrl());
            return parser.parseChapterContent(chapterId);
        } catch (Exception e) {
            LOG.error("获取章节内容失败: " + chapterId, e);
            return "无法获取章节内容，请检查网络连接或联系开发者。错误信息：" + e.getMessage();
        }
    }

    @Override
    @NotNull
    public List<Book> searchBooks(@NotNull String keyword) {
        LOG.info("通用解析器不支持搜索功能");
        // 通用解析器不支持搜索
        return new ArrayList<>();
    }
} 