package com.lv.tool.privatereader.ui.factory;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.ui.BookListPopupMenu;

import javax.swing.*;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleContext;
import java.awt.*;

/**
 * UI组件工厂
 * 负责创建和配置UI组件
 */
public class UIComponentFactory {
    
    /**
     * 创建书籍列表
     */
    public static JBList<Book> createBookList(Project project) {
        JBList<Book> bookList = new JBList<>();
        bookList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Book) {
                    Book book = (Book) value;
                    setText(book.getTitle());
                }
                return this;
            }
        });
        
        // 添加右键菜单监听器
        BookListPopupMenu popupMenuListener = new BookListPopupMenu(project, bookList);
        bookList.addMouseListener(popupMenuListener);
        
        return bookList;
    }
    
    /**
     * 创建章节列表
     */
    public static JBList<Chapter> createChapterList() {
        JBList<Chapter> chapterList = new JBList<>();
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Chapter) {
                    Chapter chapter = (Chapter) value;
                    setText(chapter.title());
                }
                return this;
            }
        });
        
        return chapterList;
    }
    
    /**
     * 创建内容显示区域
     */
    public static JTextPane createContentArea() {
        JTextPane contentArea = new JTextPane(new DefaultStyledDocument(new StyleContext()));
        contentArea.setEditable(false);
        contentArea.setMargin(JBUI.insets(10));
        
        return contentArea;
    }
    
    /**
     * 创建带滚动条的面板
     */
    public static JBScrollPane createScrollPane(Component view) {
        JBScrollPane scrollPane = new JBScrollPane(view);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        return scrollPane;
    }
    
    /**
     * 创建工具栏按钮
     */
    public static JButton createToolbarButton(String text, Icon icon) {
        JButton button = new JButton(text);
        if (icon != null) {
            button.setIcon(icon);
        }
        button.setFocusable(false);
        
        return button;
    }
    
    /**
     * 创建分割面板
     */
    public static JSplitPane createSplitPane(int orientation, Component leftComponent, Component rightComponent) {
        JSplitPane splitPane = new JSplitPane(orientation, leftComponent, rightComponent);
        splitPane.setDividerSize(3);
        splitPane.setDividerLocation(250);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(JBUI.Borders.empty());
        
        return splitPane;
    }
} 