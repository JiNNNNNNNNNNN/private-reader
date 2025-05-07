package com.lv.tool.privatereader.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.async.ReactiveTaskManager;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookProgressData;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * BookService接口的实现类
 * 使用响应式编程处理书籍相关操作
 */
@Service(Service.Level.APP)
public final class BookServiceImpl implements BookService {
    private static final Logger LOG = Logger.getInstance(BookServiceImpl.class);
    private BookRepository bookRepository;
    private ReadingProgressRepository readingProgressRepository;
    private final ReactiveTaskManager reactiveTaskManager;
    private final ReactiveSchedulers reactiveSchedulers;

    // 缓存相关
    private final Map<String, Book> bookCache = new ConcurrentHashMap<>();
    private List<Book> allBooksCache;

    private final Scheduler ioScheduler; // Use Reactor Schedulers

    /**
     * 无参构造方法
     */
    public BookServiceImpl() {
        LOG.info("初始化BookServiceImpl");
        this.reactiveTaskManager = ReactiveTaskManager.getInstance();
        this.reactiveSchedulers = ReactiveSchedulers.getInstance();
        // 其他服务会在首次使用时延迟初始化
        // Use Reactor Schedulers for IO tasks
        this.ioScheduler = Schedulers.boundedElastic(); // Suitable for blocking IO
    }

    /**
     * 确保服务已初始化
     */
    private void ensureServicesInitialized() {
        if (bookRepository == null) {
            bookRepository = ApplicationManager.getApplication().getService(BookRepository.class);
        }

        if (readingProgressRepository == null) {
            readingProgressRepository = ApplicationManager.getApplication().getService(ReadingProgressRepository.class);
        }

        // ReadingProgressManager initialization removed as the class is deleted
    }

    /**
     * Helper method to combine book metadata with its progress data.
     * Updates the provided bookMetadata object with values from progressDataOpt.
     *
     * @param bookMetadata The book object containing metadata.
     * @param progressDataOpt Optional containing the progress data.
     * @return The updated bookMetadata object.
     */
    private Book combineBookAndProgress(Book bookMetadata, @NotNull Optional<BookProgressData> progressDataOpt) {
        progressDataOpt.ifPresent(progress -> {
            bookMetadata.setLastReadChapterId(progress.lastReadChapterId());
            bookMetadata.setLastReadChapter(progress.lastReadChapterTitle()); // Assuming Book has this setter
            bookMetadata.setLastReadPosition(progress.lastReadPosition());
            bookMetadata.setLastReadPage(progress.lastReadPage()); // Assuming Book has this setter
            bookMetadata.setFinished(progress.isFinished());
            bookMetadata.setLastReadTimeMillis(progress.lastReadTimestamp());
        });
        return bookMetadata; // Return the modified book metadata
    }

    // Helper to wrap blocking calls in Mono/Flux on IO scheduler
    private <T> Mono<T> asyncMono(Callable<T> blockingCall) {
        return Mono.fromCallable(blockingCall).subscribeOn(ioScheduler);
    }

    private <T> Flux<T> asyncFlux(Supplier<List<T>> blockingCall) {
        return Mono.fromSupplier(blockingCall)
                   .subscribeOn(ioScheduler)
                   .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<Book> getAllBooks() {
        // Defer ensures initialization happens on subscription
        return Flux.defer(() -> {
            ensureServicesInitialized();
            LOG.debug("Getting all books");

            // Use helper to get metadata asynchronously
            return asyncFlux(() -> bookRepository.getAllBooks()) // Get List<Book> metadata
                .flatMap(bookMeta ->
                    // For each book metadata, get progress asynchronously
                    asyncMono(() -> readingProgressRepository.getProgress(bookMeta.getId()))
                        .map(progressOpt -> combineBookAndProgress(bookMeta, progressOpt))
                )
                .sort(Comparator.comparing(Book::getTitle)); // Sort alphabetically by title
        }); //.subscribeOn(ioScheduler); // Defer handles subscription context
    }

    @Override
    public Mono<Book> getBookById(@NotNull String bookId) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.debug("Getting book by ID: " + bookId);

            // Get metadata and progress asynchronously
            Mono<Book> bookMetadataMono = asyncMono(() -> bookRepository.getBook(bookId));
            Mono<Optional<BookProgressData>> progressDataMono = asyncMono(() -> readingProgressRepository.getProgress(bookId));

            // Combine results when both are available
            return Mono.zip(bookMetadataMono, progressDataMono)
                    .flatMap(tuple -> {
                        Book bookMeta = tuple.getT1();
                        Optional<BookProgressData> progressOpt = tuple.getT2();
                        if (bookMeta != null) {
                            return Mono.just(combineBookAndProgress(bookMeta, progressOpt));
                        } else {
                            LOG.warn("No book metadata found for ID: " + bookId);
                            return Mono.empty(); // Return empty Mono if metadata not found
                        }
                    });
        });
    }

    @Override
    public Mono<Boolean> addBook(@NotNull Book book) {
        // Defer ensures initialization happens on subscription
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("Adding book: " + book.getTitle());
            // Run the blocking add operation on the IO scheduler
            return asyncMono(() -> {
                bookRepository.addBook(book);
                return true; // Return true on success
            })
            .doOnSuccess(success -> {
                if (success) invalidateCache();
            })
            .onErrorResume(e -> {
                LOG.error("Error adding book metadata: " + book.getTitle(), e);
                return Mono.just(false);
            });
        });
    }

    @Override
    public Mono<Boolean> removeBook(@NotNull Book book) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            String bookId = book.getId();
            LOG.info("Removing book: " + book.getTitle() + " (ID: " + bookId + ")");

            // Create Monos for removing metadata and progress, run on IO scheduler
            Mono<Void> removeMetaMono = Mono.fromRunnable(() -> bookRepository.removeBook(book)).subscribeOn(ioScheduler).then();
            Mono<Void> removeProgressMono = Mono.fromRunnable(() -> readingProgressRepository.resetProgress(book)).subscribeOn(ioScheduler).then();

            // Run both in parallel
            return Mono.when(removeMetaMono, removeProgressMono)
                    .then(Mono.just(true)) // Return true if both complete successfully
                    .doOnSuccess(success -> {
                        if (success) {
                            bookCache.remove(bookId);
                            invalidateCache();
                        }
                    })
                    .onErrorResume(e -> {
                        LOG.error("Error removing book: " + book.getTitle(), e);
                        return Mono.just(false);
                    });
        });
    }

    @Override
    public Mono<Boolean> updateBook(@NotNull Book book) {
        // This method primarily updates metadata via FileBookRepository.
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("Updating book metadata: " + book.getTitle());
            // Run the blocking update on the IO scheduler
            return asyncMono(() -> {
                bookRepository.updateBook(book); // Updates the JSON file
                return true;
            })
            .doOnSuccess(success -> {
                if (success) {
                    // Invalidate cache - simple approach for now
                    bookCache.remove(book.getId());
                    invalidateCache();
                }
            })
            .onErrorResume(e -> {
                LOG.error("Error updating book metadata: " + book.getTitle(), e);
                return Mono.just(false);
            });
        });
    }

    @Override
    public Mono<Book> getLastReadBook() {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.debug("Getting last read book from progress repository");
            // Get the latest progress entry asynchronously
            return asyncMono(readingProgressRepository::getLastReadProgressData)
                .flatMap(progressDataOpt -> {
                    if (progressDataOpt.isPresent()) {
                        String lastReadBookId = progressDataOpt.get().bookId();
                        LOG.debug("Last read book ID from progress: " + lastReadBookId);
                        // Fetch the corresponding book metadata and combine
                        return getBookById(lastReadBookId);
                    } else {
                        LOG.info("No reading progress found. Falling back to first book alphabetically.");
                        // Fall back to the first book alphabetically
                        return getAllBooks()
                               .sort(Comparator.comparing(Book::getTitle))
                               .next(); // Get the first element after sorting
                    }
                });
        });
    }

    @Override
    public Mono<Void> saveReadingProgress(@NotNull Book book, @NotNull String chapterId, String chapterTitle, int position) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.debug(String.format("Service saving progress: Book=%s, ChapterID=%s, Pos=%d, Finished=%b",
                    book.getId(), chapterId, position, book.isFinished()));

            // Update the book object in memory immediately
            final long timestamp = System.currentTimeMillis();
            // 使用传入的position参数作为页码（通知栏模式下）
            // 注意：position是0基索引，页码应该是1基索引，所以加1
            final int page = position + 1;

            // 记录详细日志，用于调试
            LOG.info(String.format("[页码调试] 保存阅读进度: 书籍=%s, 章节=%s, 位置=%d, 计算页码=%d, 原页码=%d",
                    book.getId(), chapterId, position, page, book.getLastReadPage()));

            book.setLastReadChapterId(chapterId);
            book.setLastReadChapter(chapterTitle);
            book.setLastReadPosition(position);
            book.setLastReadTimeMillis(timestamp);
            book.setLastReadPage(page); // 使用计算出的页码更新book对象

            // Run the blocking repository update on the IO scheduler
            return Mono.fromRunnable(() -> {
                readingProgressRepository.updateProgress(book, chapterId, chapterTitle, position, page);
            })
            .subscribeOn(ioScheduler)
            .then() // Converts completion signal to Mono<Void>
            .doOnSuccess(v -> {
                // Update cache with the book containing latest progress
                bookCache.put(book.getId(), book);
                LOG.info(String.format("[页码调试] 阅读进度保存成功: 书籍=%s, 章节=%s, 位置=%d, 页码=%d",
                        book.getId(), chapterId, position, page));
            })
            .doOnError(e -> LOG.error("Error saving progress in service for book: " + book.getId(), e));
        });
    }

    // --- Cache Invalidation --- //
    private void invalidateCache() {
        LOG.debug("Invalidating book cache");
        bookCache.clear();
        allBooksCache = null;
    }

    @Override
    @Nullable
    public List<ChapterService.EnhancedChapter> getChaptersSync(@NotNull String bookId) {
        ensureServicesInitialized();
        LOG.debug("同步获取书籍章节列表: " + bookId);

        try {
            // 获取书籍对象
            Book book = bookRepository.getBook(bookId);
            if (book == null) {
                LOG.warn("书籍不存在: " + bookId);
                return null;
            }

            // 获取章节服务
            ChapterService chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
            if (chapterService == null) {
                LOG.error("无法获取章节服务");
                return null;
            }

            // 获取章节列表
            List<com.lv.tool.privatereader.parser.NovelParser.Chapter> chapters =
                chapterService.getChapterList(book).block(); // 阻塞操作

            if (chapters == null || chapters.isEmpty()) {
                LOG.warn("书籍章节列表为空: " + bookId);
                return List.of(); // 返回空列表
            }

            // 转换为增强章节列表
            return chapters.stream()
                .map(chapter -> {
                    String content = chapterService.getChapterContent(bookId, chapter.url());
                    return new ChapterService.EnhancedChapter(chapter.title(), chapter.url(), content);
                })
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            LOG.error("获取书籍章节列表时出错: " + bookId, e);
            return null;
        }
    }
}