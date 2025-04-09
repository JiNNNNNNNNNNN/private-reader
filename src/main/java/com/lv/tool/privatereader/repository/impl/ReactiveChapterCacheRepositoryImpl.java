package com.lv.tool.privatereader.repository.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.repository.ReactiveChapterCacheRepository;
import com.lv.tool.privatereader.settings.CacheSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 响应式章节缓存仓库实现类
 * 使用响应式编程处理缓存操作
 */
@Singleton
public class ReactiveChapterCacheRepositoryImpl implements ReactiveChapterCacheRepository {
    private static final Logger LOG = Logger.getInstance(ReactiveChapterCacheRepositoryImpl.class);
    private static final String CACHE_DIR_NAME = "chapter_cache";
    private static final long DEFAULT_CACHE_EXPIRY_HOURS = 24 * 7; // 默认缓存过期时间：7天
    private static final int DEFAULT_MAX_CACHE_SIZE_MB = 100; // 默认最大缓存大小：100MB
    
    private final CacheSettings cacheSettings;
    private final String cacheDir;
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, String> memoryCache = new ConcurrentHashMap<>();
    
    /**
     * 构造函数，用于 IntelliJ 服务系统
     * 
     * @param application Application 实例
     */
    public ReactiveChapterCacheRepositoryImpl(Application application) {
        this(application.getService(CacheSettings.class));
        LOG.info("通过 Application 初始化 ReactiveChapterCacheRepositoryImpl");
    }
    
    @Inject
    public ReactiveChapterCacheRepositoryImpl(CacheSettings cacheSettings) {
        this.cacheSettings = cacheSettings;
        this.cacheDir = initCacheDir();
        
        // 启动定期清理任务
        scheduleCleanupTask();
    }
    
    private String initCacheDir() {
        String userHome = System.getProperty("user.home");
        String cacheDirPath = Paths.get(userHome, ".privatereader", CACHE_DIR_NAME).toString();
        
        try {
            Files.createDirectories(Paths.get(cacheDirPath));
            LOG.info("缓存目录初始化成功: " + cacheDirPath);
        } catch (IOException e) {
            LOG.error("创建缓存目录失败: " + cacheDirPath, e);
        }
        
        return cacheDirPath;
    }
    
    private void scheduleCleanupTask() {
        // 使用响应式定时器定期清理缓存
        Flux.interval(Duration.ofHours(6))
            .flatMap(tick -> cleanupCacheReactive())
            .doOnError(e -> LOG.error("缓存清理任务失败", e))
            .subscribe();
    }
    
    @Override
    public Mono<String> getCachedContentReactive(String bookId, String chapterId) {
        return Mono.defer(() -> {
            // 先检查内存缓存
            String cacheKey = getCacheKey(bookId, chapterId);
            String cachedContent = memoryCache.get(cacheKey);
            if (cachedContent != null) {
                LOG.debug("从内存缓存获取章节内容: " + cacheKey);
                return Mono.just(cachedContent);
            }
            
            // 检查文件缓存
            File cacheFile = getCacheFile(bookId, chapterId);
            if (!cacheFile.exists()) {
                LOG.debug("缓存文件不存在: " + cacheFile.getPath());
                return Mono.empty();
            }
            
            // 检查缓存是否过期
            if (isCacheExpired(cacheFile)) {
                LOG.debug("缓存已过期: " + cacheFile.getPath());
                return Mono.empty();
            }
            
            // 读取缓存文件
            return Mono.fromCallable(() -> {
                try {
                    String content = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
                    // 添加到内存缓存
                    memoryCache.put(cacheKey, content);
                    LOG.debug("从文件缓存获取章节内容: " + cacheFile.getPath());
                    return content;
                } catch (IOException e) {
                    LOG.warn("读取缓存文件失败: " + cacheFile.getPath(), e);
                    return null;
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .filter(content -> content != null);
        });
    }
    
    @Override
    public Mono<String> getFallbackCachedContentReactive(String bookId, String chapterId) {
        return Mono.defer(() -> {
            // 先检查内存缓存
            String cacheKey = getCacheKey(bookId, chapterId);
            String cachedContent = memoryCache.get(cacheKey);
            if (cachedContent != null) {
                LOG.debug("从内存缓存获取备用章节内容: " + cacheKey);
                return Mono.just(cachedContent);
            }
            
            // 检查文件缓存
            File cacheFile = getCacheFile(bookId, chapterId);
            if (!cacheFile.exists()) {
                LOG.debug("备用缓存文件不存在: " + cacheFile.getPath());
                return Mono.empty();
            }
            
            // 读取缓存文件，忽略过期检查
            return Mono.fromCallable(() -> {
                try {
                    String content = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
                    // 添加到内存缓存
                    memoryCache.put(cacheKey, content);
                    LOG.debug("从文件缓存获取备用章节内容: " + cacheFile.getPath());
                    return content;
                } catch (IOException e) {
                    LOG.warn("读取备用缓存文件失败: " + cacheFile.getPath(), e);
                    return null;
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .filter(content -> content != null);
        });
    }
    
    @Override
    public Mono<Void> cacheContentReactive(String bookId, String chapterId, String content) {
        return Mono.defer(() -> {
            if (content == null || content.isEmpty()) {
                LOG.debug("内容为空，不进行缓存");
                return Mono.empty();
            }
            
            String cacheKey = getCacheKey(bookId, chapterId);
            
            // 添加到内存缓存
            memoryCache.put(cacheKey, content);
            
            // 写入文件缓存
            File cacheFile = getCacheFile(bookId, chapterId);
            File parentDir = cacheFile.getParentFile();
            
            return Mono.fromRunnable(() -> {
                try {
                    if (!parentDir.exists() && !parentDir.mkdirs()) {
                        LOG.error("创建缓存目录失败: " + parentDir.getPath());
                        return;
                    }
                    
                    Files.writeString(cacheFile.toPath(), content, StandardCharsets.UTF_8);
                    LOG.debug("缓存章节内容成功: " + cacheFile.getPath());
                } catch (IOException e) {
                    LOG.error("写入缓存文件失败: " + cacheFile.getPath(), e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
        });
    }
    
    @Override
    public Mono<Void> clearCacheReactive(String bookId) {
        return Mono.defer(() -> {
            // 清除内存缓存
            memoryCache.keySet().removeIf(key -> key.startsWith(bookId + ":"));
            
            // 清除文件缓存
            File bookCacheDir = new File(cacheDir, bookId);
            if (!bookCacheDir.exists()) {
                return Mono.empty();
            }
            
            return Mono.fromRunnable(() -> {
                try {
                    deleteDirectory(bookCacheDir);
                    LOG.info("清除书籍缓存成功: " + bookId);
                } catch (IOException e) {
                    LOG.error("清除书籍缓存失败: " + bookId, e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
        });
    }
    
    @Override
    public Mono<Void> checkAndEvictCacheReactive() {
        return cleanupCacheReactive();
    }
    
    @Override
    public Mono<Void> clearAllCacheReactive() {
        return Mono.defer(() -> {
            // 清除内存缓存
            memoryCache.clear();
            
            // 清除文件缓存
            File cacheDirFile = new File(cacheDir);
            if (!cacheDirFile.exists()) {
                return Mono.empty();
            }
            
            return Mono.fromRunnable(() -> {
                try {
                    deleteDirectory(cacheDirFile);
                    Files.createDirectories(cacheDirFile.toPath());
                    LOG.info("清除所有缓存成功");
                } catch (IOException e) {
                    LOG.error("清除所有缓存失败", e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
        });
    }
    
    @NotNull
    @Override
    public String getCacheDirPath() {
        return cacheDir;
    }
    
    @Override
    public Mono<Void> cleanupCacheReactive() {
        return Mono.defer(() -> {
            if (!cleanupInProgress.compareAndSet(false, true)) {
                LOG.debug("缓存清理已在进行中，跳过本次清理");
                return Mono.empty();
            }
            
            return Mono.fromRunnable(() -> {
                try {
                    LOG.info("开始清理缓存");
                    
                    // 清理过期缓存
                    cleanupExpiredCache();
                    
                    // 清理过大的缓存
                    cleanupOversizedCache();
                    
                    LOG.info("缓存清理完成");
                } catch (Exception e) {
                    LOG.error("缓存清理失败", e);
                } finally {
                    cleanupInProgress.set(false);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
        });
    }
    
    @Override
    public Mono<Void> cleanupBookCacheReactive(String bookId) {
        return Mono.defer(() -> {
            File bookCacheDir = new File(cacheDir, bookId);
            if (!bookCacheDir.exists()) {
                return Mono.empty();
            }
            
            return Mono.fromRunnable(() -> {
                try {
                    LOG.info("开始清理书籍缓存: " + bookId);
                    
                    // 清理过期缓存
                    cleanupExpiredBookCache(bookCacheDir);
                    
                    LOG.info("书籍缓存清理完成: " + bookId);
                } catch (Exception e) {
                    LOG.error("书籍缓存清理失败: " + bookId, e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
        });
    }
    
    private void cleanupExpiredCache() throws IOException {
        File cacheDirFile = new File(cacheDir);
        if (!cacheDirFile.exists()) {
            return;
        }
        
        File[] bookDirs = cacheDirFile.listFiles();
        if (bookDirs == null) {
            return;
        }
        
        for (File bookDir : bookDirs) {
            if (bookDir.isDirectory()) {
                cleanupExpiredBookCache(bookDir);
            }
        }
    }
    
    private void cleanupExpiredBookCache(File bookDir) throws IOException {
        File[] chapterFiles = bookDir.listFiles();
        if (chapterFiles == null) {
            return;
        }
        
        long expiryHours = cacheSettings.getCacheExpiryHours();
        if (expiryHours <= 0) {
            expiryHours = DEFAULT_CACHE_EXPIRY_HOURS;
        }
        
        long expiryTimeMillis = System.currentTimeMillis() - expiryHours * 3600 * 1000;
        
        for (File chapterFile : chapterFiles) {
            if (chapterFile.isFile() && chapterFile.lastModified() < expiryTimeMillis) {
                Files.delete(chapterFile.toPath());
                LOG.debug("删除过期缓存文件: " + chapterFile.getPath());
            }
        }
        
        // 如果目录为空，删除目录
        if (bookDir.list() != null && bookDir.list().length == 0) {
            Files.delete(bookDir.toPath());
            LOG.debug("删除空缓存目录: " + bookDir.getPath());
        }
    }
    
    private void cleanupOversizedCache() throws IOException {
        File cacheDirFile = new File(cacheDir);
        if (!cacheDirFile.exists() || !cacheDirFile.isDirectory()) {
            return;
        }
        
        long currentCacheSize = calculateDirectorySize(cacheDirFile);
        long maxCacheSizeBytes = cacheSettings.getMaxCacheSizeMB() * 1024 * 1024;
        
        if (currentCacheSize <= maxCacheSizeBytes) {
            LOG.debug("缓存大小在限制范围内，无需清理");
            return;
        }
        
        LOG.info(String.format("缓存大小超过限制，开始清理。当前大小: %.2f MB, 最大限制: %d MB", 
            currentCacheSize / (1024.0 * 1024.0), cacheSettings.getMaxCacheSizeMB()));
        
        // 按最后修改时间排序，删除最旧的文件
        final AtomicLong remainingCacheSize = new AtomicLong(currentCacheSize);
        final long targetMaxSize = maxCacheSizeBytes;
        
        Files.walk(cacheDirFile.toPath())
            .filter(Files::isRegularFile)
            .sorted(Comparator.comparingLong(path -> path.toFile().lastModified()))
            .forEach(path -> {
                try {
                    long fileSize = Files.size(path);
                    Files.delete(path);
                    remainingCacheSize.addAndGet(-fileSize);
                    LOG.debug("删除缓存文件以减小缓存大小: " + path);
                    
                    if (remainingCacheSize.get() <= targetMaxSize * 0.8) {
                        // 删除到缓存大小低于最大值的80%时停止
                        return;
                    }
                } catch (IOException e) {
                    LOG.warn("删除缓存文件失败: " + path, e);
                }
            });
        
        // 删除空目录
        Files.walk(cacheDirFile.toPath())
            .filter(Files::isDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    if (Files.list(path).count() == 0 && !path.equals(cacheDirFile.toPath())) {
                        Files.delete(path);
                        LOG.debug("删除空缓存目录: " + path);
                    }
                } catch (IOException e) {
                    LOG.warn("删除空缓存目录失败: " + path, e);
                }
            });
    }
    
    private long calculateDirectorySize(File directory) throws IOException {
        return Files.walk(directory.toPath())
            .filter(Files::isRegularFile)
            .mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    LOG.warn("计算文件大小失败: " + path, e);
                    return 0;
                }
            })
            .sum();
    }
    
    private void deleteDirectory(File directory) throws IOException {
        Files.walk(directory.toPath())
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOG.warn("删除文件失败: " + path, e);
                }
            });
    }
    
    private boolean isCacheExpired(File cacheFile) {
        long expiryHours = cacheSettings.getCacheExpiryHours();
        if (expiryHours <= 0) {
            expiryHours = DEFAULT_CACHE_EXPIRY_HOURS;
        }
        
        long expiryTimeMillis = System.currentTimeMillis() - expiryHours * 3600 * 1000;
        return cacheFile.lastModified() < expiryTimeMillis;
    }
    
    private File getCacheFile(String bookId, String chapterId) {
        // 对章节ID进行MD5编码，避免文件名过长或包含非法字符
        String encodedChapterId = encodeChapterId(chapterId);
        return new File(new File(cacheDir, bookId), encodedChapterId);
    }
    
    private String getCacheKey(String bookId, String chapterId) {
        return bookId + ":" + chapterId;
    }
    
    private String encodeChapterId(String chapterId) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(chapterId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            LOG.warn("MD5编码失败，使用原始章节ID", e);
            // 如果MD5编码失败，使用原始章节ID的哈希码
            return String.valueOf(chapterId.hashCode());
        }
    }
} 