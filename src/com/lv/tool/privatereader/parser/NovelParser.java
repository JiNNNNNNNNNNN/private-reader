package com.lv.tool.privatereader.parser;

import java.util.List;

/**
 * 小说解析器接口
 */
public interface NovelParser {
    /**
     * 获取小说标题
     */
    String getTitle();

    /**
     * 获取作者
     */
    String getAuthor();

    /**
     * 获取章节列表
     */
    List<Chapter> getChapterList();

    /**
     * 获取章节内容
     */
    String getChapterContent(String chapterUrl);

    /**
     * 章节信息
     */
    record Chapter(String title, String url) {}
} 