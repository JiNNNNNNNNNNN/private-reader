package com.lv.tool.privatereader.parser.common;

import java.util.regex.Pattern;

/**
 * 章节标题工具类
 * 用于统一处理章节标题的判断逻辑
 */
public final class ChapterTitleUtils {
    private static final Pattern CHAPTER_NUMBER_PATTERN = Pattern.compile("^第[0-9零一二三四五六七八九十百千万亿]+[章节卷集].*$");
    private static final Pattern CHAPTER_INDEX_PATTERN = Pattern.compile("^[0-9]+[、.][^0-9]*$");
    private static final Pattern CHAPTER_SPECIAL_PATTERN = Pattern.compile("^第[0-9零一二三四五六七八九十百千万亿]+回.*$");
    private static final Pattern CHAPTER_PREFIX_PATTERN = Pattern.compile("^[序楔终]章.*$");
    private static final Pattern PREFACE_PATTERN = Pattern.compile("^[前序楔]言.*$");
    private static final Pattern AFTERWORD_PATTERN = Pattern.compile("^[后终]记.*$");

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
        
        return CHAPTER_NUMBER_PATTERN.matcher(text).matches() ||
               CHAPTER_INDEX_PATTERN.matcher(text).matches() ||
               CHAPTER_SPECIAL_PATTERN.matcher(text).matches() ||
               CHAPTER_PREFIX_PATTERN.matcher(text).matches() ||
               PREFACE_PATTERN.matcher(text).matches() ||
               AFTERWORD_PATTERN.matcher(text).matches();
    }
} 