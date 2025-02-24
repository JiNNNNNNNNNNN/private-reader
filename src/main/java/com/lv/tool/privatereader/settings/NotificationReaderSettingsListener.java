package com.lv.tool.privatereader.settings;

import com.intellij.util.messages.Topic;

public interface NotificationReaderSettingsListener {
    Topic<NotificationReaderSettingsListener> TOPIC = Topic.create("通知栏阅读设置变更", NotificationReaderSettingsListener.class);
    
    void settingsChanged();
} 