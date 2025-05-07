package com.lv.tool.privatereader.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
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
     */
    private void ensureServicesInitialized() {
        // Ensure ChapterCacheRepository is initialized via Mono
        if (cachedRepoMono == null) {
            getInitializedChapterCacheRepository().subscribe(); // Trigger initialization if not already done
        }
        // Initialize BookRepository directly if needed
        if (bookRepository == null) {
            bookRepository = ApplicationManager.getApplication().getService(BookRepository.class);
            if (bookRepository == null) {
                LOG.error("BookRepository 服务无法初始化!", new IllegalStateException("BookRepository service is unavailable."));
                // Consider throwing or handling the error appropriately
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
                    LOG.debug("首次初始化 ChapterCacheRepository Mono");
                    result = Mono.fromCallable(() -> {
                                LOG.debug("尝试在 platformThread 上获取 ChapterCacheRepository 服务");
                                ChapterCacheRepository service = ApplicationManager.getApplication().getService(ChapterCacheRepository.class);
                                if (service == null) {
                                    LOG.error("ChapterCacheRepository 服务无法初始化!");
                                    throw new IllegalStateException("ChapterCacheRepository service is unavailable.");
                                }
                                LOG.debug("成功获取 ChapterCacheRepository 服务");
                                // 成功获取后，直接赋值给成员变量，后续可直接使用
                                // 但注意，首次调用仍然需要通过 Mono 完成
                                this.chapterCacheRepository = service;
                                return service;
                            })
                            .subscribeOn(reactiveSchedulers.platformThread()) // 在平台线程池执行 getService
                            .cache(); // 缓存结果 Mono
                    cachedRepoMono = result;
                }
            }
        }
        return result;
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
                    LOG.warn("章节未找到，尝试回退到第一章: 章节ID=" + chapterId);
                    // 确保这里的 getChapterList 也是异步感知服务的
                    return getChapterList(book)
                            .filter(list -> !list.isEmpty())
                            .map(list -> list.get(0)); // map 返回 Chapter，Mono<Chapter>
                }));
                // 不再需要 subscribeOn(io)
        }).doOnError(e -> {
            // 将错误日志移到 Mono 链的末端
            LOG.error("获取章节元数据(带回退)失败: 书籍='" + book.getTitle() + "', 章节ID=" + chapterId, e);
        });
    }

    @Override
    public Mono<List<Chapter>> getChapterList(@NotNull Book book) {
        // 使用 flatMap 确保服务初始化
        return getInitializedChapterCacheRepository().flatMap(repo -> {
            ensureServicesInitialized();
            LOG.info("获取章节列表: 书籍='" + book.getTitle() + "'");

            String cacheKey = book.getId();
            List<Chapter> cachedChapters = bookChapterListCache.get(cacheKey);

            if (cachedChapters != null) {
                LOG.info("从内存缓存获取章节列表 for book: '" + book.getTitle() + "'");
                return Mono.just(cachedChapters);
            }

            // 内存缓存未命中 for book: '{}', 尝试从本地存储加载...
            LOG.debug("内存缓存未命中 for book: '" + book.getTitle() + "', 尝试从本地存储加载...");
            return Mono.fromCallable(() -> {
                        if (bookRepository == null) {
                             LOG.warn("BookRepository 未初始化，无法从本地加载章节列表 for book: '" + book.getTitle() + "'");
                             return null; // Indicate failure to load locally
                        }
                        // Attempt to get the book details from the repository (which should load from file if not cached there)
                        return bookRepository.getBook(book.getId());
                    })
                    .subscribeOn(reactiveSchedulers.io()) // Perform file I/O on IO thread
                    .flatMap(loadedBookFromLocal -> {
                        if (loadedBookFromLocal != null && loadedBookFromLocal.getCachedChapters() != null && !loadedBookFromLocal.getCachedChapters().isEmpty()) {
                            List<Chapter> chaptersFromLocal = loadedBookFromLocal.getCachedChapters();
                            LOG.info("从本地文件加载了 " + chaptersFromLocal.size() + " 个章节 for book: '" + book.getTitle() + "'");
                            bookChapterListCache.put(cacheKey, chaptersFromLocal); // Update memory cache
                            return Mono.just(chaptersFromLocal);
                        } else {
                            LOG.debug("本地文件未找到或无章节列表 for book: '" + book.getTitle() + "', 准备从网络获取.");
                            return Mono.empty(); // Signal local load failure to trigger network fallback
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> { // Fallback to network if local load fails
                         LOG.debug("开始通过 Parser 从网络获取章节列表: 书籍='" + book.getTitle() + "'");
                         // 将解析器调用放入 Mono.fromCallable
                         return Mono.fromCallable(() -> {
                             NovelParser parser = book.getParser();
                             if (parser == null) {
                                 LOG.error("书籍 '" + book.getTitle() + "' 的 NovelParser 未初始化", new Throwable());
                                 return List.<Chapter>of();
                             }

                             List<Chapter> chaptersFromNetwork = parser.getChapterList(book);
                             if (chaptersFromNetwork != null && !chaptersFromNetwork.isEmpty()) {
                                 bookChapterListCache.put(cacheKey, chaptersFromNetwork);
                                 LOG.info("从网络获取并缓存了 " + chaptersFromNetwork.size() + " 个章节 for '" + book.getTitle() + "'");
                             } else {
                                 LOG.warn("Parser 为书籍 '" + book.getTitle() + "' 返回了空的章节列表", new Throwable());
                                 chaptersFromNetwork = List.of();
                             }
                             return chaptersFromNetwork;
                         })
                         .subscribeOn(reactiveSchedulers.io()); // IO密集操作在io调度器执行
                    }));
        });
    }

    @Override
    public Flux<Chapter> preloadChapters(@NotNull Book book, @NotNull String currentChapterId, int count) {
         // 使用 flatMapMany 确保服务初始化后再执行 Flux 相关逻辑
         return getInitializedChapterCacheRepository().flatMapMany(repo -> {
             LOG.info(String.format("预加载章节元数据: 书籍='%s', 当前ID=%s, 数量=%d", book.getTitle(), currentChapterId, count));

             // 调用已修改的 getChapterList
             return getChapterList(book)
                 .flatMapMany(chapters -> {
                     if (chapters.isEmpty()) {
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
                             reactiveTaskManager.submitTask(
                                 "cache-chapter-" + book.getId() + "-" + chapter.url(),
                                 () -> {
                                     try {
                                         NovelParser parser = book.getParser();
                                         // repo 已经通过 flatMapMany 确保非 null
                                         // if (parser != null && chapterCacheRepository != null) {
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
                                     }
                                     return null;
                                 }
                             );
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
    public String getChapterContent(@NotNull String bookId, @NotNull String chapterId) {
        ensureServicesInitialized();
        LOG.info("同步获取章节内容: 书籍ID='" + bookId + "', 章节ID=" + chapterId);
        // This is a blocking call and should be used with caution, ideally not on the UI thread.
        // It blocks on the reactive flow to get the chapter content.
        try {
            Book book = bookRepository.getBook(bookId); // Assuming bookRepository.getBook is synchronous or cached
            if (book == null) {
                LOG.warn("同步获取章节内容失败: 未找到书籍ID='" + bookId + "'");
                return "Error: Book not found.";
            }
            // Block on the Mono to get the Chapter object, then get content
            Chapter chapter = getChapter(book, chapterId).block();
            if (chapter instanceof EnhancedChapter) {
                return ((EnhancedChapter) chapter).getContent();
            } else if (chapter != null) {
                // 如果是普通的Chapter对象，尝试使用NovelParser获取内容
                LOG.info("获取到普通Chapter对象，尝试使用NovelParser获取内容: 书籍='" + book.getTitle() + "', 章节='" + chapter.title() + "'");

                try {
                    // 尝试从缓存仓库获取内容
                    if (chapterCacheRepository != null) {
                        String cachedContent = chapterCacheRepository.getCachedContent(bookId, chapterId);
                        if (cachedContent != null && !cachedContent.isEmpty()) {
                            LOG.info("从缓存获取到章节内容: 书籍='" + book.getTitle() + "', 章节='" + chapter.title() + "'");
                            return cachedContent;
                        }
                    }

                    // 缓存中没有，尝试使用解析器获取
                    NovelParser parser = book.getParser();
                    if (parser != null) {
                        LOG.info("尝试使用解析器获取章节内容: 书籍='" + book.getTitle() + "', 章节='" + chapter.title() + "'");
                        String content = parser.parseChapterContent(chapterId);

                        // 如果成功获取内容，缓存它
                        if (content != null && !content.isEmpty() && chapterCacheRepository != null) {
                            chapterCacheRepository.cacheContent(bookId, chapterId, content);
                            LOG.info("成功获取并缓存章节内容: 书籍='" + book.getTitle() + "', 章节='" + chapter.title() + "'");
                        }

                        return content != null && !content.isEmpty() ?
                               content :
                               "章节内容为空，请检查网络连接或刷新章节列表。";
                    } else {
                        LOG.warn("无法获取章节内容: 书籍='" + book.getTitle() + "' 的解析器为空");
                        return "Error: Parser not available for this book.";
                    }
                } catch (Exception e) {
                    LOG.error("尝试获取章节内容时发生错误: 书籍='" + book.getTitle() + "', 章节='" + chapter.title() + "'", e);
                    return "获取章节内容失败: " + e.getMessage();
                }
            } else {
                 LOG.warn("同步获取章节内容失败: 未找到章节ID='" + chapterId + "' for bookID='" + bookId + "'");
                 return "Error: Chapter not found.";
            }
        } catch (Exception e) {
            LOG.error("同步获取章节内容时发生错误: 书籍ID='" + bookId + "', 章节ID=" + chapterId, e);
            return "Error retrieving chapter content: " + e.getMessage();
        }
    }

    @Override
    public String getChapterTitle(@NotNull String bookId, @NotNull String chapterId) {
        ensureServicesInitialized();
        LOG.info("同步获取章节标题: 书籍ID='" + bookId + "', 章节ID=" + chapterId);
         // This is a blocking call and should be used with caution, ideally not on the UI thread.
        // It blocks on the reactive flow to get the chapter title.
        try {
            Book book = bookRepository.getBook(bookId); // Assuming bookRepository.getBook is synchronous or cached
             if (book == null) {
                LOG.warn("同步获取章节标题失败: 未找到书籍ID='" + bookId + "'");
                return "Error: Book not found.";
            }
            // Block on the Mono to get the Chapter object, then get title
            Chapter chapter = getChapter(book, chapterId).block();
            if (chapter != null) {
                return chapter.title();
            } else {
                 LOG.warn("同步获取章节标题失败: 未找到章节ID='" + chapterId + "' for bookID='" + bookId + "'");
                 return "Error: Chapter not found.";
            }
        } catch (Exception e) {
            LOG.error("同步获取章节标题时发生错误: 书籍ID='" + bookId + "', 章节ID=" + chapterId, e);
            return "Error retrieving chapter title: " + e.getMessage();
        }
    }
}