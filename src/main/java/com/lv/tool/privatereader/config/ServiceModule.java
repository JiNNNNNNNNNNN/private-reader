package com.lv.tool.privatereader.config;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.service.impl.BookServiceImpl;
import com.lv.tool.privatereader.service.impl.ChapterServiceImpl;
import com.lv.tool.privatereader.service.impl.NotificationServiceImpl;
import com.lv.tool.privatereader.settings.CacheSettings;
import com.lv.tool.privatereader.settings.NotificationReaderSettings;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.lv.tool.privatereader.settings.ReaderSettings;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;

/**
 * 服务模块
 * 
 * 负责注册和管理所有服务，提供统一的服务访问点
 * 使用ServiceLocator进行依赖注入
 */
@Service(Service.Level.APP)
public final class ServiceModule {
    private static final Logger LOG = Logger.getInstance(ServiceModule.class);
    
    private final ServiceLocator serviceLocator;
    
    public ServiceModule() {
        // 尝试获取ServiceLocator服务
        this.serviceLocator = ServiceLocator.getApplicationService(ServiceLocator.class);
        
        // 如果没有获取到ServiceLocator服务，直接创建一个新实例
        if (serviceLocator == null) {
            LOG.warn("ServiceLocator服务未初始化，尝试创建新实例");
            try {
                // 我们无法直接创建ServiceLocator实例，
                // 但我们可以注册一些基本服务到临时映射，以便本模块可以继续工作
                LOG.info("继续初始化其他服务");
                // 即使没有ServiceLocator，我们也会尝试注册必要的服务
                registerServicesWithoutLocator();
            } catch (Exception e) {
                LOG.error("初始化服务失败", e);
            }
        } else {
            // 注册服务
            registerServices();
            LOG.info("ServiceModule initialized for application");
        }
    }
    
    /**
     * 注册所有服务
     */
    private void registerServices() {
        // 注册设置服务
        registerSettingsServices();
        
        // 注册业务服务
        registerBusinessServices();
    }
    
    /**
     * 注册设置服务
     */
    private void registerSettingsServices() {
        // 应用级设置服务
        PluginSettings pluginSettings = ServiceLocator.getApplicationService(PluginSettings.class);
        ReaderSettings readerSettings = ServiceLocator.getApplicationService(ReaderSettings.class);
        NotificationReaderSettings notificationSettings = ServiceLocator.getApplicationService(NotificationReaderSettings.class);
        ReaderModeSettings modeSettings = ServiceLocator.getApplicationService(ReaderModeSettings.class);
        CacheSettings cacheSettings = ServiceLocator.getApplicationService(CacheSettings.class);
        
        // 注册到ServiceLocator
        serviceLocator.registerService(PluginSettings.class, pluginSettings);
        serviceLocator.registerService(ReaderSettings.class, readerSettings);
        serviceLocator.registerService(NotificationReaderSettings.class, notificationSettings);
        serviceLocator.registerService(ReaderModeSettings.class, modeSettings);
        serviceLocator.registerService(CacheSettings.class, cacheSettings);
    }
    
    /**
     * 注册业务服务
     */
    private void registerBusinessServices() {
        // 注册BookService
        serviceLocator.registerServiceFactory(BookService.class, this::createBookService);
        
        // 注册ChapterService
        serviceLocator.registerServiceFactory(ChapterService.class, this::createChapterService);
        
        // 注册NotificationService
        serviceLocator.registerServiceFactory(NotificationService.class, this::createNotificationService);
    }
    
    /**
     * 创建BookService
     */
    @NotNull
    private BookService createBookService() {
        return ApplicationManager.getApplication().getService(BookServiceImpl.class);
    }
    
    /**
     * 创建ChapterService
     */
    @NotNull
    private ChapterService createChapterService() {
        return ApplicationManager.getApplication().getService(ChapterServiceImpl.class);
    }
    
    /**
     * 创建NotificationService
     */
    @NotNull
    private NotificationService createNotificationService() {
        return ApplicationManager.getApplication().getService(NotificationServiceImpl.class);
    }
    
    /**
     * 在没有ServiceLocator的情况下注册服务
     * 这种情况下，服务将不会被注册到ServiceLocator中，
     * 但服务模块仍然可以继续工作，其他组件可以直接从ApplicationManager获取服务
     */
    private void registerServicesWithoutLocator() {
        // 打印警告日志
        LOG.warn("在没有ServiceLocator的情况下注册服务，这是一个备用机制");
        
        // 尝试确保服务已经实例化
        try {
            // 检查或创建BookService
            BookService bookService = ApplicationManager.getApplication().getService(BookServiceImpl.class);
            if (bookService != null) {
                LOG.info("BookService已初始化");
            } else {
                LOG.warn("无法初始化BookService");
            }
            
            // 检查或创建ChapterService
            ChapterService chapterService = ApplicationManager.getApplication().getService(ChapterServiceImpl.class);
            if (chapterService != null) {
                LOG.info("ChapterService已初始化");
            } else {
                LOG.warn("无法初始化ChapterService");
            }
            
            // 检查或创建NotificationService
            NotificationService notificationService = ApplicationManager.getApplication().getService(NotificationServiceImpl.class);
            if (notificationService != null) {
                LOG.info("NotificationService已初始化");
            } else {
                LOG.warn("无法初始化NotificationService");
            }
        } catch (Exception e) {
            LOG.error("初始化基本服务失败", e);
        }
    }
    
    /**
     * 获取服务
     * 
     * @param serviceClass 服务类
     * @param <T> 服务类型
     * @return 服务实例
     */
    public <T> T getService(Class<T> serviceClass) {
        return serviceLocator.getService(serviceClass);
    }
} 