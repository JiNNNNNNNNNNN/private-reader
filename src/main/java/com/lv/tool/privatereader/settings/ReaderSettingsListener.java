package com.lv.tool.privatereader.settings;

import com.intellij.util.messages.Topic;

public interface ReaderSettingsListener {
    Topic<ReaderSettingsListener> TOPIC = Topic.create("Reader Settings Changed", ReaderSettingsListener.class);
    
    void settingsChanged();
} 