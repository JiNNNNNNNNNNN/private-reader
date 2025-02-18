package com.lv.tool.privatereader.parser;

import com.lv.tool.privatereader.parser.site.UniversalParser;

/**
 * 解析器工厂
 */
public final class ParserFactory {
    private ParserFactory() {
        // 私有构造函数防止实例化
    }

    public static NovelParser createParser(final String url) throws RuntimeException {
        return new UniversalParser(url);
    }
} 