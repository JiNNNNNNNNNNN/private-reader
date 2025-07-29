package com.lv.tool.privatereader.service.impl;

import com.google.inject.Inject;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookProgressData;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Optional;

public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;

    @Inject
    public BookServiceImpl(BookRepository bookRepository, ReadingProgressRepository readingProgressRepository) {
        this.bookRepository = bookRepository;
        this.readingProgressRepository = readingProgressRepository;
    }

    @Override
    public Flux<Book> getAllBooks() {
        return Mono.fromCallable(() -> bookRepository.getAllBooks())
                .subscribeOn(ReactiveSchedulers.getInstance().io())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Book> getBookById(@NotNull String bookId) {
        return Mono.fromCallable(() -> Optional.ofNullable(bookRepository.getBook(bookId)))
                .subscribeOn(ReactiveSchedulers.getInstance().io())
                .flatMap(optionalBook -> optionalBook.map(Mono::just).orElseGet(Mono::empty))
                .flatMap(this::loadProgressForBook);
    }
    
    @Override
    public Mono<Boolean> addBook(@NotNull Book book) {
         return Mono.<Boolean>create(sink -> {
            try {
                bookRepository.addBook(book);
                sink.success(true);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().io());
    }
    
    @Override
    public Mono<Boolean> removeBook(@NotNull Book book) {
        return Mono.<Boolean>create(sink -> {
            try {
                bookRepository.removeBook(book);
                sink.success(true);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().io());
    }

    @Override
    public Mono<Boolean> updateBook(@NotNull Book book) {
        return Mono.<Boolean>create(sink -> {
            try {
                bookRepository.updateBook(book);
                sink.success(true);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(ReactiveSchedulers.getInstance().io());
    }

    @Override
    public Mono<Book> getLastReadBook() {
        return Mono.fromCallable(() -> readingProgressRepository.getLastReadProgressData())
                .subscribeOn(ReactiveSchedulers.getInstance().io())
                .flatMap(optionalProgress ->
                        optionalProgress.map(progress -> getBookById(progress.bookId()))
                                .orElse(Mono.empty())
                );
    }

    @Override
    public Mono<Void> saveReadingProgress(@NotNull Book book, @NotNull String chapterId, String chapterTitle, int position) {
        return Mono.fromRunnable(() ->
                        readingProgressRepository.updateProgress(book, chapterId, chapterTitle, position))
                .subscribeOn(ReactiveSchedulers.getInstance().io()).then();
    }
    
    private Mono<Book> loadProgressForBook(Book book) {
        return Mono.fromCallable(() -> readingProgressRepository.getProgress(book.getId()))
                .subscribeOn(ReactiveSchedulers.getInstance().io())
                .map(optionalProgress -> {
                    optionalProgress.ifPresent(progress -> book.updateReadingProgress(
                            progress.lastReadChapterId(),
                            progress.lastReadPosition(),
                            progress.lastReadPage()
                    ));
                    return book;
                });
    }

    @Override
    public java.util.List<ChapterService.EnhancedChapter> getChaptersSync(@NotNull String bookId) {
        return null; // Not implemented
    }

    @Override
    public void clearChaptersCache(String bookId) {
        // TODO: Implement cache clearing logic in the repository layer
    }
}