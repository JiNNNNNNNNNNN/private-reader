package com.lv.tool.privatereader.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.service.BookService;
import org.jetbrains.annotations.NotNull;
import java.time.Instant;

/**
 * 阅读进度管理器
 */
@Service(Service.Level.APP)
public final class ReadingProgressManager {
    private static final Logger LOG = Logger.getInstance(ReadingProgressManager.class);
    
    private final BookService bookService;

    public ReadingProgressManager() {
        LOG.info("初始化应用级别的 ReadingProgressManager");
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        if (this.bookService == null) {
            LOG.error("无法获取BookService服务");
        }
    }

    /**
     * 更新阅读进度
     */
    public void updateProgress(@NotNull Book book, String chapterId, String chapterTitle, int position) {
        updateProgress(book, chapterId, chapterTitle, position, 1);
    }

    public void updateProgress(Book book, String chapterId, String chapterTitle, int position, int page) {
        if (book == null || chapterId == null) {
            LOG.warn("无法更新阅读进度：book 或 chapterId 为空");
            return;
        }
        
        LOG.info(String.format("尝试更新进度: 书籍='%s'(ID:%s), 章节ID=%s, 章节标题='%s', 位置=%d, 页码=%d",
            book.getTitle(), book.getId(), chapterId, chapterTitle, position, page));
            
        // 更新书籍进度
        book.updateReadingProgress(chapterId, position, page);
        
        // 更新存储
        BookService currentService = bookService;
        if (currentService == null) {
            LOG.warn("构造函数中未能初始化BookService，尝试再次获取...");
            currentService = ApplicationManager.getApplication().getService(BookService.class);
        }
            
        if (currentService != null) {
            final BookService serviceToUse = currentService;
            LOG.info("准备调用 BookService.updateBook() 保存书籍: " + book.getTitle());
            WriteAction.run(() -> {
                try {
                    serviceToUse.updateBook(book);
                    LOG.info("成功调用 BookService.updateBook() 保存书籍: " + book.getTitle());
                } catch (Exception e) {
                    LOG.error("调用 BookService.updateBook() 时发生异常: " + book.getTitle(), e);
                }
            });
        } else {
            LOG.error("无法获取 BookService 服务实例，无法保存书籍更新: " + book.getTitle());
        }
        
        // 在应用级别服务中，不再发布项目级别的消息总线事件
    }

    /**
     * 更新章节总数
     */
    public void updateTotalChapters(@NotNull Book book, int totalChapters) {
        if (book == null) {
            LOG.warn("无法更新章节总数：book 为空");
            return;
        }
        LOG.info(String.format("尝试更新章节总数: 书籍='%s'(ID:%s), 总数=%d", 
            book.getTitle(), book.getId(), totalChapters));
            
        book.setTotalChapters(totalChapters);
        
        // 更新已读未读状态
        if (book.getCurrentChapterIndex() >= totalChapters - 1 && book.getLastReadPosition() > 0.9) {
            book.setFinished(true);
        } else {
            book.setFinished(false);
        }
        
        if (bookService != null) {
            WriteAction.run(() -> bookService.updateBook(book));
        } else {
            BookService service = ApplicationManager.getApplication().getService(BookService.class);
            if (service != null) {
                WriteAction.run(() -> service.updateBook(book));
            } else {
                LOG.error("无法获取BookService服务");
            }
        }
    }

    /**
     * 更新当前章节索引
     */
    public void updateCurrentChapter(@NotNull Book book, int currentChapterIndex) {
        if (book == null) {
            LOG.warn("无法更新当前章节：book 为空");
            return;
        }
        LOG.info(String.format("尝试更新当前章节索引: 书籍='%s'(ID:%s), 索引=%d", 
            book.getTitle(), book.getId(), currentChapterIndex));
            
        book.setCurrentChapterIndex(currentChapterIndex);
        
        // 保存更新
        if (bookService != null) {
            WriteAction.run(() -> bookService.updateBook(book));
        } else {
            BookService service = ApplicationManager.getApplication().getService(BookService.class);
            if (service != null) {
                WriteAction.run(() -> service.updateBook(book));
            } else {
                LOG.error("无法获取BookService服务");
            }
        }
        
        // 在应用级别服务中，不再发布项目级别的消息总线事件
    }

    /**
     * 标记书籍为已读
     */
    public void markAsFinished(@NotNull Book book) {
        if (book == null) {
            LOG.warn("无法标记书籍为已读：book 为空");
            return;
        }
        LOG.info(String.format("尝试标记书籍为已读: 书籍='%s'(ID:%s)", book.getTitle(), book.getId()));
        
        book.setFinished(true);
        book.setCurrentChapterIndex(book.getTotalChapters());
        
        if (bookService != null) {
            WriteAction.run(() -> bookService.updateBook(book));
        } else {
            BookService service = ApplicationManager.getApplication().getService(BookService.class);
            if (service != null) {
                WriteAction.run(() -> service.updateBook(book));
            } else {
                LOG.error("无法获取BookService服务");
            }
        }
    }

    /**
     * 标记书籍为未读
     */
    public void markAsUnfinished(@NotNull Book book) {
        if (book == null) {
            LOG.warn("无法标记书籍为未读：book 为空");
            return;
        }
        LOG.info(String.format("尝试标记书籍为未读: 书籍='%s'(ID:%s)", book.getTitle(), book.getId()));
        
        book.setFinished(false);
        
        if (bookService != null) {
            WriteAction.run(() -> bookService.updateBook(book));
        } else {
            BookService service = ApplicationManager.getApplication().getService(BookService.class);
            if (service != null) {
                WriteAction.run(() -> service.updateBook(book));
            } else {
                LOG.error("无法获取BookService服务");
            }
        }
    }

    /**
     * 重置阅读进度
     */
    public void resetProgress(@NotNull Book book) {
        if (book == null) {
            LOG.warn("无法重置阅读进度：book 为空");
            return;
        }
        LOG.info(String.format("尝试重置阅读进度: 书籍='%s'(ID:%s)", book.getTitle(), book.getId()));
        
        book.setCurrentChapterIndex(0);
        book.updateReadingProgress("", 0, 1);
        
        // 保存更新
        if (bookService != null) {
            WriteAction.run(() -> bookService.updateBook(book));
        } else {
            BookService service = ApplicationManager.getApplication().getService(BookService.class);
            if (service != null) {
                WriteAction.run(() -> service.updateBook(book));
            } else {
                LOG.error("无法获取BookService服务");
            }
        }
        
        // 在应用级别服务中，不再发布项目级别的消息总线事件
    }
}
