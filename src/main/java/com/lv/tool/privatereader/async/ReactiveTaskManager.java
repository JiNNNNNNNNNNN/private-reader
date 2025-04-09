package com.lv.tool.privatereader.async;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 响应式任务管理器
 * 使用Project Reactor实现响应式异步任务处理
 */
public class ReactiveTaskManager {
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 1000;
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    
    private final Map<String, TaskInfo<?>> taskMetrics = new ConcurrentHashMap<>();
    private final Map<String, Mono<?>> runningTasks = new ConcurrentHashMap<>();
    
    private static class SingletonHolder {
        private static final ReactiveTaskManager INSTANCE = new ReactiveTaskManager();
    }
    
    public static ReactiveTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    private static class TaskInfo<T> {
        final AtomicLong totalExecutionTime = new AtomicLong();
        final AtomicInteger executionCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();
        final AtomicInteger retryCount = new AtomicInteger();
        volatile Throwable lastError;
        volatile long lastExecutionTime;
        
        void recordExecution(long executionTime) {
            totalExecutionTime.addAndGet(executionTime);
            executionCount.incrementAndGet();
            lastExecutionTime = System.currentTimeMillis();
        }
        
        void recordFailure(Throwable error) {
            failureCount.incrementAndGet();
            lastError = error;
        }
        
        void recordRetry() {
            retryCount.incrementAndGet();
        }
        
        Map<String, Object> getMetrics() {
            return Map.of(
                "totalExecutionTime", totalExecutionTime.get(),
                "executionCount", executionCount.get(),
                "failureCount", failureCount.get(),
                "retryCount", retryCount.get(),
                "lastError", lastError != null ? lastError.getMessage() : "none",
                "lastExecutionTime", lastExecutionTime
            );
        }
    }
    
    private ReactiveTaskManager() {
        // 启动监控任务
        Flux.interval(Duration.ofMinutes(1))
            .doOnNext(tick -> monitorTasks())
            .subscribe();
    }
    
    /**
     * 提交任务并返回响应式Mono
     * @param taskName 任务名称
     * @param task 任务供应商
     * @param options 任务选项
     * @return 包含任务结果的Mono
     */
    public <T> Mono<T> submitTask(String taskName, Supplier<T> task, TaskOptions options) {
        TaskInfo<T> taskInfo = (TaskInfo<T>) taskMetrics.computeIfAbsent(taskName, k -> new TaskInfo<>());
        
        Mono<T> taskMono = Mono.fromSupplier(task)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSubscribe(s -> System.out.println("开始执行任务: " + taskName))
            .doOnSuccess(result -> {
                long executionTime = System.currentTimeMillis() - taskInfo.lastExecutionTime;
                taskInfo.recordExecution(executionTime);
                System.out.println("任务执行成功: " + taskName);
            })
            .doOnError(error -> {
                taskInfo.recordFailure(error);
                System.out.println("任务执行失败: " + taskName + ", 错误: " + error.getMessage());
                if (options.errorHandler != null) {
                    options.errorHandler.accept(error);
                }
            })
            .doFinally(signalType -> runningTasks.remove(taskName));
        
        // 添加重试逻辑
        if (options.maxRetries > 0) {
            taskMono = taskMono.retryWhen(
                Retry.backoff(options.maxRetries, Duration.ofMillis(options.retryDelayMs))
                    .doBeforeRetry(retrySignal -> {
                        taskInfo.recordRetry();
                        System.out.println("重试任务: " + taskName + ", 第" + retrySignal.totalRetries() + "次");
                    })
            );
        }
        
        // 添加超时控制
        if (options.timeoutMs > 0) {
            taskMono = taskMono.timeout(Duration.ofMillis(options.timeoutMs));
        }
        
        // 缓存任务引用
        runningTasks.put(taskName, taskMono);
        
        return taskMono;
    }
    
    /**
     * 使用默认选项提交任务
     * @param taskName 任务名称
     * @param task 任务供应商
     * @return 包含任务结果的Mono
     */
    public <T> Mono<T> submitTask(String taskName, Supplier<T> task) {
        return submitTask(taskName, task, new TaskOptions());
    }
    
    /**
     * 任务选项配置类
     */
    public static class TaskOptions {
        int priority = 0;
        int maxRetries = DEFAULT_MAX_RETRIES;
        long retryDelayMs = DEFAULT_RETRY_DELAY_MS;
        long timeoutMs = DEFAULT_TIMEOUT_MS;
        Consumer<Throwable> errorHandler = null;
        
        public TaskOptions setPriority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public TaskOptions setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public TaskOptions setRetryDelay(long delayMs) {
            this.retryDelayMs = delayMs;
            return this;
        }
        
        public TaskOptions setTimeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }
        
        public TaskOptions setErrorHandler(Consumer<Throwable> handler) {
            this.errorHandler = handler;
            return this;
        }
    }
    
    /**
     * 监控任务执行情况
     */
    private void monitorTasks() {
        try {
            int activeCount = runningTasks.size();
            
            System.out.printf("Reactive Task Pool Status: active=%d%n", activeCount);
            
            taskMetrics.forEach((taskName, info) -> {
                System.out.printf("Task '%s' metrics: %s%n", taskName, info.getMetrics());
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 取消正在执行的任务
     * @param taskName 任务名称
     * @return 是否成功取消
     */
    public boolean cancelTask(String taskName) {
        Mono<?> task = runningTasks.remove(taskName);
        return task != null;
    }
    
    /**
     * 检查任务是否正在运行
     * @param taskName 任务名称
     * @return 是否正在运行
     */
    public boolean isTaskRunning(String taskName) {
        return runningTasks.containsKey(taskName);
    }
    
    /**
     * 取消以指定前缀开头的所有任务
     * @param prefix 任务名称前缀
     * @return 是否成功取消任何任务
     */
    public boolean cancelTasksByPrefix(String prefix) {
        boolean cancelled = false;
        for (String taskName : runningTasks.keySet()) {
            if (taskName.startsWith(prefix)) {
                Mono<?> task = runningTasks.remove(taskName);
                if (task != null) {
                    cancelled = true;
                }
            }
        }
        return cancelled;
    }
    
    /**
     * 获取指定任务的度量信息
     * @param taskName 任务名称
     * @return 度量信息
     */
    public Map<String, Object> getTaskMetrics(String taskName) {
        TaskInfo<?> info = taskMetrics.get(taskName);
        return info != null ? info.getMetrics() : Map.of();
    }
    
    /**
     * 获取所有任务的度量信息
     * @return 所有任务的度量信息
     */
    public Map<String, Map<String, Object>> getAllTaskMetrics() {
        Map<String, Map<String, Object>> allMetrics = new ConcurrentHashMap<>();
        taskMetrics.forEach((taskName, info) -> 
            allMetrics.put(taskName, info.getMetrics()));
        return allMetrics;
    }
    
    /**
     * 关闭任务管理器
     */
    public void shutdown() {
        // Reactor不需要显式关闭，但可以取消所有正在运行的任务
        runningTasks.clear();
    }
} 