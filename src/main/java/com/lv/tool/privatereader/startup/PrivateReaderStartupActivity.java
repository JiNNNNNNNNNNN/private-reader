package com.lv.tool.privatereader.startup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.lv.tool.privatereader.settings.PluginSettings;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/**
 * IDE 启动时初始化阅读器面板
 */
public class PrivateReaderStartupActivity implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
        // 检查插件是否启用
        PluginSettings settings = ApplicationManager.getApplication().getService(PluginSettings.class);
        if (settings != null && settings.isEnabled()) {
            // 在 EDT 中初始化阅读器面板
            ApplicationManager.getApplication().invokeLater(() -> {
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("PrivateReader");
                if (toolWindow != null) {
                    // 确保工具窗口可见
                    toolWindow.setAvailable(true);
                    
                    // 初始化阅读器面板
                    PrivateReaderPanel panel = new PrivateReaderPanel(project);
                    
                    // 主动加载上次阅读的书籍
                    BookStorage bookStorage = project.getService(BookStorage.class);
                    List<Book> books = bookStorage.getAllBooks();
                    if (!books.isEmpty()) {
                        Book lastReadBook = books.stream()
                                .filter(book -> book.getLastReadTimeMillis() > 0)
                                .max((b1, b2) -> Long.compare(b1.getLastReadTimeMillis(), b2.getLastReadTimeMillis()))
                                .orElse(null);
                        
                        if (lastReadBook != null) {
                            panel.loadLastReadChapter();
                        }
                    }
                }
            });
        }
    }
} 