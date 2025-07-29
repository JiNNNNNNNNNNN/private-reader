package com.lv.tool.privatereader.mvi;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the immutable state of the Reader UI.
 * An instance of this class is a "snapshot" of the UI at a given moment.
 */
public final class ReaderUiState {

    private final List<Book> books;
    @Nullable
    private final String selectedBookId;

    private final List<NovelParser.Chapter> chapters;
    @Nullable
    private final String selectedChapterId;
    @Nullable
    private final String chapterContent;

    private final boolean isLoadingBooks;
    private final boolean isLoadingChapters;
    private final boolean isLoadingContent;
    @Nullable
    private final String error;

    public ReaderUiState(List<Book> books, @Nullable String selectedBookId, List<NovelParser.Chapter> chapters, @Nullable String selectedChapterId, @Nullable String chapterContent, boolean isLoadingBooks, boolean isLoadingChapters, boolean isLoadingContent, @Nullable String error) {
        this.books = Collections.unmodifiableList(books);
        this.selectedBookId = selectedBookId;
        this.chapters = Collections.unmodifiableList(chapters);
        this.selectedChapterId = selectedChapterId;
        this.chapterContent = chapterContent;
        this.isLoadingBooks = isLoadingBooks;
        this.isLoadingChapters = isLoadingChapters;
        this.isLoadingContent = isLoadingContent;
        this.error = error;
    }

    // Default initial state
    public static ReaderUiState initial() {
        return new ReaderUiState(Collections.emptyList(), null, Collections.emptyList(), null, null, true, false, false, null);
    }

    // Getters
    public List<Book> getBooks() { return books; }
    @Nullable public String getSelectedBookId() { return selectedBookId; }
    public List<NovelParser.Chapter> getChapters() { return chapters; }
    @Nullable public String getSelectedChapterId() { return selectedChapterId; }
    @Nullable public String getChapterContent() { return chapterContent; }
    public boolean isLoadingBooks() { return isLoadingBooks; }
    public boolean isLoadingChapters() { return isLoadingChapters; }
    public boolean isLoadingContent() { return isLoadingContent; }
    @Nullable public String getError() { return error; }

    // Builder-style copy method for creating new states
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReaderUiState that = (ReaderUiState) o;
        return isLoadingBooks == that.isLoadingBooks && isLoadingChapters == that.isLoadingChapters && isLoadingContent == that.isLoadingContent && books.equals(that.books) && Objects.equals(selectedBookId, that.selectedBookId) && chapters.equals(that.chapters) && Objects.equals(selectedChapterId, that.selectedChapterId) && Objects.equals(chapterContent, that.chapterContent) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(books, selectedBookId, chapters, selectedChapterId, chapterContent, isLoadingBooks, isLoadingChapters, isLoadingContent, error);
    }

    // Builder for easily creating modified state objects
    public static class Builder {
        private List<Book> books;
        private String selectedBookId;
        private List<NovelParser.Chapter> chapters;
        private String selectedChapterId;
        private String chapterContent;
        private boolean isLoadingBooks;
        private boolean isLoadingChapters;
        private boolean isLoadingContent;
        private String error;

        public Builder() { }

        private Builder(ReaderUiState currentState) {
            this.books = currentState.books;
            this.selectedBookId = currentState.selectedBookId;
            this.chapters = currentState.chapters;
            this.selectedChapterId = currentState.selectedChapterId;
            this.chapterContent = currentState.chapterContent;
            this.isLoadingBooks = currentState.isLoadingBooks;
            this.isLoadingChapters = currentState.isLoadingChapters;
            this.isLoadingContent = currentState.isLoadingContent;
            this.error = currentState.error;
        }

        public Builder books(List<Book> books) { this.books = books; return this; }
        public Builder selectedBookId(String selectedBookId) { this.selectedBookId = selectedBookId; return this; }
        public Builder chapters(List<NovelParser.Chapter> chapters) { this.chapters = chapters; return this; }
        public Builder selectedChapterId(String selectedChapterId) { this.selectedChapterId = selectedChapterId; return this; }
        public Builder chapterContent(String chapterContent) { this.chapterContent = chapterContent; return this; }
        public Builder isLoadingBooks(boolean isLoadingBooks) { this.isLoadingBooks = isLoadingBooks; return this; }
        public Builder isLoadingChapters(boolean isLoadingChapters) { this.isLoadingChapters = isLoadingChapters; return this; }
        public Builder isLoadingContent(boolean isLoadingContent) { this.isLoadingContent = isLoadingContent; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public ReaderUiState build() {
            return new ReaderUiState(books, selectedBookId, chapters, selectedChapterId, chapterContent, isLoadingBooks, isLoadingChapters, isLoadingContent, error);
        }
    }
} 