package com.lv.tool.privatereader.ui.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 缓存设置
 */
@Service(Service.Level.PROJECT)
@State(
    name = "PrivateReaderCacheSettings",
    storages = @Storage("privateReaderCache.xml")
)
public class CacheSettings implements PersistentStateComponent<CacheSettings.State> {
    private State myState = new State();

    public static CacheSettings getInstance(@NotNull Project project) {
        return project.getService(CacheSettings.class);
    }

    public static class State {
        public boolean enableCache = true;
        public int maxCacheSize = 100; // MB
        public int cacheExpiration = 7; // days
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public boolean isEnableCache() {
        return myState.enableCache;
    }

    public void setEnableCache(boolean enableCache) {
        myState.enableCache = enableCache;
    }

    public int getMaxCacheSize() {
        return myState.maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        myState.maxCacheSize = maxCacheSize;
    }

    public int getCacheExpiration() {
        return myState.cacheExpiration;
    }

    public void setCacheExpiration(int cacheExpiration) {
        myState.cacheExpiration = cacheExpiration;
    }
} 