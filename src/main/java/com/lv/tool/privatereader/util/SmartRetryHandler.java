package com.lv.tool.privatereader.util;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 智能重试处理器
 * 根据错误类型和网络状况动态调整重试策略
 */
public class SmartRetryHandler {
    private static final Logger LOG = Logger.getInstance(SmartRetryHandler.class);
    
    // 重试配置
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 10000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    
    // 错误类型统计
    private final AtomicInteger totalRetries = new AtomicInteger(0);
    private final AtomicInteger successfulRetries = new AtomicInteger(0);
    private final AtomicInteger failedRetries = new AtomicInteger(0);
    private final AtomicLong lastRetryTime = new AtomicLong(0);
    
    /**
     * 判断是否应该重试
     */
    public boolean shouldRetry(Throwable error, int attemptCount, int maxRetries) {
        if (attemptCount >= maxRetries) {
            LOG.debug("[智能重试] 已达到最大重试次数: " + attemptCount + "/" + maxRetries);
            return false;
        }
        
        // 检查错误类型
        if (isRetryableError(error)) {
            // 检查网络状况
            if (NetworkUtils.isNetworkAvailable()) {
                LOG.debug("[智能重试] 错误可重试且网络可用，将进行重试");
                return true;
            } else {
                LOG.warn("[智能重试] 错误可重试但网络不可用，跳过重试");
                return false;
            }
        } else {
            LOG.debug("[智能重试] 错误不可重试: " + error.getClass().getSimpleName());
            return false;
        }
    }
    
    /**
     * 计算重试延迟时间
     */
    public long calculateRetryDelay(int attemptCount, long baseDelayMs) {
        // 指数退避算法
        long delay = (long) (baseDelayMs * Math.pow(BACKOFF_MULTIPLIER, attemptCount));
        
        // 添加随机抖动，避免多个请求同时重试
        double jitter = 0.1 + Math.random() * 0.2; // 10%-30%的随机抖动
        delay = (long) (delay * jitter);
        
        // 限制最大延迟
        delay = Math.min(delay, MAX_DELAY_MS);
        
        LOG.debug("[智能重试] 计算重试延迟: 尝试次数=" + attemptCount + ", 延迟=" + delay + "ms");
        return delay;
    }
    
    /**
     * 判断错误是否可重试
     */
    private boolean isRetryableError(Throwable error) {
        // 网络相关错误通常可以重试
        if (error instanceof SocketTimeoutException) {
            LOG.debug("[智能重试] 超时错误，可重试");
            return true;
        }
        
        if (error instanceof ConnectException) {
            LOG.debug("[智能重试] 连接错误，可重试");
            return true;
        }
        
        if (error instanceof UnknownHostException) {
            LOG.debug("[智能重试] 主机名解析错误，可重试");
            return true;
        }
        
        // IO错误可能是临时的
        if (error instanceof IOException) {
            String message = error.getMessage();
            if (message != null) {
                message = message.toLowerCase();
                // 检查是否是临时性错误
                if (message.contains("connection reset") ||
                    message.contains("broken pipe") ||
                    message.contains("network is unreachable") ||
                    message.contains("no route to host")) {
                    LOG.debug("[智能重试] 临时IO错误，可重试: " + message);
                    return true;
                }
            }
        }
        
        // 运行时异常可能是临时的
        if (error instanceof RuntimeException) {
            String message = error.getMessage();
            if (message != null) {
                message = message.toLowerCase();
                if (message.contains("timeout") ||
                    message.contains("connection") ||
                    message.contains("network")) {
                    LOG.debug("[智能重试] 临时运行时错误，可重试: " + message);
                    return true;
                }
            }
        }
        
        LOG.debug("[智能重试] 错误不可重试: " + error.getClass().getSimpleName() + " - " + error.getMessage());
        return false;
    }
    
    /**
     * 记录重试事件
     */
    public void recordRetry(boolean success) {
        totalRetries.incrementAndGet();
        if (success) {
            successfulRetries.incrementAndGet();
        } else {
            failedRetries.incrementAndGet();
        }
        lastRetryTime.set(System.currentTimeMillis());
    }
    
    /**
     * 获取重试统计信息
     */
    public String getRetryStats() {
        int total = totalRetries.get();
        int success = successfulRetries.get();
        int failed = failedRetries.get();
        
        if (total == 0) {
            return "暂无重试统计";
        }
        
        double successRate = (double) success / total * 100;
        return String.format("总重试: %d, 成功: %d (%.1f%%), 失败: %d", 
                total, success, successRate, failed);
    }
    
    /**
     * 重置重试统计
     */
    public void resetRetryStats() {
        totalRetries.set(0);
        successfulRetries.set(0);
        failedRetries.set(0);
        lastRetryTime.set(0);
        LOG.info("[智能重试] 重试统计已重置");
    }
    
    /**
     * 创建重试条件
     */
    public Predicate<Throwable> createRetryCondition(int maxRetries) {
        return error -> {
            // 这里可以根据需要添加更复杂的重试逻辑
            return isRetryableError(error);
        };
    }
    
    /**
     * 获取建议的重试次数
     */
    public int getSuggestedRetryCount(String errorType) {
        switch (errorType.toLowerCase()) {
            case "timeout":
                return 2; // 超时错误重试2次
            case "connection":
                return 3; // 连接错误重试3次
            case "unknown_host":
                return 1; // 主机名错误重试1次
            default:
                return DEFAULT_MAX_RETRIES;
        }
    }
} 