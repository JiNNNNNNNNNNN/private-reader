package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.storage.StorageManager;
import com.lv.tool.privatereader.ui.settings.CacheSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;

/**
 * 章节缓存管理器
 * 
 * 负责管理章节内容的缓存，提供以下功能：
 * - 缓存章节内容
 * - 获取缓存的章节内容
 * - 清理过期缓存
 * - 管理缓存大小
 */
@Service(Service.Level.PROJECT)
public final class ChapterCacheManager {
    private static final Logger LOG = Logger.getInstance(ChapterCacheManager.class);
    private final Project project;
    private final Path cacheDir;
    private final StorageManager storageManager;

    public ChapterCacheManager(Project project) {
        this.project = project;
        this.storageManager = project.getService(StorageManager.class);
        // 使用StorageManager获取缓存目录路径
        this.cacheDir = Path.of(storageManager.getCachePath());
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
            return Files.readString(cachePath);
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
            return Files.readString(cachePath);
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
            Files.writeString(cachePath, content);
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
        // 将 URL 转换为有效的文件名
        String safeChapterId = chapterId.replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("https?_", "")  // 移除 http(s)_ 前缀
            .replaceAll("www_", "")     // 移除 www_ 前缀
            .replaceAll("_+", "_");     // 合并多个下划线
        return cacheDir.resolve(bookId).resolve(safeChapterId + ".txt");
    }

    /**
     * 获取缓存设置
     * 
     * @return 缓存设置
     */
    @NotNull
    private CacheSettings getCacheSettings() {
        return project.getService(CacheSettings.class);
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
} 