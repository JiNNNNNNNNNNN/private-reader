package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.service.NotificationService;
import com.intellij.openapi.application.ApplicationManager;
import com.lv.tool.privatereader.ui.ReaderPanel;
import com.lv.tool.privatereader.ui.ReaderToolWindowFactory;
import com.lv.tool.privatereader.ui.dialog.ChapterListDialog;
import com.lv.tool.privatereader.util.PluginUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 显示章节列表的动作
 * 用于在菜单栏中添加"章节列表"选项
 */
public class ShowChapterListAction extends BaseAction {
    private static final Logger LOG = Logger.getInstance(ShowChapterListAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            LOG.info("触发显示章节列表动作");
            ReaderPanel panel = ReaderToolWindowFactory.findPanel(project);
            if (panel != null) {
                Book selectedBook = panel.getSelectedBook();
                if (selectedBook != null) {
                    LOG.info("显示章节列表对话框：书籍=" + selectedBook.getTitle());
                    try {
                        // 检查书籍解析器
                        if (selectedBook.getParser() == null) {
                            LOG.error("无法显示章节列表：书籍解析器为空，书籍=" + selectedBook.getTitle() + ", URL=" + selectedBook.getUrl());
                            ApplicationManager.getApplication().getService(NotificationService.class).showError("无法显示章节列表", "书籍解析器初始化失败");
                            return;
                        }

                        ChapterListDialog dialog = new ChapterListDialog(project, selectedBook);
                        if (dialog.showAndGet()) {
                            // 对话框通过OK按钮关闭，获取选中的章节
                            NovelParser.Chapter selectedChapter = dialog.getSelectedChapter();
                            if (selectedChapter != null) {
                                LOG.info("用户选择了章节：" + selectedChapter.title());
                                // 加载选中的章节
                                panel.loadChapter(selectedBook, selectedChapter);
                            }
                        }
                    } catch (Exception ex) {
                        LOG.error("显示章节列表对话框失败：" + ex.getMessage(), ex);
                        ApplicationManager.getApplication().getService(NotificationService.class).showError("显示章节列表对话框失败", ex.getMessage());
                    }
                } else {
                    LOG.warn("无法显示章节列表：未选择书籍");
                    Messages.showWarningDialog(project, "请先选择一本书籍", "提示");
                }
            } else {
                LOG.error("无法显示章节列表：未找到阅读器面板");
                ApplicationManager.getApplication().getService(NotificationService.class).showError("错误", "无法显示章节列表：未找到阅读器面板");
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
        if (project != null && PluginUtil.isPluginEnabled()) {
            ReaderPanel panel = ReaderToolWindowFactory.findPanel(project);
            e.getPresentation().setEnabled(panel != null && panel.getSelectedBook() != null);
        } else {
            e.getPresentation().setEnabled(false);
        }
    }
}