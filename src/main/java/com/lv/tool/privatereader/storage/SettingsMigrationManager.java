package com.lv.tool.privatereader.storage;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设置迁移管理器
 * 
 * 负责管理设置的版本迁移
 */
public class SettingsMigrationManager {
    private static final Logger LOG = Logger.getInstance(SettingsMigrationManager.class);
    
    private final Map<String, SettingsMigration> migrations = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> currentVersions = new ConcurrentHashMap<>();
    
    /**
     * 注册迁移
     * @param fromVersion 源版本
     * @param toVersion 目标版本
     * @param migration 迁移实现
     */
    public void registerMigration(@NotNull String fromVersion, @NotNull String toVersion, @NotNull SettingsMigration migration) {
        String key = getMigrationKey(fromVersion, toVersion);
        migrations.put(key, migration);
        LOG.info("注册设置迁移: " + key);
    }
    
    /**
     * 注册当前版本
     * @param settingsClass 设置类
     * @param version 版本号
     */
    public void registerCurrentVersion(@NotNull Class<?> settingsClass, @NotNull String version) {
        currentVersions.put(settingsClass, version);
        LOG.info("注册设置版本: " + settingsClass.getSimpleName() + " -> " + version);
    }
    
    /**
     * 执行迁移
     * @param settingsClass 设置类
     * @param currentVersion 当前版本
     * @param targetVersion 目标版本
     * @param settings 设置对象
     * @return 迁移后的设置对象
     */
    @SuppressWarnings("unchecked")
    public <T> T migrate(@NotNull Class<T> settingsClass, @NotNull String currentVersion, @NotNull String targetVersion, @NotNull T settings) {
        if (currentVersion.equals(targetVersion)) {
            return settings;
        }
        
        String key = getMigrationKey(currentVersion, targetVersion);
        SettingsMigration migration = migrations.get(key);
        
        if (migration == null) {
            LOG.error("找不到迁移实现: " + key);
            throw new SettingsMigrationException("找不到迁移实现: " + key);
        }
        
        try {
            LOG.info("开始迁移设置: " + settingsClass.getSimpleName() + " " + currentVersion + " -> " + targetVersion);
            return (T) migration.migrate(settings);
        } catch (Exception e) {
            LOG.error("迁移设置失败: " + e.getMessage(), e);
            throw new SettingsMigrationException("迁移设置失败", e);
        }
    }
    
    /**
     * 获取迁移键
     * @param fromVersion 源版本
     * @param toVersion 目标版本
     * @return 迁移键
     */
    private String getMigrationKey(@NotNull String fromVersion, @NotNull String toVersion) {
        return fromVersion + "->" + toVersion;
    }
    
    /**
     * 设置迁移接口
     */
    public interface SettingsMigration {
        /**
         * 执行迁移
         * @param settings 设置对象
         * @return 迁移后的设置对象
         */
        Object migrate(Object settings);
    }
    
    /**
     * 设置迁移异常
     */
    public static class SettingsMigrationException extends RuntimeException {
        public SettingsMigrationException(String message) {
            super(message);
        }
        
        public SettingsMigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 