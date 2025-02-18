package com.lv.tool.privatereader.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 书籍列表右键菜单
 */
public final class BookListPopupMenu extends MouseAdapter {
    private final Project project;
    private final JBList<Book> bookList;

    public BookListPopupMenu(@NotNull Project project, @NotNull JBList<Book> bookList) {
        this.project = project;
        this.bookList = bookList;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopupMenu(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopupMenu(e);
        }
    }

    private void showPopupMenu(MouseEvent e) {
        int index = bookList.locationToIndex(e.getPoint());
        if (index != -1) {
            bookList.setSelectedIndex(index);
            
            DefaultActionGroup group = new DefaultActionGroup();
            group.add(ActionManager.getInstance().getAction("PrivateReader.OpenBook"));
            group.add(ActionManager.getInstance().getAction("PrivateReader.RemoveBook"));

            ActionPopupMenu popupMenu = ActionManager.getInstance()
                .createActionPopupMenu("PrivateReader.BookList", group);
            
            popupMenu.getComponent().show(bookList, e.getX(), e.getY());
        }
    }
}