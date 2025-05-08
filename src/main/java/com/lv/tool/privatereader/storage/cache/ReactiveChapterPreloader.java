package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.settings.CacheSettings;
import com.lv.tool.privatereader.settings.PluginSettings;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 响应式章节预加载服务
 * 使用响应式编程在后台同时预加载当前章节前后的章节，提升连续阅读体验
 * 通过并行预加载前后章节，减少阅读等待时间
 */
@Service(Service.Level.APP)
public final class ReactiveChapterPreloader {
    private static final Logger LOG = Logger.getInstance(ReactiveChapterPreloader.class);

    private final AtomicBoolean isPreloading = new AtomicBoolean(false);
    private final AtomicReference<String> currentPreloadingBookId = new AtomicReference<>(null);

    public ReactiveChapterPreloader() {
        LOG.info("初始化响应式章节预加载器");
    }

    /**
     * 停止指定书籍的预加载任务
     * @param bookId 书籍ID
     */
    public void stopPreload(String bookId) {
        if (bookId != null && bookId.equals(currentPreloadingBookId.get())) {
            LOG.info("停止书籍预加载任务: " + bookId);
            isPreloading.set(false);
            currentPreloadingBookId.set(null);
        }
    }

    /**
     * 响应式预加载指定书籍前后章节
     * 同时预加载当前章节前后的章节，提高阅读体验
     * @param book 当前阅读的书籍
     * @param currentChapterIndex 当前章节索引
     * @return 预加载操作的Mono
     */
    public Mono<Void> preloadChaptersReactive(Book book, int currentChapterIndex) {
        return Mono.defer(() -> {
            // 检查插件是否启用
            PluginSettings pluginSettings = ApplicationManager.getApplication().getService(PluginSettings.class);
            if (!pluginSettings.isEnabled()) {
                LOG.debug("插件已禁用，不执行预加载");
                return Mono.empty();
            }

            // 获取缓存设置
            CacheSettings cacheSettings = ApplicationManager.getApplication().getService(CacheSettings.class);

            // 检查预加载是否启用
            if (!cacheSettings.isEnablePreload()) {
                LOG.debug("预加载功能已禁用，不执行预加载");
                return Mono.empty();
            }

            // 避免重复预加载
            if (isPreloading.get()) {
                LOG.debug("已有预加载任务在执行，跳过本次预加载");
                return Mono.empty();
            }

            // 设置预加载状态
            if (!isPreloading.compareAndSet(false, true)) {
                return Mono.empty();
            }
            currentPreloadingBookId.set(book.getId());

            LOG.info("开始响应式预加载前后章节，书籍: " + book.getTitle() + "，当前章节索引: " + currentChapterIndex);

            if (book == null || book.getParser() == null) {
                LOG.warn("书籍或解析器为空，无法预加载");
                resetPreloadingState();
                return Mono.empty();
            }

            List<NovelParser.Chapter> chapters = book.getCachedChapters();
            if (chapters == null || chapters.isEmpty()) {
                LOG.warn("章节列表为空，无法预加载");
                resetPreloadingState();
                return Mono.empty();
            }

            // 获取预加载配置
            int preloadCount = cacheSettings.getPreloadCount();
            int preloadDelay = cacheSettings.getPreloadDelay();

            int totalChapters = chapters.size();

            // 计算后续章节的预加载范围
            int endIndex = Math.min(currentChapterIndex + preloadCount, totalChapters - 1);

            // 计算前面章节的预加载范围
            int startIndex = Math.max(0, currentChapterIndex - preloadCount);

            // 创建前面章节的预加载流
            Flux<Void> prevChaptersFlux = Flux.range(startIndex, currentChapterIndex - startIndex)
                // 检查是否仍在预加载状态
                .takeWhile(i -> isPreloading.get())
                // 获取章节对象
                .map(i -> chapters.get(i))
                // 过滤掉null章节
                .filter(chapter -> chapter != null)
                // 对每个章节进行预加载处理
                .concatMap(chapter -> preloadChapter(book, chapter, preloadDelay));

            // 创建后续章节的预加载流
            Flux<Void> nextChaptersFlux = Flux.range(currentChapterIndex + 1, endIndex - currentChapterIndex)
                // 检查是否仍在预加载状态
                .takeWhile(i -> isPreloading.get())
                // 获取章节对象
                .map(i -> chapters.get(i))
                // 过滤掉null章节
                .filter(chapter -> chapter != null)
                // 对每个章节进行预加载处理
                .concatMap(chapter -> preloadChapter(book, chapter, preloadDelay));

            // 使用merge而不是concat，允许前后章节并行预加载
            return Flux.merge(nextChaptersFlux, prevChaptersFlux)
                // 使用弹性线程池执行
                .subscribeOn(Schedulers.boundedElastic())
                // 完成后记录日志
                .doOnComplete(() -> LOG.info("章节预加载完成，书籍: " + book.getTitle() + "，预加载范围: 前面(" + startIndex + " - " + (currentChapterIndex - 1) + "), 后面(" + (currentChapterIndex + 1) + " - " + endIndex + ")"))
                // 错误处理
                .doOnError(e -> LOG.error("章节预加载过程发生错误: " + e.getMessage(), e))
                // 无论成功失败都重置状态
                .doFinally(signal -> resetPreloadingState())
                // 转换为Mono<Void>
                .then();
        });
    }

    /**
     * 预加载单个章节
     * 检查缓存是否存在，如不存在则获取内容并缓存
     * @param book 书籍
     * @param chapter 章节
     * @param delayMs 延迟毫秒数，避免请求过于频繁
     * @return 预加载操作的Mono
     */
    private Mono<Void> preloadChapter(Book book, NovelParser.Chapter chapter, int delayMs) {
        return Mono.defer(() -> {
            // 检查缓存是否已存在
            ChapterCacheManager cacheManager = ApplicationManager.getApplication().getService(ChapterCacheManager.class);
            String cachedContent = cacheManager.getCachedContent(book.getId(), chapter.url());

            // 如果缓存已存在，跳过预加载
            if (cachedContent != null) {
                LOG.debug("章节已缓存，跳过预加载: " + chapter.title());
                return Mono.empty();
            }

            // 预加载章节内容
            return Mono.fromCallable(() -> {
                LOG.info("预加载章节: " + chapter.title() + "，书籍: " + book.getTitle());
                return book.getParser().parseChapterContent(chapter.url());
            })
            // 过滤掉空内容
            .filter(content -> content != null && !content.isEmpty())
            // 缓存内容
            .doOnNext(content -> {
                cacheManager.cacheContent(book.getId(), chapter.url(), content);
                LOG.info("成功预加载并缓存章节: " + chapter.title() + "，书籍: " + book.getTitle() + "，内容长度: " + content.length());
            })
            // 添加延迟，避免请求过于频繁
            .delayElement(Duration.ofMillis(delayMs))
            // 错误处理
            .onErrorResume(e -> {
                LOG.warn("预加载章节失败: " + chapter.title() + "，书籍: " + book.getTitle() + ", 错误: " + e.getMessage());
                return Mono.empty();
            })
            // 转换为Mono<Void>
            .then();
        });
    }

    /**
     * 重置预加载状态
     */
    private void resetPreloadingState() {
        isPreloading.set(false);
        currentPreloadingBookId.set(null);
    }

    /**
     * 预加载指定书籍前后章节（兼容旧API）
     * 同时预加载当前章节前后的章节，提高阅读体验
     * @param book 当前阅读的书籍
     * @param currentChapterIndex 当前章节索引
     */
    public void preloadChapters(Book book, int currentChapterIndex) {
        preloadChaptersReactive(book, currentChapterIndex)
            .subscribe();
    }
}