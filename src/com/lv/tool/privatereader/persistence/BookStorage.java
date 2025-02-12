package com.lv.tool.privatereader.persistence;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍存储管理
 */
@State(
    name = "BookStorage",
    storages = @Storage("bookshelf.xml")
)
public final class BookStorage implements PersistentStateComponent<BookStorage.State> {
    private State state = new State();
    private boolean changed = false;

    public List<Book> getBooks() {
        return state.books;
    }

    public void addBook(Book book) {
        if (!state.books.contains(book)) {
            state.books.add(book);
            changed = true;
        }
    }

    public void removeBook(Book book) {
        if (state.books.remove(book)) {
            changed = true;
        }
    }

    public boolean isChanged() {
        return changed;
    }

    public void resetChanged() {
        changed = false;
    }

    @Override
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        changed = true;
    }

    public static class State {
        public List<Book> books = new ArrayList<>();
    }
} 