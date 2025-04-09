package com.lv.tool.privatereader.monitor;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器
 * 用于跟踪和记录关键操作的性能指标
 */
public class PerformanceMonitor {
    private static final Logger LOG = Logger.getInstance(PerformanceMonitor.class);
    
    private final Map<String, AtomicLong> operationTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    
    /**
     * 开始记录操作时间
     */
    public void startOperation(@NotNull String operation) {
        operationTimes.put(operation, new AtomicLong(System.currentTimeMillis()));
    }
    
    /**
     * 结束操作并记录耗时
     */
    public void endOperation(@NotNull String operation) {
        AtomicLong startTime = operationTimes.remove(operation);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime.get();
            operationCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
            LOG.info(String.format("操作 '%s' 耗时: %d ms", operation, duration));
        }
    }
    
    /**
     * 获取操作的平均耗时
     */
    public double getAverageOperationTime(@NotNull String operation) {
        AtomicLong count = operationCounts.get(operation);
        if (count == null || count.get() == 0) {
            return 0;
        }
        return (double) operationTimes.getOrDefault(operation, new AtomicLong(0)).get() / count.get();
    }
    
    /**
     * 获取所有操作的统计信息
     */
    public String getStats() {
        StringBuilder stats = new StringBuilder("性能统计:\n");
        operationCounts.forEach((operation, count) -> {
            double avgTime = getAverageOperationTime(operation);
            stats.append(String.format("- %s: 执行次数=%d, 平均耗时=%.2f ms\n",
                operation, count.get(), avgTime));
        });
        return stats.toString();
    }
    
    /**
     * 重置所有统计信息
     */
    public void reset() {
        operationTimes.clear();
        operationCounts.clear();
        LOG.info("性能统计已重置");
    }
} 