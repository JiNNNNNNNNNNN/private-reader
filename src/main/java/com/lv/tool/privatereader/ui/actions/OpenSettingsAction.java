package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.lv.tool.privatereader.ui.settings.CacheConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * 打开设置动作
 */
public class OpenSettingsAction extends AnAction implements DumbAware {
    public OpenSettingsAction() {
        super("缓存设置", "打开缓存设置", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            e.getProject(),
            CacheConfigurable.class
        );
    }
    
    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        // 告诉 IntelliJ 在后台线程而非 EDT 线程中执行 update 方法
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只要有项目打开就可以打开设置
        e.getPresentation().setEnabled(e.getProject() != null);
    }
} 