package com.lv.tool.privatereader.util;

import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.settings.PluginSettings;

public class PluginUtil {
    public static boolean isPluginEnabled() {
        PluginSettings settings = ApplicationManager.getApplication().getService(PluginSettings.class);
        return settings != null && settings.isEnabled();
    }
} 