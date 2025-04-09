package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import com.lv.tool.privatereader.settings.ReaderModeSettings;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public class PrevPageAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                panel.prevPage();
            }
        }
    }
    
    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        // 告诉 IntelliJ 在后台线程而非 EDT 线程中执行 update 方法
        return ActionUpdateThread.BGT;
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            // ReaderModeSettings settings = ApplicationManager.getApplication().getService(ReaderModeSettings.class); // No longer needed
            
            // Checklist Item 9: Modify enabled condition for PrevPageAction
            // if (panel != null && settings != null) {
            //    boolean enabled = settings.isNotificationMode() && 
            //           panel.getBookList().getSelectedValue() != null && 
            //           panel.getChapterList().getSelectedValue() != null;
            //    e.getPresentation().setEnabled(enabled);
            // } else {
            //    e.getPresentation().setEnabled(false);
            // }
             boolean enabled = panel != null && panel.canGoToPrevPage();
             e.getPresentation().setEnabled(enabled);
        } else {
            e.getPresentation().setEnabled(false);
        }
    }
} 