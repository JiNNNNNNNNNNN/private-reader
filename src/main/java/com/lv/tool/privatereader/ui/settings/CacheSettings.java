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
    private boolean enablePreload = true; // 是否启用预加载
    private int preloadCount = 50; // 预加载章节数量
    private int preloadDelay = 1000; // 预加载请求间隔(毫秒)

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
    
    public boolean isEnablePreload() {
        return enablePreload;
    }

    public void setEnablePreload(boolean enablePreload) {
        this.enablePreload = enablePreload;
    }

    public int getPreloadCount() {
        return preloadCount;
    }

    public void setPreloadCount(int preloadCount) {
        this.preloadCount = preloadCount;
    }
    
    public int getPreloadDelay() {
        return preloadDelay;
    }

    public void setPreloadDelay(int preloadDelay) {
        this.preloadDelay = preloadDelay;
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