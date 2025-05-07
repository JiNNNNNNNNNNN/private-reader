package com.lv.tool.privatereader.repository;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.repository.impl.FileBookRepository;
import com.lv.tool.privatereader.repository.impl.FileChapterCacheRepository;
import com.lv.tool.privatereader.repository.impl.SqliteReadingProgressRepository;
import com.lv.tool.privatereader.repository.impl.FileStorageRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.messages.MessageBus;
// REMOVE: import com.lv.tool.privatereader.events.BookDataListener;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 存储仓库模块
 * 
 * 负责创建和管理各种Repository实例，提供统一的访问点。
 * 使用依赖注入模式，确保各个Repository之间的依赖关系正确。
 */
@Service(Service.Level.APP)
@Singleton
public final class RepositoryModule {
    private static final Logger LOG = Logger.getInstance(RepositoryModule.class);
    private static volatile RepositoryModule instance;
    
    private StorageRepository storageRepository;
    private BookRepository bookRepository;
    private ReadingProgressRepository readingProgressRepository;
    private ChapterCacheRepository chapterCacheRepository;
    private volatile boolean dataReady = false; // Add dataReady flag
    private boolean initializationOk = true;
    
    @Inject
    public RepositoryModule() {
        LOG.info("RepositoryModule 初始化...");
        // 延迟初始化仓库实例
        // initializeRepositoriesLazily(); // 改为在 get 方法中按需初始化
    }
    
    // 确保在使用前进行初始化
    private void ensureInitialized() {
        // 可以在这里添加一个全局的初始化锁，如果多线程访问get方法可能在初始化完成前发生
        if (storageRepository == null) initializeStorageRepository();
        if (bookRepository == null) initializeBookRepository();
        if (readingProgressRepository == null) initializeReadingProgressRepository();
        if (chapterCacheRepository == null) initializeChapterCacheRepository();
        if (!initializationOk) {
             LOG.error("仓库模块初始化失败，部分仓库可能不可用。");
             // Consider throwing an exception or setting a state
        }
    }

    private void initializeStorageRepository() {
        if (storageRepository == null) {
             try {
                 this.storageRepository = ApplicationManager.getApplication().getService(StorageRepository.class);
                 if (this.storageRepository == null) {
                     LOG.warn("未能从应用服务获取 StorageRepository，创建新实例");
                     this.storageRepository = new FileStorageRepository();
                     if (this.storageRepository == null) {
                          LOG.error("创建 FileStorageRepository 新实例失败！");
                          initializationOk = false;
                     } else {
                          LOG.info("成功创建 FileStorageRepository 实例");
                     }
                 } else {
                     LOG.info("成功获取 StorageRepository 服务实例");
                 }
             } catch (Exception e) {
                 LOG.error("初始化 StorageRepository 时出错", e);
                 initializationOk = false;
             }
        }
    }
    
    private void initializeBookRepository() {
         if (bookRepository == null) {
             initializeStorageRepository(); // 调用正确的初始化方法
             if (!initializationOk) return; // 如果依赖初始化失败，则无法继续
             try {
                 this.bookRepository = ApplicationManager.getApplication().getService(BookRepository.class);
                 if (this.bookRepository == null) {
                     LOG.warn("未能从应用服务获取 BookRepository，创建新实例");
                     this.bookRepository = new FileBookRepository(this.storageRepository); // 需要 StorageRepository
                     if (this.bookRepository == null) {
                          LOG.error("创建 FileBookRepository 新实例失败！");
                          initializationOk = false;
                     } else {
                          LOG.info("成功创建 FileBookRepository 实例");
                     }
                 } else {
                      LOG.info("成功获取 BookRepository 服务实例");
                 }
             } catch (Exception e) {
                  LOG.error("初始化 BookRepository 时出错", e);
                  initializationOk = false;
             }
         }
    }
    
    private void initializeReadingProgressRepository() {
         if (readingProgressRepository == null) {
             try {
                 // 总是尝试从服务容器获取，因为它应该是通过 @Service 和绑定正确提供的
                 this.readingProgressRepository = ApplicationManager.getApplication().getService(ReadingProgressRepository.class);
                 if (this.readingProgressRepository == null) {
                     // 如果服务容器没有提供实例（可能绑定失败或类未找到），记录错误。
                     // 不再尝试手动创建，因为它的依赖（DatabaseManager）也应该由容器管理。
                     LOG.error("未能从应用服务获取 ReadingProgressRepository 实例。请检查服务绑定和服务类是否正确。");
                     initializationOk = false; 
                 } else {
                     LOG.info("成功获取 ReadingProgressRepository 服务实例");
                 }
             } catch (Exception e) {
                 LOG.error("获取 ReadingProgressRepository 服务实例时出错", e);
                 initializationOk = false;
             }
         }
    }
    
    private void initializeChapterCacheRepository() {
        if (chapterCacheRepository == null) {
            try {
                this.chapterCacheRepository = ApplicationManager.getApplication().getService(ChapterCacheRepository.class);
                if (this.chapterCacheRepository == null) {
                    LOG.warn("未能从应用服务获取 ChapterCacheRepository，创建新实例");
                    if (this.storageRepository != null) {
                        this.chapterCacheRepository = new FileChapterCacheRepository(this.storageRepository);
                        if (this.chapterCacheRepository == null) {
                            LOG.error("创建 FileChapterCacheRepository 新实例失败！");
                            initializationOk = false;
                        } else {
                            LOG.info("成功创建 FileChapterCacheRepository 实例");
                        }
                    } else {
                        LOG.error("无法创建 FileChapterCacheRepository，因为 StorageRepository 未初始化！");
                        initializationOk = false;
                    }
                } else {
                    LOG.info("成功从应用服务获取 ChapterCacheRepository 实例");
                }
            } catch (Exception e) {
                LOG.error("获取或创建 ChapterCacheRepository 失败，尝试创建新实例", e);
                if (this.storageRepository != null) {
                    try {
                        this.chapterCacheRepository = new FileChapterCacheRepository(this.storageRepository);
                        if (this.chapterCacheRepository == null) {
                            LOG.error("创建 FileChapterCacheRepository 新实例也失败！");
                            initializationOk = false;
                        } else {
                            LOG.info("成功创建 FileChapterCacheRepository 实例 (catch block)");
                        }
                    } catch (Exception e2) {
                        LOG.error("创建 FileChapterCacheRepository 新实例时发生严重错误！", e2);
                        initializationOk = false;
                    }
                } else {
                    LOG.error("无法在 catch 块中创建 FileChapterCacheRepository，因为 StorageRepository 未初始化！");
                    initializationOk = false;
                }
            }
            if (this.chapterCacheRepository == null) {
                LOG.error("ChapterCacheRepository 最终未能初始化！");
                initializationOk = false;
            }
        }
    }
    
    /**
     * Checks if the core data repositories are initialized and ready.
     * @return true if data is ready, false otherwise.
     */
    public boolean isDataReady() {
        return dataReady;
    }

    /**
     * 获取存储仓库
     */
    @Nullable
    public StorageRepository getStorageRepository() {
        return storageRepository;
    }
    
    /**
     * 获取书籍仓库
     */
    @Nullable
    public BookRepository getBookRepository() {
        return bookRepository;
    }
    
    /**
     * 获取阅读进度仓库
     */
    @Nullable
    public ReadingProgressRepository getReadingProgressRepository() {
        return readingProgressRepository;
    }
    
    /**
     * 获取章节缓存仓库
     */
    @Nullable
    public ChapterCacheRepository getChapterCacheRepository() {
        return chapterCacheRepository;
    }
    
    /**
     * 获取 RepositoryModule 实例
     * 
     * 首先尝试从应用级服务获取，如果失败则创建一个新实例
     */
    @NotNull
    public static RepositoryModule getInstance() {
        LOG.info("=== 获取 RepositoryModule 实例 ===");
        try {
            RepositoryModule module = ApplicationManager.getApplication().getService(RepositoryModule.class);
            if (module == null) {
                LOG.warn("无法从应用级服务获取 RepositoryModule，创建新实例");
                module = new RepositoryModule();
            } else {
                LOG.info("成功从应用级服务获取 RepositoryModule 实例");
                LOG.info("仓库状态: " + 
                        "StorageRepository=" + (module.getStorageRepository() != null ? "已初始化" : "未初始化") + ", " +
                        "BookRepository=" + (module.getBookRepository() != null ? "已初始化" : "未初始化") + ", " +
                        "ReadingProgressRepository=" + (module.getReadingProgressRepository() != null ? "已初始化" : "未初始化") + ", " +
                        "ChapterCacheRepository=" + (module.getChapterCacheRepository() != null ? "已初始化" : "未初始化"));
            }
            return module;
        } catch (Exception e) {
            LOG.error("获取 RepositoryModule 实例时发生异常", e);
            // 创建新实例作为备用
            return new RepositoryModule();
        }
    }
}