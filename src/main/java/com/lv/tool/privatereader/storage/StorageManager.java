package com.lv.tool.privatereader.storage;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 存储管理器
 * 
 * 统一管理应用的各种存储需求，包括：
 * - 书籍数据存储
 * - 章节缓存
 * - 设置存储
 * - 阅读进度
 * 
 * 提供统一的存储路径管理和存储操作接口
 */
@Service(Service.Level.PROJECT)
public class StorageManager {
    private static final Logger LOG = Logger.getInstance(StorageManager.class);
    
    private final Project project;
    private final Path baseStoragePath;
    private final Path booksPath;
    private final Path cachePath;
    private final Path settingsPath;
    private final Path backupPath;
    
    public StorageManager(Project project) {
        this.project = project;
        
        // 使用IDE配置目录下的private-reader目录作为基础存储路径
        this.baseStoragePath = Path.of(PathManager.getConfigPath(), "private-reader");
        this.booksPath = baseStoragePath.resolve("books");
        this.cachePath = baseStoragePath.resolve("cache");
        this.settingsPath = baseStoragePath.resolve("settings");
        this.backupPath = baseStoragePath.resolve("backup");
        
        // 创建存储目录结构
        createStorageDirectories();
    }
    
    /**
     * 创建存储目录结构
     */
    private void createStorageDirectories() {
        try {
            Files.createDirectories(baseStoragePath);
            Files.createDirectories(booksPath);
            Files.createDirectories(cachePath);
            Files.createDirectories(settingsPath);
            Files.createDirectories(backupPath);
            LOG.info("创建存储目录结构: " + baseStoragePath);
        } catch (IOException e) {
            LOG.error("创建存储目录结构失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取书籍数据存储服务
     * @return 书籍存储服务
     */
    @NotNull
    public BookStorage getBookStorage() {
        return project.getService(BookStorage.class);
    }
    
    /**
     * 获取章节缓存管理器
     * @return 章节缓存管理器
     */
    @NotNull
    public ChapterCacheManager getChapterCacheManager() {
        return project.getService(ChapterCacheManager.class);
    }
    
    /**
     * 获取基础存储路径
     * @return 基础存储路径
     */
    @NotNull
    public String getBaseStoragePath() {
        return baseStoragePath.toString();
    }
    
    /**
     * 获取书籍数据存储路径
     * @return 书籍数据存储路径
     */
    @NotNull
    public String getBooksPath() {
        return booksPath.toString();
    }
    
    /**
     * 获取缓存存储路径
     * @return 缓存存储路径
     */
    @NotNull
    public String getCachePath() {
        return cachePath.toString();
    }
    
    /**
     * 获取设置存储路径
     * @return 设置存储路径
     */
    @NotNull
    public String getSettingsPath() {
        return settingsPath.toString();
    }
    
    /**
     * 获取备份存储路径
     * @return 备份存储路径
     */
    @NotNull
    public String getBackupPath() {
        return backupPath.toString();
    }
    
    /**
     * 获取书籍数据文件路径
     * @return 书籍数据文件路径
     */
    @NotNull
    public String getBooksFilePath() {
        return booksPath.resolve("books.json").toString();
    }
    
    /**
     * 创建书籍专属目录
     * @param bookId 书籍ID
     * @return 书籍专属目录路径
     */
    @NotNull
    public String createBookDirectory(String bookId) {
        Path bookDir = booksPath.resolve(bookId);
        try {
            Files.createDirectories(bookDir);
            LOG.info("创建书籍目录: " + bookDir);
        } catch (IOException e) {
            LOG.error("创建书籍目录失败: " + e.getMessage(), e);
        }
        return bookDir.toString();
    }
    
    /**
     * 获取书籍专属目录
     * @param bookId 书籍ID
     * @return 书籍专属目录路径
     */
    @NotNull
    public String getBookDirectory(String bookId) {
        Path bookDir = booksPath.resolve(bookId);
        if (!Files.exists(bookDir)) {
            return createBookDirectory(bookId);
        }
        return bookDir.toString();
    }
    
    /**
     * 清理所有存储数据
     * 谨慎使用，会删除所有数据
     */
    public void clearAllStorage() {
        try {
            // 删除所有书籍数据
            getBookStorage().clearAllBooks();
            
            // 删除所有缓存
            getChapterCacheManager().clearAllCache();
            
            // 删除所有目录内容
            deleteDirectoryContents(baseStoragePath.toFile());
            
            // 重新创建目录结构
            createStorageDirectories();
            
            LOG.info("已清理所有存储数据");
        } catch (Exception e) {
            LOG.error("清理存储数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建数据备份
     * @return 备份文件路径
     */
    @NotNull
    public String createBackup() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path backupFile = backupPath.resolve("backup-" + timestamp + ".zip");
        
        // TODO: 实现备份逻辑
        
        return backupFile.toString();
    }
    
    /**
     * 从备份恢复数据
     * @param backupFilePath 备份文件路径
     * @return 是否恢复成功
     */
    public boolean restoreFromBackup(String backupFilePath) {
        // TODO: 实现恢复逻辑
        return false;
    }
    
    /**
     * 递归删除目录内容
     * @param directory 要删除内容的目录
     */
    private void deleteDirectoryContents(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectoryContents(file);
                    }
                    file.delete();
                }
            }
        }
    }
} 