package com.lv.tool.privatereader.settings;

import java.util.EventListener;

/**
 * 阅读模式设置变更监听器
 */
public interface ReaderModeSettingsListener extends EventListener {
    /**
     * 阅读模式设置变更时调用
     */
    void readerModeSettingsChanged();
} 