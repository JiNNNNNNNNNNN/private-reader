package com.lv.tool.privatereader.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class OpenBookAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor("txt"), 
            e.getProject(), 
            null, 
            file -> openBookFile(file, e.getProject()));
    }
    
    private void openBookFile(VirtualFile file, Project project) {
        try {
            String content = new String(file.contentsToByteArray());
            FileEditorManager.getInstance(project).openTextEditor(
                new OpenFileDescriptor(project, file),
                true
            );
        } catch (IOException ex) {
            Messages.showErrorDialog("文件读取失败: " + ex.getMessage(), "打开失败");
        }
    }
} 