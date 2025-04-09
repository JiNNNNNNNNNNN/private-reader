package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.repository.RepositoryModule;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import org.jetbrains.annotations.NotNull;

/**
 * 重置阅读进度动作
 */
public class ResetProgressAction extends AnAction implements DumbAware {
    public ResetProgressAction() {
        super("重置进度", "重置选中书籍的阅读进度", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PrivateReaderPanel readerPanel = PrivateReaderPanel.getInstance(project);
        if (readerPanel == null) {
            Messages.showWarningDialog(project,
                "阅读器窗口未初始化",
                "提示");
            return;
        }

        Book selectedBook = readerPanel.getBookList().getSelectedValue();
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

        // 尝试使用Repository接口
        RepositoryModule repositoryModule = RepositoryModule.getInstance();
        if (repositoryModule != null) {
            ReadingProgressRepository progressRepository = repositoryModule.getReadingProgressRepository();
            if (progressRepository != null) {
                progressRepository.resetProgress(selectedBook);
                readerPanel.refresh();
                
                Messages.showInfoMessage(project,
                    String.format("已重置《%s》的阅读进度", selectedBook.getTitle()),
                    "重置成功");
                return;
            }
        }
        
        // 回退到旧的实现
        ReadingProgressManager progressManager = project.getService(ReadingProgressManager.class);
        progressManager.resetProgress(selectedBook);
        readerPanel.refresh();
        
        Messages.showInfoMessage(project,
            String.format("已重置《%s》的阅读进度", selectedBook.getTitle()),
            "重置成功");
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

        PrivateReaderPanel readerPanel = PrivateReaderPanel.getInstance(project);
        if (readerPanel == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        Book selectedBook = readerPanel.getBookList().getSelectedValue();
        e.getPresentation().setEnabled(selectedBook != null);
    }
} 