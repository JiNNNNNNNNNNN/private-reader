package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.ui.settings.ReaderModeConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * 打开阅读模式设置的操作
 */
public class OpenReaderModeSettingsAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
                e.getProject(),
                ReaderModeConfigurable.class
        );
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        PluginSettings pluginSettings = ApplicationManager.getApplication().getService(PluginSettings.class);
        e.getPresentation().setEnabled(pluginSettings.isEnabled());
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        // 告诉 IntelliJ 在后台线程而非 EDT 线程中执行 update 方法
        return ActionUpdateThread.BGT;
    }
} 