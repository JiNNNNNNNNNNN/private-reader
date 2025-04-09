package com.lv.tool.privatereader.startup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.lv.tool.privatereader.config.GuiceInjector;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ide.util.RunOnceUtil;
import com.lv.tool.privatereader.ui.PrivateReaderWindowFactory;

import java.io.File;

/**
 * IDE 启动时初始化阅读器面板
 */
public class PrivateReaderStartupActivity implements com.intellij.openapi.startup.StartupActivity, DumbAware {
    private static final Logger LOG = Logger.getInstance(PrivateReaderStartupActivity.class);
    private static final String RUN_ONCE_KEY = "com.lv.tool.privatereader.startup.loadLastPosition";
    private static final String CLEANUP_RUN_ONCE_KEY = "com.lv.tool.privatereader.startup.cleanupLegacyFiles";
    
    @Override
    public void runActivity(@NotNull Project project) {
        // 初始化Guice依赖注入
        initGuiceInjection();
        
        // 检查插件是否启用
        PluginSettings settings = GuiceInjector.getInstance(PluginSettings.class);
        if (settings == null || !settings.isEnabled()) {
            LOG.info("插件未启用，跳过启动活动");
            return;
        }
        
        // 清理旧的配置文件
        RunOnceUtil.runOnceForProject(project, CLEANUP_RUN_ONCE_KEY, () -> {
            LOG.info("清理旧的配置文件");
            cleanupLegacyFiles(project);
        });
        
        // 使用单独的RunOnce键来确保位置恢复只发生一次
        // 这样可以防止其他部分的代码变更影响到加载位置的逻辑
        RunOnceUtil.runOnceForProject(project, RUN_ONCE_KEY, () -> {
            LOG.info("开始恢复上次阅读位置");
            
            // 延迟一点时间再执行，确保工具窗口和面板已完全初始化
            ApplicationManager.getApplication().invokeLater(() -> {
                // 获取PrivateReader工具窗口
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("PrivateReader");
                if (toolWindow == null) {
                    LOG.warn("找不到PrivateReader工具窗口，无法恢复阅读位置");
                    return;
                }
                
                // 工具窗口内容可能尚未初始化，需要先激活工具窗口
                if (!toolWindow.isActive()) {
                    toolWindow.show(() -> loadLastPosition(project, toolWindow));
                } else {
                    loadLastPosition(project, toolWindow);
                }
            }, project.getDisposed());
        });
    }
    
    /**
     * 清理旧的配置文件
     * 删除不再使用的.idea目录下的private-reader-books.xml文件
     */
    private void cleanupLegacyFiles(@NotNull Project project) {
        try {
            LOG.info("清理项目中的旧配置文件");
            if (project.getBasePath() != null) {
                com.lv.tool.privatereader.storage.BookFileStorage.cleanLegacyProjectFile(project.getBasePath());
            } else {
                LOG.warn("无法获取项目路径，跳过清理");
            }
        } catch (Exception e) {
            LOG.error("清理旧配置文件时发生异常", e);
        }
    }
    
    /**
     * 加载最后阅读位置
     */
    private void loadLastPosition(Project project, ToolWindow toolWindow) {
        try {
            // 从面板缓存中获取实例
            PrivateReaderPanel panel = PrivateReaderWindowFactory.getPanel(project);
            if (panel == null) {
                LOG.warn("找不到PrivateReaderPanel实例，将尝试从工具窗口内容中获取");
                
                // 备用方法：从工具窗口内容中获取面板实例
                Content content = toolWindow.getContentManager().getSelectedContent();
                if (content == null || !(content.getComponent() instanceof PrivateReaderPanel)) {
                    LOG.warn("无法从工具窗口内容中获取面板实例，无法恢复阅读位置");
                    return;
                }
                
                panel = (PrivateReaderPanel) content.getComponent();
            }
            
            LOG.info("找到PrivateReaderPanel实例，准备加载上次阅读位置");
            
            // 确保在EDT线程中执行UI操作
            final PrivateReaderPanel finalPanel = panel;
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // 加载上次阅读位置
                    finalPanel.loadLastReadChapter();
                    LOG.info("已成功恢复上次阅读位置");
                } catch (Exception e) {
                    LOG.error("加载上次阅读位置时发生异常", e);
                }
            }, project.getDisposed());
        } catch (Exception e) {
            LOG.error("恢复阅读位置过程中发生异常", e);
        }
    }
    
    /**
     * 初始化Guice依赖注入
     */
    private void initGuiceInjection() {
        try {
            LOG.info("初始化Guice依赖注入");
            GuiceInjector.initialize();
            LOG.info("Guice依赖注入初始化完成");
        } catch (Exception e) {
            LOG.error("初始化Guice依赖注入失败", e);
        }
    }
}
