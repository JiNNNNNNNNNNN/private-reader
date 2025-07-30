package com.lv.tool.privatereader.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.exception.PrivateReaderException;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 响应式任务管理器
 * 使用响应式编程处理异步任务
 * 
 * 现在使用全局的ReactiveTaskManager作为实现，以统一异步处理模型
 */
@Singleton
public class ReactiveTaskManager {
    private static final Logger LOG = Logger.getInstance(ReactiveTaskManager.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    // 使用全局的ReactiveTaskManager，使用完全限定名称
    private final com.lv.tool.privatereader.async.ReactiveTaskManager globalTaskManager;
    private final Scheduler scheduler;
    
    public ReactiveTaskManager() {
        // 获取全局ReactiveTaskManager实例
        this.globalTaskManager = com.lv.tool.privatereader.async.ReactiveTaskManager.getInstance();
        // 创建有界弹性调度器，适用于I/O密集型操作
        this.scheduler = Schedulers.boundedElastic();
        LOG.info("响应式任务管理器初始化完成");
    }
    
    /**
     * 提交响应式任务
     *
     * @param taskSupplier 任务提供者
     * @param <T>          结果类型
     * @return 包含结果的Mono
     */
    public <T> Mono<T> submitTask(@NotNull Supplier<Mono<T>> taskSupplier) {
        return submitTask(taskSupplier, DEFAULT_TIMEOUT_SECONDS);
    }
    
    /**
     * 提交响应式任务，带超时
     *
     * @param taskSupplier   任务提供者
     * @param timeoutSeconds 超时时间（秒）
     * @param <T>            结果类型
     * @return 包含结果的Mono
     */
    public <T> Mono<T> submitTask(@NotNull Supplier<Mono<T>> taskSupplier, int timeoutSeconds) {
        // 使用全局ReactiveTaskManager的功能
        return Mono.defer(taskSupplier)
            .subscribeOn(scheduler)
            .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .doOnError(e -> LOG.warn("任务执行失败", e))
            .onErrorMap(e -> {
                if (e instanceof java.util.concurrent.TimeoutException) {
                    return new PrivateReaderException("任务执行超时", e, PrivateReaderException.ExceptionType.NETWORK_TIMEOUT);
                }
                return new PrivateReaderException("任务执行失败: " + e.getMessage(), e, PrivateReaderException.ExceptionType.UNKNOWN_ERROR);
            });
    }
    
    /**
     * 提交阻塞任务
     *
     * @param task 阻塞任务
     * @param <T>  结果类型
     * @return 包含结果的Mono
     */
    public <T> Mono<T> submitBlockingTask(@NotNull Callable<T> task) {
        return submitBlockingTask(task, DEFAULT_TIMEOUT_SECONDS);
    }
    
    /**
     * 提交阻塞任务，带超时
     *
     * @param task           阻塞任务
     * @param timeoutSeconds 超时时间（秒）
     * @param <T>            结果类型
     * @return 包含结果的Mono
     */
    public <T> Mono<T> submitBlockingTask(@NotNull Callable<T> task, int timeoutSeconds) {
        // 使用全局ReactiveTaskManager的功能
        com.lv.tool.privatereader.async.ReactiveTaskManager.TaskOptions options = 
            new com.lv.tool.privatereader.async.ReactiveTaskManager.TaskOptions()
                .setTimeout(timeoutSeconds * 1000L);
            
        return globalTaskManager.submitTask(
            "blocking-task-" + System.currentTimeMillis(),
            () -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            options
        );
    }
    
    /**
     * 提交异步任务，返回CompletableFuture
     * 用于兼容现有代码
     *
     * @param task 任务
     * @param <T>  结果类型
     * @return 包含结果的CompletableFuture
     */
    public <T> CompletableFuture<T> submitCompletableFutureTask(@NotNull Callable<T> task) {
        return submitBlockingTask(task).toFuture();
    }
    
    /**
     * 提交异步任务，返回CompletableFuture，带超时
     * 用于兼容现有代码
     *
     * @param task           任务
     * @param timeoutSeconds 超时时间（秒）
     * @param <T>            结果类型
     * @return 包含结果的CompletableFuture
     */
    public <T> CompletableFuture<T> submitCompletableFutureTask(@NotNull Callable<T> task, int timeoutSeconds) {
        return submitBlockingTask(task, timeoutSeconds).toFuture();
    }
    
    /**
     * 将CompletableFuture转换为Mono
     *
     * @param future CompletableFuture
     * @param <T>    结果类型
     * @return 包含结果的Mono
     */
    public <T> Mono<T> fromCompletableFuture(@NotNull CompletableFuture<T> future) {
        return Mono.fromFuture(future)
            .doOnError(e -> LOG.warn("CompletableFuture执行失败", e))
            .onErrorMap(e -> new PrivateReaderException("CompletableFuture执行失败: " + e.getMessage(), e, PrivateReaderException.ExceptionType.UNKNOWN_ERROR));
    }
    
    /**
     * 获取调度器
     *
     * @return 调度器
     */
    public Scheduler getScheduler() {
        return scheduler;
    }
} 