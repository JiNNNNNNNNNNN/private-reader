package com.lv.tool.privatereader.service.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ChapterService接口的实现类
 */
public final class ChapterServiceImpl implements ChapterService {
    private static final Logger LOG = Logger.getInstance(ChapterServiceImpl.class);
    private final Project project;
    private final ChapterCacheManager cacheManager;

    public ChapterServiceImpl(Project project) {
        LOG.info("初始化ChapterServiceImpl");
        this.project = project;
        this.cacheManager = project.getService(ChapterCacheManager.class);
        
        if (this.cacheManager == null) {
            LOG.error("ChapterCacheManager服务未初始化");
        }
    }

    @Override
    public List<Chapter> getChapters(Book book) {
        if (book == null) {
            LOG.error("书籍对象为空");
            return List.of();
        }
        
        // 确保Book对象的project属性被设置
        if (book.getProject() == null) {
            LOG.info("设置Book对象的project属性");
            book.setProject(project);
        }
        
        if (book.getParser() == null) {
            LOG.error("书籍解析器未初始化");
            return List.of();
        }
        
        try {
            return book.getParser().getChapterList(book);
        } catch (Exception e) {
            LOG.error("获取章节列表失败", e);
            return List.of();
        }
    }

    @Override
    public String getChapterContent(Book book, String chapterId) {
        if (book == null || chapterId == null) {
            LOG.error("书籍对象或章节ID为空");
            return "无法加载章节内容：参数无效";
        }
        
        // 确保Book对象的project属性被设置
        if (book.getProject() == null) {
            LOG.info("设置Book对象的project属性");
            book.setProject(project);
        }
        
        if (book.getParser() == null) {
            LOG.error("书籍解析器未初始化");
            return "无法加载章节内容：解析器未初始化";
        }
        
        try {
            return book.getParser().getChapterContent(chapterId, book);
        } catch (Exception e) {
            LOG.error("获取章节内容失败", e);
            return "获取章节内容失败: " + e.getMessage();
        }
    }

    @Override
    public CompletableFuture<String> getChapterContentAsync(Book book, String chapterId) {
        return CompletableFuture.supplyAsync(() -> getChapterContent(book, chapterId));
    }

    @Override
    public String refreshChapterContent(Book book, String chapterId) {
        if (book == null || chapterId == null) {
            LOG.error("书籍对象或章节ID为空");
            return "无法刷新章节内容：参数无效";
        }
        
        // 确保Book对象的project属性被设置
        if (book.getProject() == null) {
            book.setProject(project);
        }
        
        if (cacheManager == null) {
            LOG.error("ChapterCacheManager服务未初始化");
            return "无法刷新章节内容：缓存管理器未初始化";
        }
        
        // 清除缓存
        cacheManager.clearCache(book.getId());
        // 重新获取内容
        return getChapterContent(book, chapterId);
    }

    @Override
    @Nullable
    public Chapter getPreviousChapter(Book book, String currentChapterId) {
        if (book == null || currentChapterId == null) {
            return null;
        }
        
        // 确保Book对象的project属性被设置
        if (book.getProject() == null) {
            book.setProject(project);
        }
        
        List<Chapter> chapters = getChapters(book);
        if (chapters == null || chapters.isEmpty()) {
            return null;
        }

        // 查找当前章节的索引
        int currentIndex = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).url().equals(currentChapterId)) {
                currentIndex = i;
                break;
            }
        }

        // 如果找到当前章节且不是第一章，返回上一章
        if (currentIndex > 0) {
            return chapters.get(currentIndex - 1);
        }
        
        return null;
    }

    @Override
    @Nullable
    public Chapter getNextChapter(Book book, String currentChapterId) {
        if (book == null || currentChapterId == null) {
            return null;
        }
        
        // 确保Book对象的project属性被设置
        if (book.getProject() == null) {
            book.setProject(project);
        }
        
        List<Chapter> chapters = getChapters(book);
        if (chapters == null || chapters.isEmpty()) {
            return null;
        }

        // 查找当前章节的索引
        int currentIndex = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).url().equals(currentChapterId)) {
                currentIndex = i;
                break;
            }
        }

        // 如果找到当前章节且不是最后一章，返回下一章
        if (currentIndex >= 0 && currentIndex < chapters.size() - 1) {
            return chapters.get(currentIndex + 1);
        }
        
        return null;
    }
} 