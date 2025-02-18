package com.lv.tool.privatereader.ui.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBList;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.ui.PrivateReaderPanel;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.lv.tool.privatereader.parser.NovelParser;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AddBookDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(AddBookDialog.class);
    private final Project project;
    private JBTextField urlField;

    public AddBookDialog(Project project) {
        super(project);
        this.project = project;
        init();
        setTitle("添加书籍");
        LOG.debug("创建添加书籍对话框");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        LOG.debug("创建对话框界面");
        JPanel panel = new JPanel(new GridLayout(1, 2, 5, 5));
        
        urlField = new JBTextField();
        
        panel.add(new JLabel("网址:"));
        panel.add(urlField);
        
        return panel;
    }

    @Override
    protected ValidationInfo doValidate() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            LOG.debug("网址为空，验证失败");
            return new ValidationInfo("网址不能为空", urlField);
        }
        LOG.debug("验证通过");
        return null;
    }

    @Override
    protected void doOKAction() {
        String url = urlField.getText().trim();
        LOG.info("开始添加书籍，URL: " + url);
        
        try {
            // 创建解析器并获取书籍信息
            LOG.debug("创建解析器");
            NovelParser parser = ParserFactory.createParser(url);
            
            LOG.debug("获取书籍信息");
            String title = parser.getTitle();
            String author = parser.getAuthor();
            
            LOG.info(String.format("解析到书籍信息 - 标题: %s, 作者: %s", title, author));
            
            Book book = new Book();
            // 生成唯一ID
            String uniqueId = String.format("%s_%s_%d", 
                title.replaceAll("[^a-zA-Z0-9]", "_"),
                author.replaceAll("[^a-zA-Z0-9]", "_"),
                System.currentTimeMillis()
            );
            book.setId(uniqueId);
            book.setTitle(title);
            book.setAuthor(author);
            book.setUrl(url);
            book.setProject(project);
            book.setParser(parser);  // 设置解析器
            
            // 获取章节列表
            LOG.debug("获取章节列表");
            List<NovelParser.Chapter> chapters = parser.getChapterList(book);
            book.setCachedChapters(chapters);
            book.setTotalChapters(chapters.size());
            LOG.info(String.format("获取到章节数量: %d", chapters.size()));
            
            BookStorage bookStorage = project.getService(BookStorage.class);
            bookStorage.addBook(book);
            
            // 刷新阅读器面板
            LOG.debug("刷新阅读器面板");
            PrivateReaderPanel panel = PrivateReaderPanel.getInstance(project);
            if (panel != null) {
                panel.refresh();
                // 自动选中新添加的书籍
                JBList<Book> bookList = panel.getBookList();
                ListModel<Book> model = bookList.getModel();
                for (int i = 0; i < model.getSize(); i++) {
                    if (model.getElementAt(i).getId().equals(book.getId())) {
                        bookList.setSelectedIndex(i);
                        bookList.ensureIndexIsVisible(i);
                        break;
                    }
                }
            }
            
            LOG.info("成功添加书籍: " + title);
            super.doOKAction();
        } catch (Exception e) {
            LOG.error("添加书籍失败: " + e.getMessage(), e);
            setErrorText("无法解析书籍信息：" + e.getMessage(), urlField);
        }
    }
} 