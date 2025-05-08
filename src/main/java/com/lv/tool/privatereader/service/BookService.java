package com.lv.tool.privatereader.service;

import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 书籍服务接口
 * 提供响应式API获取和管理书籍
 */
public interface BookService {
    /**
     * 获取所有书籍
     *
     * @return 书籍列表的Flux流
     */
    Flux<Book> getAllBooks();

    /**
     * 添加书籍
     *
     * @param book 书籍对象
     * @return 添加成功返回true，否则返回false
     */
    Mono<Boolean> addBook(@NotNull Book book);

    /**
     * 删除书籍
     *
     * @param book 书籍对象
     * @return 删除成功返回true，否则返回false
     */
    Mono<Boolean> removeBook(@NotNull Book book);

    /**
     * 更新书籍信息
     *
     * @param book 书籍对象
     * @return 更新成功返回true，否则返回false
     */
    Mono<Boolean> updateBook(@NotNull Book book);

    /**
     * 获取最近阅读的书籍
     *
     * @return 最近阅读的书籍，如果没有则返回空Mono
     */
    Mono<Book> getLastReadBook();

    /**
     * 根据ID获取书籍
     *
     * @param bookId 书籍ID
     * @return 包含书籍的Mono，如果未找到则为空Mono
     */
    Mono<Book> getBookById(@NotNull String bookId);

    /**
     * 保存阅读进度
     *
     * @param book         书籍对象
     * @param chapterId    章节ID
     * @param chapterTitle 章节标题
     * @param position     阅读位置
     * @return 完成信号
     */
    Mono<Void> saveReadingProgress(@NotNull Book book, @NotNull String chapterId, String chapterTitle, int position);

    /**
     * 同步获取书籍的章节列表 (包含内容)
     * 注意：这是一个阻塞操作，谨慎使用。
     *
     * @param bookId 书籍ID
     * @return 包含内容的章节列表，如果书籍不存在或获取失败则返回 null 或空列表
     */
    @Nullable
    List<ChapterService.EnhancedChapter> getChaptersSync(@NotNull String bookId);

    /**
     * 清除章节缓存
     * 当书籍内容更新时调用
     *
     * @param bookId 书籍ID，如果为null则清除所有缓存
     */
    void clearChaptersCache(@Nullable String bookId);
}