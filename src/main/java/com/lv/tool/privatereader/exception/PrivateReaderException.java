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
         * 网络超时
         */
        NETWORK_TIMEOUT,

        /**
         * 网络连接被拒绝
         */
        NETWORK_CONNECTION_REFUSED,

        /**
         * SSL/TLS错误
         */
        NETWORK_SSL_ERROR,

        /**
         * 解析错误
         */
        PARSE_ERROR,
        
        /**
         * HTML解析错误
         */
        HTML_PARSE_ERROR,

        /**
         * JSON解析错误
         */
        JSON_PARSE_ERROR,

        /**
         * 存储错误
         */
        STORAGE_ERROR,

        /**
         * 文件读取错误
         */
        FILE_READ_ERROR,

        /**
         * 文件写入错误
         */
        FILE_WRITE_ERROR,
        
        /**
         * 配置错误
         */
        CONFIG_ERROR,

        /**
         * 权限错误
         */
        PERMISSION_ERROR,

        /**
         * 资源不存在
         */
        RESOURCE_NOT_FOUND,

        /**
         * 并发错误
         */
        CONCURRENCY_ERROR,

        /**
         * 未知错误
         */
        UNKNOWN_ERROR
    }
}