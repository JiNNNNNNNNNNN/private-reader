package com.lv.tool.privatereader.service.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.repository.RepositoryModule;
import com.lv.tool.privatereader.service.BookService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * BookService接口的实现类
 */
public final class BookServiceImpl implements BookService {
    private static final Logger LOG = Logger.getInstance(BookServiceImpl.class);
    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;

    public BookServiceImpl(Project project) {
        LOG.info("初始化BookServiceImpl");
        
        // 使用RepositoryModule获取Repository实例
        RepositoryModule repositoryModule = project.getService(RepositoryModule.class);
        if (repositoryModule != null) {
            this.bookRepository = repositoryModule.getBookRepository();
            this.readingProgressRepository = repositoryModule.getReadingProgressRepository();
        } else {
            LOG.error("RepositoryModule服务未初始化，尝试使用旧的存储服务");
            // 兼容旧版本，使用旧的存储服务
            this.bookRepository = null;
            this.readingProgressRepository = null;
        }
        
        if (this.bookRepository == null || this.readingProgressRepository == null) {
            LOG.error("BookRepository或ReadingProgressRepository服务未初始化");
        }
    }

    @Override
    public List<Book> getAllBooks() {
        if (bookRepository == null) {
            LOG.error("BookRepository服务未初始化");
            return List.of();
        }
        return bookRepository.getAllBooks();
    }

    @Override
    public boolean addBook(Book book) {
        if (bookRepository == null) {
            LOG.error("BookRepository服务未初始化");
            return false;
        }
        bookRepository.addBook(book);
        return true;
    }

    @Override
    public boolean removeBook(Book book) {
        if (bookRepository == null) {
            LOG.error("BookRepository服务未初始化");
            return false;
        }
        bookRepository.removeBook(book);
        return true;
    }

    @Override
    public boolean updateBook(Book book) {
        if (bookRepository == null) {
            LOG.error("BookRepository服务未初始化");
            return false;
        }
        bookRepository.updateBook(book);
        return true;
    }

    @Override
    public Book getLastReadBook() {
        if (bookRepository == null) {
            LOG.error("BookRepository服务未初始化");
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
        if (readingProgressRepository == null) {
            LOG.error("ReadingProgressRepository服务未初始化");
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
        
        readingProgressRepository.updateProgress(book, chapterId, chapterTitle, position);
        book.setLastReadChapterId(chapterId);
        book.setLastReadTimeMillis(System.currentTimeMillis());
        updateBook(book);
    }
} 