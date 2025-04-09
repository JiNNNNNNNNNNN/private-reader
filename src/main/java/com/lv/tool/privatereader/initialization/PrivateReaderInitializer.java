package com.lv.tool.privatereader.initialization;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.storage.StorageManager;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Private Reader 初始化器
 * 在应用启动完成后执行后台任务，例如数据迁移。
 */
public class PrivateReaderInitializer implements ApplicationInitializedListener {
    private static final Logger LOG = Logger.getInstance(PrivateReaderInitializer.class);

    @Override
    public void componentsInitialized() {
        LOG.info("Private Reader 应用组件初始化完成，开始执行后台任务...");
        StorageManager storageManager = ApplicationManager.getApplication().getService(StorageManager.class);
        if (storageManager != null) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    storageManager.performPostInitializationTasks();
                } catch (Exception e) {
                    LOG.error("执行 Private Reader 初始化后任务失败", e);
                }
            });
        } else {
            LOG.error("无法获取 StorageManager 服务，初始化后任务无法执行");
        }
    }
} 