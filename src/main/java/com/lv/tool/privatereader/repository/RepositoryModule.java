package com.lv.tool.privatereader.repository;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.repository.impl.FileBookRepository;
import com.lv.tool.privatereader.repository.impl.FileChapterCacheRepository;
import com.lv.tool.privatereader.repository.impl.FileReadingProgressRepository;
import com.lv.tool.privatereader.repository.impl.FileStorageRepository;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.storage.StorageManager;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 存储仓库模块
 * 
 * 负责创建和管理各种Repository实例，提供统一的访问点。
 * 使用依赖注入模式，确保各个Repository之间的依赖关系正确。
 * 
 * 同时提供兼容层，支持从旧的存储类获取数据。
 */
@Service(Service.Level.PROJECT)
public class RepositoryModule {
    private static final Logger LOG = Logger.getInstance(RepositoryModule.class);
    
    private final Project project;
    private final StorageRepository storageRepository;
    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final ChapterCacheRepository chapterCacheRepository;
    
    // 旧的存储类实例，用于兼容
    private final StorageManager storageManager;
    private final BookStorage bookStorage;
    private final ReadingProgressManager readingProgressManager;
    private final ChapterCacheManager chapterCacheManager;
    
    public RepositoryModule(Project project) {
        this.project = project;
        
        // 获取旧的存储类实例
        this.storageManager = project.getService(StorageManager.class);
        this.bookStorage = project.getService(BookStorage.class);
        this.readingProgressManager = project.getService(ReadingProgressManager.class);
        this.chapterCacheManager = project.getService(ChapterCacheManager.class);
        
        // 创建新的Repository实例
        if (storageManager != null) {
            // 如果旧的存储类可用，创建适配器
            LOG.info("使用旧的存储类创建Repository适配器");
            this.storageRepository = createStorageRepositoryAdapter();
            this.bookRepository = createBookRepositoryAdapter();
            this.readingProgressRepository = createReadingProgressRepositoryAdapter();
            this.chapterCacheRepository = createChapterCacheRepositoryAdapter();
        } else {
            // 如果旧的存储类不可用，创建新的实现
            LOG.info("创建新的Repository实现");
            this.storageRepository = new FileStorageRepository(project);
            this.bookRepository = new FileBookRepository(project, storageRepository);
            this.readingProgressRepository = new FileReadingProgressRepository(project, bookRepository);
            this.chapterCacheRepository = new FileChapterCacheRepository(project, storageRepository);
        }
    }
    
    /**
     * 获取存储仓库
     */
    public StorageRepository getStorageRepository() {
        return storageRepository;
    }
    
    /**
     * 获取书籍仓库
     */
    public BookRepository getBookRepository() {
        return bookRepository;
    }
    
    /**
     * 获取阅读进度仓库
     */
    public ReadingProgressRepository getReadingProgressRepository() {
        return readingProgressRepository;
    }
    
    /**
     * 获取章节缓存仓库
     */
    public ChapterCacheRepository getChapterCacheRepository() {
        return chapterCacheRepository;
    }
    
    /**
     * 创建StorageRepository适配器
     */
    private StorageRepository createStorageRepositoryAdapter() {
        return new StorageRepository() {
            @Override
            @NotNull
            public String getBaseStoragePath() {
                return storageManager.getBaseStoragePath();
            }
            
            @Override
            @NotNull
            public String getBooksPath() {
                return storageManager.getBooksPath();
            }
            
            @Override
            @NotNull
            public String getCachePath() {
                return storageManager.getCachePath();
            }
            
            @Override
            @NotNull
            public String getSettingsPath() {
                return storageManager.getSettingsPath();
            }
            
            @Override
            @NotNull
            public String getBackupPath() {
                return storageManager.getBackupPath();
            }
            
            @Override
            @NotNull
            public String getBooksFilePath() {
                return storageManager.getBooksPath() + "/index.json";
            }
            
            @Override
            @NotNull
            public String createBookDirectory(String bookId) {
                return storageManager.createBookDirectory(bookId);
            }
            
            @Override
            @NotNull
            public String getBookDirectory(String bookId) {
                return storageManager.getBookDirectory(bookId);
            }
            
            @Override
            public void clearAllStorage() {
                storageManager.clearAllStorage();
            }
            
            @Override
            @NotNull
            public String createBackup() {
                return storageManager.createBackup();
            }
            
            @Override
            public boolean restoreFromBackup(String backupFilePath) {
                return storageManager.restoreFromBackup(backupFilePath);
            }
            
            @Override
            @NotNull
            public String getSafeFileName(@NotNull String fileName) {
                return storageManager.getSafeFileName(fileName);
            }
            
            @Override
            @NotNull
            public String getCacheFileName(@NotNull String url) {
                return storageManager.getCacheFileName(url);
            }
        };
    }
    
    /**
     * 创建BookRepository适配器
     */
    private BookRepository createBookRepositoryAdapter() {
        return new BookRepository() {
            @Override
            @NotNull
            public List<Book> getAllBooks(boolean loadDetails) {
                return bookStorage.getAllBooks(loadDetails);
            }
            
            @Override
            @NotNull
            public List<Book> getAllBooks() {
                return bookStorage.getAllBooks();
            }
            
            @Override
            @Nullable
            public Book getBook(String bookId) {
                return bookStorage.getBook(bookId);
            }
            
            @Override
            public void addBook(@NotNull Book book) {
                bookStorage.addBook(book);
            }
            
            @Override
            public void updateBook(@NotNull Book book) {
                bookStorage.updateBook(book);
            }
            
            @Override
            public void updateBooks(@NotNull List<Book> books) {
                for (Book book : books) {
                    bookStorage.updateBook(book);
                }
            }
            
            @Override
            public void removeBook(@NotNull Book book) {
                bookStorage.removeBook(book);
            }
            
            @Override
            public void clearAllBooks() {
                bookStorage.clearAllBooks();
            }
            
            @Override
            public String getIndexFilePath() {
                return storageManager.getBooksPath() + "/index.json";
            }
        };
    }
    
    /**
     * 创建ReadingProgressRepository适配器
     */
    private ReadingProgressRepository createReadingProgressRepositoryAdapter() {
        return new ReadingProgressRepository() {
            @Override
            public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position) {
                readingProgressManager.updateProgress(book, chapterId, chapterTitle, position);
            }
            
            @Override
            public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position, int page) {
                readingProgressManager.updateProgress(book, chapterId, chapterTitle, position, page);
            }
            
            @Override
            public void updateTotalChapters(@NotNull Book book, int totalChapters) {
                readingProgressManager.updateTotalChapters(book, totalChapters);
            }
            
            @Override
            public void updateCurrentChapter(@NotNull Book book, int currentChapterIndex) {
                readingProgressManager.updateCurrentChapter(book, currentChapterIndex);
            }
            
            @Override
            public void markAsFinished(@NotNull Book book) {
                readingProgressManager.markAsFinished(book);
            }
            
            @Override
            public void markAsUnfinished(@NotNull Book book) {
                readingProgressManager.markAsUnfinished(book);
            }
            
            @Override
            public void resetProgress(@NotNull Book book) {
                readingProgressManager.resetProgress(book);
            }
        };
    }
    
    /**
     * 创建ChapterCacheRepository适配器
     */
    private ChapterCacheRepository createChapterCacheRepositoryAdapter() {
        return new ChapterCacheRepository() {
            @Override
            @Nullable
            public String getCachedContent(String bookId, String chapterId) {
                return chapterCacheManager.getCachedContent(bookId, chapterId);
            }
            
            @Override
            @Nullable
            public String getFallbackCachedContent(String bookId, String chapterId) {
                return chapterCacheManager.getFallbackCachedContent(bookId, chapterId);
            }
            
            @Override
            public void cacheContent(String bookId, String chapterId, String content) {
                chapterCacheManager.cacheContent(bookId, chapterId, content);
            }
            
            @Override
            public void clearCache(String bookId) {
                chapterCacheManager.clearCache(bookId);
            }
            
            @Override
            public void checkAndEvictCache() {
                chapterCacheManager.cleanupCache();
            }
            
            @Override
            public void clearAllCache() {
                chapterCacheManager.clearAllCache();
            }
            
            @Override
            @NotNull
            public String getCacheDirPath() {
                return storageManager.getCachePath();
            }
            
            @Override
            public void cleanupCache() {
                chapterCacheManager.cleanupCache();
            }
            
            @Override
            public void cleanupBookCache(String bookId) {
                chapterCacheManager.clearCache(bookId);
            }
        };
    }
}