package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class AboutAction extends AnAction {
    private static final String ABOUT_CONTENT = 
        "Private Reader v1.0.0\n\n" +
        "一个轻量级的小说阅读插件，支持多个小说网站的内容抓取和本地阅读。\n\n" +
        "主要功能：\n" +
        "- 支持通用网站内容智能抓取\n" +
        "- 本地阅读进度保存\n" +
        "- 章节内容缓存\n" +
        "- 优雅的阅读界面\n" +
        "- 快捷键支持\n" +
        "- 自定义字体和大小\n\n" +
        "作者：J1N-\n" +
        "GitHub：https://github.com/JiNNNNNNNNNNN/private-reader";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            Messages.showInfoMessage(project, ABOUT_CONTENT, "关于 Private Reader");
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
        e.getPresentation().setEnabled(project != null);
    }
} 