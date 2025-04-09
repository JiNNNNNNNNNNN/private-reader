package com.lv.tool.privatereader.startup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.config.PrivateReaderConfig;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * 插件项目活动
 * 在项目打开时初始化组件和服务
 * 
 * 这个类替代了旧的 ReactiveStartupActivity 类
 */
public class PrivateReaderProjectActivity implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(PrivateReaderProjectActivity.class);
    
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        LOG.info("PrivateReaderProjectActivity 开始执行");
        
        // 初始化Guice依赖注入
        boolean guiceInitialized = false;
        try {
            LOG.info("初始化Guice依赖注入");
            guiceInitialized = com.lv.tool.privatereader.config.GuiceInjector.initialize();
            if (guiceInitialized) {
                LOG.info("Guice依赖注入初始化成功");
            } else {
                LOG.warn("Guice依赖注入初始化失败，但将继续启动插件 - 某些高级功能可能不可用");
            }
        } catch (Exception e) {
            LOG.error("初始化Guice依赖注入失败，但将继续启动插件 - 某些高级功能可能不可用", e);
        }
        
        LOG.info("初始化Private Reader插件...");
        
        try {
            // 清理旧的配置文件
            cleanupLegacyFiles(project);
            
            // 加载配置
            PluginSettings pluginSettings = ApplicationManager.getApplication().getService(PluginSettings.class);
            if (pluginSettings == null) {
                LOG.error("无法获取PluginSettings服务");
                return Unit.INSTANCE; // 返回 Kotlin Unit 表示完成
            }
            
            LOG.info("插件状态: " + (pluginSettings.isEnabled() ? "已启用" : "已禁用"));
            if (!pluginSettings.isEnabled()) {
                LOG.info("插件已禁用，跳过初始化");
                return Unit.INSTANCE;
            }
            
            // 初始化配置
            PrivateReaderConfig config = ApplicationManager.getApplication().getService(PrivateReaderConfig.class);
            if (config != null) {
                LOG.info("阅读模式: " + config.getReaderMode());
            } else {
                LOG.warn("无法获取PrivateReaderConfig服务");
            }
            
            // 初始化响应式调度器
            ReactiveSchedulers schedulers = ReactiveSchedulers.getInstance();
            
            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("应用关闭，清理资源...");
                schedulers.shutdown();
            }));
            
            // 检查BookService是否正常初始化
            try {
                BookService bookService = ApplicationManager.getApplication().getService(BookService.class);
                if (bookService != null) {
                    LOG.info("BookService已初始化");
                    // 测试获取最近阅读的书籍
                    try {
                        LOG.info("获取上次阅读书籍...");
                        bookService.getLastReadBook()
                            .publishOn(ReactiveSchedulers.getInstance().ui())
                            .subscribe(
                                lastReadBook -> {
                                    PrivateReaderPanel panel = ApplicationManager.getApplication().getService(PrivateReaderPanel.class);
                                    
                                    if (lastReadBook != null) {
                                        LOG.info("找到上次阅读书籍，尝试在面板中加载: " + lastReadBook.getTitle());
                                        if (panel != null) {
                                            panel.loadSpecificBookProgress(lastReadBook);
                                        } else {
                                            LOG.warn("PrivateReaderPanel 实例为 null，无法加载书籍进度。");
                                        }
                                    } else {
                                        LOG.info("没有找到上次阅读的书籍。");
                                        if (panel != null && panel.getBookList() != null && panel.getBookList().getModel().getSize() > 0) {
                                            panel.getBookList().setSelectedIndex(0);
                                        }
                                    }
                                },
                                error -> {
                                    LOG.error("启动时加载上次阅读书籍失败", error);
                                    PrivateReaderPanel panel = ApplicationManager.getApplication().getService(PrivateReaderPanel.class);
                                    if (panel != null && panel.getBookList() != null && panel.getBookList().getModel().getSize() > 0) {
                                        panel.getBookList().setSelectedIndex(0);
                                    }
                                }
                            );
                    } catch (Exception e) {
                        LOG.error("获取最近阅读书籍时发生异常", e);
                    }
                } else {
                    LOG.error("BookService未初始化");
                }
            } catch (Exception e) {
                LOG.error("获取BookService时发生异常", e);
            }
            
            LOG.info("尝试延迟加载初始化窗口面板内容");
            
            // 延迟一点时间，确保UI组件都已经加载完毕
            ApplicationManager.getApplication().invokeLater(() -> {
                LOG.info("PrivateReaderPanel将自动加载上次阅读章节，无需在ProjectActivity中尝试加载");
                
                // 无需在这里尝试加载上次章节，因为面板构造完成后会自动加载
                javax.swing.SwingUtilities.invokeLater(() -> {
                    LOG.info("PrivateReaderPanel的构造完成后会自动处理章节加载");
                });
            });
            
            LOG.info("Private Reader插件初始化完成");
        } catch (Exception e) {
            LOG.error("初始化插件时发生错误", e);
        }
        
        LOG.info("PrivateReaderProjectActivity 执行结束");
        return Unit.INSTANCE; // 返回 Kotlin Unit 表示完成
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
} 