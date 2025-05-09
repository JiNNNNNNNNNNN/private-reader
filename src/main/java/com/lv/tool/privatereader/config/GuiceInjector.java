package com.lv.tool.privatereader.config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.repository.*;
import com.lv.tool.privatereader.repository.impl.FileBookRepository;
import com.lv.tool.privatereader.repository.impl.FileChapterCacheRepository;
import com.lv.tool.privatereader.repository.impl.SqliteReadingProgressRepository;
import com.lv.tool.privatereader.repository.impl.FileStorageRepository;
import com.lv.tool.privatereader.service.*;
import com.lv.tool.privatereader.service.impl.*;

/**
 * Guice注入器管理类
 *
 * 负责初始化和管理Guice注入器，提供全局访问点
 * 确保在整个应用程序生命周期中只有一个Guice注入器实例
 * 所有通过此类获取的服务实例都是应用级别的单例
 */
public class GuiceInjector {
    private static final Logger LOG = Logger.getInstance(GuiceInjector.class);
    private static Injector injector;
    private static boolean initialized = false;

    static {
        initialize();
    }

    /**
     * 初始化Guice注入器
     *
     * 此方法应在应用程序启动时调用一次
     * 如果初始化失败，会尝试使用不依赖Project的备用模块
     *
     * @return 是否成功初始化
     */
    public static synchronized boolean initialize() {
        if (initialized) {
            return true;
        }
        try {
            LOG.debug("初始化应用级别的Guice注入器");
            injector = Guice.createInjector(new AppModule());
            initialized = true;
            LOG.debug("应用级别的Guice注入器初始化完成");
            return true;
        } catch (Exception e) {
            LOG.error("初始化Guice注入器失败，尝试使用备用模块", e);

            // 尝试使用备用模块
            try {
                LOG.debug("使用备用模块初始化Guice注入器");
                injector = Guice.createInjector(createFallbackModule());
                initialized = true;
                LOG.debug("使用备用模块初始化Guice注入器成功");
                return true;
            } catch (Exception e2) {
                LOG.error("使用备用模块初始化Guice注入器也失败", e2);
                initialized = false;
                return false;
            }
        }
    }

    /**
     * 创建不依赖Project的备用模块
     */
    private static com.google.inject.Module createFallbackModule() {
        return new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                LOG.debug("配置备用Guice模块");

                try {
                    // 绑定最基本的服务
                    bind(com.lv.tool.privatereader.repository.StorageRepository.class)
                        .to(com.lv.tool.privatereader.repository.impl.FileStorageRepository.class)
                        .in(com.google.inject.Singleton.class);

                    bind(com.lv.tool.privatereader.repository.BookRepository.class)
                        .to(com.lv.tool.privatereader.repository.impl.FileBookRepository.class)
                        .in(com.google.inject.Singleton.class);

                    bind(com.lv.tool.privatereader.repository.ReadingProgressRepository.class)
                        .to(com.lv.tool.privatereader.repository.impl.SqliteReadingProgressRepository.class)
                        .in(com.google.inject.Singleton.class);

                    bind(com.lv.tool.privatereader.service.BookService.class)
                        .to(com.lv.tool.privatereader.service.impl.BookServiceImpl.class)
                        .in(com.google.inject.Singleton.class);

                    LOG.debug("备用Guice模块配置完成");
                } catch (Exception e) {
                    LOG.error("配置备用Guice模块失败", e);
                }
            }
        };
    }

    /**
     * 获取Guice注入器
     *
     * @return Guice注入器实例
     */
    public static synchronized Injector getInjector() {
        if (injector == null) {
            initialize();
        }
        return injector;
    }

    /**
     * 获取指定类型的实例
     *
     * @param type 类型
     * @return 实例
     * @throws IllegalStateException 如果注入器未初始化
     */
    public static <T> T getInstance(Class<T> type) {
        if (!initialized || injector == null) {
             LOG.warn("Guice Injector not initialized or initialization failed. Attempting re-initialization...");
             initialize(); // Attempt to initialize again
             if (!initialized || injector == null) {
                   LOG.error("Failed to initialize Guice Injector on demand for type: " + type.getName());
                  throw new IllegalStateException("Guice Injector is not initialized.");
             }
        }
        try {
            return injector.getInstance(type);
        } catch (Exception e) {
            LOG.error("Failed to get instance of type: " + type.getName(), e);
            // Depending on requirements, could return null or re-throw
            throw new RuntimeException("Failed to get Guice instance for " + type.getName(), e);
        }
    }

    /**
     * 检查Guice注入器是否已初始化
     *
     * @return 如果Guice注入器已初始化，则返回true
     */
    public static boolean isInitialized() {
        return initialized;
    }
}