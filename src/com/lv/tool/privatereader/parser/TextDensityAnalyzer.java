package com.lv.tool.privatereader.parser;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 文本密度分析器
 * 用于智能识别网页中的主要内容区域
 */
public final class TextDensityAnalyzer {
    private static final int MIN_TEXT_LENGTH = 100;
    private static final double MIN_TEXT_DENSITY = 0.5;
    private static final double MIN_PUNCTUATION_DENSITY = 0.02;
    private static final String PUNCTUATION_REGEX = "[\\u3002\\uff01\\uff1f\\uff0c\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09\\u300a\\u300b\\u3008\\u3009\\u3010\\u3011\\u300e\\u300f\\u300c\\u300d\\ufe43\\ufe44\\u3014\\u3015\\u2026\\u2014\\uff5e\\ufe4f\\uffe5]";

    /**
     * 分析元素的文本密度得分
     */
    public static double analyzeTextDensity(Element element) {
        String text = element.text();
        int textLength = text.length();
        int tagCount = element.getAllElements().size();
        int linkTextLength = element.select("a").stream()
                .mapToInt(e -> e.text().length())
                .sum();
        
        // 计算文本密度
        double density = (double) (textLength - linkTextLength) / (tagCount + 1);
        
        // 计算标点符号密度
        long punctuationCount = text.chars()
                .mapToObj(ch -> String.valueOf((char) ch))
                .filter(ch -> ch.matches(PUNCTUATION_REGEX))
                .count();
        double punctuationDensity = (double) punctuationCount / textLength;
        
        // 如果文本太短或标点符号太少，降低得分
        if (textLength < MIN_TEXT_LENGTH || punctuationDensity < MIN_PUNCTUATION_DENSITY) {
            density *= 0.5;
        }
        
        return density;
    }

    /**
     * 查找最可能的正文内容元素
     */
    public static Element findMainContent(Element root) {
        Elements paragraphs = root.select("p, div");
        Element bestMatch = null;
        double maxScore = 0;
        
        for (Element element : paragraphs) {
            // 跳过导航、页脚等区域
            if (isNoiseElement(element)) {
                continue;
            }
            
            double score = analyzeTextDensity(element);
            if (score > maxScore && score >= MIN_TEXT_DENSITY) {
                maxScore = score;
                bestMatch = element;
            }
        }
        
        return bestMatch;
    }

    /**
     * 判断是否是干扰元素
     */
    private static boolean isNoiseElement(Element element) {
        String classNames = element.className().toLowerCase();
        String id = element.id().toLowerCase();
        
        // 检查常见的干扰元素类名和ID
        return classNames.contains("header") ||
               classNames.contains("footer") ||
               classNames.contains("sidebar") ||
               classNames.contains("comment") ||
               classNames.contains("menu") ||
               classNames.contains("nav") ||
               classNames.contains("ad") ||
               id.contains("header") ||
               id.contains("footer") ||
               id.contains("sidebar") ||
               id.contains("comment") ||
               id.contains("menu") ||
               id.contains("nav");
    }
} 