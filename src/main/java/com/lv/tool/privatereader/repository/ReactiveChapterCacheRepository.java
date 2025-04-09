package com.lv.tool.privatereader.repository;

import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * 响应式章节缓存仓库接口
 * 
 * 定义章节缓存的响应式存储和检索操作，提供统一的响应式数据访问接口。
 * 实现类负责具体的缓存实现细节。
 */
public interface ReactiveChapterCacheRepository {
    
    /**
     * 响应式获取缓存的章节内容
     * 如果缓存不存在或已过期，返回空Mono
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 包含缓存章节内容的Mono，如果不存在或已过期则返回空Mono
     */
    Mono<String> getCachedContentReactive(String bookId, String chapterId);
    
    /**
     * 响应式获取缓存内容，即使已过期
     * 用于在网络获取失败时作为备用
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 包含缓存章节内容的Mono，如果不存在则返回空Mono
     */
    Mono<String> getFallbackCachedContentReactive(String bookId, String chapterId);
    
    /**
     * 响应式缓存章节内容
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @param content 章节内容
     * @return 完成信号的Mono
     */
    Mono<Void> cacheContentReactive(String bookId, String chapterId, String content);
    
    /**
     * 响应式清除指定书籍的缓存
     * 
     * @param bookId 书籍ID
     * @return 完成信号的Mono
     */
    Mono<Void> clearCacheReactive(String bookId);
    
    /**
     * 响应式检查并清理过期缓存
     * 
     * @return 完成信号的Mono
     */
    Mono<Void> checkAndEvictCacheReactive();
    
    /**
     * 响应式清除所有缓存
     * 
     * @return 完成信号的Mono
     */
    Mono<Void> clearAllCacheReactive();
    
    /**
     * 获取缓存目录路径
     * 
     * @return 缓存目录路径
     */
    @NotNull
    String getCacheDirPath();
    
    /**
     * 响应式清理缓存
     * 删除过期和过多的缓存文件
     * 
     * @return 完成信号的Mono
     */
    Mono<Void> cleanupCacheReactive();
    
    /**
     * 响应式清理指定书籍的缓存
     * 
     * @param bookId 书籍ID
     * @return 完成信号的Mono
     */
    Mono<Void> cleanupBookCacheReactive(String bookId);
    
    /**
     * 兼容性方法：获取缓存的章节内容
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存的章节内容，如果不存在或已过期则返回null
     */
    default String getCachedContent(String bookId, String chapterId) {
        return getCachedContentReactive(bookId, chapterId).block();
    }
    
    /**
     * 兼容性方法：获取缓存内容，即使已过期
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存的章节内容，如果不存在则返回null
     */
    default String getFallbackCachedContent(String bookId, String chapterId) {
        return getFallbackCachedContentReactive(bookId, chapterId).block();
    }
    
    /**
     * 兼容性方法：缓存章节内容
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @param content 章节内容
     */
    default void cacheContent(String bookId, String chapterId, String content) {
        cacheContentReactive(bookId, chapterId, content).block();
    }
    
    /**
     * 兼容性方法：清除指定书籍的缓存
     * 
     * @param bookId 书籍ID
     */
    default void clearCache(String bookId) {
        clearCacheReactive(bookId).block();
    }
    
    /**
     * 兼容性方法：检查并清理过期缓存
     */
    default void checkAndEvictCache() {
        checkAndEvictCacheReactive().block();
    }
    
    /**
     * 兼容性方法：清除所有缓存
     */
    default void clearAllCache() {
        clearAllCacheReactive().block();
    }
    
    /**
     * 兼容性方法：清理缓存
     */
    default void cleanupCache() {
        cleanupCacheReactive().block();
    }
    
    /**
     * 兼容性方法：清理指定书籍的缓存
     * 
     * @param bookId 书籍ID
     */
    default void cleanupBookCache(String bookId) {
        cleanupBookCacheReactive(bookId).block();
    }
} 