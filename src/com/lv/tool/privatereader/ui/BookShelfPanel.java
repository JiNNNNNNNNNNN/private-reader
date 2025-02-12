package com.lv.tool.privatereader.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.persistence.BookStorage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * 书架面板
 */
public final class BookShelfPanel extends JPanel {
    private final Project project;
    private final JBList<Book> bookList;
    private final DefaultListModel<Book> listModel;
    private final BookStorage bookStorage;

    public BookShelfPanel(@NotNull Project project) {
        this.project = project;
        this.bookStorage = project.getService(BookStorage.class);
        this.listModel = new DefaultListModel<>();
        this.bookList = new JBList<>(listModel);

        setupUI();
        loadBooks();
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        // 工具栏
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(ActionManager.getInstance().getAction("PrivateReader.AddBook"));
        actionGroup.add(ActionManager.getInstance().getAction("PrivateReader.RemoveBook"));

        ActionToolbar toolbar = ActionManager.getInstance()
            .createActionToolbar("PrivateReader.Toolbar", actionGroup, true);
        add(toolbar.getComponent(), BorderLayout.NORTH);

        // 书籍列表
        bookList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Book book) {
                    setText(book.toString());
                }
                return this;
            }
        });

        bookList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookList.addMouseListener(new BookListPopupMenu(project, bookList));

        add(new JBScrollPane(bookList), BorderLayout.CENTER);
    }

    private void loadBooks() {
        listModel.clear();
        bookStorage.getBooks().forEach(listModel::addElement);
    }

    public void refresh() {
        loadBooks();
    }

    public JBList<Book> getBookList() {
        return bookList;
    }

    public DefaultListModel<Book> getListModel() {
        return listModel;
    }
} 