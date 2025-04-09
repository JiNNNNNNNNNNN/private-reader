package com.lv.tool.privatereader.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.settings.*;

import java.util.Arrays;
import java.util.List;

/**
 * 设置迁移工具
 * 
 * 用于将现有的 XML 设置迁移到 JSON 文件
 */
public class SettingsMigrationTool {
    private static final Logger LOG = Logger.getInstance(SettingsMigrationTool.class);
    
    private final StorageManager storageManager;
    
    public SettingsMigrationTool(StorageManager storageManager) {
        this.storageManager = storageManager;
    }
    
    /**
     * 执行设置迁移
     */
    public void migrateSettings() {
        LOG.info("开始迁移设置到 JSON 文件...");
        
        // 获取所有需要迁移的设置类
        List<Class<? extends BaseSettings<?>>> settingsClasses = Arrays.asList(
            ReaderSettings.class,
            PluginSettings.class,
            NotificationReaderSettings.class,
            ReaderModeSettings.class,
            CacheSettings.class
        );
        
        // 迁移每个设置类
        for (Class<? extends BaseSettings<?>> settingsClass : settingsClasses) {
            try {
                migrateSettingsClass(settingsClass);
            } catch (Exception e) {
                LOG.error("迁移设置类失败: " + settingsClass.getSimpleName(), e);
            }
        }
        
        LOG.info("设置迁移完成");
    }
    
    /**
     * 迁移单个设置类
     * @param settingsClass 设置类
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void migrateSettingsClass(Class<? extends BaseSettings<?>> settingsClass) {
        LOG.info("迁移设置类: " + settingsClass.getSimpleName());
        
        try {
            // 从 IntelliJ 服务获取设置实例
            BaseSettings settings = ApplicationManager.getApplication().getService(settingsClass);
            
            // 保存设置
            storageManager.getSettingsStorage().saveSettings(settings);
            
            LOG.info("成功迁移设置类: " + settingsClass.getSimpleName());
        } catch (Exception e) {
            LOG.error("迁移设置类失败: " + settingsClass.getSimpleName(), e);
        }
    }
} 