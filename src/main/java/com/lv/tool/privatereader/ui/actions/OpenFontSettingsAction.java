package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.ui.settings.ReaderConfigurable;
import org.jetbrains.annotations.NotNull;

public class OpenFontSettingsAction extends BaseAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, ReaderConfigurable.class);
        }
    }
} 