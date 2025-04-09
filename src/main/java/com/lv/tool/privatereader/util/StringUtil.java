package com.lv.tool.privatereader.util;

/**
 * 字符串工具类
 * 提供常用的字符串操作方法
 */
public class StringUtil {
    
    /**
     * 检查字符串是否为空或null
     * @param str 要检查的字符串
     * @return 如果字符串为null或空字符串，返回true；否则返回false
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 检查字符串是否不为空且不为null
     * @param str 要检查的字符串
     * @return 如果字符串不为null且不为空字符串，返回true；否则返回false
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
    
    /**
     * 获取字符串的安全长度，避免NPE
     * @param str 要获取长度的字符串
     * @return 字符串的长度，如果字符串为null则返回0
     */
    public static int safeLength(String str) {
        return str == null ? 0 : str.length();
    }
    
    /**
     * 安全地获取字符串，如果为null则返回空字符串
     * @param str 要处理的字符串
     * @return 原字符串，如果为null则返回空字符串
     */
    public static String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
    
    /**
     * 安全地截取字符串，处理各种边界情况
     * @param str 要截取的字符串
     * @param start 起始位置（包含）
     * @param end 结束位置（不包含）
     * @return 截取后的字符串，如果参数无效则返回空字符串
     */
    public static String safeSubstring(String str, int start, int end) {
        if (str == null) {
            return "";
        }
        
        int length = str.length();
        if (start < 0) {
            start = 0;
        }
        if (end > length) {
            end = length;
        }
        if (start > end) {
            return "";
        }
        
        return str.substring(start, end);
    }
} 