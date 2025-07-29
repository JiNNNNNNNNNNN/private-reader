package com.lv.tool.privatereader.ui.mvi;

import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class ReaderUiState {
    private final boolean isLoadingBooks;
    private final boolean isLoadingChapters;
    private final boolean isLoadingContent;
    private final List<Book> books;
    private final List<Chapter> chapters;
    private final String content;
    private final String currentChapterTitle;
    @Nullable
    private final String selectedBookId;
    @Nullable
    private final String selectedChapterId;
    @Nullable
    private final String error;
    public ReaderUiState(boolean isLoadingBooks, boolean isLoadingChapters, boolean isLoadingContent, List<Book> books, List<Chapter> chapters, String content, String currentChapterTitle, @Nullable String selectedBookId, @Nullable String selectedChapterId, @Nullable String error) {
        this.isLoadingBooks = isLoadingBooks;
        this.isLoadingChapters = isLoadingChapters;
        this.isLoadingContent = isLoadingContent;
        this.books = books;
        this.chapters = chapters;
        this.content = content;
        this.currentChapterTitle = currentChapterTitle;
        this.selectedBookId = selectedBookId;
        this.selectedChapterId = selectedChapterId;
        this.error = error;
    }

    public static ReaderUiState initial() {
        return new ReaderUiState(true, false, false, Collections.emptyList(), Collections.emptyList(), "", "", null, null, null);
    }

    // Getters
    public boolean isLoadingBooks() { return isLoadingBooks; }
    public boolean isLoadingChapters() { return isLoadingChapters; }
    public boolean isLoadingContent() { return isLoadingContent; }
    public List<Book> getBooks() { return books; }
    public List<Chapter> getChapters() { return chapters; }
    public String getContent() { return content; }
    public String getCurrentChapterTitle() { return currentChapterTitle; }
    @Nullable public String getSelectedBookId() { return selectedBookId; }
    @Nullable public String getSelectedChapterId() { return selectedChapterId; }
    @Nullable public String getError() { return error; }
    // Using a Builder for a cleaner copyWith pattern
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private boolean isLoadingBooks;
        private boolean isLoadingChapters;
        private boolean isLoadingContent;
        private List<Book> books;
        private List<Chapter> chapters;
        private String content;
        private String currentChapterTitle;
        @Nullable private String selectedBookId;
        @Nullable private String selectedChapterId;
        @Nullable private String error;
        public Builder() {}

        public Builder(ReaderUiState state) {
            this.isLoadingBooks = state.isLoadingBooks;
            this.isLoadingChapters = state.isLoadingChapters;
            this.isLoadingContent = state.isLoadingContent;
            this.books = state.books;
            this.chapters = state.chapters;
            this.content = state.content;
            this.currentChapterTitle = state.currentChapterTitle;
            this.selectedBookId = state.selectedBookId;
            this.selectedChapterId = state.selectedChapterId;
            this.error = state.error;
        }

        public Builder isLoadingBooks(boolean isLoadingBooks) { this.isLoadingBooks = isLoadingBooks; return this; }
        public Builder isLoadingChapters(boolean isLoadingChapters) { this.isLoadingChapters = isLoadingChapters; return this; }
        public Builder isLoadingContent(boolean isLoadingContent) { this.isLoadingContent = isLoadingContent; return this; }
        public Builder books(List<Book> books) { this.books = books; return this; }
        public Builder chapters(List<Chapter> chapters) { this.chapters = chapters; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder currentChapterTitle(String currentChapterTitle) { this.currentChapterTitle = currentChapterTitle; return this; }
        public Builder selectedBookId(@Nullable String selectedBookId) { this.selectedBookId = selectedBookId; return this; }
        public Builder selectedChapterId(@Nullable String selectedChapterId) { this.selectedChapterId = selectedChapterId; return this; }
        public Builder error(@Nullable String error) { this.error = error; return this; }
        public ReaderUiState build() {
            return new ReaderUiState(isLoadingBooks, isLoadingChapters, isLoadingContent, books, chapters, content, currentChapterTitle, selectedBookId, selectedChapterId, error);
        }
    }
}