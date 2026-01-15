package com.lv.tool.privatereader.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.exception.PrivateReaderException;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.async.ReactiveTaskManager;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import com.lv.tool.privatereader.service.ChapterService;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * ChapterService接口的实现类
 * 使用响应式编程处理章节内容相关操作
 */
@Service
public final class ChapterServiceImpl implements ChapterService {
    private static final Logger LOG = Logger.getInstance(ChapterServiceImpl.class);
    private ChapterCacheRepository chapterCacheRepository;
    private BookRepository bookRepository;
    private final ReactiveTaskManager reactiveTaskManager;
    private final ReactiveSchedulers reactiveSchedulers;

    // 缓存相关 - 使用 Guava Cache 替代无限制的 ConcurrentHashMap
    // 书籍章节列表缓存：最多缓存 50 本书的章节列表，访问后 30 分钟过期
    private final Cache<String, List<Chapter>> bookChapterListCache = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
            
    // 正在进行的请求缓存：最多 50 个并发请求，写入后 5 分钟过期
    private final Cache<String, Mono<List<Chapter>>> chapterListMonoCache = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    // 网络请求频率限制缓存：记录上次网络请求时间，避免频繁刷新
    private final Cache<String, Long> lastNetworkCheckCache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    // 引入 Cache，避免重复创建 Mono
    private volatile Mono<ChapterCacheRepository> cachedRepoMono;

    /**
     * 无参构造方法
     */
    public ChapterServiceImpl() {
        LOG.info("初始化ChapterServiceImpl");
        this.reactiveTaskManager = ReactiveTaskManager.getInstance();
        this.reactiveSchedulers = ReactiveSchedulers.getInstance();
        // 服务将在首次需要时异步初始化
    }

    /**
     * 确保服务已初始化
     *
     * @throws IllegalStateException 如果服务初始化失败
     */
    private void ensureServicesInitialized() {
        // Ensure ChapterCacheRepository is initialized via Mono
        if (cachedRepoMono == null) {
            LOG.debug("尝试初始化 ChapterCacheRepository 服务");
            try {
                // 触发初始化，但不阻塞
                getInitializedChapterCacheRepository().subscribe(
                    repo -> LOG.debug("ChapterCacheRepository 服务初始化成功"),
                    error -> LOG.error("ChapterCacheRepository 服务初始化失败: " + error.getMessage(), error)
                );
            } catch (Exception e) {
                LOG.error("触发 ChapterCacheRepository 服务初始化时出错: " + e.getMessage(), e);
            }
        }

        // Initialize BookRepository directly if needed
        if (bookRepository == null) {
            LOG.debug("尝试初始化 BookRepository 服务");
            try {
                bookRepository = ApplicationManager.getApplication().getService(BookRepository.class);
                if (bookRepository == null) {
                    String errorMsg = "BookRepository 服务无法初始化!";
                    LOG.error(errorMsg, new IllegalStateException(errorMsg));
                    // 不抛出异常，让调用方决定如何处理
                } else {
                    LOG.debug("BookRepository 服务初始化成功");
                }
            } catch (Exception e) {
                LOG.error("初始化 BookRepository 服务时出错: " + e.getMessage(), e);
                // 不抛出异常，让调用方决定如何处理
            }
        }
    }

    /**
     * 异步获取并缓存 ChapterCacheRepository 服务实例。
     * 使用 Double-Checked Locking 和 volatile 保证线程安全和可见性。
     * 使用 platformThread() 调度器执行阻塞的 getService 调用。
     * 使用 .cache() 缓存结果 Mono，避免重复执行。
     */
    private Mono<ChapterCacheRepository> getInitializedChapterCacheRepository() {
        Mono<ChapterCacheRepository> result = cachedRepoMono;
        if (result == null) {
            synchronized (this) {
                result = cachedRepoMono;
                if (result == null) {
                    LOG.info("首次初始化 ChapterCacheRepository Mono");

                    // 首先尝试直接获取服务，如果已经初始化则可以避免异步操作
                    ChapterCacheRepository directService = null;
                    try {
                        directService = ApplicationManager.getApplication().getService(ChapterCacheRepository.class);
                        if (directService != null) {
                            LOG.info("直接获取 ChapterCacheRepository 服务成功");
                            this.chapterCacheRepository = directService;
                        }
                    } catch (Exception e) {
                        LOG.warn("直接获取 ChapterCacheRepository 服务失败: " + e.getMessage());
                    }

                    // 如果直接获取成功，创建一个已完成的 Mono
                    if (directService != null) {
                        result = Mono.just(directService);
                    } else {
                        // 否则，使用异步方式获取
                        result = Mono.fromCallable(() -> {
                                    LOG.debug("尝试在 platformThread 上获取 ChapterCacheRepository 服务");
                                    ChapterCacheRepository service = ApplicationManager.getApplication().getService(ChapterCacheRepository.class);
                                    if (service == null) {
                                        LOG.error("ChapterCacheRepository 服务无法初始化!");
                                        throw new IllegalStateException("ChapterCacheRepository service is unavailable.");
                                    }
                                    LOG.info("成功获取 ChapterCacheRepository 服务");
                                    // 成功获取后，直接赋值给成员变量，后续可直接使用
                                    // 但注意，首次调用仍然需要通过 Mono 完成
                                    this.chapterCacheRepository = service;
                                    return service;
                                })
                                .doOnError(error -> LOG.error("获取 ChapterCacheRepository 服务失败: " + error.getMessage(), error))
                                .retryWhen(reactor.util.retry.Retry.fixedDelay(3, java.time.Duration.ofSeconds(1))
                                    .filter(e -> !(e instanceof IllegalStateException))) // 不重试服务不可用的情况
                                .subscribeOn(reactiveSchedulers.platformThread()) // 在平台线程池执行 getService
                                .cache(); // 缓存结果 Mono
                    }

                    cachedRepoMono = result;
                }
            }
        }
        return result;
    }
    
    @Override
    public Mono<String> getChapterContent(@NotNull Book book, @NotNull String chapterId) {
        ensureServicesInitialized();
        return getInitializedChapterCacheRepository().flatMap(repo -> {
            // 首先检查有效缓存
            String cachedContent = repo.getCachedContent(book.getId(), chapterId);
            if (cachedContent != null) {
                return Mono.just(cachedContent);
            }
            // 从网络获取
            return Mono.fromCallable(() -> {
                NovelParser parser = book.getParser();
                if (parser == null) {
                    throw new IllegalStateException("Parser not available for book: " + book.getTitle());
                }
                String content = parser.parseChapterContent(chapterId);
                // 成功后缓存内容
                if (content != null && !content.isEmpty()) {
                    repo.cacheContent(book.getId(), chapterId, content);
                }
                return content;
            }).subscribeOn(reactiveSchedulers.io())
            // 网络失败后回退到备用缓存
            .onErrorResume(e -> {
                LOG.warn("从网络获取章节内容失败，回退到缓存: " + e.getMessage());
                String fallbackContent = repo.getFallbackCachedContent(book.getId(), chapterId);
                if (fallbackContent != null) {
                    return Mono.just(fallbackContent);
                }
                return Mono.error(e); // 如果备用缓存也没有，则传递原始错误
            });
        });
    }

    @Override
    public String getChapterContentSync(@NotNull String bookId, @NotNull String chapterId) {
        ensureServicesInitialized();
        try {
            Book book = bookRepository.getBook(bookId);
            if (book == null) {
                return "错误: 未找到书籍。";
            }
            return getChapterContent(book, chapterId).block();
        } catch (Exception e) {
            return "获取章节内容失败: " + e.getMessage();
        }
    }

    @Override
    public Mono<Chapter> getChapter(@NotNull Book book, @NotNull String chapterId) {
        // 使用 flatMap 确保服务在使用前已初始化
        return getInitializedChapterCacheRepository().flatMap(repo -> {
            ensureServicesInitialized();
            // 将原有逻辑放入 flatMap
            LOG.info("获取章节元数据: 书籍='" + book.getTitle() + "', 章节ID=" + chapterId);

            return getChapterList(book) // getChapterList 内部也需要修改以处理异步服务
                .flatMap(chapters -> {
                    return Flux.fromIterable(chapters)
                        .filter(chapter -> chapterId.equals(chapter.url()))
                        .next(); // .next() 返回 Mono<Chapter>
                });
                // 不再需要 subscribeOn(io)，因为 getChapterList 内部会处理
        }).doOnError(e -> {
            // 将错误日志移到 Mono 链的末端
            LOG.error("获取章节元数据失败: 书籍='" + book.getTitle() + "', 章节ID=" + chapterId, e);
        });
    }

    @Override
    public Mono<Chapter> getChapterWithFallback(@NotNull Book book, @NotNull String chapterId) {
        // 同样使用 flatMap 确保服务初始化
        return getInitializedChapterCacheRepository().flatMap(repo -> {
            ensureServicesInitialized();
            LOG.info("获取章节元数据(带回退): 书籍='" + book.getTitle() + "', 章节ID=" + chapterId);

            // 调用已修改的 getChapter 方法
            return getChapter(book, chapterId)
                .switchIfEmpty(Mono.defer(() -> {
                    LOG.warn("在 getChapterWithFallback 中未找到章节: " + chapterId);
                    // 当找不到章节时，直接返回一个明确的错误，而不是回退到第一章
                    return Mono.error(new PrivateReaderException("无法找到章节: " + chapterId,
                        PrivateReaderException.ExceptionType.RESOURCE_NOT_FOUND));
                }));
                // 不再需要 subscribeOn(io)
        }).doOnError(e -> {
            // 将错误日志移到 Mono 链的末端
            LOG.error("获取章节元数据(带回退)失败: 书籍='" + book.getTitle() + "', 章节ID=" + chapterId, e);
        });
    }

    @Override
    public Mono<List<Chapter>> getChapterList(@NotNull Book book) {
        ensureServicesInitialized();
        String bookId = book.getId();
        LOG.info("获取章节列表: 书籍='" + book.getTitle() + "' (缓存优先策略)");

        // 1. 缓存优先：立即从缓存返回数据
        Mono<List<Chapter>> cachedChaptersMono = fallbackToCache(book)
                .filter(chapters -> !chapters.isEmpty());

        // 2. 后台异步从网络获取最新数据
        Mono<List<Chapter>> networkChaptersMono = Mono.fromCallable(() -> {
                    NovelParser parser = book.getParser();
                    if (parser == null) {
                        throw new IllegalStateException("Parser not initialized for book: " + book.getTitle());
                    }
                    return parser.parseChapterList();
                })
                .subscribeOn(reactiveSchedulers.io())
                .doOnSuccess(chaptersFromNetwork -> {
                    if (chaptersFromNetwork != null && !chaptersFromNetwork.isEmpty()) {
                        LOG.info("后台网络请求成功获取 " + chaptersFromNetwork.size() + " 个章节 for '" + book.getTitle() + "'. 更新缓存.");
                        // 检查与缓存是否相同，避免不必要的更新
                        List<Chapter> cachedList = bookChapterListCache.getIfPresent(bookId);
                        if (!chaptersFromNetwork.equals(cachedList)) {
                             // 更新内存缓存
                            bookChapterListCache.put(bookId, chaptersFromNetwork);
                            // 更新 Book 对象自身的持久化缓存
                            book.setCachedChapters(chaptersFromNetwork);
                            LOG.info("缓存已更新 for '" + book.getTitle() + "'.");
                        } else {
                            LOG.info("网络章节列表与缓存一致，无需更新 for '" + book.getTitle() + "'.");
                        }
                    } else {
                        LOG.warn("后台网络获取的章节列表为空 for '" + book.getTitle() + "'.");
                    }
                })
                .doOnError(error -> LOG.warn("后台网络获取章节列表失败 for '" + book.getTitle() + "': " + error.getMessage()));

        // 3. 合并缓存和网络请求
        // a. 立即返回缓存的结果
        // b. 触发后台网络请求，但不等待其完成（带频率限制和并发控制）
        // c. 如果缓存为空，则等待网络请求的结果
        return cachedChaptersMono
                .doOnNext(chapters -> {
                    // 缓存命中时，检查是否需要后台刷新
                    if (lastNetworkCheckCache.getIfPresent(bookId) == null) {
                        try {
                            // 使用 chapterListMonoCache 确保同一时间只有一个后台更新任务在运行
                            chapterListMonoCache.get(bookId, () -> {
                                LOG.info("触发后台章节列表更新 for '" + book.getTitle() + "'");
                                lastNetworkCheckCache.put(bookId, System.currentTimeMillis());
                                
                                // 创建并启动任务，任务完成后自动清理并发锁
                                Mono<List<Chapter>> task = networkChaptersMono
                                    .doFinally(signal -> chapterListMonoCache.invalidate(bookId));
                                
                                task.subscribe();
                                return task;
                            });
                        } catch (Exception e) {
                            LOG.warn("触发后台更新时发生异常: " + e.getMessage());
                        }
                    } else {
                        LOG.debug("跳过后台章节列表更新 (最近已更新) for '" + book.getTitle() + "'");
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 缓存未命中，执行网络请求并记录时间
                    // 同样使用并发控制，防止多个线程同时触发网络请求
                    try {
                        return chapterListMonoCache.get(bookId, () -> {
                            LOG.info("缓存未命中，执行网络请求 for '" + book.getTitle() + "'");
                            lastNetworkCheckCache.put(bookId, System.currentTimeMillis());
                            return networkChaptersMono
                                .doFinally(signal -> chapterListMonoCache.invalidate(bookId));
                        });
                    } catch (Exception e) {
                        LOG.error("获取章节列表失败: " + e.getMessage());
                        return networkChaptersMono; // 降级处理
                    }
                }));
    }

    /**
     * 回退到缓存的辅助方法
     */
    private Mono<List<Chapter>> fallbackToCache(Book book) {
        // 优先检查内存缓存
        List<Chapter> cachedChapters = bookChapterListCache.getIfPresent(book.getId());
        if (cachedChapters != null && !cachedChapters.isEmpty()) {
            LOG.info("从内存缓存回退成功 for book: '" + book.getTitle() + "'");
            // 同时，确保Book对象自身的缓存也是最新的
            if (book.getCachedChapters() == null || book.getCachedChapters().size() != cachedChapters.size()) {
                book.setCachedChapters(cachedChapters);
            }
            return Mono.just(cachedChapters);
        }

        // 其次检查Book对象自身的持久化缓存
        List<Chapter> bookCachedChapters = book.getCachedChapters();
        if (bookCachedChapters != null && !bookCachedChapters.isEmpty()) {
            LOG.info("从Book对象持久化缓存回退成功 for book: '" + book.getTitle() + "'");
            // 将其放入内存缓存以备后用
            bookChapterListCache.put(book.getId(), bookCachedChapters);
            return Mono.just(bookCachedChapters);
        }

        LOG.error("网络和所有缓存都获取章节列表失败 for book: '" + book.getTitle() + "'");
        return Mono.just(List.of()); // 返回空列表作为最终的失败结果
    }

    @Override
    public Mono<Void> clearBookCache(@NotNull Book book) {
        // 使用 flatMap 确保服务初始化
        return getInitializedChapterCacheRepository().flatMap(repo -> {
            ensureServicesInitialized();
            String bookId = book.getId();
            LOG.info("清除书籍缓存: " + book.getTitle() + " (ID: " + bookId + ")");
            // 将 Runnable 包装在 Mono 中，并在 io 线程执行
            return Mono.fromRunnable(() -> {
                bookChapterListCache.invalidate(bookId);
                chapterListMonoCache.invalidate(bookId);
                lastNetworkCheckCache.invalidate(bookId);
                // repo 已确保非 null
                // if (chapterCacheRepository != null) {
                repo.clearCache(bookId);
                /*
                } else {
                    LOG.warn("ChapterCacheRepository 未初始化，无法清除书籍 '" + book.getTitle() + "' 的内容缓存", new Throwable());
                }
                */
            })
            .subscribeOn(reactiveSchedulers.io()) // 文件操作在 io 线程
            .then(); // 转换回 Mono<Void>
        });
    }

    @Override
    public Mono<Void> clearAllCache() {
        // 使用 flatMap 确保服务初始化
        return getInitializedChapterCacheRepository().flatMap(repo -> {
             ensureServicesInitialized();
             LOG.info("清除所有章节缓存");
             // 将 Runnable 包装在 Mono 中，并在 io 线程执行
             return Mono.fromRunnable(() -> {
                 bookChapterListCache.invalidateAll();
                 chapterListMonoCache.invalidateAll();
                 lastNetworkCheckCache.invalidateAll();
                 // repo 已确保非 null
                 // if (chapterCacheRepository != null) {
                 repo.clearAllCache();
                 /*
                 } else {
                     LOG.warn("ChapterCacheRepository 未初始化，无法清除所有内容缓存");
                 }
                 */
             })
             .subscribeOn(reactiveSchedulers.io()) // 文件操作在 io 线程
             .then(); // 转换回 Mono<Void>
         });
     }

    @Override
    public Mono<String> getChapterTitle(@NotNull String bookId, @NotNull String chapterId) {
        ensureServicesInitialized();
        LOG.info("异步获取章节标题: 书籍ID='" + bookId + "', 章节ID=" + chapterId);

        return Mono.fromCallable(() -> bookRepository.getBook(bookId))
            .subscribeOn(reactiveSchedulers.io())
            .flatMap(book -> {
                if (book == null) {
                    LOG.warn("异步获取章节标题失败: 未找到书籍ID='" + bookId + "'");
                    return Mono.just("Error: Book not found.");
                }
                return getChapterList(book)
                    .map(chapters -> chapters.stream()
                        .filter(c -> chapterId.equals(c.url()))
                        .findFirst()
                        .map(Chapter::title)
                        .orElse("Error: Chapter not found in list."));
            })
            .doOnError(e -> LOG.error("异步获取章节标题时发生错误: 书籍ID='" + bookId + "', 章节ID=" + chapterId, e));
    }
}