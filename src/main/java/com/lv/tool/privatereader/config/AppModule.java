package com.lv.tool.privatereader.config;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ChapterCacheRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.repository.StorageRepository;
import com.lv.tool.privatereader.repository.impl.FileBookRepository;
import com.lv.tool.privatereader.repository.impl.FileChapterCacheRepository;
import com.lv.tool.privatereader.repository.impl.SqliteReadingProgressRepository;
import com.lv.tool.privatereader.repository.impl.FileStorageRepository;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.service.impl.BookServiceImpl;
import com.lv.tool.privatereader.service.impl.ChapterServiceImpl;
import com.lv.tool.privatereader.service.impl.NotificationServiceImpl;
import com.intellij.openapi.diagnostic.Logger;

/**
 * 应用程序依赖注入模块
 * 
 * 使用Guice框架配置应用程序的依赖注入关系
 * 所有服务和仓库都被配置为应用级别的单例（@Singleton），
 * 与IntelliJ平台的Service.Level.APP等效，确保在整个IDE中共享同一个实例
 */
public class AppModule extends AbstractModule {
    private static final Logger LOG = Logger.getInstance(AppModule.class);
    
    @Override
    protected void configure() {
        LOG.info("配置应用级别的依赖注入关系");
        
        try {
            // 绑定Repository接口到实现类，并设置为应用级别单例
            bind(StorageRepository.class).to(FileStorageRepository.class).in(Singleton.class);
            bind(BookRepository.class).to(FileBookRepository.class).in(Singleton.class);
            bind(ReadingProgressRepository.class).to(SqliteReadingProgressRepository.class).in(Singleton.class);
            bind(ChapterCacheRepository.class).to(FileChapterCacheRepository.class).in(Singleton.class);
            
            // 绑定Service接口到实现类，并设置为应用级别单例
            bind(BookService.class).to(BookServiceImpl.class).in(Singleton.class);
            bind(ChapterService.class).to(ChapterServiceImpl.class).in(Singleton.class);
            
            // 注册NotificationService，使用Provider以避免直接依赖于构造函数
            bind(NotificationService.class).toProvider(() -> {
                try {
                    BookService bookService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(BookService.class);
                    ChapterService chapterService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(ChapterService.class);
                    
                    if (bookService == null || chapterService == null) {
                        LOG.error("无法获取BookService或ChapterService，将尝试使用Guice");
                        // 如果从ApplicationManager获取失败，尝试从Guice获取
                        bookService = getInstance(BookService.class);
                        chapterService = getInstance(ChapterService.class); 
                    }
                    
                    return new NotificationServiceImpl();
                } catch (Exception e) {
                    LOG.error("创建NotificationServiceImpl失败", e);
                    throw new RuntimeException("创建NotificationServiceImpl失败", e);
                }
            }).in(Singleton.class);
            
            LOG.info("应用级别的依赖注入关系配置完成");
        } catch (Exception e) {
            LOG.error("配置Guice绑定关系时发生错误", e);
        }
    }

    /**
     * 从Guice获取实例的辅助方法
     */
    private <T> T getInstance(Class<T> clazz) {
        try {
            return getProvider(clazz).get();
        } catch (Exception e) {
            LOG.error("从Guice获取" + clazz.getSimpleName() + "实例失败", e);
            return null;
        }
    }
} 