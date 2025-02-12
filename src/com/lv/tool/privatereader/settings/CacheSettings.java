package com.lv.tool.privatereader.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 缓存设置
 */
@Service(Service.Level.PROJECT)
@State(
    name = "CacheSettings",
    storages = @Storage("privateReaderCache.xml")
)
public final class CacheSettings implements PersistentStateComponent<CacheSettings.State> {
    private State state = new State();

    public static CacheSettings getInstance(@NotNull Project project) {
        return project.getService(CacheSettings.class);
    }

    public int getMaxCacheSize() {
        return state.maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        state.maxCacheSize = maxCacheSize;
    }

    @Override
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State {
        public int maxCacheSize = 500; // 默认500MB
    }
} 