package com.lv.tool.privatereader.service.impl;

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

    // 缓存相关
    private final Map<String, List<Chapter>> bookChapterListCache = new ConcurrentHashMap<>();
    private final Map<String, Mono<List<Chapter>>> chapterListMonoCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> preloadingChapters = new ConcurrentHashMap<>();

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
        LOG.info("获取章节列表: 书籍='" + book.getTitle() + "' (网络优先策略)");

        // 1. 网络优先：总是先尝试从网络获取最新章节列表
        return Mono.fromCallable(() -> {
                    NovelParser parser = book.getParser();
                    if (parser == null) {
                        LOG.error("书籍 '" + book.getTitle() + "' 的 NovelParser 未初始化", new Throwable());
                        // 抛出特定异常，以便在 onErrorResume 中处理
                        throw new IllegalStateException("Parser not initialized for book: " + book.getTitle());
                    }
                    // 直接调用 parseChapterList，绕过 getChapterList 中陈旧的缓存逻辑
                    return parser.parseChapterList();
                })
                .subscribeOn(reactiveSchedulers.io())
                .flatMap(chaptersFromNetwork -> {
                    // 2. 成功获取后，验证并更新所有缓存
                    if (chaptersFromNetwork != null && !chaptersFromNetwork.isEmpty()) {
                        LOG.info("从网络成功获取 " + chaptersFromNetwork.size() + " 个章节 for '" + book.getTitle() + "'. 更新所有缓存.");
                        // 更新内存缓存
                        bookChapterListCache.put(bookId, chaptersFromNetwork);
                        // 更新 Book 对象自身的持久化缓存
                        book.setCachedChapters(chaptersFromNetwork);
                        return Mono.just(chaptersFromNetwork);
                    } else {
                        LOG.warn("网络获取的章节列表为空 for '" + book.getTitle() + "'. 将尝试使用旧缓存.");
                        // 返回一个空的 Mono，触发 onErrorResume 中的回退逻辑
                        return Mono.empty();
                    }
                })
                // 3. 只有在网络失败或返回空列表时，才回退到旧缓存
                .onErrorResume(error -> {
                    LOG.warn("网络获取章节列表失败 for '" + book.getTitle() + "': " + error.getMessage() + ". 正在回退到旧缓存.");
                    return fallbackToCache(book);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 当网络成功但返回空列表时，也回退到旧缓存
                    LOG.warn("网络获取成功但列表为空 for '" + book.getTitle() + "'. 正在回退到旧缓存.");
                    return fallbackToCache(book);
                }));
    }

    /**
     * 回退到缓存的辅助方法
     */
    private Mono<List<Chapter>> fallbackToCache(Book book) {
        // 优先检查内存缓存
        List<Chapter> cachedChapters = bookChapterListCache.get(book.getId());
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
    public Flux<Chapter> preloadChapters(@NotNull Book book, @NotNull String currentChapterId, int count) {
         // 使用 flatMapMany 确保服务初始化后再执行 Flux 相关逻辑
         return getInitializedChapterCacheRepository().flatMapMany(repo -> {
             LOG.info(String.format("预加载章节元数据: 书籍='%s', 当前ID=%s, 数量=%d", book.getTitle(), currentChapterId, count));

             // 调用已修改的 getChapterList
             return Mono.fromCallable(() -> bookChapterListCache.get(book.getId()))
                 .flatMapMany(chapters -> {
                     if (chapters == null || chapters.isEmpty()) {
                         LOG.warn("预加载失败：章节列表尚未缓存 for book: " + book.getTitle());
                         return Flux.empty();
                     }

                     AtomicInteger currentIndex = new AtomicInteger(-1);
                     IntStream.range(0, chapters.size())
                         .filter(i -> currentChapterId.equals(chapters.get(i).url()))
                         .findFirst()
                         .ifPresent(currentIndex::set);

                     if (currentIndex.get() == -1) {
                         LOG.warn("预加载失败：当前章节 ID '" + currentChapterId + "' 在列表中未找到");
                         return Flux.empty();
                     }

                     int startIndex = currentIndex.get() + 1;
                     int endIndex = Math.min(startIndex + count, chapters.size());

                     if (startIndex >= endIndex) {
                         LOG.info("没有更多章节可预加载");
                         return Flux.empty();
                     }

                     LOG.debug(String.format("预加载章节索引范围 [%d..%d]", startIndex, endIndex - 1));
                     List<Chapter> chaptersToPreload = chapters.subList(startIndex, endIndex);

                     return Flux.fromIterable(chaptersToPreload)
                         .doOnNext(chapter -> {
                             String taskKey = book.getId() + "::" + chapter.url();
                             if (preloadingChapters.putIfAbsent(taskKey, true) == null) {
                                 reactiveTaskManager.submitTask(
                                     "cache-chapter-" + taskKey,
                                     () -> {
                                         try {
                                             NovelParser parser = book.getParser();
                                             if (parser != null) {
                                                 String cachedContent = repo.getCachedContent(book.getId(), chapter.url());
                                                 if (cachedContent == null) {
                                                     LOG.debug("后台缓存章节: " + chapter.title());
                                                     String content = parser.parseChapterContent(chapter.url());
                                                     if (content != null) {
                                                         repo.cacheContent(book.getId(), chapter.url(), content);
                                                     }
                                                 } else {
                                                     LOG.trace("章节已在缓存中，跳过后台缓存: " + chapter.title());
                                                 }
                                             }
                                         } catch (Exception e) {
                                             LOG.error("后台预加载章节内容失败: 书籍='" + book.getTitle() + "', 章节='" + chapter.title() + "'", e);
                                         } finally {
                                             preloadingChapters.remove(taskKey);
                                         }
                                         return null;
                                     }
                                 );
                             } else {
                                 LOG.debug("章节正在预加载中，跳过重复任务: " + chapter.title());
                             }
                         });
                 });
            // 不再需要 subscribeOn(io)，因为 getChapterList 和 submitTask 内部会处理
         });
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
                bookChapterListCache.remove(bookId);
                chapterListMonoCache.remove(bookId);
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
                 bookChapterListCache.clear();
                 chapterListMonoCache.clear();
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