package com.lv.tool.privatereader.parser.common;

import java.util.regex.Pattern;

/**
 * 章节标题工具类
 * 用于统一处理章节标题的判断逻辑
 */
public final class ChapterTitleUtils {
    private static final Pattern CHAPTER_NUMBER_PATTERN = Pattern.compile("^第[0-9零一二三四五六七八九十百千万亿]+[章节卷集部篇].*$");
    private static final Pattern CHAPTER_INDEX_PATTERN = Pattern.compile("^[0-9]+[、.][^0-9]*$");
    private static final Pattern CHAPTER_SPECIAL_PATTERN = Pattern.compile("^第[0-9零一二三四五六七八九十百千万亿]+回.*$");
    private static final Pattern CHAPTER_PREFIX_PATTERN = Pattern.compile("^[序楔终][章话].*$");
    private static final Pattern PREFACE_PATTERN = Pattern.compile("^[前序楔引]言.*$");
    private static final Pattern AFTERWORD_PATTERN = Pattern.compile("^[后终]记.*$");
    private static final Pattern VOLUME_PATTERN = Pattern.compile("^[卷部篇][0-9零一二三四五六七八九十百千万亿]+.*$");
    private static final Pattern SIMPLE_NUMBER_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern SPECIAL_CHAPTER_PATTERN = Pattern.compile("^[上中下]篇.*$|^番外.*$|^特别篇.*$|^外传.*$");
    private static final Pattern TIME_CHAPTER_PATTERN = Pattern.compile("^[早中午晚]章.*$|^[春夏秋冬]章.*$");
    private static final Pattern INTERLUDE_PATTERN = Pattern.compile("^(间|幕)?插.*$");

    private ChapterTitleUtils() {
        // 私有构造函数防止实例化
    }

    /**
     * 判断是否是有效的章节标题
     */
    public static boolean isChapterTitle(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        text = text.trim();
        
        // 过滤掉明显不是章节标题的内容
        if (text.length() > 50 || text.contains("http") || text.contains("www")) {
            return false;
        }
        
        return CHAPTER_NUMBER_PATTERN.matcher(text).matches() ||
               CHAPTER_INDEX_PATTERN.matcher(text).matches() ||
               CHAPTER_SPECIAL_PATTERN.matcher(text).matches() ||
               CHAPTER_PREFIX_PATTERN.matcher(text).matches() ||
               PREFACE_PATTERN.matcher(text).matches() ||
               AFTERWORD_PATTERN.matcher(text).matches() ||
               VOLUME_PATTERN.matcher(text).matches() ||
               SIMPLE_NUMBER_PATTERN.matcher(text).matches() ||
               SPECIAL_CHAPTER_PATTERN.matcher(text).matches() ||
               TIME_CHAPTER_PATTERN.matcher(text).matches() ||
               INTERLUDE_PATTERN.matcher(text).matches();
    }
} 