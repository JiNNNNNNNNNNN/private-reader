package com.lv.tool.privatereader.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.repository.StorageRepository;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.util.DiagnosticTool;
import org.jetbrains.annotations.NotNull;

/**
 * 诊断命令类
 * 
 * 用于在IDE中执行诊断命令，检查和修复项目中的问题。
 */
public class DiagnosticAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(DiagnosticAction.class);
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        LOG.info("开始执行诊断命令...");
        
        // 获取StorageRepository服务
        StorageRepository storageRepository = ApplicationManager.getApplication().getService(StorageRepository.class);
        if (storageRepository == null) {
            ApplicationManager.getApplication().getService(NotificationService.class).showError("诊断失败", "无法获取StorageRepository服务");
            return;
        }
        
        // 创建诊断工具
        DiagnosticTool diagnosticTool = new DiagnosticTool(storageRepository);
        
        // 运行诊断
        DiagnosticTool.DiagnosticResult result = diagnosticTool.runDiagnostic();
        
        // 显示诊断结果
        String message = result.toString();
        if (result.hasIssues()) {
            int choice = Messages.showYesNoDialog(
                project,
                message + "\n\n是否尝试修复这些问题？",
                "诊断结果",
                "修复问题",
                "取消",
                Messages.getQuestionIcon()
            );
            
            if (choice == Messages.YES) {
                // 修复问题
                int repairedCount = diagnosticTool.repairAllCorruptedJsonFiles(result);
                
                // 显示修复结果
                Messages.showInfoMessage(
                    project,
                    "修复完成，共修复 " + repairedCount + " 个文件。\n\n" + result.toString(),
                    "修复结果"
                );
            }
        } else {
            Messages.showInfoMessage(project, message, "诊断结果");
        }
        
        LOG.info("诊断命令执行完成");
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只在有项目打开时启用
        e.getPresentation().setEnabled(e.getProject() != null);
    }
}
