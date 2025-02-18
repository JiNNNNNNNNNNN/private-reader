package com.lv.tool.privatereader.parser;

import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * 小说解析器接口
 */
public interface NovelParser {
    /**
     * 获取小说标题
     */
    String getTitle();

    /**
     * 获取作者
     */
    String getAuthor();

    /**
     * 获取章节列表
     * 优先尝试重新解析，解析失败时使用缓存
     */
    default List<Chapter> getChapterList(Book book) {
        try {
            List<Chapter> chapters = parseChapterList();
            // 更新缓存
            book.setCachedChapters(chapters);
            return chapters;
        } catch (Exception e) {
            // 解析失败，使用缓存
            List<Chapter> cachedChapters = book.getCachedChapters();
            if (cachedChapters != null && !cachedChapters.isEmpty()) {
                return cachedChapters;
            }
            // 缓存也不可用，抛出异常
            throw new RuntimeException("无法获取章节列表", e);
        }
    }

    /**
     * 解析章节列表
     * 实际的解析逻辑由具体实现类提供
     */
    List<Chapter> parseChapterList();

    /**
     * 获取章节内容
     * 优先尝试重新获取，失败时使用缓存
     */
    default String getChapterContent(String chapterId, Book book) {
        Project project = book.getProject();
        if (project == null) {
            throw new IllegalStateException("Book project is not set");
        }

        try {
            String content = parseChapterContent(chapterId);
            // 更新缓存
            project.getService(ChapterCacheManager.class)
                .cacheContent(book.getId(), chapterId, content);
            return content;
        } catch (Exception e) {
            // 获取失败，尝试使用缓存
            String cachedContent = project.getService(ChapterCacheManager.class)
                .getCachedContent(book.getId(), chapterId);
            if (cachedContent != null) {
                return cachedContent;
            }
            // 缓存也不可用，返回错误信息
            return "章节内容暂时无法访问：" + e.getMessage();
        }
    }

    /**
     * 解析章节内容
     * 实际的解析逻辑由具体实现类提供
     */
    String parseChapterContent(String chapterId);

    /**
     * 章节信息
     */
    @Tag("Chapter")
    public static class Chapter {
        @Tag private String title;
        @Tag private String url;

        public Chapter() {
            // 默认构造函数
        }

        public Chapter(String title, String url) {
            this.title = title;
            this.url = url;
        }

        @NotNull
        public String title() {
            return title == null ? "" : title;
        }

        @NotNull
        public String url() {
            return url == null ? "" : url;
        }

        public void setTitle(@NotNull String title) {
            this.title = title;
        }

        public void setUrl(@NotNull String url) {
            this.url = url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Chapter chapter = (Chapter) o;
            return Objects.equals(title, chapter.title) && Objects.equals(url, chapter.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, url);
        }

        @Override
        public String toString() {
            return title;
        }
    }
} 