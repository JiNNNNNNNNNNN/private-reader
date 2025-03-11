package com.lv.tool.privatereader.service.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.storage.BookStorage;
import com.lv.tool.privatereader.storage.ReadingProgressManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * BookService接口的实现类
 */
public final class BookServiceImpl implements BookService {
    private static final Logger LOG = Logger.getInstance(BookServiceImpl.class);
    private final BookStorage bookStorage;
    private final ReadingProgressManager progressManager;

    public BookServiceImpl(Project project) {
        LOG.info("初始化BookServiceImpl");
        this.bookStorage = project.getService(BookStorage.class);
        this.progressManager = project.getService(ReadingProgressManager.class);
        
        if (this.bookStorage == null || this.progressManager == null) {
            LOG.error("BookStorage或ReadingProgressManager服务未初始化");
        }
    }

    @Override
    public List<Book> getAllBooks() {
        if (bookStorage == null) {
            LOG.error("BookStorage服务未初始化");
            return List.of();
        }
        return bookStorage.getAllBooks();
    }

    @Override
    public boolean addBook(Book book) {
        if (bookStorage == null) {
            LOG.error("BookStorage服务未初始化");
            return false;
        }
        bookStorage.addBook(book);
        return true;
    }

    @Override
    public boolean removeBook(Book book) {
        if (bookStorage == null) {
            LOG.error("BookStorage服务未初始化");
            return false;
        }
        bookStorage.removeBook(book);
        return true;
    }

    @Override
    public boolean updateBook(Book book) {
        if (bookStorage == null) {
            LOG.error("BookStorage服务未初始化");
            return false;
        }
        bookStorage.updateBook(book);
        return true;
    }

    @Override
    public Book getLastReadBook() {
        if (bookStorage == null) {
            LOG.error("BookStorage服务未初始化");
            return null;
        }
        
        List<Book> books = getAllBooks();
        if (books.isEmpty()) {
            return null;
        }
        
        return books.stream()
                .filter(book -> book.getLastReadTimeMillis() > 0)
                .max((b1, b2) -> Long.compare(b1.getLastReadTimeMillis(), b2.getLastReadTimeMillis()))
                .orElse(null);
    }

    @Override
    public void saveReadingProgress(@NotNull Book book, String chapterId, int position) {
        if (progressManager == null) {
            LOG.error("ReadingProgressManager服务未初始化");
            return;
        }
        
        // 获取章节标题
        String chapterTitle = "";
        if (book.getCachedChapters() != null) {
            chapterTitle = book.getCachedChapters().stream()
                    .filter(chapter -> chapterId.equals(chapter.url()))
                    .map(chapter -> chapter.title())
                    .findFirst()
                    .orElse("");
        }
        
        progressManager.updateProgress(book, chapterId, chapterTitle, position);
        book.setLastReadChapterId(chapterId);
        book.setLastReadTimeMillis(System.currentTimeMillis());
        updateBook(book);
    }
} 