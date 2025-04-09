package com.lv.tool.privatereader.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.storage.SettingsStorage;
import com.lv.tool.privatereader.storage.StorageManager;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.messages.MessageBus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 基础设置类
 * 
 * 所有设置类的基类，提供 JSON 文件存储支持
 */
public abstract class BaseSettings<T extends BaseSettings<T>> {
    private static final Logger LOG = Logger.getInstance(BaseSettings.class);
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    private transient boolean loaded = false;
    private transient boolean dirty = false;
    
    protected BaseSettings() {
        // 构造函数不再自动加载设置，避免递归调用
    }
    
    protected void ensureSettingsLoaded() {
        if (!loaded) {
            loadSettings();
        }
    }
    
    @SuppressWarnings("unchecked")
    protected void loadSettings() {
        if (loaded) {
            return;
        }
        
        try {
            T loadedSettings = (T) SettingsStorage.getInstance().loadSettings(getClass());
            if (loadedSettings != null) {
                copyFrom(loadedSettings);
            } else {
                // 如果没有加载到设置，使用默认值
                T defaultSettings = getDefault();
                if (defaultSettings != null) {
                    copyFrom(defaultSettings);
                }
            }
            loaded = true;
            dirty = false;
        } catch (Exception e) {
            LOG.error("加载设置失败: " + getClass().getSimpleName(), e);
            // 加载失败时使用默认值
            T defaultSettings = getDefault();
            if (defaultSettings != null) {
                copyFrom(defaultSettings);
            }
            loaded = true;
            dirty = false;
        }
    }
    
    @SuppressWarnings("unchecked")
    protected void saveSettings() {
        if (dirty && loaded) {
            try {
                SettingsStorage.getInstance().saveSettings((T) this);
                dirty = false;
            } catch (Exception e) {
                LOG.error("保存设置失败: " + getClass().getSimpleName(), e);
            }
        }
    }
    
    protected void markDirty() {
        this.dirty = true;
    }
    
    protected boolean isDirty() {
        return dirty;
    }
    
    protected boolean isLoaded() {
        return loaded;
    }
    
    /**
     * 从源设置复制
     */
    protected abstract void copyFrom(@NotNull T other);
    
    /**
     * 获取默认设置
     * @return 默认设置
     */
    protected abstract T getDefault();
} 