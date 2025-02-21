package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 缓存设置
 */
@State(
    name = "PrivateReaderCacheSettings",
    storages = @Storage("private-reader-cache-settings.xml")
)
public class CacheSettings implements PersistentStateComponent<CacheSettings> {
    private boolean enableCache = true;
    private int maxCacheSize = 100; // MB
    private int maxCacheAge = 7; // days

    public boolean isEnableCache() {
        return enableCache;
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public int getMaxCacheAge() {
        return maxCacheAge;
    }

    public void setMaxCacheAge(int maxCacheAge) {
        this.maxCacheAge = maxCacheAge;
    }

    @Override
    public @Nullable CacheSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CacheSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 