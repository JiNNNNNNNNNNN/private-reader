package com.lv.tool.privatereader.initialization;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.lv.tool.privatereader.service.ReaderModeSwitcher;
import com.lv.tool.privatereader.storage.StorageManager;
import com.lv.tool.privatereader.storage.DatabaseManager;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用 StartupActivity 替代 AppLifecycleListener 来执行应用启动后的初始化任务。
 * 这是一个过渡方案，未来可能需要迁移到 ProjectActivity（需要 Kotlin）。
 *
 * 实现 DumbAware 接口以允许在索引过程中运行（因为我们的初始化任务不依赖于索引）。
 */
public class PrivateReaderStartupActivity implements StartupActivity, DumbAware {

    private static final Logger LOG = Logger.getInstance(PrivateReaderStartupActivity.class);
    private static boolean initialized = false;

    @Override
    public void runActivity(@NotNull Project project) {
        // 确保只初始化一次，因为 runActivity 会为每个打开的项目调用一次
        if (!initialized) {
            LOG.info("PrivateReaderStartupActivity: runActivity() called for the first time.");
            initialized = true;

            // 使用CompletableFuture进行并行初始化
            CompletableFuture<Void> initFuture = CompletableFuture.runAsync(() -> {
                try {
                    LOG.info("PrivateReaderStartupActivity: 开始并行初始化关键服务...");
                    
                    // 并行初始化关键服务
                    CompletableFuture<ReaderModeSwitcher> switcherFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            LOG.info("PrivateReaderStartupActivity: 初始化 ReaderModeSwitcher 服务...");
                            ReaderModeSwitcher switcher = ApplicationManager.getApplication().getService(ReaderModeSwitcher.class);
                            if (switcher != null) {
                                LOG.info("PrivateReaderStartupActivity: ReaderModeSwitcher service obtained successfully.");
                            } else {
                                LOG.warn("PrivateReaderStartupActivity: ReaderModeSwitcher service obtained as null.");
                            }
                            return switcher;
                        } catch (Throwable t) {
                            LOG.error("PrivateReaderStartupActivity: Error explicitly getting ReaderModeSwitcher service", t);
                            return null;
                        }
                    });
                    
                    // 并行执行数据库迁移
                    CompletableFuture<Void> migrationFuture = CompletableFuture.runAsync(() -> {
                        try {
                            LOG.info("PrivateReaderStartupActivity: 执行数据库迁移...");
                            DatabaseManager databaseManager = ApplicationManager.getApplication().getService(DatabaseManager.class);
                            if (databaseManager != null) {
                                databaseManager.runMigrationIfNeeded();
                                LOG.info("PrivateReaderStartupActivity: Database migration check completed.");
                            } else {
                                LOG.error("PrivateReaderStartupActivity: DatabaseManager service not found, cannot run migration.");
                            }
                        } catch (Throwable t) {
                            LOG.error("PrivateReaderStartupActivity: Error during database migration check", t);
                        }
                    });
                    
                    // 等待所有初始化任务完成
                    CompletableFuture.allOf(switcherFuture, migrationFuture).join();
                    LOG.info("PrivateReaderStartupActivity: 关键服务并行初始化完成");
                    
                } catch (Exception e) {
                    LOG.error("PrivateReaderStartupActivity: 并行初始化过程中发生错误", e);
                }
            });
            
            // 异步执行耗时任务
            StorageManager storageManager = ApplicationManager.getApplication().getService(StorageManager.class);
            if (storageManager != null) {
                LOG.info("PrivateReaderStartupActivity: Scheduling post-initialization tasks...");
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        // 等待关键服务初始化完成
                        try {
                            initFuture.get(5, TimeUnit.SECONDS); // 设置超时时间
                        } catch (Exception e) {
                            LOG.warn("PrivateReaderStartupActivity: 等待关键服务初始化超时，继续执行后续任务", e);
                        }
                        
                        LOG.info("PrivateReaderStartupActivity: Starting post-initialization tasks in background...");
                        storageManager.performPostInitializationTasks();
                        LOG.info("PrivateReaderStartupActivity: Post-initialization tasks finished successfully.");
                    } catch (Exception e) {
                        LOG.error("PrivateReaderStartupActivity: Error during post-initialization tasks execution", e);
                    }
                });
            } else {
                LOG.error("PrivateReaderStartupActivity: StorageManager service not found, cannot schedule post-initialization tasks.");
            }

            LOG.info("PrivateReaderStartupActivity: runActivity() finished scheduling tasks.");
        } else {
            LOG.debug("PrivateReaderStartupActivity: runActivity() called for subsequent project, skipping initialization.");
        }
    }
}
