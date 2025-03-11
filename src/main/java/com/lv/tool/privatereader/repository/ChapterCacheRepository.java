package com.lv.tool.privatereader.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 章节缓存仓库接口
 * 
 * 定义章节缓存的存储和检索操作，提供统一的数据访问接口。
 * 实现类负责具体的缓存实现细节。
 */
public interface ChapterCacheRepository {
    
    /**
     * 获取缓存的章节内容
     * 如果缓存不存在或已过期，返回null
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存的章节内容，如果不存在或已过期则返回null
     */
    @Nullable
    String getCachedContent(String bookId, String chapterId);
    
    /**
     * 获取缓存内容，即使已过期
     * 用于在网络获取失败时作为备用
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存的章节内容，如果不存在则返回null
     */
    @Nullable
    String getFallbackCachedContent(String bookId, String chapterId);
    
    /**
     * 缓存章节内容
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @param content 章节内容
     */
    void cacheContent(String bookId, String chapterId, String content);
    
    /**
     * 清除指定书籍的缓存
     * 
     * @param bookId 书籍ID
     */
    void clearCache(String bookId);
    
    /**
     * 检查并清理过期缓存
     */
    void checkAndEvictCache();
    
    /**
     * 清除所有缓存
     */
    void clearAllCache();
    
    /**
     * 获取缓存目录路径
     * 
     * @return 缓存目录路径
     */
    @NotNull
    String getCacheDirPath();
    
    /**
     * 清理缓存
     * 删除过期和过多的缓存文件
     */
    void cleanupCache();
    
    /**
     * 清理指定书籍的缓存
     * 
     * @param bookId 书籍ID
     */
    void cleanupBookCache(String bookId);
} 