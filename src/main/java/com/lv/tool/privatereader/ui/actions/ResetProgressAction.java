package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.ui.ReaderPanel;
import com.lv.tool.privatereader.ui.ReaderToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 重置阅读进度动作
 */
public class ResetProgressAction extends AnAction implements DumbAware {
    private static final Logger LOG = LoggerFactory.getLogger(ResetProgressAction.class);

    public ResetProgressAction() {
        super("重置进度", "重置选中书籍的阅读进度", null);
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
                "请先选择要重置的书籍",
                "提示");
            return;
        }

        int result = Messages.showYesNoDialog(project,
            String.format("确定要重置《%s》的阅读进度吗？这将清除所有阅读记录。", selectedBook.getTitle()),
            "重置进度",
            Messages.getQuestionIcon());

        if (result != Messages.YES) return;

        // Directly get the ReadingProgressRepository service
        ReadingProgressRepository progressRepository = ApplicationManager.getApplication().getService(ReadingProgressRepository.class);
        if (progressRepository != null) {
            // Run the reset operation (which should be quick, but consider background thread if it involved IO)
             try {
                 progressRepository.resetProgress(selectedBook);
                 // Refresh UI after successful reset
                 readerPanel.refreshChapterList(); // Consider if this needs more specific refresh
                 
                 Messages.showInfoMessage(project,
                     String.format("已重置《%s》的阅读进度", selectedBook.getTitle()),
                     "重置成功");
             } catch (Exception ex) {
                  Messages.showErrorDialog(project, "重置进度时出错: " + ex.getMessage(), "错误");
                  LOG.error("Error resetting progress for book: " + selectedBook.getId(), ex);
             }
        } else {
             Messages.showErrorDialog(project, "无法获取阅读进度服务，无法重置进度", "错误");
             LOG.error("Could not get ReadingProgressRepository service.");
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