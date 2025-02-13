package com.lv.tool.privatereader.cache;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.settings.CacheSettings;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 章节缓存管理
 */
@Service(Service.Level.PROJECT)
@State(
    name = "ChapterCacheManager",
    storages = @Storage("chapterCache.xml")
)
public final class ChapterCacheManager implements PersistentStateComponent<ChapterCacheManager.State> {
    private final CacheSettings cacheSettings;
    private State state = new State();

    public ChapterCacheManager(Project project) {
        this.cacheSettings = project.getService(CacheSettings.class);
    }

    public void cacheChapter(String bookUrl, String chapterUrl, String content) {
        checkAndEvictCache();
        state.cachedChapters.put(getCacheKey(bookUrl, chapterUrl), 
            new CachedChapter(content, System.currentTimeMillis()));
    }

    public String getCachedChapter(String bookUrl, String chapterUrl) {
        CachedChapter cached = state.cachedChapters.get(getCacheKey(bookUrl, chapterUrl));
        return cached != null ? cached.content : null;
    }

    private String getCacheKey(String bookUrl, String chapterUrl) {
        return bookUrl + "::" + chapterUrl;
    }

    public void checkAndEvictCache() {
        long maxSize = cacheSettings.getMaxCacheSize() * 1024L * 1024L;
        long currentSize = getCurrentCacheSize();
        if (currentSize > maxSize) {
            evictOldestEntries(currentSize - maxSize);
        }
    }

    private long getCurrentCacheSize() {
        return state.cachedChapters.values().stream()
            .mapToLong(c -> c.content.getBytes().length)
            .sum();
    }

    private void evictOldestEntries(long bytesToRemove) {
        final AtomicLong remainingBytes = new AtomicLong(bytesToRemove);
        
        List<Map.Entry<String, CachedChapter>> sortedEntries = state.cachedChapters.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().timestamp))
            .collect(Collectors.toList());
            
        List<String> entriesToRemove = new ArrayList<>();
        for (Map.Entry<String, CachedChapter> entry : sortedEntries) {
            if (remainingBytes.get() <= 0) break;
            long entrySize = entry.getValue().content.getBytes().length;
            remainingBytes.addAndGet(-entrySize);
            entriesToRemove.add(entry.getKey());
        }

        entriesToRemove.forEach(state.cachedChapters::remove);
    }

    @Override
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State {
        public Map<String, CachedChapter> cachedChapters = new HashMap<>();
    }

    public static class CachedChapter {
        public String content;
        public long timestamp;

        public CachedChapter() {
            // 默认构造函数用于序列化
        }

        public CachedChapter(String content, long timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }
    }
} 