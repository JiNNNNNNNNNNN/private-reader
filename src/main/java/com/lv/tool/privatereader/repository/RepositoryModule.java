package com.lv.tool.privatereader.repository;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.repository.impl.FileBookRepository;
import com.lv.tool.privatereader.repository.impl.FileChapterCacheRepository;
import com.lv.tool.privatereader.repository.impl.FileReadingProgressRepository;
import com.lv.tool.privatereader.repository.impl.FileStorageRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.messages.MessageBus;
// REMOVE: import com.lv.tool.privatereader.events.BookDataListener;

/**
 * 存储仓库模块
 * 
 * 负责创建和管理各种Repository实例，提供统一的访问点。
 * 使用依赖注入模式，确保各个Repository之间的依赖关系正确。
 */
@Service(Service.Level.APP)
public final class RepositoryModule {
    private static final Logger LOG = Logger.getInstance(RepositoryModule.class);
    private static volatile RepositoryModule instance;
    
    private StorageRepository storageRepository;
    private BookRepository bookRepository;
    private ReadingProgressRepository readingProgressRepository;
    private ChapterCacheRepository chapterCacheRepository;
    private volatile boolean dataReady = false; // Add dataReady flag
    
    public RepositoryModule() {
        LOG.info("初始化应用级别的 RepositoryModule");
        boolean initializationOk = true;
        
        try {
            // 先创建核心的 StorageRepository
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
                    LOG.info("成功从应用服务获取 StorageRepository 实例");
                }
            } catch (Exception e) {
                LOG.error("获取或创建 StorageRepository 失败，尝试创建新实例", e);
                try {
                    this.storageRepository = new FileStorageRepository();
                    if (this.storageRepository == null) {
                        LOG.error("创建 FileStorageRepository 新实例也失败！");
                        initializationOk = false;
                    } else {
                        LOG.info("成功创建 FileStorageRepository 实例 (catch block)");
                    }
                } catch (Exception e2) {
                    LOG.error("创建 FileStorageRepository 新实例时发生严重错误！", e2);
                    initializationOk = false;
                }
            }
            if (this.storageRepository == null) {
                LOG.error("StorageRepository 最终未能初始化！");
                initializationOk = false;
            }
            
            // 创建 BookRepository (依赖 StorageRepository)
            try {
                this.bookRepository = ApplicationManager.getApplication().getService(BookRepository.class);
                if (this.bookRepository == null) {
                    LOG.warn("未能从应用服务获取 BookRepository，创建新实例");
                    if (this.storageRepository != null) {
                        this.bookRepository = new FileBookRepository(ApplicationManager.getApplication(), this.storageRepository);
                        if (this.bookRepository == null) {
                            LOG.error("创建 FileBookRepository 新实例失败！");
                            initializationOk = false;
                        } else {
                            LOG.info("成功创建 FileBookRepository 实例");
                        }
                    } else {
                        LOG.error("无法创建 FileBookRepository，因为 StorageRepository 未初始化！");
                        initializationOk = false;
                    }
                } else {
                    LOG.info("成功从应用服务获取 BookRepository 实例");
                }
            } catch (Exception e) {
                LOG.error("获取或创建 BookRepository 失败，尝试创建新实例", e);
                if (this.storageRepository != null) {
                    try {
                        this.bookRepository = new FileBookRepository(ApplicationManager.getApplication(), this.storageRepository);
                        if (this.bookRepository == null) {
                            LOG.error("创建 FileBookRepository 新实例也失败！");
                            initializationOk = false;
                        } else {
                            LOG.info("成功创建 FileBookRepository 实例 (catch block)");
                        }
                    } catch (Exception e2) {
                        LOG.error("创建 FileBookRepository 新实例时发生严重错误！", e2);
                        initializationOk = false;
                    }
                } else {
                    LOG.error("无法在 catch 块中创建 FileBookRepository，因为 StorageRepository 未初始化！");
                    initializationOk = false;
                }
            }
            if (this.bookRepository == null) {
                LOG.error("BookRepository 最终未能初始化！");
                initializationOk = false;
            }
            
            // 创建 ReadingProgressRepository
            try {
                this.readingProgressRepository = ApplicationManager.getApplication().getService(ReadingProgressRepository.class);
                if (this.readingProgressRepository == null) {
                    LOG.warn("未能从应用服务获取 ReadingProgressRepository，创建新实例");
                    this.readingProgressRepository = new FileReadingProgressRepository(ApplicationManager.getApplication());
                    if (this.readingProgressRepository == null) {
                        LOG.error("创建 FileReadingProgressRepository 新实例失败！");
                        initializationOk = false;
                    } else {
                        LOG.info("成功创建 FileReadingProgressRepository 实例");
                    }
                } else {
                    LOG.info("成功从应用服务获取 ReadingProgressRepository 实例");
                }
            } catch (Exception e) {
                LOG.error("获取或创建 ReadingProgressRepository 失败，尝试创建新实例", e);
                try {
                    this.readingProgressRepository = new FileReadingProgressRepository(ApplicationManager.getApplication());
                    if (this.readingProgressRepository == null) {
                        LOG.error("创建 FileReadingProgressRepository 新实例也失败！");
                        initializationOk = false;
                    } else {
                        LOG.info("成功创建 FileReadingProgressRepository 实例 (catch block)");
                    }
                } catch (Exception e2) {
                    LOG.error("创建 FileReadingProgressRepository 新实例时发生严重错误！", e2);
                    initializationOk = false;
                }
            }
            if (this.readingProgressRepository == null) {
                LOG.error("ReadingProgressRepository 最终未能初始化！");
                initializationOk = false;
            }
            
            // 创建 ChapterCacheRepository (依赖 StorageRepository)
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
            
        } catch (Throwable t) {
            LOG.error("初始化 RepositoryModule 时发生严重错误", t);
            initializationOk = false;
        }
        
        // 记录最终初始化结果
        LOG.info("RepositoryModule 初始化完成，最终状态: " +
                "StorageRepository=" + (storageRepository != null ? "成功" : "失败") + ", " +
                "BookRepository=" + (bookRepository != null ? "成功" : "失败") + ", " +
                "ReadingProgressRepository=" + (readingProgressRepository != null ? "成功" : "失败") + ", " +
                "ChapterCacheRepository=" + (chapterCacheRepository != null ? "成功" : "失败") + ". " + 
                (initializationOk ? "所有仓库初始化成功。" : "部分或全部仓库初始化失败！"));
                
        // If all repositories initialized successfully, mark data as ready and publish event
        if (initializationOk) {
            this.dataReady = true;
            LOG.info("数据已准备就绪 (dataReady=true)，发布 BOOK_DATA_TOPIC 事件...");
            // TODO: Restore event publishing once BookDataListener is correctly placed in events package
            // MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
            // BookDataListener publisher = messageBus.syncPublisher(BookDataListener.BOOK_DATA_TOPIC);
            // publisher.bookDataLoaded();
            LOG.info("事件发布逻辑已暂时注释。"); // Adjusted log message
        } else {
             LOG.warn("部分仓库初始化失败，数据未完全就绪，不发布事件。");
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