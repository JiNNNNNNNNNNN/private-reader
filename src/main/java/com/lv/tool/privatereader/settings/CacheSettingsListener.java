package com.lv.tool.privatereader.settings;

import com.intellij.util.messages.Topic;

public interface CacheSettingsListener {
    Topic<CacheSettingsListener> TOPIC = Topic.create("Cache Settings", CacheSettingsListener.class);
    
    void cacheSettingsChanged(CacheSettings settings);
} 