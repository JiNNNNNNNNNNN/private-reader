package com.lv.tool.privatereader.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务定位器
 * 
 * 提供统一的服务访问点，减少对ApplicationManager.getApplication().getService()的直接依赖
 * 支持应用级服务的注册和获取
 * 支持服务的懒加载和自定义工厂方法
 */
@Service(Service.Level.APP)
public final class ServiceLocator {
    private static final Logger LOG = Logger.getInstance(ServiceLocator.class);
    
    private final Map<Class<?>, Object> services = new HashMap<>();
    private final Map<Class<?>, Supplier<?>> serviceFactories = new HashMap<>();
    
    public ServiceLocator() {
        LOG.info("ServiceLocator initialized for application");
    }
    
    /**
     * 获取应用级服务
     * 
     * @param serviceClass 服务类
     * @param <T> 服务类型
     * @return 服务实例
     */
    @Nullable
    public static <T> T getApplicationService(@NotNull Class<T> serviceClass) {
        return ApplicationManager.getApplication().getService(serviceClass);
    }
    
    /**
     * 获取服务
     * 
     * @param serviceClass 服务类
     * @param <T> 服务类型
     * @return 服务实例
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getService(@NotNull Class<T> serviceClass) {
        // 检查是否已注册服务实例
        Object service = services.get(serviceClass);
        if (service != null) {
            return (T) service;
        }
        
        // 检查是否有工厂方法
        Supplier<?> factory = serviceFactories.get(serviceClass);
        if (factory != null) {
            // 使用工厂方法创建服务实例
            service = factory.get();
            if (service != null) {
                // 缓存服务实例
                services.put(serviceClass, service);
                return (T) service;
            }
        }
        
        // 尝试从ApplicationManager获取
        return getApplicationService(serviceClass);
    }
    
    /**
     * 注册服务
     * 
     * @param serviceClass 服务类
     * @param service 服务实例
     * @param <T> 服务类型
     */
    public <T> void registerService(@NotNull Class<T> serviceClass, @NotNull T service) {
        services.put(serviceClass, service);
    }
    
    /**
     * 注册服务工厂
     * 
     * @param serviceClass 服务类
     * @param factory 服务工厂
     * @param <T> 服务类型
     */
    public <T> void registerServiceFactory(@NotNull Class<T> serviceClass, @NotNull Supplier<T> factory) {
        serviceFactories.put(serviceClass, factory);
    }
    
    /**
     * 清除所有注册的服务
     */
    public void clear() {
        services.clear();
        serviceFactories.clear();
    }
} 