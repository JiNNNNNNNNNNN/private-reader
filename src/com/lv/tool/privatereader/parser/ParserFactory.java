package com.lv.tool.privatereader.parser;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public final class ParserFactory {
    private ParserFactory() {
        // 私有构造函数防止实例化
    }

    private static final Map<String, ParserConfig> SITE_PATTERNS = Map.of(
        "qidian.com", new ParserConfig(QidianParser.class, "起点中文网"),
        "zongheng.com", new ParserConfig(ZonghengParser.class, "纵横中文网")
    );

    public static NovelParser createParser(final String url) throws RuntimeException {
        for (final Map.Entry<String, ParserConfig> entry : SITE_PATTERNS.entrySet()) {
            if (url.contains(entry.getKey())) {
                try {
                    return entry.getValue().parserClass().getConstructor(String.class).newInstance(url);
                } catch (InstantiationException | IllegalAccessException | 
                         NoSuchMethodException | InvocationTargetException e) {
                    throw new RuntimeException("Parser init failed", e);
                }
            }
        }
        return new CommonNovelParser(url); // 默认通用解析器
    }
}

final record ParserConfig(Class<? extends NovelParser> parserClass, String siteName) {} 