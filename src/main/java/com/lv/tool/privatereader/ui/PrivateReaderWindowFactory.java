package com.lv.tool.privatereader.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.service.impl.BookServiceImpl;
import com.lv.tool.privatereader.service.impl.ChapterServiceImpl;
import com.lv.tool.privatereader.service.impl.NotificationServiceImpl;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.settings.PluginSettingsListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 私人阅读器窗口工厂
 */
public class PrivateReaderWindowFactory implements ToolWindowFactory, Disposable {
    private static final Logger LOG = Logger.getInstance(PrivateReaderWindowFactory.class);
    private static final ConcurrentMap<Project, PrivateReaderPanel> PROJECT_PANELS = new ConcurrentHashMap<>();
    
    /**
     * 获取指定项目的阅读器面板
     * 
     * @param project 项目
     * @return 阅读器面板，如果不存在则返回null
     */
    public static PrivateReaderPanel getPanel(Project project) {
        return PROJECT_PANELS.get(project);
    }
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 检查服务是否已注册，但不抛出异常
        boolean servicesAvailable = checkServicesAvailable(project);
        
        if (servicesAvailable) {
            // 服务可用，正常创建面板
            // 检查是否已经有面板实例
            PrivateReaderPanel readerPanelTemp = PROJECT_PANELS.get(project);
            if (readerPanelTemp == null) {
                // 如果还没有，创建新的面板
                readerPanelTemp = PrivateReaderPanel.createInstance(project);
                PROJECT_PANELS.put(project, readerPanelTemp);
            }
            
            // 将 readerPanel 声明为 final
            final PrivateReaderPanel readerPanel = readerPanelTemp;
            
            Content content = ContentFactory.getInstance().createContent(readerPanel, "", false);
            content.setDisposer(this); // 设置资源释放器
            toolWindow.getContentManager().addContent(content);
            
            LOG.info("已创建阅读器面板并添加到工具窗口");
            
            // 面板会自动加载上次阅读的章节，无需在这里调用
            LOG.info("面板会自动加载上次阅读章节，无需在工厂类中调用");
        } else {
            // 服务不可用，创建错误提示面板
            JPanel errorPanel = new JPanel(new BorderLayout());
            JBLabel errorLabel = new JBLabel("服务初始化失败，请重启IDE或重新安装插件", SwingConstants.CENTER);
            errorPanel.add(errorLabel, BorderLayout.CENTER);
            
            Content content = ContentFactory.getInstance().createContent(errorPanel, "", false);
            content.setDisposer(this);
            toolWindow.getContentManager().addContent(content);
            
            LOG.error("服务初始化失败，创建了错误提示面板");
        }

        // 监听插件设置变更
        ApplicationManager.getApplication()
            .getMessageBus()
            .connect()
            .subscribe(PluginSettingsListener.TOPIC, () -> {
                boolean shouldBeAvailable = shouldBeAvailable(project);
                toolWindow.setAvailable(shouldBeAvailable);
                if (shouldBeAvailable) {
                    PrivateReaderPanel panel = PROJECT_PANELS.get(project);
                    if (panel != null) {
                        panel.refresh();
                    }
                }
            });
    }

    /**
     * 检查服务是否可用
     * 
     * @param project 项目
     * @return 服务是否可用
     */
    private boolean checkServicesAvailable(Project project) {
        LOG.info("检查服务是否已注册");
        
        try {
            // 检查BookService
            BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
            if (bookService == null) {
                LOG.warn("BookService未注册，尝试直接获取实现类");
                // 尝试直接获取实现类
                BookServiceImpl bookServiceImpl = ApplicationManager.getApplication().getService(BookServiceImpl.class);
                if (bookServiceImpl == null) {
                    LOG.warn("BookServiceImpl也未注册");
                    return false;
                } else {
                    LOG.info("BookServiceImpl已注册");
                }
            } else {
                LOG.info("BookService已注册");
            }
            
            // 检查ChapterService
            ChapterService chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
            if (chapterService == null) {
                LOG.warn("ChapterService未注册，尝试直接获取实现类");
                // 尝试直接获取实现类
                ChapterServiceImpl chapterServiceImpl = ApplicationManager.getApplication().getService(ChapterServiceImpl.class);
                if (chapterServiceImpl == null) {
                    LOG.warn("ChapterServiceImpl也未注册");
                    return false;
                } else {
                    LOG.info("ChapterServiceImpl已注册");
                }
            } else {
                LOG.info("ChapterService已注册");
            }
            
            // 检查NotificationService
            NotificationService notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
            if (notificationService == null) {
                LOG.warn("NotificationService未注册，尝试直接获取实现类");
                // 尝试直接获取实现类
                NotificationServiceImpl notificationServiceImpl = ApplicationManager.getApplication().getService(NotificationServiceImpl.class);
                if (notificationServiceImpl == null) {
                    LOG.warn("NotificationServiceImpl也未注册");
                    return false;
                } else {
                    LOG.info("NotificationServiceImpl已注册");
                }
            } else {
                LOG.info("NotificationService已注册");
            }
            
            return true;
        } catch (Exception e) {
            LOG.warn("检查服务可用性时发生异常", e);
            return false;
        }
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("私人阅读器");
        toolWindow.setStripeTitle("私人阅读器");
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        PluginSettings settings = ApplicationManager.getApplication().getService(PluginSettings.class);
        return settings != null && settings.isEnabled();
    }
    
    @Override
    public void dispose() {
        for (Project project : PROJECT_PANELS.keySet()) {
            PrivateReaderPanel panel = PROJECT_PANELS.remove(project);
            if (panel != null) {
                panel.dispose();
            }
        }
    }
} 