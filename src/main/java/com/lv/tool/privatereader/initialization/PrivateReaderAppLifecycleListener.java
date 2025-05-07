package com.lv.tool.privatereader.initialization;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.service.ReaderModeSwitcher;
import com.lv.tool.privatereader.storage.StorageManager;

/**
 * 使用 AppLifecycleListener 替代 ApplicationInitializedListener 来执行应用启动后的初始化任务。
 */
public class PrivateReaderAppLifecycleListener implements AppLifecycleListener {

    private static final Logger LOG = Logger.getInstance(PrivateReaderAppLifecycleListener.class);

    @Override
    public void appStarted() {
        LOG.info("PrivateReaderAppLifecycleListener: appStarted() called.");

        // 在这里执行原 PrivateReaderInitializer 中的初始化逻辑
        // 强制加载 ReaderModeSwitcher 服务以确保早期初始化
        LOG.info("PrivateReaderAppLifecycleListener: Attempting to force load ReaderModeSwitcher service...");
        try {
            ReaderModeSwitcher switcher = ApplicationManager.getApplication().getService(ReaderModeSwitcher.class);
            if (switcher != null) {
                 LOG.info("PrivateReaderAppLifecycleListener: ReaderModeSwitcher service obtained successfully.");
            } else {
                 LOG.warn("PrivateReaderAppLifecycleListener: ReaderModeSwitcher service obtained as null.");
            }
        } catch (Throwable t) {
            LOG.error("PrivateReaderAppLifecycleListener: Error explicitly getting ReaderModeSwitcher service", t);
        }

        // 异步执行耗时任务
        StorageManager storageManager = ApplicationManager.getApplication().getService(StorageManager.class);
        if (storageManager != null) {
            LOG.info("PrivateReaderAppLifecycleListener: Scheduling post-initialization tasks...");
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    LOG.info("PrivateReaderAppLifecycleListener: Starting post-initialization tasks in background...");
                    storageManager.performPostInitializationTasks();
                    LOG.info("PrivateReaderAppLifecycleListener: Post-initialization tasks finished successfully.");
                } catch (Exception e) {
                    LOG.error("PrivateReaderAppLifecycleListener: Error during post-initialization tasks execution", e);
                }
            });
        } else {
            LOG.error("PrivateReaderAppLifecycleListener: StorageManager service not found, cannot schedule post-initialization tasks.");
        }

        LOG.info("PrivateReaderAppLifecycleListener: appStarted() finished scheduling tasks.");
    }

    // 可以根据需要实现 AppLifecycleListener 的其他方法，例如 appClosing()
} 