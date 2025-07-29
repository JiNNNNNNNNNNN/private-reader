package com.lv.tool.privatereader.ui.mvi;

/**
 * Defines all possible user intentions that can be dispatched from the View to the ViewModel.
 * Using a sealed interface ensures that all intent types are known at compile time,
 * providing a robust and type-safe way to handle user actions.
 */
public sealed interface IReaderIntent {
    /**
     * Represents a one-time event to load the initial data for the screen.
     * This includes the book list and the last read book/chapter.
     */
    final class LoadInitialData implements IReaderIntent {}

    /**
     * User selects a book from the list.
     * @param bookId The unique identifier of the selected book.
     */
    final record SelectBook(String bookId) implements IReaderIntent {}

    /**
     * User adds a new book via URL.
     * @param url The URL of the book to be added.
     */
    final record AddBook(String url) implements IReaderIntent {}

    /**
     * User deletes the selected book.
     * @param bookId The unique identifier of the book to be deleted.
     */
    final record DeleteBook(String bookId) implements IReaderIntent {}

    /**
     * User searches for a book by a keyword.
     * @param keyword The search term.
     */
    final record SearchBook(String keyword) implements IReaderIntent {}

    /**
     * User selects a chapter from the list.
     * @param chapterId The unique URL or identifier of the selected chapter.
     */
    final record SelectChapter(String chapterId) implements IReaderIntent {}

    /**
     * User requests to refresh the chapter list of the selected book.
     */
    final class RefreshChapters implements IReaderIntent {}

    /**
     * User navigates to the next or previous chapter.
     * @param direction -1 for previous, 1 for next.
     */
    final record NavigateChapter(int direction) implements IReaderIntent {}

    /**
     * User's reading progress needs to be saved.
     * @param chapterId The unique URL or identifier of the chapter being read.
     * @param position The scroll position within the chapter.
     */
     final record SaveProgress(String chapterId, int position) implements IReaderIntent {}
 
    /**
     * An external event (e.g., from Notification Bar) changed the chapter.
     * @param book The book associated with the chapter change.
     * @param chapter The new chapter.
     */
    final record HandleExternalChapterChange(com.lv.tool.privatereader.model.Book book, com.lv.tool.privatereader.parser.NovelParser.Chapter chapter) implements IReaderIntent {}
 }