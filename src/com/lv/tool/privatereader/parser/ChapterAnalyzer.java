package com.lv.tool.privatereader.parser;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 章节分析器
 * 用于智能识别小说章节
 */
public final class ChapterAnalyzer {
    // 常见的章节标题模式
    private static final Pattern[] CHAPTER_PATTERNS = {
        Pattern.compile("^第[0-9零一二三四五六七八九十百千万亿]+[章节卷集].*$"),
        Pattern.compile("^[0-9]+[、.][^0-9]*$"),
        Pattern.compile("^第[0-9零一二三四五六七八九十百千万亿]+回.*$"),
        Pattern.compile("^[序楔终]章.*$"),
        Pattern.compile("^[前序楔]言.*$"),
        Pattern.compile("^[后终]记.*$")
    };

    // 常见的章节容器类名
    private static final String[] CHAPTER_CONTAINERS = {
        "div.catalog",
        "div.directory",
        "div.chapter-list",
        "div.novel-list",
        "div.volume-list",
        "ul.chapter-list",
        "ul.volume-list"
    };

    /**
     * 查找章节列表容器
     */
    public static Element findChapterContainer(Element root) {
        for (String selector : CHAPTER_CONTAINERS) {
            Element container = root.selectFirst(selector);
            if (container != null && hasValidChapters(container)) {
                return container;
            }
        }
        return null;
    }

    /**
     * 从容器中提取章节列表
     */
    public static List<com.lv.tool.privatereader.parser.NovelParser.Chapter> extractChapters(Element container, String baseUrl) {
        List<com.lv.tool.privatereader.parser.NovelParser.Chapter> chapters = new ArrayList<>();
        Elements links = container.select("a");
        
        for (Element link : links) {
            String title = link.text().trim();
            String url = link.absUrl("href");
            
            if (!url.isEmpty() && isChapterTitle(title)) {
                chapters.add(new com.lv.tool.privatereader.parser.NovelParser.Chapter(title, url));
            }
        }
        
        return chapters;
    }

    /**
     * 判断是否是有效的章节标题
     */
    public static boolean isChapterTitle(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        text = text.trim();
        
        // 检查是否匹配任一章节标题模式
        for (Pattern pattern : CHAPTER_PATTERNS) {
            if (pattern.matcher(text).matches()) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查容器是否包含有效的章节
     */
    private static boolean hasValidChapters(Element container) {
        Elements links = container.select("a");
        int validChapters = 0;
        
        for (Element link : links) {
            if (isChapterTitle(link.text())) {
                validChapters++;
                if (validChapters >= 3) { // 至少需要3个有效章节
                    return true;
                }
            }
        }
        
        return false;
    }
} 