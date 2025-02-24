package com.lv.tool.privatereader.settings;

import com.intellij.util.messages.Topic;

public interface PluginSettingsListener {
    Topic<PluginSettingsListener> TOPIC = Topic.create("Plugin Settings Changed", PluginSettingsListener.class);
    
    void settingsChanged();
} 