package com.lv.tool.privatereader.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.repository.RepositoryModule;
import com.lv.tool.privatereader.repository.StorageRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 存储适配器
 * 
 * 将旧的存储API适配到新的Repository接口，用于平滑过渡。
 * 这个类实现了旧的存储接口，但内部使用新的Repository实现。
 */
@Service(Service.Level.APP)
public final class StorageAdapter {
    private static final Logger LOG = Logger.getInstance(StorageAdapter.class);
    
    private final StorageRepository storageRepository;
    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final ChapterCacheRepository chapterCacheRepository;
    
    public StorageAdapter() {
        LOG.info("初始化应用级别的 StorageAdapter");
        
        // 获取应用级别的 Repository 实例
        RepositoryModule repositoryModule = RepositoryModule.getInstance();
        this.storageRepository = repositoryModule.getStorageRepository();
        this.bookRepository = repositoryModule.getBookRepository();
        this.readingProgressRepository = repositoryModule.getReadingProgressRepository();
        this.chapterCacheRepository = repositoryModule.getChapterCacheRepository();
    }
    
    // ===== StorageManager API =====
    
    /**
     * 获取基础存储路径
     */
    public String getBaseStoragePath() {
        return storageRepository.getBaseStoragePath();
    }
    
    /**
     * 获取书籍存储路径
     */
    public String getBooksPath() {
        return storageRepository.getBooksPath();
    }
    
    /**
     * 获取缓存存储路径
     */
    public String getCachePath() {
        return storageRepository.getCachePath();
    }
    
    /**
     * 获取设置存储路径
     */
    public String getSettingsPath() {
        return storageRepository.getSettingsPath();
    }
    
    /**
     * 获取备份存储路径
     */
    public String getBackupPath() {
        return storageRepository.getBackupPath();
    }
    
    /**
     * 创建书籍目录
     */
    public String createBookDirectory(String bookId) {
        return storageRepository.createBookDirectory(bookId);
    }
    
    /**
     * 获取书籍目录
     */
    public String getBookDirectory(String bookId) {
        return storageRepository.getBookDirectory(bookId);
    }
    
    /**
     * 清空所有存储
     */
    public void clearAllStorage() {
        storageRepository.clearAllStorage();
    }
    
    /**
     * 创建备份
     */
    public String createBackup() {
        return storageRepository.createBackup();
    }
    
    /**
     * 从备份恢复
     */
    public boolean restoreFromBackup(String backupFilePath) {
        return storageRepository.restoreFromBackup(backupFilePath);
    }
    
    /**
     * 获取安全的文件名
     */
    public String getSafeFileName(String fileName) {
        return storageRepository.getSafeFileName(fileName);
    }
    
    /**
     * 获取缓存文件名
     */
    public String getCacheFileName(String url) {
        return storageRepository.getCacheFileName(url);
    }
    
    // ===== BookStorage API =====
    
    /**
     * 获取所有书籍
     */
    @NotNull
    public List<Book> getAllBooks(boolean loadDetails) {
        return bookRepository.getAllBooks(loadDetails);
    }
    
    /**
     * 获取所有书籍（默认加载详细信息）
     */
    @NotNull
    public List<Book> getAllBooks() {
        return bookRepository.getAllBooks();
    }
    
    /**
     * 根据ID获取书籍
     */
    @Nullable
    public Book getBook(String bookId) {
        return bookRepository.getBook(bookId);
    }
    
    /**
     * 添加书籍
     */
    public void addBook(@NotNull Book book) {
        bookRepository.addBook(book);
    }
    
    /**
     * 更新书籍
     */
    public void updateBook(@NotNull Book book) {
        bookRepository.updateBook(book);
    }
    
    /**
     * 批量更新书籍
     */
    public void updateBooks(@NotNull List<Book> books) {
        bookRepository.updateBooks(books);
    }
    
    /**
     * 删除书籍
     */
    public void removeBook(@NotNull Book book) {
        bookRepository.removeBook(book);
    }
    
    /**
     * 清空所有书籍
     */
    public void clearAllBooks() {
        bookRepository.clearAllBooks();
    }
    
    // ===== ReadingProgressManager API =====
    
    /**
     * 更新阅读进度
     */
    public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position) {
        readingProgressRepository.updateProgress(book, chapterId, chapterTitle, position);
    }
    
    /**
     * 更新阅读进度
     */
    public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position, int page) {
        readingProgressRepository.updateProgress(book, chapterId, chapterTitle, position, page);
    }
    
    /**
     * 更新章节总数
     */
    public void updateTotalChapters(@NotNull Book book, int totalChapters) {
        readingProgressRepository.updateTotalChapters(book, totalChapters);
    }
    
    /**
     * 更新当前章节索引
     */
    public void updateCurrentChapter(@NotNull Book book, int currentChapterIndex) {
        readingProgressRepository.updateCurrentChapter(book, currentChapterIndex);
    }
    
    /**
     * 标记书籍为已读
     */
    public void markAsFinished(@NotNull Book book) {
        readingProgressRepository.markAsFinished(book);
    }
    
    /**
     * 标记书籍为未读
     */
    public void markAsUnfinished(@NotNull Book book) {
        readingProgressRepository.markAsUnfinished(book);
    }
    
    /**
     * 重置阅读进度
     */
    public void resetProgress(@NotNull Book book) {
        readingProgressRepository.resetProgress(book);
    }
    
    // ===== ChapterCacheManager API =====
    
    /**
     * 获取缓存的章节内容
     */
    @Nullable
    public String getCachedContent(String bookId, String chapterId) {
        return chapterCacheRepository.getCachedContent(bookId, chapterId);
    }
    
    /**
     * 获取缓存内容，即使已过期
     */
    @Nullable
    public String getFallbackCachedContent(String bookId, String chapterId) {
        return chapterCacheRepository.getFallbackCachedContent(bookId, chapterId);
    }
    
    /**
     * 缓存章节内容
     */
    public void cacheContent(String bookId, String chapterId, String content) {
        chapterCacheRepository.cacheContent(bookId, chapterId, content);
    }
    
    /**
     * 清除指定书籍的缓存
     */
    public void clearCache(String bookId) {
        chapterCacheRepository.clearCache(bookId);
    }
    
    /**
     * 检查并清理过期缓存
     */
    public void checkAndEvictCache() {
        chapterCacheRepository.checkAndEvictCache();
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        chapterCacheRepository.clearAllCache();
    }
    
    /**
     * 获取缓存目录路径
     */
    @NotNull
    public String getCacheDirPath() {
        return chapterCacheRepository.getCacheDirPath();
    }
    
    /**
     * 清理缓存
     */
    public void cleanupCache() {
        chapterCacheRepository.cleanupCache();
    }
    
    /**
     * 清理指定书籍的缓存
     */
    public void cleanupBookCache(String bookId) {
        chapterCacheRepository.cleanupBookCache(bookId);
    }
} 
