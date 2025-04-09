package com.lv.tool.privatereader.config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.intellij.openapi.diagnostic.Logger;

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
    
    /**
     * 初始化Guice注入器
     * 
     * 此方法应在应用程序启动时调用一次
     * 如果初始化失败，会尝试使用不依赖Project的备用模块
     * 
     * @return 是否成功初始化
     */
    public static synchronized boolean initialize() {
        if (injector == null) {
            try {
                LOG.info("初始化应用级别的Guice注入器");
                injector = Guice.createInjector(new AppModule());
                initialized = true;
                LOG.info("应用级别的Guice注入器初始化完成");
                return true;
            } catch (Exception e) {
                LOG.error("初始化Guice注入器失败，尝试使用备用模块", e);
                
                // 尝试使用备用模块
                try {
                    LOG.info("使用备用模块初始化Guice注入器");
                    injector = Guice.createInjector(createFallbackModule());
                    initialized = true;
                    LOG.info("使用备用模块初始化Guice注入器成功");
                    return true;
                } catch (Exception e2) {
                    LOG.error("使用备用模块初始化Guice注入器也失败", e2);
                    initialized = false;
                    return false;
                }
            }
        } else {
            LOG.info("Guice注入器已经初始化，跳过重复初始化");
            return true;
        }
    }
    
    /**
     * 创建不依赖Project的备用模块
     */
    private static com.google.inject.Module createFallbackModule() {
        return new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                LOG.info("配置备用Guice模块");
                
                try {
                    // 绑定最基本的服务
                    bind(com.lv.tool.privatereader.repository.StorageRepository.class)
                        .to(com.lv.tool.privatereader.repository.impl.FileStorageRepository.class)
                        .in(com.google.inject.Singleton.class);
                    
                    bind(com.lv.tool.privatereader.repository.BookRepository.class)
                        .to(com.lv.tool.privatereader.repository.impl.FileBookRepository.class)
                        .in(com.google.inject.Singleton.class);
                    
                    bind(com.lv.tool.privatereader.repository.ReadingProgressRepository.class)
                        .to(com.lv.tool.privatereader.repository.impl.FileReadingProgressRepository.class)
                        .in(com.google.inject.Singleton.class);
                    
                    bind(com.lv.tool.privatereader.service.BookService.class)
                        .to(com.lv.tool.privatereader.service.impl.BookServiceImpl.class)
                        .in(com.google.inject.Singleton.class);
                    
                    LOG.info("备用Guice模块配置完成");
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
     * @param clazz 要获取的类型
     * @return 指定类型的实例
     */
    public static <T> T getInstance(Class<T> clazz) {
        if (clazz == null) {
            LOG.error("无法获取实例：类型为null");
            return null;
        }
        
        try {
            T instance = getInjector().getInstance(clazz);
            if (instance == null) {
                LOG.error("无法获取类型为 " + clazz.getName() + " 的实例");
            }
            return instance;
        } catch (Exception e) {
            LOG.error("获取类型为 " + clazz.getName() + " 的实例失败", e);
            return null;
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