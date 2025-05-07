package com.lv.tool.privatereader.service;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 章节服务接口
 * 提供响应式API获取章节内容和管理章节缓存
 */
public interface ChapterService {
    /**
     * 获取章节对象
     *
     * @param book      书籍
     * @param chapterId 章节ID
     * @return 章节对象
     */
    Mono<Chapter> getChapter(@NotNull Book book, @NotNull String chapterId);

    /**
     * 获取章节对象，如果获取失败则尝试使用缓存中的过期内容
     *
     * @param book      书籍
     * @param chapterId 章节ID
     * @return 章节对象
     */
    Mono<Chapter> getChapterWithFallback(@NotNull Book book, @NotNull String chapterId);

    /**
     * 获取章节列表
     *
     * @param book 书籍
     * @return 章节列表
     */
    Mono<List<Chapter>> getChapterList(@NotNull Book book);

    /**
     * 预加载章节
     *
     * @param book             书籍
     * @param currentChapterId 当前章节ID
     * @param count            预加载数量
     * @return 预加载的章节流
     */
    Flux<Chapter> preloadChapters(@NotNull Book book, @NotNull String currentChapterId, int count);

    /**
     * 清除书籍缓存
     *
     * @param book 书籍
     * @return 完成信号
     */
    Mono<Void> clearBookCache(@NotNull Book book);

    /**
     * 清除所有缓存
     *
     * @return 完成信号
     */
    Mono<Void> clearAllCache();
    
    /**
     * 获取章节内容 (同步)
     *
     * @param bookId    书籍ID
     * @param chapterId 章节ID
     * @return 章节内容
     */
    String getChapterContent(@NotNull String bookId, @NotNull String chapterId);

    /**
     * 获取章节标题 (同步)
     *
     * @param bookId    书籍ID
     * @param chapterId 章节ID
     * @return 章节标题
     */
    String getChapterTitle(@NotNull String bookId, @NotNull String chapterId);

    /**
     * 扩展章节类，包含内容
     */
    class EnhancedChapter extends Chapter {
        private final String content;

        public EnhancedChapter(String title, String url, String content) {
            super(title, url);
            this.content = content != null ? content : "";
        }

        public String getContent() {
            return content;
        }
    }
}