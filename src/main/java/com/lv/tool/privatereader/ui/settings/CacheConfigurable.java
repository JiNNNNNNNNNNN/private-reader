package com.lv.tool.privatereader.ui.settings;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.icons.AllIcons;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.StorageManager;
import com.lv.tool.privatereader.storage.cache.ChapterCacheManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;

/**
 * 缓存设置界面
 */
public class CacheConfigurable implements Configurable {
    private static final Logger LOG = Logger.getInstance(CacheConfigurable.class);
    
    private JPanel mainPanel;
    private JBCheckBox enableCacheCheckBox;
    private JSpinner maxCacheSizeSpinner;
    private JSpinner maxCacheAgeSpinner;
    private JBCheckBox enablePreloadCheckBox;
    private JSpinner preloadCountSpinner;
    private JSpinner preloadDelaySpinner;
    private JLabel cachePathLabel;
    private JLabel booksPathLabel;
    private JButton clearCacheButton;
    private final CacheSettings settings;

    public CacheConfigurable() {
        settings = new CacheSettings();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Private Reader Cache Settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enableCacheCheckBox = new JBCheckBox("启用缓存");
        
        SpinnerNumberModel sizeModel = new SpinnerNumberModel(100, 10, 1000, 10);
        maxCacheSizeSpinner = new JSpinner(sizeModel);
        
        SpinnerNumberModel ageModel = new SpinnerNumberModel(7, 1, 30, 1);
        maxCacheAgeSpinner = new JSpinner(ageModel);
        
        // 预加载设置
        enablePreloadCheckBox = new JBCheckBox("启用章节预加载（后台自动缓存后续章节）");
        
        SpinnerNumberModel countModel = new SpinnerNumberModel(50, 1, 100, 5);
        preloadCountSpinner = new JSpinner(countModel);
        
        SpinnerNumberModel delayModel = new SpinnerNumberModel(1000, 500, 5000, 100);
        preloadDelaySpinner = new JSpinner(delayModel);
        
        // 获取存储管理器
        StorageManager storageManager = null;
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length > 0) {
            storageManager = openProjects[0].getService(StorageManager.class);
        }
        
        // 缓存路径显示
        String cachePath = storageManager != null ? storageManager.getCachePath() : settings.getCacheDirectoryPath();
        String shortCachePath = getShortPath(cachePath);
        cachePathLabel = createPathLabel(shortCachePath, cachePath, true);
        
        // 打开缓存目录的链接
        LinkLabel<String> openCacheLink = new LinkLabel<>("打开缓存目录", null);
        openCacheLink.setListener((source, data) -> {
            File cacheDir = new File(cachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            openDirectory(cacheDir);
        }, null);
        
        // 书架数据存储路径显示
        final String[] booksPathHolder = new String[1];
        booksPathHolder[0] = "";
        
        try {
            if (storageManager != null) {
                booksPathHolder[0] = storageManager.getBooksFilePath();
                LOG.info("从StorageManager获取书籍数据路径: " + booksPathHolder[0]);
            } else if (openProjects.length > 0) {
                BookStorage bookStorage = openProjects[0].getService(BookStorage.class);
                booksPathHolder[0] = bookStorage.getBooksFilePath();
                LOG.info("从BookStorage获取书籍数据路径: " + booksPathHolder[0]);
            } else {
                LOG.warn("无法获取书籍数据路径: 没有打开的项目");
                booksPathHolder[0] = "未找到书籍数据路径 (无打开项目)";
            }
        } catch (Exception e) {
            LOG.error("获取书籍数据路径失败: " + e.getMessage(), e);
            booksPathHolder[0] = "获取书籍数据路径失败: " + e.getMessage();
        }
        
        // 检查书籍数据文件是否存在
        File booksFile = new File(booksPathHolder[0]);
        boolean booksFileExists = booksFile.exists() && booksFile.isFile();
        String shortBooksPath = getShortPath(booksPathHolder[0]);
        
        if (booksFileExists) {
            long fileSize = booksFile.length();
            shortBooksPath += " (" + formatFileSize(fileSize) + ")";
        }
        
        booksPathLabel = createPathLabel(shortBooksPath, booksPathHolder[0], booksFileExists);
        
        // 打开书籍数据目录的链接
        LinkLabel<String> openBooksLink = new LinkLabel<>("打开书籍数据目录", null);
        openBooksLink.setListener((source, data) -> {
            try {
                File booksDir = new File(booksPathHolder[0]).getParentFile();
                if (booksDir != null && !booksDir.exists()) {
                    booksDir.mkdirs();
                }
                if (booksDir != null && booksDir.exists()) {
                    openDirectory(booksDir);
                } else {
                    Messages.showErrorDialog("无法打开书籍数据目录: 目录不存在", "错误");
                }
            } catch (Exception e) {
                LOG.error("打开书籍数据目录失败: " + e.getMessage(), e);
                Messages.showErrorDialog("打开书籍数据目录失败: " + e.getMessage(), "错误");
            }
        }, null);
        
        // 复制缓存路径按钮
        JButton copyCachePathButton = new JButton("复制路径");
        copyCachePathButton.addActionListener(e -> copyToClipboard(cachePath));
        
        // 复制书籍数据路径按钮
        JButton copyBooksPathButton = new JButton("复制路径");
        copyBooksPathButton.addActionListener(e -> copyToClipboard(booksPathHolder[0]));
        
        // 清理缓存按钮
        clearCacheButton = new JButton("清理缓存");
        clearCacheButton.addActionListener(e -> {
            int result = Messages.showYesNoDialog(
                "确定要清理所有缓存吗？这将删除所有缓存的章节内容。",
                "清理缓存",
                Messages.getQuestionIcon()
            );
            if (result == Messages.YES) {
                try {
                    Project[] projects = ProjectManager.getInstance().getOpenProjects();
                    if (projects.length > 0) {
                        ChapterCacheManager cacheManager = projects[0].getService(ChapterCacheManager.class);
                        cacheManager.clearAllCache();
                        Messages.showInfoMessage("缓存已清理完成", "清理缓存");
                    } else {
                        Messages.showErrorDialog("无法清理缓存: 没有打开的项目", "错误");
                    }
                } catch (Exception ex) {
                    LOG.error("清理缓存失败: " + ex.getMessage(), ex);
                    Messages.showErrorDialog("清理缓存失败: " + ex.getMessage(), "错误");
                }
            }
        });
        
        // 缓存路径面板
        JPanel cachePathPanel = new JPanel(new BorderLayout());
        cachePathPanel.add(cachePathLabel, BorderLayout.CENTER);
        cachePathPanel.add(copyCachePathButton, BorderLayout.EAST);
        
        // 书籍数据路径面板
        JPanel booksPathPanel = new JPanel(new BorderLayout());
        booksPathPanel.add(booksPathLabel, BorderLayout.CENTER);
        booksPathPanel.add(copyBooksPathButton, BorderLayout.EAST);
        
        // 构建表单
        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("启用缓存:", enableCacheCheckBox)
            .addLabeledComponent("最大缓存大小 (MB):", maxCacheSizeSpinner)
            .addLabeledComponent("缓存过期时间 (天):", maxCacheAgeSpinner)
            .addSeparator(10)
            .addLabeledComponent("预加载设置:", enablePreloadCheckBox)
            .addLabeledComponent("预加载章节数:", preloadCountSpinner)
            .addLabeledComponent("预加载延迟 (毫秒):", preloadDelaySpinner)
            .addSeparator(10)
            .addLabeledComponent("缓存目录:", cachePathPanel)
            .addComponentToRightColumn(openCacheLink)
            .addLabeledComponent("书籍数据:", booksPathPanel)
            .addComponentToRightColumn(openBooksLink)
                .addComponentFillVertically(new JPanel(), 0)
            .addComponent(clearCacheButton)
                .getPanel();
        
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        reset();
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return settings.isEnableCache() != enableCacheCheckBox.isSelected() ||
               settings.getMaxCacheSize() != (Integer) maxCacheSizeSpinner.getValue() ||
               settings.getMaxCacheAge() != (Integer) maxCacheAgeSpinner.getValue() ||
               settings.isEnablePreload() != enablePreloadCheckBox.isSelected() ||
               settings.getPreloadCount() != (Integer) preloadCountSpinner.getValue() ||
               settings.getPreloadDelay() != (Integer) preloadDelaySpinner.getValue();
    }

    @Override
    public void apply() throws ConfigurationException {
        settings.setEnableCache(enableCacheCheckBox.isSelected());
        settings.setMaxCacheSize((Integer) maxCacheSizeSpinner.getValue());
        settings.setMaxCacheAge((Integer) maxCacheAgeSpinner.getValue());
        settings.setEnablePreload(enablePreloadCheckBox.isSelected());
        settings.setPreloadCount((Integer) preloadCountSpinner.getValue());
        settings.setPreloadDelay((Integer) preloadDelaySpinner.getValue());
    }

    @Override
    public void reset() {
        enableCacheCheckBox.setSelected(settings.isEnableCache());
        maxCacheSizeSpinner.setValue(settings.getMaxCacheSize());
        maxCacheAgeSpinner.setValue(settings.getMaxCacheAge());
        enablePreloadCheckBox.setSelected(settings.isEnablePreload());
        preloadCountSpinner.setValue(settings.getPreloadCount());
        preloadDelaySpinner.setValue(settings.getPreloadDelay());
        
        // 更新启用状态
        preloadCountSpinner.setEnabled(enablePreloadCheckBox.isSelected());
        preloadDelaySpinner.setEnabled(enablePreloadCheckBox.isSelected());
    }

    /**
     * 安全地打开目录
     * @param directory 要打开的目录
     */
    private void openDirectory(File directory) {
        try {
            // 检查目录是否存在
            if (!directory.exists()) {
                LOG.info("目录不存在，尝试创建: " + directory);
                boolean created = directory.mkdirs();
                LOG.info("目录创建结果: " + created);
            }
            
            // 检查是否是目录
            if (!directory.isDirectory()) {
                LOG.warn("路径不是目录: " + directory);
                directory = directory.getParentFile();
                LOG.info("使用父目录: " + directory);
            }
            
            // 尝试使用Desktop API打开
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                LOG.info("使用Desktop API打开目录: " + directory);
                Desktop.getDesktop().open(directory);
                return;
            }
            
            // 根据操作系统使用不同的命令
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                command = new String[]{"explorer", directory.getAbsolutePath()};
            } else if (os.contains("mac")) {
                command = new String[]{"open", directory.getAbsolutePath()};
            } else if (os.contains("nix") || os.contains("nux")) {
                command = new String[]{"xdg-open", directory.getAbsolutePath()};
            } else {
                // 最后尝试使用BrowserUtil
                LOG.info("使用BrowserUtil打开目录: " + directory);
                BrowserUtil.browse(directory);
                return;
            }
            
            LOG.info("使用命令打开目录: " + String.join(" ", command));
            Runtime.getRuntime().exec(command);
            
        } catch (Exception e) {
            LOG.error("打开目录失败: " + e.getMessage(), e);
            Messages.showErrorDialog("打开目录失败: " + e.getMessage(), "错误");
            
            // 最后尝试使用BrowserUtil作为备选方案
            try {
                LOG.info("尝试使用BrowserUtil作为备选方案");
                BrowserUtil.browse(directory);
            } catch (Exception ex) {
                LOG.error("使用BrowserUtil打开目录失败: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * 将长路径转换为更短的显示形式
     * @param path 完整路径
     * @return 缩短后的路径
     */
    private String getShortPath(String path) {
        if (path == null || path.isEmpty()) {
            return "未知路径";
        }
        
        // 替换用户主目录为 ~
        String userHome = System.getProperty("user.home");
        if (path.startsWith(userHome)) {
            path = "~" + path.substring(userHome.length());
        }
        
        // 如果路径太长，截断中间部分
        if (path.length() > 50) {
            int start = 20;
            int end = path.length() - 25;
            path = path.substring(0, start) + "..." + path.substring(end);
        }
        
        return path;
    }
    
    /**
     * 格式化文件大小为人类可读形式
     * @param size 文件大小（字节）
     * @return 格式化后的大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    /**
     * 将文本复制到剪贴板
     * @param text 要复制的文本
     */
    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            Messages.showInfoMessage("路径已复制到剪贴板", "复制成功");
        } catch (Exception e) {
            LOG.error("复制到剪贴板失败: " + e.getMessage(), e);
            Messages.showErrorDialog("复制到剪贴板失败: " + e.getMessage(), "错误");
        }
    }

    /**
     * 创建带有图标的路径标签
     * @param displayText 显示文本
     * @param fullPath 完整路径（用于工具提示）
     * @param exists 文件或目录是否存在
     * @return 格式化的标签
     */
    private JLabel createPathLabel(String displayText, String fullPath, boolean exists) {
        JLabel label = new JLabel(displayText);
        
        // 设置图标
        Icon icon;
        if (exists) {
            // 使用文件夹图标
            icon = AllIcons.Nodes.Folder;
        } else {
            // 使用警告图标
            icon = AllIcons.General.Warning;
        }
        label.setIcon(icon);
        
        // 设置工具提示
        label.setToolTipText("<html><b>完整路径:</b><br>" + fullPath + "</html>");
        
        // 设置样式
        label.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
        label.setBorder(JBUI.Borders.empty(2));
        
        return label;
    }
} 