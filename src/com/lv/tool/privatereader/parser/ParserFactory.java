package com.lv.tool.privatereader.parser;

/**
 * 解析器工厂
 */
public final class ParserFactory {
    private ParserFactory() {
        // 私有构造函数防止实例化
    }

    public static com.lv.tool.privatereader.parser.NovelParser createParser(final String url) throws RuntimeException {
        // 如果是本地文件，使用通用TXT解析器
        if (url.startsWith("file:")) {
            return new com.lv.tool.privatereader.parser.CommonNovelParser(url);
        }

        // 使用通用网页解析器
        return new UniversalParser(url);
    }
} 