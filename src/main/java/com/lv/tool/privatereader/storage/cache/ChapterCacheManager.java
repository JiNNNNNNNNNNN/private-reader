package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.settings.CacheSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;

/**
 * 章节缓存管理器
 */
@Service(Service.Level.PROJECT)
public final class ChapterCacheManager {
    private final Project project;
    private final Path cacheDir;

    public ChapterCacheManager(Project project) {
        this.project = project;
        this.cacheDir = Path.of(project.getBasePath(), ".private-reader", "cache");
        createCacheDir();
    }

    private void createCacheDir() {
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            // 忽略创建目录失败的错误
        }
    }

    public String getCachedContent(String bookId, String chapterId) {
        if (!isCacheEnabled()) return null;

        Path cachePath = getCachePath(bookId, chapterId);
        if (!Files.exists(cachePath)) return null;

        try {
            // 检查缓存是否过期
            if (isExpired(bookId, chapterId)) {
                // 不删除过期内容，只返回null表示需要重新获取
                return null;
            }
            return Files.readString(cachePath);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 获取缓存内容，即使已过期
     * 用于在网络获取失败时作为备用
     */
    public String getFallbackCachedContent(String bookId, String chapterId) {
        if (!isCacheEnabled()) return null;

        Path cachePath = getCachePath(bookId, chapterId);
        if (!Files.exists(cachePath)) return null;

        try {
            return Files.readString(cachePath);
        } catch (IOException e) {
            return null;
        }
    }

    public void cacheContent(String bookId, String chapterId, String content) {
        if (!isCacheEnabled()) return;

        try {
            // 检查并清理缓存
            checkAndEvictCache();
            // 写入新缓存
            Path cachePath = getCachePath(bookId, chapterId);
            Files.createDirectories(cachePath.getParent());
            Files.writeString(cachePath, content);
        } catch (IOException e) {
            // 忽略缓存写入失败的错误
        }
    }

    public void clearCache(String bookId) {
        try {
            Path bookCacheDir = cacheDir.resolve(bookId);
            if (Files.exists(bookCacheDir)) {
                try (Stream<Path> walk = Files.walk(bookCacheDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            // 忽略清理缓存失败的错误
        }
    }

    public void checkAndEvictCache() {
        try {
            long maxSize = getCacheSettings().getMaxCacheSize() * 1024L * 1024L; // 转换为字节
            try (Stream<Path> walk = Files.walk(cacheDir)) {
                walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::getLastModifiedTime))
                    .forEach(path -> {
                        try {
                            if (getCacheSize() > maxSize) {
                                Files.delete(path);
                            }
                        } catch (IOException e) {
                            // 忽略删除失败的错误
                        }
                    });
            }
        } catch (IOException e) {
            // 忽略清理缓存失败的错误
        }
    }

    private boolean isCacheEnabled() {
        return getCacheSettings().isEnableCache();
    }

    private boolean isExpired(String bookId, String chapterId) {
        Path cachePath = getCachePath(bookId, chapterId);
        if (!Files.exists(cachePath)) return true;
        
        try {
            // 获取文件最后修改时间
            long lastModified = Files.getLastModifiedTime(cachePath).toMillis();
            // 获取缓存过期时间设置
            int expirationDays = getCacheSettings().getMaxCacheAge();
            // 计算过期时间
            long expirationTime = lastModified + TimeUnit.DAYS.toMillis(expirationDays);
            
            return System.currentTimeMillis() > expirationTime;
        } catch (IOException e) {
            return true;
        }
    }

    private long getCacheSize() throws IOException {
        try (Stream<Path> walk = Files.walk(cacheDir)) {
            return walk.filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        }
    }

    private long getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path getCachePath(String bookId, String chapterId) {
        // 将 URL 转换为有效的文件名
        String safeChapterId = chapterId.replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("https?_", "")  // 移除 http(s)_ 前缀
            .replaceAll("www_", "")     // 移除 www_ 前缀
            .replaceAll("_+", "_");     // 合并多个下划线
        return cacheDir.resolve(bookId).resolve(safeChapterId + ".txt");
    }

    @NotNull
    private CacheSettings getCacheSettings() {
        return project.getService(CacheSettings.class);
    }
} 