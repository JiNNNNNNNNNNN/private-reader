package com.lv.tool.privatereader.storage.cache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.ui.settings.CacheSettings;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 章节预加载服务
 * 在后台预加载后续章节，提升连续阅读体验
 */
@Service(Service.Level.PROJECT)
public final class ChapterPreloader {
    private static final Logger LOG = Logger.getInstance(ChapterPreloader.class);
    private static final int DEFAULT_PRELOAD_COUNT = 50; // 默认预加载50章
    
    private final Project project;
    private final ExecutorService executorService;
    private boolean isPreloading = false;
    
    public ChapterPreloader(Project project) {
        this.project = project;
        // 创建一个单线程执行器，避免过多线程导致服务器压力过大
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ChapterPreloader");
            thread.setDaemon(true); // 设置为守护线程，不阻止JVM退出
            return thread;
        });
    }
    
    /**
     * 预加载指定书籍后续章节
     * @param book 当前阅读的书籍
     * @param currentChapterIndex 当前章节索引
     */
    public void preloadChapters(Book book, int currentChapterIndex) {
        // 检查插件是否启用
        PluginSettings pluginSettings = ApplicationManager.getApplication().getService(PluginSettings.class);
        if (!pluginSettings.isEnabled()) {
            LOG.debug("插件已禁用，不执行预加载");
            return;
        }
        
        // 获取缓存设置
        CacheSettings cacheSettings = project.getService(CacheSettings.class);
        
        // 检查预加载是否启用
        if (!cacheSettings.isEnablePreload()) {
            LOG.debug("预加载功能已禁用，不执行预加载");
            return;
        }
        
        // 避免重复预加载
        if (isPreloading) {
            LOG.debug("已有预加载任务在执行，跳过本次预加载");
            return;
        }
        
        isPreloading = true;
        
        CompletableFuture.runAsync(() -> {
            try {
                LOG.info("开始预加载后续章节，当前章节索引: " + currentChapterIndex);
                
                if (book == null || book.getParser() == null) {
                    LOG.warn("书籍或解析器为空，无法预加载");
                    return;
                }
                
                // 确保书籍关联了项目
                book.setProject(project);
                
                List<NovelParser.Chapter> chapters = book.getCachedChapters();
                if (chapters == null || chapters.isEmpty()) {
                    LOG.warn("章节列表为空，无法预加载");
                    return;
                }
                
                // 获取预加载配置
                int preloadCount = cacheSettings.getPreloadCount();
                int preloadDelay = cacheSettings.getPreloadDelay();
                
                int totalChapters = chapters.size();
                int endIndex = Math.min(currentChapterIndex + preloadCount, totalChapters - 1);
                
                // 预加载后续章节
                for (int i = currentChapterIndex + 1; i <= endIndex; i++) {
                    NovelParser.Chapter chapter = chapters.get(i);
                    if (chapter == null) continue;
                    
                    try {
                        // 检查缓存是否已存在
                        ChapterCacheManager cacheManager = project.getService(ChapterCacheManager.class);
                        String cachedContent = cacheManager.getCachedContent(book.getId(), chapter.url());
                        
                        // 如果缓存不存在或已过期，则获取内容并缓存
                        if (cachedContent == null) {
                            LOG.info("预加载章节: " + chapter.title());
                            String content = book.getParser().parseChapterContent(chapter.url());
                            if (content != null && !content.isEmpty()) {
                                cacheManager.cacheContent(book.getId(), chapter.url(), content);
                                LOG.info("成功预加载并缓存章节: " + chapter.title());
                            }
                        } else {
                            LOG.debug("章节已缓存，跳过预加载: " + chapter.title());
                        }
                        
                        // 短暂暂停，避免请求过于频繁
                        Thread.sleep(preloadDelay);
                    } catch (Exception e) {
                        LOG.warn("预加载章节失败: " + chapter.title() + ", 错误: " + e.getMessage());
                        // 继续预加载下一章，不中断整个过程
                    }
                }
                
                LOG.info("章节预加载完成，预加载范围: " + (currentChapterIndex + 1) + " - " + endIndex);
            } catch (Exception e) {
                LOG.error("章节预加载过程发生错误: " + e.getMessage(), e);
            } finally {
                isPreloading = false;
            }
        }, executorService);
    }
    
    /**
     * 关闭预加载服务
     */
    public void shutdown() {
        executorService.shutdown();
    }
} 