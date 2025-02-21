package com.lv.tool.privatereader.ui.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class FeedbackAction extends AnAction {
    private static final String FEEDBACK_URL = "https://github.com/JiNNNNNNNNNNN/private-reader/issues";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        BrowserUtil.browse(FEEDBACK_URL);
    }
} 