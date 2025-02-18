package com.lv.tool.privatereader.parser.common;

import com.intellij.openapi.diagnostic.Logger;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.*;

/**
 * 文本密度分析器
 * 用于智能识别网页中的正文内容
 */
public class TextDensityAnalyzer {
    private static final Logger LOG = Logger.getInstance(TextDensityAnalyzer.class);
    private static final int MIN_TEXT_LENGTH = 50;
    private static final double TEXT_DENSITY_THRESHOLD = 0.3;
    private static final Set<String> CONTENT_TAGS = Set.of("article", "div", "p", "section");
    private static final Set<String> NOISE_CLASS_KEYWORDS = Set.of(
        "copyright", "footer", "header", "comment", "menu", "nav", "sidebar",
        "ad", "author", "meta", "recommend", "related", "share", "tag", "tool"
    );

    /**
     * 分析元素的文本密度
     */
    public static double getTextDensity(Element element) {
        String text = element.text();
        int textLength = text.length();
        int htmlLength = element.html().length();
        
        double density = htmlLength > 0 ? (double) textLength / htmlLength : 0;
        LOG.debug(String.format("文本密度分析 - 文本长度: %d, HTML长度: %d, 密度: %.2f", 
            textLength, htmlLength, density));
        return density;
    }

    /**
     * 智能识别正文内容元素
     */
    public static Element findContentElement(Element root) {
        LOG.debug("开始查找正文内容...");
        
        // 1. 首先尝试常见的内容容器class/id
        LOG.debug("尝试通过常见选择器查找...");
        Element element = findByCommonSelectors(root);
        if (isValidContentElement(element)) {
            LOG.info("通过常见选择器找到有效内容");
            return element;
        }

        // 2. 基于文本密度分析
        LOG.debug("尝试通过文本密度分析查找...");
        return findByTextDensity(root);
    }

    private static Element findByCommonSelectors(Element root) {
        // 常见的内容容器选择器
        String[] selectors = {
            "div.content", "div.article", "article", "div.post-content",
            "div.entry-content", "div.main-content", "div.article-content",
            "div#content", "div#article", "div.chapter-content",
            // 添加更多选择器
            "div.read-content", "div.chapter", "div.txt", "div.text",
            "div#BookText", "div#booktext", "div#htmlContent", "div#chaptercontent",
            "div.showtxt", "div#content_1", "div.box_con", "div.contentbox",
            // 88小说网特定选择器
            "div.content_read", "div.box_con #content", "div#content1"
        };

        for (String selector : selectors) {
            LOG.debug("尝试选择器: " + selector);
            Element element = root.selectFirst(selector);
            if (element != null) {
                LOG.debug("找到匹配元素");
                if (isValidContentElement(element)) {
                    LOG.debug("元素内容有效");
                    return element;
                }
            }
        }

        LOG.debug("未通过常见选择器找到有效内容");
        return null;
    }

    private static Element findByTextDensity(Element root) {
        List<Element> candidates = new ArrayList<>();
        
        // 收集所有可能的内容容器
        Elements elements = root.select(String.join(",", CONTENT_TAGS));
        LOG.debug("找到潜在内容容器数量: " + elements.size());
        
        for (Element element : elements) {
            if (isValidContentElement(element) && !containsNoiseKeywords(element)) {
                candidates.add(element);
                LOG.debug("添加候选内容容器，文本长度: " + element.text().length());
            }
        }

        // 按文本密度排序
        candidates.sort((a, b) -> Double.compare(
            getTextDensity(b) * b.text().length(),
            getTextDensity(a) * a.text().length()
        ));

        if (!candidates.isEmpty()) {
            Element best = candidates.get(0);
            LOG.info(String.format("找到最佳内容容器 - 文本长度: %d, 密度: %.2f", 
                best.text().length(), getTextDensity(best)));
            return best;
        }

        LOG.warn("未找到有效的内容容器");
        return null;
    }

    private static boolean isValidContentElement(Element element) {
        if (element == null) return false;
        
        String text = element.text().trim();
        if (text.length() < MIN_TEXT_LENGTH) {
            LOG.debug("内容长度不足: " + text.length());
            return false;
        }

        double density = getTextDensity(element);
        boolean valid = density >= TEXT_DENSITY_THRESHOLD;
        
        if (!valid) {
            LOG.debug("文本密度不足: " + density);
        }
        
        return valid;
    }

    private static boolean containsNoiseKeywords(Element element) {
        String classNames = element.className().toLowerCase();
        String id = element.id().toLowerCase();
        
        boolean hasNoise = NOISE_CLASS_KEYWORDS.stream()
            .anyMatch(keyword -> 
                classNames.contains(keyword) || id.contains(keyword));
                
        if (hasNoise) {
            LOG.debug("发现噪音关键词 - class: " + classNames + ", id: " + id);
        }
        
        return hasNoise;
    }
} 