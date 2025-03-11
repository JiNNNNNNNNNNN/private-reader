package com.lv.tool.privatereader.repository.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.ReadingProgressRepository;
import com.lv.tool.privatereader.ui.topics.BookshelfTopics;
import org.jetbrains.annotations.NotNull;

/**
 * 文件阅读进度仓库实现
 * 
 * 基于文件系统实现阅读进度仓库接口，管理书籍阅读进度的持久化存储。
 * 通过BookRepository更新书籍信息，实现阅读进度的存储。
 */
@Service(Service.Level.PROJECT)
public class FileReadingProgressRepository implements ReadingProgressRepository {
    private static final Logger LOG = Logger.getInstance(FileReadingProgressRepository.class);
    
    private final Project project;
    private final BookRepository bookRepository;
    
    public FileReadingProgressRepository(Project project, BookRepository bookRepository) {
        this.project = project;
        this.bookRepository = bookRepository;
    }
    
    @Override
    public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position) {
        updateProgress(book, chapterId, chapterTitle, position, 1);
    }
    
    @Override
    public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position, int page) {
        if (book == null || chapterId == null) {
            LOG.warn("无法更新阅读进度：book 或 chapterId 为空");
            return;
        }
        
        // 更新书籍进度
        book.updateReadingProgress(chapterId, chapterTitle, position, page);
        
        // 更新存储
        bookRepository.updateBook(book);
        
        // 发布更新事件
        project.getMessageBus().syncPublisher(BookshelfTopics.BOOK_UPDATED).bookUpdated(book);
    }
    
    @Override
    public void updateTotalChapters(@NotNull Book book, int totalChapters) {
        if (book == null) {
            LOG.warn("无法更新章节总数：book 为空");
            return;
        }
        
        book.setTotalChapters(totalChapters);
        
        // 更新已读未读状态
        if (book.getCurrentChapterIndex() >= totalChapters - 1 && book.getLastReadPosition() > 0.9) {
            book.setFinished(true);
        } else {
            book.setFinished(false);
        }
        
        bookRepository.updateBook(book);
    }
    
    @Override
    public void updateCurrentChapter(@NotNull Book book, int currentChapterIndex) {
        if (book == null) {
            LOG.warn("无法更新当前章节：book 为空");
            return;
        }
        
        book.setCurrentChapterIndex(currentChapterIndex);
        
        // 保存更新
        bookRepository.updateBook(book);
        
        // 发布更新事件
        project.getMessageBus().syncPublisher(BookshelfTopics.BOOK_UPDATED).bookUpdated(book);
    }
    
    @Override
    public void markAsFinished(@NotNull Book book) {
        if (book == null) {
            LOG.warn("无法标记为已读：book 为空");
            return;
        }
        
        book.setFinished(true);
        book.setCurrentChapterIndex(book.getTotalChapters());
        
        bookRepository.updateBook(book);
    }
    
    @Override
    public void markAsUnfinished(@NotNull Book book) {
        if (book == null) {
            LOG.warn("无法标记为未读：book 为空");
            return;
        }
        
        book.setFinished(false);
        
        bookRepository.updateBook(book);
    }
    
    @Override
    public void resetProgress(@NotNull Book book) {
        if (book == null) {
            LOG.warn("无法重置阅读进度：book 为空");
            return;
        }
        
        book.setCurrentChapterIndex(0);
        book.updateReadingProgress("", "", 0, 1);
        
        // 保存更新
        bookRepository.updateBook(book);
        
        // 发布更新事件
        project.getMessageBus().syncPublisher(BookshelfTopics.BOOK_UPDATED).bookUpdated(book);
    }
}