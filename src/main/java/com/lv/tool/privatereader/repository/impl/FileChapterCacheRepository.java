package com.lv.tool.privatereader.repository.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import com.lv.tool.privatereader.repository.StorageRepository;
import com.lv.tool.privatereader.settings.CacheSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件章节缓存仓库实现
 * 
 * 基于文件系统实现章节缓存仓库接口，管理章节内容的缓存。
 */
@Service(Service.Level.PROJECT)
public class FileChapterCacheRepository implements ChapterCacheRepository {
    private static final Logger LOG = Logger.getInstance(FileChapterCacheRepository.class);
    private static final long MAX_CACHE_AGE_MILLIS = TimeUnit.DAYS.toMillis(7); // 默认缓存7天
    private static final long MIN_FREE_SPACE_MB = 100; // 最小剩余空间(MB)
    
    private final Project project;
    private final StorageRepository storageRepository;
    private final Path cacheDir;
    
    public FileChapterCacheRepository(Project project, StorageRepository storageRepository) {
        this.project = project;
        this.storageRepository = storageRepository;
        this.cacheDir = Path.of(storageRepository.getCachePath());
    }
    
    @Override
    @Nullable
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
    
    @Override
    @Nullable
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
    
    @Override
    public void cacheContent(String bookId, String chapterId, String content) {
        if (!isCacheEnabled() || content == null || content.isEmpty()) return;
        
        try {
            // 检查磁盘空间
            if (!hasEnoughDiskSpace()) {
                LOG.warn("磁盘空间不足，跳过缓存");
                return;
            }
            
            // 创建缓存目录
            Path bookCacheDir = getBookCacheDir(bookId);
            Files.createDirectories(bookCacheDir);
            
            // 写入缓存文件
            Path cachePath = getCachePath(bookId, chapterId);
            Files.writeString(cachePath, content);
            
            LOG.debug("缓存章节内容: " + cachePath);
        } catch (IOException e) {
            LOG.warn("缓存章节内容失败: " + e.getMessage());
        }
    }
    
    @Override
    public void clearCache(String bookId) {
        if (bookId == null || bookId.isEmpty()) return;
        
        try {
            Path bookCacheDir = getBookCacheDir(bookId);
            if (Files.exists(bookCacheDir)) {
                deleteDirectory(bookCacheDir);
                LOG.info("清除书籍缓存: " + bookId);
            }
        } catch (IOException e) {
            LOG.warn("清除缓存失败: " + e.getMessage());
        }
    }
    
    @Override
    public void checkAndEvictCache() {
        try {
            // 检查并清理过期缓存
            cleanupCache();
        } catch (Exception e) {
            LOG.warn("检查缓存失败: " + e.getMessage());
        }
    }
    
    @Override
    public void clearAllCache() {
        try {
            deleteDirectory(cacheDir);
            Files.createDirectories(cacheDir);
            LOG.info("清除所有缓存");
        } catch (IOException e) {
            LOG.warn("清除所有缓存失败: " + e.getMessage());
        }
    }
    
    @Override
    @NotNull
    public String getCacheDirPath() {
        return cacheDir.toString();
    }
    
    @Override
    public void cleanupCache() {
        try {
            // 检查磁盘空间
            if (!hasEnoughDiskSpace()) {
                // 删除最旧的缓存文件，直到有足够空间
                deleteOldestCacheFiles();
            }
            
            // 删除过期缓存
            deleteExpiredCacheFiles();
            
            LOG.debug("清理缓存完成");
        } catch (Exception e) {
            LOG.warn("清理缓存失败: " + e.getMessage());
        }
    }
    
    @Override
    public void cleanupBookCache(String bookId) {
        if (bookId == null || bookId.isEmpty()) return;
        
        try {
            Path bookCacheDir = getBookCacheDir(bookId);
            if (!Files.exists(bookCacheDir)) return;
            
            // 删除过期的章节缓存
            try (Stream<Path> paths = Files.list(bookCacheDir)) {
                paths.filter(Files::isRegularFile)
                    .filter(this::isExpiredCacheFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            LOG.debug("删除过期缓存: " + path);
                        } catch (IOException e) {
                            LOG.warn("删除缓存文件失败: " + path);
                        }
                    });
            }
        } catch (IOException e) {
            LOG.warn("清理书籍缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取书籍缓存目录
     */
    private Path getBookCacheDir(String bookId) {
        return cacheDir.resolve(storageRepository.getSafeFileName(bookId));
    }
    
    /**
     * 获取章节缓存路径
     */
    private Path getCachePath(String bookId, String chapterId) {
        return getBookCacheDir(bookId).resolve(storageRepository.getSafeFileName(chapterId) + ".html");
    }
    
    /**
     * 检查缓存是否过期
     */
    private boolean isExpired(String bookId, String chapterId) {
        try {
            Path cachePath = getCachePath(bookId, chapterId);
            if (!Files.exists(cachePath)) return true;
            
            long lastModified = Files.getLastModifiedTime(cachePath).toMillis();
            long now = System.currentTimeMillis();
            
            return (now - lastModified) > getCacheMaxAge();
        } catch (IOException e) {
            return true;
        }
    }
    
    /**
     * 检查缓存文件是否过期
     */
    private boolean isExpiredCacheFile(Path path) {
        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            long now = System.currentTimeMillis();
            
            return (now - lastModified) > getCacheMaxAge();
        } catch (IOException e) {
            return true;
        }
    }
    
    /**
     * 获取缓存最大有效期
     */
    private long getCacheMaxAge() {
        CacheSettings settings = project.getService(CacheSettings.class);
        if (settings != null) {
            return TimeUnit.DAYS.toMillis(settings.getMaxCacheAge());
        }
        return MAX_CACHE_AGE_MILLIS;
    }
    
    /**
     * 检查是否启用缓存
     */
    private boolean isCacheEnabled() {
        CacheSettings settings = project.getService(CacheSettings.class);
        return settings == null || settings.isEnableCache();
    }
    
    /**
     * 检查是否有足够的磁盘空间
     */
    private boolean hasEnoughDiskSpace() {
        File cacheFile = cacheDir.toFile();
        long freeSpace = cacheFile.getFreeSpace();
        return freeSpace > MIN_FREE_SPACE_MB * 1024 * 1024;
    }
    
    /**
     * 删除最旧的缓存文件
     */
    private void deleteOldestCacheFiles() throws IOException {
        // 获取所有缓存文件，按修改时间排序
        List<Path> cacheFiles = getAllCacheFiles();
        
        // 删除最旧的文件，直到有足够空间或没有文件可删
        for (Path file : cacheFiles) {
            if (hasEnoughDiskSpace()) break;
            
            try {
                Files.delete(file);
                LOG.debug("删除旧缓存文件: " + file);
            } catch (IOException e) {
                LOG.warn("删除缓存文件失败: " + file);
            }
        }
    }
    
    /**
     * 删除过期的缓存文件
     */
    private void deleteExpiredCacheFiles() throws IOException {
        // 获取所有缓存文件
        List<Path> cacheFiles = getAllCacheFiles();
        
        // 删除过期文件
        for (Path file : cacheFiles) {
            if (isExpiredCacheFile(file)) {
                try {
                    Files.delete(file);
                    LOG.debug("删除过期缓存文件: " + file);
                } catch (IOException e) {
                    LOG.warn("删除缓存文件失败: " + file);
                }
            }
        }
    }
    
    /**
     * 获取所有缓存文件，按修改时间排序
     */
    private List<Path> getAllCacheFiles() throws IOException {
        try (Stream<Path> bookDirs = Files.list(cacheDir).filter(Files::isDirectory)) {
            return bookDirs.flatMap(dir -> {
                try {
                    return Files.list(dir).filter(Files::isRegularFile);
                } catch (IOException e) {
                    return Stream.empty();
                }
            })
            .sorted(Comparator.comparing(path -> {
                try {
                    return Files.getLastModifiedTime(path).toMillis();
                } catch (IOException e) {
                    return Long.MAX_VALUE;
                }
            }))
            .collect(Collectors.toList());
        }
    }
    
    /**
     * 删除目录及其内容
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOG.warn("删除文件失败: " + path);
                    }
                });
        }
    }
}