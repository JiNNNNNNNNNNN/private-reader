package com.lv.tool.privatereader.reader;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.components.Service;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.async.ReactiveTaskManager;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 章节预加载器
 * 使用响应式编程预加载章节内容，提高阅读体验 (Note: Name might be misleading now)
 */
@Service
public final class ReactiveChapterPreloader {
    private static final Logger LOG = Logger.getInstance(ReactiveChapterPreloader.class);
    private static final int DEFAULT_PRELOAD_COUNT = 3;
    
    private ChapterService chapterService;
    private final ReactiveTaskManager taskManager;
    private final ConcurrentHashMap<String, AtomicBoolean> preloadingFlags;
    
    public ReactiveChapterPreloader() {
        this.taskManager = ReactiveTaskManager.getInstance();
        this.preloadingFlags = new ConcurrentHashMap<>();
        LOG.info("初始化 ReactiveChapterPreloader"); // Keep name for now
    }
    
    /**
     * 预加载章节内容
     *
     * @param book 书籍
     * @param currentChapterIndex 当前章节索引
     * @return 预加载操作的Mono
     */
    public Mono<Void> preloadChapters(@NotNull Book book, int currentChapterIndex) { // Rename method to remove Reactive suffix?
        String bookId = book.getId();
        
        // 确保服务已初始化
        ensureServiceInitialized();
        
        // 检查是否已经在预加载
        if (isPreloading(bookId)) {
            LOG.info("已有预加载任务正在进行，跳过: " + bookId);
            return Mono.empty();
        }
        
        // 设置预加载标志
        setPreloading(bookId, true);
        
        // 创建任务选项
        ReactiveTaskManager.TaskOptions options = new ReactiveTaskManager.TaskOptions()
            .setPriority(2)  // 低优先级，后台任务
            .setMaxRetries(1)
            .setRetryDelay(1000)
            .setTimeout(30000)
            .setErrorHandler(e -> {
                LOG.error("预加载章节失败: " + bookId, e);
                setPreloading(bookId, false);
            });
        
        // 提交预加载任务
        return taskManager.submitTask(
            "preload-chapters-" + bookId + "-" + currentChapterIndex,
            () -> preloadChaptersInternal(book, currentChapterIndex), // Supplier<Void>
            options
        ).doFinally(signal -> setPreloading(bookId, false));
    }
    
    /**
     * 确保服务已初始化
     */
    private void ensureServiceInitialized() {
        if (chapterService == null) {
            // Update getService call
            chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
            if (chapterService == null) {
                LOG.error("ChapterService 未能初始化");
            }
        }
    }

    /**
     * 内部预加载逻辑
     */
    private Void preloadChaptersInternal(@NotNull Book book, int currentChapterIndex) {
        LOG.debug("开始内部预加载: " + book.getTitle() + ", 当前索引: " + currentChapterIndex);
        if (chapterService == null) {
            LOG.error("ChapterService 未初始化，无法执行预加载内部逻辑");
            return null; // Return null for Supplier<Void>
        }
        
        try {
            // Get chapter list (blocking call, as this runs in background thread via submitTask)
            List<Chapter> chapters = chapterService.getChapterList(book).block(); 
            if (chapters == null || chapters.isEmpty() || currentChapterIndex < 0 || currentChapterIndex >= chapters.size()) {
                LOG.warn("章节列表为空或索引无效，无法预加载");
                return null;
            }
            
            String currentChapterId = chapters.get(currentChapterIndex).url();
            
            // Call the preloadChapters method on the service (which now returns Flux<Chapter>)
            // We subscribe to trigger the background caching defined within that method.
            chapterService.preloadChapters(book, currentChapterId, DEFAULT_PRELOAD_COUNT)
                .doOnComplete(() -> LOG.debug("预加载流完成 for: " + book.getTitle()))
                .doOnError(e -> LOG.error("预加载流出错 for: " + book.getTitle(), e))
                .subscribe(); // Subscribe to trigger the preloading Flux
                
        } catch (Exception e) {
            LOG.error("预加载内部逻辑失败 for book: " + book.getTitle(), e);
        }
        return null; // Return null for Supplier<Void>
    }

    /**
     * 检查是否正在预加载
     *
     * @param bookId 书籍ID
     * @return 是否正在预加载
     */
    private boolean isPreloading(String bookId) {
        AtomicBoolean flag = preloadingFlags.get(bookId);
        return flag != null && flag.get();
    }

    /**
     * 设置预加载标志
     *
     * @param bookId 书籍ID
     * @param isPreloading 是否正在预加载
     */
    private void setPreloading(String bookId, boolean isPreloading) {
        preloadingFlags.compute(bookId, (key, oldValue) -> {
            if (oldValue == null) {
                return new AtomicBoolean(isPreloading);
            } else {
                oldValue.set(isPreloading);
                return oldValue;
            }
        });
    }
    
    /**
     * 取消预加载
     *
     * @param book 书籍
     * @return 取消操作的Mono
     */
    public Mono<Boolean> cancelPreloading(@NotNull Book book) { // Rename method
        String bookId = book.getId();
        
        if (!isPreloading(bookId)) {
            return Mono.just(false);
        }
        
        // 设置预加载标志为false
        setPreloading(bookId, false);
        
        // 取消相关任务
        return Mono.fromCallable(() -> {
            boolean cancelled = taskManager.cancelTasksByPrefix("preload-chapters-" + bookId);
            LOG.info("取消预加载任务: " + bookId + ", 结果: " + cancelled);
            return cancelled;
        });
    }
    
    /**
     * 关闭预加载器，取消所有任务
     * 在程序关闭时调用，释放资源
     */
    public void shutdown() {
        LOG.info("关闭 ReactiveChapterPreloader，取消所有预加载任务");
        
        // 清空所有预加载标志
        preloadingFlags.forEach((bookId, flag) -> flag.set(false));
        preloadingFlags.clear();
        
        // 取消所有预加载任务
        boolean cancelled = taskManager.cancelTasksByPrefix("preload-chapters-");
        LOG.info("预加载任务取消结果: " + cancelled);
    }
} 