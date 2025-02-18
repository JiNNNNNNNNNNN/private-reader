package com.lv.tool.privatereader.parser.common;

/**
 * 文本格式化工具
 * 用于处理小说文本的格式化，包括段落、对话、场景分隔符和标题的处理
 */
public class TextFormatter {
    private static final String SCENE_BREAK = "══════════════";
    private static final String TITLE_BREAK = "══════════════";

    /**
     * 格式化文本内容
     * @param text 要格式化的文本
     * @return 格式化后的文本
     */
    public static String format(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        text = preprocess(text);
        return formatParagraphs(text);
    }

    /**
     * 预处理文本，处理换行符、空白字符、场景分隔符和标题
     */
    private static String preprocess(String text) {
        // 标准化换行符
        text = text.replaceAll("\\r\\n|\\r", "\n");
        
        // 移除多余的空白字符
        text = text.replaceAll("[ \\t]+", " ").trim();
        
        // 处理场景分隔符，确保场景分隔符独立成段
        text = text.replaceAll("(?m)^[ \\t]*\\*{3,}[ \\t]*$", 
            "\n\n" + centerText(SCENE_BREAK) + "\n\n");
        
        // 处理标题，确保标题独立成段并美化格式
        text = text.replaceAll("(?m)^(第[零一二三四五六七八九十百千万]+[章节]\\s*[^\\n]+)$", 
            "\n\n" + centerText(TITLE_BREAK) + "\n" + centerText("$1") + "\n" + centerText(TITLE_BREAK) + "\n\n");
        
        // 确保段落之间有适当的空行
        text = text.replaceAll("\n{3,}", "\n\n");
        
        return text;
    }

    /**
     * 格式化段落，处理对话、场景分隔符和标题的排版
     */
    private static String formatParagraphs(String text) {
        StringBuilder result = new StringBuilder();
        String[] paragraphs = text.split("\n\n");
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            
            // 处理对话段落
            if (paragraph.startsWith("\u201C") || paragraph.startsWith("\u300C")) {
                result.append("    ").append(paragraph);
            }
            // 处理场景分隔符和标题
            else if (paragraph.contains(SCENE_BREAK) || paragraph.contains(TITLE_BREAK)) {
                result.append(paragraph);
            }
            // 普通段落
            else {
                result.append("    ").append(paragraph);
            }
            result.append("\n\n");
        }
        
        return result.toString().trim();
    }
    
    /**
     * 文本居中显示
     */
    private static String centerText(String text) {
        return String.format("%n%s%n", text);
    }
} 