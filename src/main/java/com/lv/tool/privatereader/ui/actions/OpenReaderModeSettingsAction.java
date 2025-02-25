package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
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
} 