package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.storage.StorageManager;
import com.lv.tool.privatereader.settings.CacheSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/**
 * 章节缓存管理器
 * 
 * 负责管理章节内容的缓存，提供以下功能：
 * - 缓存章节内容
 * - 获取缓存的章节内容
 * - 清理过期缓存
 * - 管理缓存大小
 */
@Service(Service.Level.APP)
public final class ChapterCacheManager {
    private static final Logger LOG = Logger.getInstance(ChapterCacheManager.class);
    private final Path cacheDir;
    private final StorageManager storageManager;
    private static final long MAX_CACHE_AGE_MILLIS = TimeUnit.DAYS.toMillis(7); // 默认缓存7天
    private static final long MIN_FREE_SPACE_MB = 100; // 最小剩余空间(MB)

    public ChapterCacheManager() {
        LOG.info("初始化应用级别的 ChapterCacheManager");
        this.storageManager = ApplicationManager.getApplication().getService(StorageManager.class);
        this.cacheDir = Path.of(storageManager.getCachePath());
        // 不再在启动时清理缓存
        // cleanupCache();
    }

    /**
     * 获取缓存的章节内容
     * 如果缓存不存在或已过期，返回null
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存的章节内容，如果不存在或已过期则返回null
     */
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
            return Files.readString(cachePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("读取缓存失败: " + cachePath + ", 错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取缓存内容，即使已过期
     * 用于在网络获取失败时作为备用
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存的章节内容，如果不存在则返回null
     */
    public String getFallbackCachedContent(String bookId, String chapterId) {
        if (!isCacheEnabled()) return null;

        Path cachePath = getCachePath(bookId, chapterId);
        if (!Files.exists(cachePath)) return null;

        try {
            return Files.readString(cachePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("读取备用缓存失败: " + cachePath + ", 错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 缓存章节内容
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @param content 章节内容
     */
    public void cacheContent(String bookId, String chapterId, String content) {
        if (!isCacheEnabled()) return;

        try {
            // 检查并清理缓存
            checkAndEvictCache();
            // 写入新缓存
            Path cachePath = getCachePath(bookId, chapterId);
            Files.createDirectories(cachePath.getParent());
            Files.writeString(cachePath, content, StandardCharsets.UTF_8);
            LOG.debug("缓存内容已写入: " + cachePath);
        } catch (IOException e) {
            LOG.error("缓存内容写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理指定书籍的所有缓存
     * 
     * @param bookId 书籍ID
     */
    public void clearCache(String bookId) {
        try {
            Path bookCacheDir = cacheDir.resolve(bookId);
            if (Files.exists(bookCacheDir)) {
                try (Stream<Path> walk = Files.walk(bookCacheDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
                LOG.info("已清理书籍缓存: " + bookId);
            }
        } catch (IOException e) {
            LOG.error("清理缓存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查并清理过期缓存
     * 根据缓存大小限制，删除最旧的缓存文件
     */
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
                                LOG.debug("已删除过期缓存: " + path);
                            }
                        } catch (IOException e) {
                            LOG.warn("删除缓存文件失败: " + path + ", 错误: " + e.getMessage());
                        }
                    });
            }
        } catch (IOException e) {
            LOG.error("清理缓存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查缓存是否启用
     * 
     * @return 是否启用缓存
     */
    private boolean isCacheEnabled() {
        return getCacheSettings().isEnableCache();
    }

    /**
     * 检查缓存是否过期
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 是否过期
     */
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
            LOG.warn("检查缓存过期失败: " + cachePath + ", 错误: " + e.getMessage());
            return true;
        }
    }

    /**
     * 获取当前缓存总大小
     * 
     * @return 缓存总大小（字节）
     */
    private long getCacheSize() throws IOException {
        try (Stream<Path> walk = Files.walk(cacheDir)) {
            return walk.filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        LOG.warn("获取文件大小失败: " + path + ", 错误: " + e.getMessage());
                        return 0L;
                    }
                })
                .sum();
        }
    }

    /**
     * 获取文件最后修改时间
     * 
     * @param path 文件路径
     * @return 最后修改时间（毫秒）
     */
    private long getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            LOG.warn("获取文件修改时间失败: " + path + ", 错误: " + e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取缓存文件路径
     * 
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 缓存文件路径
     */
    private Path getCachePath(String bookId, String chapterId) {
        String safeBookId = StorageManager.getSafeFileName(bookId);
        String safeFileName = StorageManager.getCacheFileName(chapterId);
        return cacheDir.resolve(safeBookId).resolve(safeFileName);
    }

    /**
     * 获取缓存设置
     * 
     * @return 缓存设置
     */
    @NotNull
    private CacheSettings getCacheSettings() {
        return ApplicationManager.getApplication().getService(CacheSettings.class);
    }

    /**
     * 清理所有缓存
     */
    public void clearAllCache() {
        try {
            if (Files.exists(cacheDir)) {
                try (Stream<Path> walk = Files.walk(cacheDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
                // 重新创建缓存目录
                Files.createDirectories(cacheDir);
                LOG.info("已清理所有缓存");
            }
        } catch (IOException e) {
            LOG.error("清理所有缓存失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取缓存目录路径
     * 
     * @return 缓存目录路径
     */
    public String getCacheDirPath() {
        return cacheDir.toString();
    }

    /**
     * 清理缓存
     * - 删除过期缓存
     * - 检查磁盘空间
     * - 如果空间不足，删除最旧的缓存
     */
    public void cleanupCache() {
        if (!Files.exists(cacheDir)) return;

        try {
            LOG.info("开始清理缓存...");
            long startTime = System.currentTimeMillis();
            
            // 1. 删除过期缓存
            deleteExpiredCache();
            
            // 2. 检查磁盘空间
            long freeSpace = cacheDir.toFile().getFreeSpace() / (1024 * 1024); // 转换为MB
            if (freeSpace < MIN_FREE_SPACE_MB) {
                LOG.info("磁盘空间不足，当前剩余: " + freeSpace + "MB，开始清理旧缓存");
                deleteOldCache();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("缓存清理完成，耗时: " + duration + "ms");
        } catch (Exception e) {
            LOG.error("缓存清理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除过期缓存
     */
    private void deleteExpiredCache() {
        try {
            long now = System.currentTimeMillis();
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    try {
                        long lastModified = Files.getLastModifiedTime(p).toMillis();
                        return (now - lastModified) > MAX_CACHE_AGE_MILLIS;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        Files.delete(p);
                        LOG.debug("删除过期缓存: " + p);
                    } catch (IOException e) {
                        LOG.warn("删除过期缓存失败: " + p, e);
                    }
                });
        } catch (IOException e) {
            LOG.error("删除过期缓存失败", e);
        }
    }

    /**
     * 删除旧缓存文件直到空间足够
     */
    private void deleteOldCache() {
        try {
            // 获取所有缓存文件并按修改时间排序
            List<Path> cacheFiles = Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .collect(Collectors.toList());

            // 从最旧的文件开始删除，直到空间足够
            for (Path file : cacheFiles) {
                if (cacheDir.toFile().getFreeSpace() / (1024 * 1024) >= MIN_FREE_SPACE_MB) {
                    break;
                }
                try {
                    Files.delete(file);
                    LOG.debug("删除旧缓存: " + file);
                } catch (IOException e) {
                    LOG.warn("删除旧缓存失败: " + file, e);
                }
            }
        } catch (IOException e) {
            LOG.error("删除旧缓存失败", e);
        }
    }

    /**
     * 清理指定书籍的缓存
     * @param bookId 书籍ID
     */
    public void cleanupBookCache(String bookId) {
        if (bookId == null || bookId.isEmpty()) return;
        
        Path bookCacheDir = cacheDir.resolve(bookId);
        if (!Files.exists(bookCacheDir)) return;
        
        try {
            Files.walk(bookCacheDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            LOG.info("清理书籍缓存完成: " + bookId);
        } catch (IOException e) {
            LOG.error("清理书籍缓存失败: " + bookId, e);
        }
    }
} 