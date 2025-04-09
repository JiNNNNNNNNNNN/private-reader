package com.lv.tool.privatereader.source;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 源管理器接口
 * 
 * 负责管理和获取不同来源的解析器，提供统一的接口访问不同来源的内容。
 */
public interface SourceManager {
    /**
     * 获取指定ID的源
     *
     * @param sourceId 源ID
     * @return 源实例
     */
    @NotNull
    Source getSource(@NotNull String sourceId);
    
    /**
     * 获取所有可用的源
     *
     * @return 源列表
     */
    @NotNull
    List<Source> getAllSources();
    
    /**
     * 根据URL获取合适的源
     *
     * @param url 书籍URL
     * @return 源实例，如果没有找到合适的源则返回null
     */
    @Nullable
    Source getSourceByUrl(@NotNull String url);
    
    /**
     * 注册新的源
     *
     * @param source 源实例
     */
    void registerSource(@NotNull Source source);
} 