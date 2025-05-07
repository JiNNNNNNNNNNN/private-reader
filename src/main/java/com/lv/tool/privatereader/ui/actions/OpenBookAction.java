package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.ui.ReaderPanel;
import com.lv.tool.privatereader.ui.ReaderToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 打开书籍动作
 */
public class OpenBookAction extends AnAction implements DumbAware {
    public OpenBookAction() {
        super("打开书籍", "打开选中的书籍", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ReaderPanel readerPanel = ReaderToolWindowFactory.findPanel(project);
        if (readerPanel == null) {
            Messages.showWarningDialog(project,
                "阅读器窗口未初始化",
                "提示");
            return;
        }

        Book selectedBook = readerPanel.getSelectedBook();
        if (selectedBook == null) {
            Messages.showWarningDialog(project,
                "请先选择要打开的书籍",
                "提示");
            return;
        }

        // 获取工具窗口并显示
        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("PrivateReader");
        if (toolWindow != null) {
            toolWindow.show();
        }
        
        // 加载上次阅读的章节
        readerPanel.triggerLoadLastReadState();
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
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        ReaderPanel readerPanel = ReaderToolWindowFactory.findPanel(project);
        if (readerPanel == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        Book selectedBook = readerPanel.getSelectedBook();
        e.getPresentation().setEnabled(selectedBook != null);
    }
} 