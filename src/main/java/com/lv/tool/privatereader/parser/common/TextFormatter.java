package com.lv.tool.privatereader.parser.common;

/**
 * 文本格式化工具
 * 用于处理小说文本的格式化，包括段落、对话、场景分隔符和标题的处理
 */
public class TextFormatter {
    private static final int MAX_PARAGRAPH_LENGTH = 500;
    private static final int MIN_PARAGRAPH_LENGTH = 10;
    
    private static final String[] PARAGRAPH_INDICATORS = {
        // 时间转换
        "一天", "这天", "那天", "某天", "第二天", "翌日", "次日", "当天", "后来", "此后", "从此",
        // 场景转换
        "这时", "此时", "这会", "不一会", "片刻", "一会儿", "一阵子", "转眼", "眨眼",
        // 视角转换
        "另一边", "与此同时", "同时", "这边", "那边", "远处", "不远处",
        // 情节转折
        "然而", "但是", "不过", "可是", "突然", "忽然", "猛然", "蓦地",
        // 人物动作
        "只见", "只听", "就见", "就听"
    };

    private static final String[] DIALOG_MARKERS = {
        "「", "」", "\u201C", "\u201D", "『", "』", "'", "'", "\"", "'"
    };

    private static final String[] PUNCTUATION_MARKS = {
        "，", "。", "！", "？", "；", "：", "、", "…"
    };

    /**
     * 格式化文本内容
     */
    public static String format(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        text = preprocess(text);
        text = formatParagraphs(text);
        text = postprocess(text);
        return text;
    }

    /**
     * 预处理文本
     */
    private static String preprocess(String text) {
        // 基础清理
        text = text.replaceAll("\\r\\n|\\r", "\n")
                  .replaceAll("[ 　\\t]+", " ")
                  .trim();
        
        // 标点规范化
        text = text.replaceAll("…{2,}|\\.\\.\\.", "……")
                  .replaceAll("!{2,}", "！！")
                  .replaceAll("\\?{2,}", "？？");
        
        // 在句末添加空格
        text = text.replaceAll("([。！？])(?=[^「」『』\\s])", "$1 ");
        text = text.replaceAll("([。！？])(?=\\n)", "$1 ");
        
        // 处理对话
        text = text.replaceAll("([。！？][「」『』])(?=[^，。！？])", "$1 ");
        
        // 处理标题
        text = text.replaceAll("(?m)^(第[零一二三四五六七八九十百千万]+[章节]\\s*[^\\n]+)$", 
            "\n\n        $1\n\n");
        
        return text;
    }

    /**
     * 格式化段落
     */
    private static String formatParagraphs(String text) {
        // 按基本规则分段
        String[] rawParagraphs = text.split(
            "\\n\\s*\\n+" +                     // 空行
            "|(?<=[。！？])(?=\\s*\\n)" +       // 句末换行
            "|(?<=[。！？])(?=[^，。！？]*?(" + String.join("|", PARAGRAPH_INDICATORS) + "))" // 指示词
        );
        
        StringBuilder result = new StringBuilder();
        boolean lastWasDialog = false;
        
        for (String para : rawParagraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;
            
            // 标题处理
            if (para.matches("^\\s*第[零一二三四五六七八九十百千万]+[章节].*$")) {
                result.append("\n    ").append(para).append("\n");
                lastWasDialog = false;
                continue;
            }
            
            // 对话段落处理
            if (containsDialog(para)) {
                result.append("    ").append(para).append("\n");
                lastWasDialog = true;
                continue;
            }
            
            // 长段落智能拆分
            if (para.length() > MAX_PARAGRAPH_LENGTH) {
                String[] sentences = para.split("(?<=[。！？])");
                StringBuilder temp = new StringBuilder();
                
                for (String sent : sentences) {
                    if (temp.length() + sent.length() > MAX_PARAGRAPH_LENGTH) {
                        if (temp.length() > 0) {
                            result.append("    ").append(temp).append("\n");
                            temp = new StringBuilder();
                        }
                    }
                    temp.append(sent);
                }
                
                if (temp.length() > 0) {
                    result.append("    ").append(temp).append("\n");
                }
                lastWasDialog = false;
                continue;
            }
            
            // 常规段落处理
            if (startsWithIndicator(para)) {
                result.append("    ").append(para).append("\n");
            } else if (lastWasDialog) {
                result.append("    ").append(para).append("\n");
            } else {
                result.append("    ").append(para).append("\n");
            }
            lastWasDialog = false;
        }
        
        return result.toString().trim();
    }

    /**
     * 判断是否包含对话
     */
    private static boolean containsDialog(String text) {
        return text.matches(".*[「『].*[」』].*") || 
               text.matches(".*[「『][^「『」』]+$") ||  // 未闭合的引号(跨段对话)
               text.startsWith("「") || text.startsWith("『");
    }

    /**
     * 判断是否以段落指示词开头
     */
    private static boolean startsWithIndicator(String text) {
        for (String indicator : PARAGRAPH_INDICATORS) {
            if (text.startsWith(indicator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 后处理文本
     */
    private static String postprocess(String text) {
        // 处理标点符号前后的空格，但保留句末空格
        for (String mark : PUNCTUATION_MARKS) {
            if (mark.equals("。") || mark.equals("！") || mark.equals("？")) {
                text = text.replaceAll("\\s*" + mark + "(?!\\s)", mark + " ");
            } else {
                text = text.replaceAll("\\s*" + mark + "\\s*", mark);
            }
        }
        
        // 处理对话标记前后的空格
        for (String marker : DIALOG_MARKERS) {
            text = text.replaceAll("\\s*" + marker + "\\s*", marker);
        }
        
        // 处理括号前后的空格
        text = text.replaceAll("\\s*([（）()《》「」『』]+)\\s*", "$1");
        
        return text;
    }
} 