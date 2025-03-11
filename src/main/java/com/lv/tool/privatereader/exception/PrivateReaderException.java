package com.lv.tool.privatereader.exception;

/**
 * 私人阅读器异常基类
 */
public class PrivateReaderException extends RuntimeException {
    
    /**
     * 异常类型
     */
    private final ExceptionType type;
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param type 异常类型
     */
    public PrivateReaderException(String message, ExceptionType type) {
        super(message);
        this.type = type;
    }
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 原始异常
     * @param type 异常类型
     */
    public PrivateReaderException(String message, Throwable cause, ExceptionType type) {
        super(message, cause);
        this.type = type;
    }
    
    /**
     * 获取异常类型
     * 
     * @return 异常类型
     */
    public ExceptionType getType() {
        return type;
    }
    
    /**
     * 异常类型枚举
     */
    public enum ExceptionType {
        /**
         * 网络错误
         */
        NETWORK_ERROR,
        
        /**
         * 解析错误
         */
        PARSE_ERROR,
        
        /**
         * 存储错误
         */
        STORAGE_ERROR,
        
        /**
         * 配置错误
         */
        CONFIG_ERROR,
        
        /**
         * 未知错误
         */
        UNKNOWN_ERROR
    }
}