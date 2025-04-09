package com.lv.tool.privatereader.settings;

/**
 * 阅读模式设置监听器
 * 用于监听阅读模式的变化
 */
public interface ReaderModeSettingsListener {
    /**
     * 阅读模式变化时调用
     * @param notificationMode 是否为通知栏模式
     */
    void modeChanged(boolean notificationMode);
} 