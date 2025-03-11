package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 章节缓存适配器
 * 
 * 将旧的ChapterCacheManager适配到新的ChapterCacheRepository接口，用于平滑过渡。
 */
@Service(Service.Level.PROJECT)
public class ChapterCacheAdapter implements ChapterCacheRepository {
    private static final Logger LOG = Logger.getInstance(ChapterCacheAdapter.class);
    
    private final ChapterCacheManager cacheManager;
    
    public ChapterCacheAdapter(Project project) {
        this.cacheManager = project.getService(ChapterCacheManager.class);
        if (this.cacheManager == null) {
            LOG.error("ChapterCacheManager服务未初始化");
        } else {
            LOG.info("初始化ChapterCacheAdapter");
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
        // 由于cacheManager.cacheDir是私有的，我们需要通过其他方式获取缓存目录
        // 这里我们直接返回一个固定路径，实际应用中应该通过适当的方式获取
        return System.getProperty("user.home") + "/.private-reader/cache";
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