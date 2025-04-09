package com.lv.tool.privatereader.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.async.ReactiveTaskManager;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Comparator;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BookService接口的实现类
 * 使用响应式编程处理书籍相关操作
 */
@Service
public final class BookServiceImpl implements BookService {
    private static final Logger LOG = Logger.getInstance(BookServiceImpl.class);
    private BookRepository bookRepository;
    private ReadingProgressRepository readingProgressRepository;
    private ReadingProgressManager readingProgressManager;
    private final ReactiveTaskManager reactiveTaskManager;
    private final ReactiveSchedulers reactiveSchedulers;
    
    // 缓存相关
    private final Map<String, Book> bookCache = new ConcurrentHashMap<>();
    private List<Book> allBooksCache;

    /**
     * 无参构造方法
     */
    public BookServiceImpl() {
        LOG.info("初始化BookServiceImpl");
        this.reactiveTaskManager = ReactiveTaskManager.getInstance();
        this.reactiveSchedulers = ReactiveSchedulers.getInstance();
        // 其他服务会在首次使用时延迟初始化
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
        
        // 初始化 readingProgressManager
        if (readingProgressManager == null) {
            readingProgressManager = ApplicationManager.getApplication().getService(ReadingProgressManager.class);
            if (readingProgressManager == null) {
                 LOG.error("无法获取 ReadingProgressManager 服务实例");
                 // 可以考虑抛出异常或采取其他错误处理措施
            }
        }
    }

    @Override
    public Flux<Book> getAllBooks() {
        return Flux.defer(() -> {
            ensureServicesInitialized();
            LOG.info("获取所有书籍");
            
            if (allBooksCache != null) {
                LOG.info("从缓存获取所有书籍");
                return Flux.fromIterable(allBooksCache);
            }
            
            return Mono.fromCallable(() -> {
                if (bookRepository == null) {
                    LOG.error("BookRepository服务未初始化");
                    return List.<Book>of();
                }
                
                List<Book> books = bookRepository.getAllBooks();
                if (books != null) {
                    allBooksCache = books;
                    books.forEach(book -> bookCache.put(book.getId(), book));
                }
                return books != null ? books : List.<Book>of();
            })
            .flatMapMany(Flux::fromIterable)
            .subscribeOn(reactiveSchedulers.io());
        });
    }

    @Override
    public Mono<Boolean> addBook(@NotNull Book book) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("添加书籍: " + book.getTitle());
            
            return Mono.fromCallable(() -> {
                if (bookRepository == null || book == null) {
                    LOG.error("BookRepository 或 book 为 null，无法添加");
                    return false;
                }
                
                try {
                    bookRepository.addBook(book);
                    bookCache.put(book.getId(), book);
                    allBooksCache = null; // 清除所有书籍缓存
                    LOG.info("书籍添加成功: " + book.getTitle());
                    return true;
                } catch (Exception e) {
                    LOG.error("添加书籍时发生错误: " + book.getTitle(), e);
                    return false;
                }
            })
            .subscribeOn(reactiveSchedulers.io());
        });
    }

    @Override
    public Mono<Boolean> removeBook(@NotNull Book book) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("删除书籍: " + book.getTitle());
            
            return Mono.fromCallable(() -> {
                if (bookRepository == null || book == null) {
                    LOG.error("BookRepository 或 book 为 null，无法删除");
                    return false;
                }
                
                try {
                    bookRepository.removeBook(book);
                    bookCache.remove(book.getId());
                    allBooksCache = null; // 清除所有书籍缓存
                    LOG.info("书籍删除成功: " + book.getTitle());
                    return true;
                } catch (Exception e) {
                    LOG.error("删除书籍时发生错误: " + book.getTitle(), e);
                    return false;
                }
            })
            .subscribeOn(reactiveSchedulers.io());
        });
    }

    @Override
    public Mono<Boolean> updateBook(@NotNull Book book) {
        return Mono.defer(() -> {
            ensureServicesInitialized();
            LOG.info("更新书籍: " + book.getTitle());
            
            return Mono.fromCallable(() -> {
                if (bookRepository == null || book == null) {
                    LOG.error("BookRepository 或 book 为 null，无法更新");
                    return false;
                }
                
                try {
                    bookRepository.updateBook(book);
                    bookCache.put(book.getId(), book);
                    if (allBooksCache != null) {
                         allBooksCache.removeIf(b -> b.getId().equals(book.getId()));
                         allBooksCache.add(book);
                    }
                   LOG.info("书籍更新成功: " + book.getTitle());
                    return true;
                } catch (Exception e) {
                    LOG.error("更新书籍时发生错误: " + book.getTitle(), e);
                    return false;
                }
            })
            .subscribeOn(reactiveSchedulers.io());
        });
    }

    @Override
    public Mono<Book> getLastReadBook() {
        return getAllBooks()
                .collectList()
                .flatMap(books -> {
                    if (books.isEmpty()) {
                        LOG.info("没有找到任何书籍，无法获取最后阅读的书籍");
                        return Mono.empty();
                    }
                    
                    return Mono.justOrEmpty(books.stream()
                            .filter(book -> book.getLastReadTimeMillis() > 0)
                            .max(Comparator.comparingLong(Book::getLastReadTimeMillis))
                            .orElse(books.get(0))); 
                })
                .doOnSuccess(book -> {
                    if (book != null) {
                         LOG.info("找到最后阅读的书籍: " + book.getTitle() + " (ID: " + book.getId() + ")");
                    } else {
                         LOG.info("未找到有阅读记录的书籍，返回第一本或空");
                    }
                })
                .subscribeOn(reactiveSchedulers.io());
    }

    @Override
    public Mono<Void> saveReadingProgress(@NotNull Book book, @NotNull String chapterId, String chapterTitle, int position) {
        // 使用 readingProgressManager.updateProgress 包装在 Mono 中
        return Mono.fromRunnable(() -> {
            ensureServicesInitialized(); // 确保 manager 已初始化
            if (readingProgressManager != null) {
                 readingProgressManager.updateProgress(book, chapterId, chapterTitle, position);
            } else {
                 LOG.error("ReadingProgressManager 未初始化，无法保存进度 for book: " + book.getId());
                 // 可以考虑抛出异常或返回错误 Mono
            }
        }).subscribeOn(reactiveSchedulers.io()).then(); // 确保在 IO 线程执行并返回 Mono<Void>
    }

    @Override
    public Mono<Book> getBookById(@NotNull String bookId) {
        // 注意：这是一个基于全列表过滤的简单实现，性能可能不高。
        // 如果书籍数量庞大，应考虑更高效的查找方式（例如 Map 或数据库查询）。
        return getAllBooks()
                .filter(book -> book.getId().equals(bookId))
                .next(); // 返回找到的第一个元素或空Mono
    }
} 