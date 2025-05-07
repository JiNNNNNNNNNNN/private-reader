package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.util.ConfigDiagnosticTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * 配置诊断动作
 *
 * 用于诊断配置问题
 */
public class DiagnosticAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(DiagnosticAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        LOG.info("[配置诊断] 开始运行配置诊断");

        try {
            // 运行诊断
            Map<String, Object> result = ConfigDiagnosticTool.runDiagnostic();
            LOG.info("[配置诊断] 诊断完成，结果: " + result);

            // 显示诊断结果
            showDiagnosticResult(project, result);
        } catch (Exception ex) {
            LOG.error("[配置诊断] 诊断过程中出错: " + ex.getMessage(), ex);
            Messages.showErrorDialog(project, "诊断过程中出错: " + ex.getMessage(), "配置诊断");
        }
    }

    /**
     * 显示诊断结果
     *
     * @param project 项目
     * @param result 诊断结果
     */
    private void showDiagnosticResult(Project project, Map<String, Object> result) {
        DiagnosticDialog dialog = new DiagnosticDialog(project, result);
        dialog.show();
    }

    /**
     * 诊断结果对话框
     */
    private static class DiagnosticDialog extends DialogWrapper {
        private final Map<String, Object> result;

        public DiagnosticDialog(Project project, Map<String, Object> result) {
            super(project);
            this.result = result;
            setTitle("配置诊断结果");
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setPreferredSize(new Dimension(600, 400));

            // 创建结果文本区域
            JTextArea textArea = new JTextArea();
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

            // 格式化诊断结果
            StringBuilder sb = new StringBuilder();
            sb.append("配置诊断结果:\n\n");

            // 配置目录信息
            sb.append("=== 配置目录信息 ===\n");
            sb.append("配置目录路径: ").append(result.get("configDirPath")).append("\n");
            sb.append("配置目录存在: ").append(result.get("configDirExists")).append("\n");
            sb.append("配置目录可读: ").append(result.get("configDirReadable")).append("\n");
            sb.append("配置目录可写: ").append(result.get("configDirWritable")).append("\n\n");

            // 配置文件信息
            sb.append("=== 配置文件信息 ===\n");
            appendFileInfo(sb, "PluginSettings", result);
            appendFileInfo(sb, "ReaderSettings", result);
            appendFileInfo(sb, "CacheSettings", result);
            appendFileInfo(sb, "NotificationReaderSettings", result);
            appendFileInfo(sb, "ReaderModeSettings", result);
            sb.append("\n");

            // 配置值信息
            sb.append("=== 配置值信息 ===\n");

            // 插件设置
            if (result.containsKey("pluginSettings")) {
                sb.append("插件设置:\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> pluginSettings = (Map<String, Object>) result.get("pluginSettings");
                for (Map.Entry<String, Object> entry : pluginSettings.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }

            // 阅读器设置
            if (result.containsKey("readerSettings")) {
                sb.append("阅读器设置:\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> readerSettings = (Map<String, Object>) result.get("readerSettings");
                for (Map.Entry<String, Object> entry : readerSettings.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }

            // 缓存设置
            if (result.containsKey("cacheSettings")) {
                sb.append("缓存设置:\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> cacheSettings = (Map<String, Object>) result.get("cacheSettings");
                for (Map.Entry<String, Object> entry : cacheSettings.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }

            // 阅读模式设置
            if (result.containsKey("modeSettings")) {
                sb.append("阅读模式设置:\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> modeSettings = (Map<String, Object>) result.get("modeSettings");
                for (Map.Entry<String, Object> entry : modeSettings.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }

            // 通知栏设置
            if (result.containsKey("notificationSettings")) {
                sb.append("通知栏设置:\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> notificationSettings = (Map<String, Object>) result.get("notificationSettings");
                for (Map.Entry<String, Object> entry : notificationSettings.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                sb.append("\n");
            }

            // 阅读模式状态
            if (result.containsKey("readerModeNotification")) {
                sb.append("=== 阅读模式状态 ===\n");
                boolean notificationMode = (boolean) result.get("readerModeNotification");
                sb.append("当前阅读模式: ").append(notificationMode ? "通知栏模式" : "阅读器模式").append("\n\n");
            }

            textArea.setText(sb.toString());

            // 添加滚动条
            JBScrollPane scrollPane = new JBScrollPane(textArea);
            scrollPane.setBorder(JBUI.Borders.empty(10));
            panel.add(scrollPane, BorderLayout.CENTER);

            return panel;
        }

        @Override
        protected JComponent createSouthPanel() {
            JPanel panel = new JPanel(new BorderLayout());

            // 创建按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

            // 重置配置按钮
            JButton resetButton = new JButton("重置所有配置");
            resetButton.addActionListener(e -> {
                int result = Messages.showYesNoDialog(
                    "确定要重置所有配置吗？这将删除所有配置文件，恢复默认设置。",
                    "重置配置",
                    Messages.getQuestionIcon()
                );
                if (result == Messages.YES) {
                    try {
                        boolean resetResult = ConfigDiagnosticTool.resetAllConfigs();
                        if (resetResult) {
                            Messages.showInfoMessage("已重置所有配置，请重启 IDE 以应用更改。", "重置配置");
                            close(OK_EXIT_CODE);
                        } else {
                            Messages.showErrorDialog("重置配置失败，请查看日志了解详情。", "错误");
                        }
                    } catch (Exception ex) {
                        LOG.error("[配置诊断] 重置配置时出错: " + ex.getMessage(), ex);
                        Messages.showErrorDialog("重置配置时出错: " + ex.getMessage(), "错误");
                    }
                }
            });

            // 强制保存配置按钮
            JButton saveButton = new JButton("强制保存配置");
            saveButton.addActionListener(e -> {
                try {
                    boolean saveResult = ConfigDiagnosticTool.forceSaveAllConfigs();
                    if (saveResult) {
                        Messages.showInfoMessage("已强制保存所有配置。", "保存配置");
                        close(OK_EXIT_CODE);
                    } else {
                        Messages.showErrorDialog("保存配置失败，请查看日志了解详情。", "错误");
                    }
                } catch (Exception ex) {
                    LOG.error("[配置诊断] 保存配置时出错: " + ex.getMessage(), ex);
                    Messages.showErrorDialog("保存配置时出错: " + ex.getMessage(), "错误");
                }
            });

            // 应用阅读模式按钮
            JButton applyModeButton = new JButton("应用阅读模式");
            applyModeButton.addActionListener(e -> {
                try {
                    boolean applyResult = ConfigDiagnosticTool.applyReaderMode();
                    if (applyResult) {
                        Messages.showInfoMessage("已应用阅读模式。", "应用阅读模式");
                        close(OK_EXIT_CODE);
                    } else {
                        Messages.showErrorDialog("应用阅读模式失败，请查看日志了解详情。", "错误");
                    }
                } catch (Exception ex) {
                    LOG.error("[配置诊断] 应用阅读模式时出错: " + ex.getMessage(), ex);
                    Messages.showErrorDialog("应用阅读模式时出错: " + ex.getMessage(), "错误");
                }
            });

            // 关闭按钮
            JButton closeButton = new JButton("关闭");
            closeButton.addActionListener(e -> close(OK_EXIT_CODE));

            buttonPanel.add(resetButton);
            buttonPanel.add(saveButton);
            buttonPanel.add(applyModeButton);
            buttonPanel.add(closeButton);

            panel.add(buttonPanel, BorderLayout.CENTER);
            panel.setBorder(JBUI.Borders.empty(10, 0, 0, 0));

            return panel;
        }

        /**
         * 添加文件信息
         *
         * @param sb 字符串构建器
         * @param fileKey 文件键
         * @param result 诊断结果
         */
        private void appendFileInfo(StringBuilder sb, String fileKey, Map<String, Object> result) {
            sb.append(fileKey).append(".json:\n");
            sb.append("  存在: ").append(result.get(fileKey + "Exists")).append("\n");
            sb.append("  可读: ").append(result.get(fileKey + "Readable")).append("\n");
            sb.append("  可写: ").append(result.get(fileKey + "Writable")).append("\n");
            sb.append("  大小: ").append(result.get(fileKey + "Size")).append(" 字节\n");

            // 检查是否有错误
            if (result.containsKey(fileKey + "CheckError")) {
                sb.append("  错误: ").append(result.get(fileKey + "CheckError")).append("\n");
            }

            sb.append("\n");
        }
    }
}
