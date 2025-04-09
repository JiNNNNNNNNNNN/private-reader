package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 章节缓存适配器
 * 
 * 将旧的ChapterCacheManager适配到新的ChapterCacheRepository接口，用于平滑过渡。
 */
@Service(Service.Level.APP)
public final class ChapterCacheAdapter implements ChapterCacheRepository {
    private static final Logger LOG = Logger.getInstance(ChapterCacheAdapter.class);
    
    private final ChapterCacheManager cacheManager;
    
    public ChapterCacheAdapter() {
        LOG.info("初始化应用级别的 ChapterCacheAdapter");
        this.cacheManager = ApplicationManager.getApplication().getService(ChapterCacheManager.class);
        if (this.cacheManager == null) {
            LOG.error("ChapterCacheManager服务未初始化");
        }
    }
    
    @Override
    @Nullable
    public String getCachedContent(String bookId, String chapterId) {
        if (cacheManager == null) {
            return null;
        }
        return cacheManager.getCachedContent(bookId, chapterId);
    }
    
    @Override
    @Nullable
    public String getFallbackCachedContent(String bookId, String chapterId) {
        if (cacheManager == null) {
            return null;
        }
        return cacheManager.getFallbackCachedContent(bookId, chapterId);
    }
    
    @Override
    public void cacheContent(String bookId, String chapterId, String content) {
        if (cacheManager == null) {
            return;
        }
        cacheManager.cacheContent(bookId, chapterId, content);
    }
    
    @Override
    public void clearCache(String bookId) {
        if (cacheManager == null) {
            return;
        }
        cacheManager.clearCache(bookId);
    }
    
    @Override
    public void checkAndEvictCache() {
        if (cacheManager == null) {
            return;
        }
        // 旧的ChapterCacheManager没有直接对应的方法，但可以调用cleanupCache
        cacheManager.cleanupCache();
    }
    
    @Override
    public void clearAllCache() {
        if (cacheManager == null) {
            return;
        }
        cacheManager.clearAllCache();
    }
    
    @Override
    @NotNull
    public String getCacheDirPath() {
        if (cacheManager == null) {
            return "";
        }
        return cacheManager.getCacheDirPath();
    }
    
    @Override
    public void cleanupCache() {
        if (cacheManager == null) {
            return;
        }
        cacheManager.cleanupCache();
    }
    
    @Override
    public void cleanupBookCache(String bookId) {
        if (cacheManager == null) {
            return;
        }
        // 旧的ChapterCacheManager没有直接对应的方法，但可以调用clearCache
        cacheManager.clearCache(bookId);
    }
}