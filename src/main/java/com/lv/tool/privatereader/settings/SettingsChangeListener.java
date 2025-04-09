package com.lv.tool.privatereader.settings;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * 设置变更监听器
 * 
 * 用于监听设置变更事件
 */
public interface SettingsChangeListener {
    Topic<SettingsChangeListener> TOPIC = Topic.create("Settings Change", SettingsChangeListener.class);
    
    /**
     * 设置变更时调用
     * @param settings 变更后的设置对象
     */
    void onSettingsChanged(@NotNull Object settings);
    
    /**
     * 设置错误时调用
     * @param settingsClass 设置类
     * @param e 异常
     */
    void onSettingsError(@NotNull Class<?> settingsClass, @NotNull Exception e);
    
    /**
     * 设置加载时调用
     * @param settings 加载的设置对象
     */
    void onSettingsLoaded(@NotNull Object settings);
    
    /**
     * 设置保存时调用
     * @param settings 保存的设置对象
     */
    void onSettingsSaved(@NotNull Object settings);
} 