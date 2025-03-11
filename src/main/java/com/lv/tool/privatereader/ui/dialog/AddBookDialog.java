package com.lv.tool.privatereader.ui.dialog;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.exception.ExceptionHandler;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.service.BookService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

public class AddBookDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(AddBookDialog.class);
    private final JBTextField urlField;
    private final Project project;
    private final BookService bookService;

    public AddBookDialog(Project project) {
        super(project);
        this.project = project;
        this.bookService = project.getService(BookService.class);
        
        setTitle("添加书籍");
        setSize(400, 150);
        
        urlField = new JBTextField();
        urlField.setPreferredSize(new Dimension(300, 30));
        
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(new JBLabel("书籍网址:"), BorderLayout.WEST);
        inputPanel.add(urlField, BorderLayout.CENTER);
        
        panel.add(inputPanel, BorderLayout.NORTH);
        
        JLabel tipLabel = new JBLabel("提示: 输入小说网址，系统将自动解析书籍信息");
        tipLabel.setForeground(Color.GRAY);
        panel.add(tipLabel, BorderLayout.SOUTH);
        
        return panel;
    }

    @Override
    protected void doOKAction() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            Messages.showErrorDialog(project, "请输入书籍网址", "错误");
            return;
        }
        
        try {
            // 创建解析器
            NovelParser parser = ParserFactory.createParser(url);
            if (parser == null) {
                Messages.showErrorDialog(project, "不支持的网站或网址格式不正确", "错误");
                return;
            }
            
            // 获取书籍信息
            String title = parser.getTitle();
            String author = parser.getAuthor();
            
            if (title == null || title.isEmpty()) {
                Messages.showErrorDialog(project, "无法获取书籍标题", "错误");
                return;
            }
            
            // 创建书籍对象
            Book book = new Book();
            book.setId(UUID.randomUUID().toString());
            book.setTitle(title);
            book.setAuthor(author != null ? author : "未知");
            book.setUrl(url);
            book.setCreateTimeMillis(System.currentTimeMillis());
            book.setParser(parser);
            
            // 添加书籍
            boolean success = bookService.addBook(book);
            if (success) {
                super.doOKAction();
            } else {
                Messages.showErrorDialog(project, "添加书籍失败", "错误");
            }
        } catch (Exception e) {
            ExceptionHandler.handle(project, e, "添加书籍失败: " + e.getMessage());
            LOG.error("添加书籍失败", e);
        }
    }
} 