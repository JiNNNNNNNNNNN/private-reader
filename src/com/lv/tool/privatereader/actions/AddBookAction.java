package com.lv.tool.privatereader.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.persistence.BookStorage;
import com.lv.tool.privatereader.ui.BookShelfPanel;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 添加书籍动作
 */
public class AddBookAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String url = Messages.showInputDialog(project,
            "请输入小说URL",
            "添加书籍",
            Messages.getQuestionIcon());

        if (url == null || url.trim().isEmpty()) return;

        try {
            // 验证并规范化 URL
            URI uri = new URI(url.trim());
            String normalizedUrl = uri.normalize().toString();

            NovelParser parser = ParserFactory.createParser(normalizedUrl);
            String title = parser.getTitle();
            String author = parser.getAuthor();

            Book book = new Book(title, author, normalizedUrl);
            BookStorage bookStorage = project.getService(BookStorage.class);
            bookStorage.addBook(book);

            // 刷新书架UI
            BookShelfPanel bookShelfPanel = BookShelfPanel.getInstance(project);
            if (bookShelfPanel != null) {
                bookShelfPanel.refresh();
            }

            Messages.showInfoMessage(project,
                String.format("成功添加《%s》 - %s", title, author),
                "添加成功");

        } catch (URISyntaxException ex) {
            Messages.showErrorDialog(project,
                "无效的URL格式: " + ex.getMessage(),
                "错误");
        } catch (Exception ex) {
            Messages.showErrorDialog(project,
                "添加书籍失败: " + ex.getMessage(),
                "错误");
        }
    }
} 