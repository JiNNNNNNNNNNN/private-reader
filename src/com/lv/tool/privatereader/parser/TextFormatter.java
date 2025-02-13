package com.lv.tool.privatereader.parser;

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
        text = text.replaceAll("[ \t]+", " ").trim();
        
        // 处理场景分隔符，确保场景分隔符独立成段
        text = text.replaceAll("(?m)^[ \t]*\\*{3,}[ \t]*$", 
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
        String[] paragraphs = text.split("\n\\s*\n");
        boolean inDialog = false;
        String lastParagraph = "";
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            
            // 检查是否是场景分隔符或标题分隔符
            if (paragraph.equals(SCENE_BREAK) || paragraph.equals(TITLE_BREAK)) {
                if (i > 0) {
                    result.append("\n\n");
                }
                result.append(centerText(paragraph)).append("\n\n");
                lastParagraph = paragraph;
                continue;
            }
            
            // 检查是否是标题
            if (paragraph.matches("第[零一二三四五六七八九十百千万]+[章节].*")) {
                if (i > 0) {
                    result.append("\n\n");
                }
                result.append(centerText(paragraph)).append("\n\n");
                lastParagraph = paragraph;
                continue;
            }
            
            // 处理对话
            boolean startsWithDialog = paragraph.startsWith("\u201c");
            boolean endsWithDialog = paragraph.endsWith("\u201d");
            
            // 处理段落
            if (startsWithDialog) {
                inDialog = true;
            }
            
            // 添加段落
            if (i > 0) {
                // 检查是否是连续对话
                if (inDialog && startsWithDialog && lastParagraph.endsWith("\u201d")) {
                    // 如果是连续对话，只添加一个空格
                    result.append(" ");
                } else if (!lastParagraph.equals(SCENE_BREAK) && !lastParagraph.equals(TITLE_BREAK)) {
                    // 如果上一段不是分隔符或标题，添加空行
                    result.append("\n\n");
                }
            }
            
            result.append(paragraph);
            lastParagraph = paragraph;
            
            if (endsWithDialog) {
                inDialog = false;
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * 将文本居中对齐
     * @param text 要居中的文本
     * @return 居中后的文本
     */
    private static String centerText(String text) {
        int width = 60;  // 设置固定宽度
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text;
    }
} 