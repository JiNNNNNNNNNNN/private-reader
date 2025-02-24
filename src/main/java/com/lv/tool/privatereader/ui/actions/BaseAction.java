package com.lv.tool.privatereader.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.util.PluginUtil;
import org.jetbrains.annotations.NotNull;

public abstract class BaseAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null && PluginUtil.isPluginEnabled());
    }
} 