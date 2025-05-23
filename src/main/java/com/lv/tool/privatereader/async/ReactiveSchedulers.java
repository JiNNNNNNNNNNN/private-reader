package com.lv.tool.privatereader.async;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 响应式调度器管理类
 * 优化和统一项目中的调度器使用
 */
public class ReactiveSchedulers {
    private static final Logger LOG = Logger.getInstance(ReactiveSchedulers.class);
    
    // 单例实例
    private static final ReactiveSchedulers INSTANCE = new ReactiveSchedulers();
    
    // 调度器类型
    public enum SchedulerType {
        IO,         // I/O密集型操作（网络请求、文件读写）
        COMPUTE,    // CPU密集型操作（数据处理、计算）
        UI,         // UI线程操作
        BACKGROUND, // 后台任务（低优先级）
        TIMER,      // 定时任务
        PLATFORM    // 平台调度器
    }
    
    // 自定义线程工厂
    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);
        
        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
    
    // 调度器实例
    private final Scheduler ioScheduler;
    private final Scheduler computeScheduler;
    private final Scheduler uiScheduler;
    private final Scheduler backgroundScheduler;
    private final Scheduler timerScheduler;
    private final Scheduler platformScheduler;
    
    // 监控数据
    private final AtomicLong ioTaskCount = new AtomicLong(0);
    private final AtomicLong computeTaskCount = new AtomicLong(0);
    private final AtomicLong uiTaskCount = new AtomicLong(0);
    private final AtomicLong backgroundTaskCount = new AtomicLong(0);
    private final AtomicLong timerTaskCount = new AtomicLong(0);
    private final AtomicLong platformTaskCount = new AtomicLong(0);
    
    // 监控线程
    private final ScheduledExecutorService monitorExecutor;
    
    private ReactiveSchedulers() {
        // 创建自定义调度器
        ioScheduler = Schedulers.fromExecutorService(
            Executors.newFixedThreadPool(
                calculateOptimalThreads(1.5), // I/O密集型任务，线程数可以多一些
                new NamedThreadFactory("PrivateReader-IO")
            )
        );
        
        computeScheduler = Schedulers.fromExecutorService(
            Executors.newFixedThreadPool(
                calculateOptimalThreads(1.0), // CPU密集型任务，线程数等于CPU核心数
                new NamedThreadFactory("PrivateReader-Compute")
            )
        );
        
        uiScheduler = Schedulers.fromExecutor(
            ApplicationManager.getApplication()::invokeLater
        );
        
        backgroundScheduler = Schedulers.fromExecutorService(
            Executors.newFixedThreadPool(
                2, // 后台任务，使用较少的线程
                new NamedThreadFactory("PrivateReader-Background")
            )
        );
        
        timerScheduler = Schedulers.fromExecutorService(
            Executors.newScheduledThreadPool(
                1,
                new NamedThreadFactory("PrivateReader-Timer")
            )
        );
        
        // 新增：创建平台调度器，使用ApplicationManager的线程池
        platformScheduler = Schedulers.fromExecutor(
            ApplicationManager.getApplication()::executeOnPooledThread
        );
        
        // 初始化监控
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("PrivateReader-Monitor")
        );
        
        // 启动监控任务
        startMonitoring();
        
        LOG.info("ReactiveSchedulers 初始化完成");
    }
    
    /**
     * 获取单例实例
     */
    public static ReactiveSchedulers getInstance() {
        return INSTANCE;
    }
    
    /**
     * 根据调度器类型获取对应的调度器
     */
    public Scheduler get(SchedulerType type) {
        switch (type) {
            case IO:
                ioTaskCount.incrementAndGet();
                return ioScheduler;
            case COMPUTE:
                computeTaskCount.incrementAndGet();
                return computeScheduler;
            case UI:
                uiTaskCount.incrementAndGet();
                return uiScheduler;
            case BACKGROUND:
                backgroundTaskCount.incrementAndGet();
                return backgroundScheduler;
            case TIMER:
                timerTaskCount.incrementAndGet();
                return timerScheduler;
            case PLATFORM:
                platformTaskCount.incrementAndGet();
                return platformScheduler;
            default:
                LOG.warn("未知的调度器类型: " + type + "，使用IO调度器代替");
                ioTaskCount.incrementAndGet();
                return ioScheduler;
        }
    }
    
    /**
     * 获取I/O调度器
     * 适用于网络请求、文件读写等I/O密集型操作
     */
    public Scheduler io() {
        ioTaskCount.incrementAndGet();
        return ioScheduler;
    }
    
    /**
     * 获取计算调度器
     * 适用于数据处理、计算等CPU密集型操作
     */
    public Scheduler compute() {
        computeTaskCount.incrementAndGet();
        return computeScheduler;
    }
    
    /**
     * 获取UI调度器
     * 适用于UI更新操作
     */
    public Scheduler ui() {
        uiTaskCount.incrementAndGet();
        return uiScheduler;
    }
    
    /**
     * 获取后台调度器
     * 适用于低优先级的后台任务
     */
    public Scheduler background() {
        backgroundTaskCount.incrementAndGet();
        return backgroundScheduler;
    }
    
    /**
     * 获取定时调度器
     * 适用于定时任务
     */
    public Scheduler timer() {
        timerTaskCount.incrementAndGet();
        return timerScheduler;
    }
    
    /**
     * 获取平台调度器
     * 适用于需要与IntelliJ平台交互且可能阻塞的操作（如 getService）
     */
    public Scheduler platformThread() {
        platformTaskCount.incrementAndGet();
        return platformScheduler;
    }
    
    /**
     * 延迟执行任务
     * 
     * @param delay 延迟时间
     * @return 延迟操作符
     */
    public <T> Mono<T> delayExecution(Mono<T> mono, Duration delay) {
        return mono.delaySubscription(delay, timerScheduler);
    }
    
    /**
     * 在UI线程上执行操作
     * 
     * @param runnable 要执行的操作
     */
    public void runOnUI(Runnable runnable) {
        uiTaskCount.incrementAndGet();
        ApplicationManager.getApplication().invokeLater(runnable);
    }
    
    /**
     * 计算最优线程数
     * 
     * @param factor 线程数系数
     * @return 最优线程数
     */
    private int calculateOptimalThreads(double factor) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, (int) (cpuCores * factor));
    }
    
    /**
     * 启动监控任务
     */
    private void startMonitoring() {
        monitorExecutor.scheduleAtFixedRate(
            this::monitorSchedulers,
            1,
            5,
            TimeUnit.MINUTES
        );
    }
    
    /**
     * 监控调度器使用情况
     */
    private void monitorSchedulers() {
        try {
            long io = ioTaskCount.get();
            long compute = computeTaskCount.get();
            long ui = uiTaskCount.get();
            long background = backgroundTaskCount.get();
            long timer = timerTaskCount.get();
            long platform = platformTaskCount.get();
            
            LOG.info("调度器使用情况 - IO: " + io + 
                     ", 计算: " + compute + 
                     ", UI: " + ui + 
                     ", 后台: " + background + 
                     ", 定时: " + timer +
                     ", 平台: " + platform);
            
            // 检查内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            LOG.info("内存使用情况: " + String.format("%.2f", memoryUsagePercent) + "% (" + 
                     formatSize(usedMemory) + "/" + formatSize(maxMemory) + ")");
            
            // 如果内存使用率过高，触发GC
            if (memoryUsagePercent > 75) {
                LOG.warn("内存使用率过高，触发GC");
                System.gc();
            }
        } catch (Exception e) {
            LOG.error("监控调度器时发生错误", e);
        }
    }
    
    /**
     * 格式化内存大小
     */
    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        try {
            LOG.info("关闭ReactiveSchedulers...");
            
            // 关闭监控线程
            monitorExecutor.shutdown();
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
            
            // 关闭自定义调度器
            ioScheduler.dispose();
            computeScheduler.dispose();
            backgroundScheduler.dispose();
            timerScheduler.dispose();
            platformScheduler.dispose();
            
            LOG.info("ReactiveSchedulers已关闭");
        } catch (Exception e) {
            LOG.error("关闭ReactiveSchedulers时发生错误", e);
        }
    }
} 