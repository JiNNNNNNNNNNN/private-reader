package com.lv.tool.privatereader.parser;

import com.lv.tool.privatereader.parser.site.UniversalParser;

/**
 * 解析器工厂
 */
public class ParserFactory {
    public static NovelParser createParser(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        return new UniversalParser(url);
    }
} 