package com.lv.tool.privatereader.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
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

/**
 * 私人阅读器窗口工厂
 */
public class PrivateReaderWindowFactory implements ToolWindowFactory, Disposable {
    private static final Logger LOG = Logger.getInstance(PrivateReaderWindowFactory.class);
    private PrivateReaderPanel readerPanel;
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 检查服务是否已注册，但不抛出异常
        boolean servicesAvailable = checkServicesAvailable(project);
        
        if (servicesAvailable) {
            // 服务可用，正常创建面板
            // 获取已经初始化的面板
            readerPanel = PrivateReaderPanel.getInstance(project);
            if (readerPanel == null) {
                // 如果还没有初始化，创建新的面板
                readerPanel = new PrivateReaderPanel(project);
            }
            
            Content content = ContentFactory.getInstance().createContent(readerPanel, "", false);
            content.setDisposer(this); // 设置资源释放器
            toolWindow.getContentManager().addContent(content);
        } else {
            // 服务不可用，创建错误提示面板
            JPanel errorPanel = new JPanel(new BorderLayout());
            JBLabel errorLabel = new JBLabel("服务初始化失败，请重启IDE或重新安装插件", SwingConstants.CENTER);
            errorPanel.add(errorLabel, BorderLayout.CENTER);
            
            Content content = ContentFactory.getInstance().createContent(errorPanel, "", false);
            content.setDisposer(this);
            toolWindow.getContentManager().addContent(content);
        }

        // 监听插件设置变更
        ApplicationManager.getApplication()
            .getMessageBus()
            .connect()
            .subscribe(PluginSettingsListener.TOPIC, () -> {
                boolean shouldBeAvailable = shouldBeAvailable(project);
                toolWindow.setAvailable(shouldBeAvailable);
                if (shouldBeAvailable && readerPanel != null) {
                    readerPanel.refresh();
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
            BookService bookService = project.getService(BookService.class);
            if (bookService == null) {
                LOG.warn("BookService未注册，尝试直接获取实现类");
                // 尝试直接获取实现类
                BookServiceImpl bookServiceImpl = project.getService(BookServiceImpl.class);
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
            ChapterService chapterService = project.getService(ChapterService.class);
            if (chapterService == null) {
                LOG.warn("ChapterService未注册，尝试直接获取实现类");
                // 尝试直接获取实现类
                ChapterServiceImpl chapterServiceImpl = project.getService(ChapterServiceImpl.class);
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
            NotificationService notificationService = project.getService(NotificationService.class);
            if (notificationService == null) {
                LOG.warn("NotificationService未注册，尝试直接获取实现类");
                // 尝试直接获取实现类
                NotificationServiceImpl notificationServiceImpl = project.getService(NotificationServiceImpl.class);
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
        if (readerPanel != null) {
            readerPanel.dispose();
            readerPanel = null;
        }
    }
} 