package com.lv.tool.privatereader.listener;

import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.lv.tool.privatereader.config.GuiceInjector;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ReaderModeSwitcher;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.storage.BookFileStorage;
import com.lv.tool.privatereader.ui.ReaderPanel;
import com.lv.tool.privatereader.ui.ReaderToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class ProjectOpenListener implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(ProjectOpenListener.class);
    // Key for cleaning up legacy files only once
    private static final String CLEANUP_RUN_ONCE_KEY = "com.lv.tool.privatereader.startup.cleanupLegacyFiles"; // Reusing key

    @Override
    public void projectOpened(@NotNull Project project) {
        LOG.info("Project opened: " + project.getName() + ". Performing Private Reader initialization...");

        // --- Ensure one-time initialization per project ---
        if (ReaderToolWindowFactory.PROJECT_PANELS.containsKey(project)) {
            LOG.info("Initialization already performed for project: " + project.getName());
            return;
        }
        LOG.info("Performing one-time initialization for project: " + project.getName());

        try {
            // 1. 初始化Guice依赖注入 (Ensure it's safe to call multiple times or is idempotent)
            initGuiceInjection();

            // 2. 检查插件是否启用
            PluginSettings settings = GuiceInjector.getInstance(PluginSettings.class);
            if (settings == null || !settings.isEnabled()) {
                LOG.info("Plugin disabled, skipping initialization.");
                // Do NOT put null in PROJECT_PANELS, factory needs to handle absent panel.
                return;
            }

            // 3. 清理旧的配置文件
            RunOnceUtil.runOnceForProject(project, CLEANUP_RUN_ONCE_KEY, () -> {
                LOG.info("Performing legacy file cleanup...");
                cleanupLegacyFiles(project);
            });

            // 4. **创建 ReaderPanel 实例并存储**
            LOG.info("Creating and storing ReaderPanel instance for project: " + project.getName());
            ReaderPanel readerPanel = new ReaderPanel(project); // Assuming constructor is safe to call here
            ReaderToolWindowFactory.PROJECT_PANELS.put(project, readerPanel);
            LOG.info("ReaderPanel instance created and stored.");

            // 5. **应用初始阅读模式**
            LOG.info("[配置诊断] 尝试应用初始阅读模式");
            ReaderModeSwitcher modeSwitcher = ApplicationManager.getApplication().getService(ReaderModeSwitcher.class);
            if (modeSwitcher != null) {
                LOG.info("[配置诊断] 获取 ReaderModeSwitcher 服务成功，应用初始阅读模式");
                modeSwitcher.applyInitialModeForProject(project);
            } else {
                LOG.warn("[配置诊断] 无法获取 ReaderModeSwitcher 服务，跳过应用初始阅读模式");
            }

            // 6. **异步加载上次阅读的书籍**
            BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
            if (bookService != null) {
                LOG.info("BookService obtained, fetching last read book asynchronously...");
                bookService.getLastReadBook()
                    .subscribe(
                        lastReadBook -> {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                ReaderPanel currentPanel = ReaderToolWindowFactory.PROJECT_PANELS.get(project);
                                if (currentPanel != null && !project.isDisposed()) {
                                    if (lastReadBook != null) {
                                        LOG.info("Last read book found: " + lastReadBook.getTitle() + ". Selecting in panel.");
                                        currentPanel.selectBookAndLoadProgress(lastReadBook);
                                    } else {
                                        LOG.info("No last read book found. Panel will show default state.");
                                    }
                                } else {
                                    LOG.warn("Panel or project became invalid before last read book could be loaded.");
                                }
                            }, project.getDisposed());
                        },
                        error -> {
                            LOG.error("Error fetching last read book during project open", error);
                        }
                    );
            } else {
                LOG.error("BookService not available during project open initialization.");
            }
            LOG.info("Private Reader initialization tasks scheduled for project: " + project.getName());

        } catch (Exception e) {
            LOG.error("Error during Private Reader project open initialization", e);
            // Cleanup if initialization failed midway
            ReaderToolWindowFactory.PROJECT_PANELS.remove(project);
        }
    }

    @Override
    public void projectClosed(@NotNull Project project) {
         // Clean up project-specific resources
         LOG.info("Project closed: " + project.getName() + ". Cleaning up Private Reader resources.");
         ReaderToolWindowFactory.PROJECT_PANELS.remove(project);
         // Add any other necessary cleanup for this project
         ProjectManagerListener.super.projectClosed(project);
    }

    // --- Helper Methods (Duplicated for now, consider a dedicated Service/Util if needed) ---

    /**
     * 初始化Guice依赖注入
     */
    private static void initGuiceInjection() {
        try {
            // Check if Guice is already initialized (optional, Guice might handle this)
             if (GuiceInjector.isInitialized()) {
                 LOG.info("Guice already initialized, skipping re-initialization.");
                 return;
             }
            LOG.info("Initializing Guice dependency injection (called by ProjectOpenListener)..." + Thread.currentThread().getName());
            GuiceInjector.initialize();
            LOG.info("Guice dependency injection initialized successfully (called by ProjectOpenListener)." + Thread.currentThread().getName());
        } catch (Exception e) {
            LOG.error("Failed to initialize Guice dependency injection (called by ProjectOpenListener)", e);
        }
    }

    /**
     * 清理旧的配置文件
     */
    private static void cleanupLegacyFiles(@NotNull Project project) {
        try {
            LOG.info("Cleaning up legacy configuration files in project (called by ProjectOpenListener): " + project.getName());
            if (project.getBasePath() != null) {
                BookFileStorage.cleanLegacyProjectFile(project.getBasePath());
                LOG.info("Legacy file cleanup successful (called by ProjectOpenListener).");
            } else {
                LOG.warn("Could not get project base path, skipping cleanup (called by ProjectOpenListener).");
            }
        } catch (Exception e) {
            LOG.error("Exception during legacy file cleanup (called by ProjectOpenListener)", e);
        }
    }
}