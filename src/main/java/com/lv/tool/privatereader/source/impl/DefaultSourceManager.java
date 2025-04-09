package com.lv.tool.privatereader.source.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.source.Source;
import com.lv.tool.privatereader.source.SourceManager;
import com.lv.tool.privatereader.source.impl.UniversalSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认的源管理器实现
 * 管理所有可用的源，并提供源查找功能
 */
public class DefaultSourceManager implements SourceManager {
    private static final Logger LOG = Logger.getInstance(DefaultSourceManager.class);
    
    private final Map<String, Source> sourcesById = new HashMap<>();
    private final List<Source> sourcesList = new ArrayList<>();
    
    /**
     * 无参构造方法，用于IntelliJ服务系统
     * 注册默认源
     */
    public DefaultSourceManager() {
        LOG.info("初始化DefaultSourceManager...");
        
        // 注册默认的通用源
        registerSource(new UniversalSource());
        
        LOG.info("DefaultSourceManager初始化完成，共注册 " + sourcesList.size() + " 个源");
    }
    
    @Override
    @NotNull
    public Source getSource(@NotNull String sourceId) {
        Source source = sourcesById.get(sourceId);
        if (source == null) {
            LOG.warn("未找到指定ID的源: " + sourceId + "，返回通用源");
            // 如果没有找到指定ID的源，返回通用源
            return getDefaultSource();
        }
        return source;
    }
    
    @Override
    @NotNull
    public List<Source> getAllSources() {
        return new ArrayList<>(sourcesList);
    }
    
    @Override
    @Nullable
    public Source getSourceByUrl(@NotNull String url) {
        for (Source source : sourcesList) {
            if (source.supports(url)) {
                LOG.info("找到匹配URL的源: " + source.getName() + " for " + url);
                return source;
            }
        }
        
        LOG.info("未找到匹配URL的特定源，使用通用源: " + url);
        // 如果没有找到匹配的源，返回通用源
        return getDefaultSource();
    }
    
    @Override
    public void registerSource(@NotNull Source source) {
        String sourceId = source.getId();
        if (sourcesById.containsKey(sourceId)) {
            LOG.warn("源ID已存在，将覆盖: " + sourceId);
        }
        
        sourcesById.put(sourceId, source);
        
        // 检查是否已存在于列表中，避免重复添加
        if (!sourcesList.contains(source)) {
            sourcesList.add(source);
        }
        
        LOG.info("注册源: " + source.getName() + " (ID: " + sourceId + ")");
    }
    
    /**
     * 获取默认源（通用源）
     */
    private Source getDefaultSource() {
        for (Source source : sourcesList) {
            if (source instanceof UniversalSource) {
                return source;
            }
        }
        
        // 如果没有找到通用源，创建一个
        Source universalSource = new UniversalSource();
        registerSource(universalSource);
        return universalSource;
    }
} 