package com.lv.tool.privatereader.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.lv.tool.privatereader.reader.ReactiveChapterPreloader;
import com.lv.tool.privatereader.service.BookService;
import com.lv.tool.privatereader.service.ChapterService;
import com.lv.tool.privatereader.service.NotificationService;
import com.lv.tool.privatereader.async.ReactiveSchedulers;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.settings.*;
import com.lv.tool.privatereader.storage.ReadingProgressManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * 响应式UI适配器
 * 用于将响应式API与IntelliJ平台的UI组件连接
 */
@Service(Service.Level.PROJECT)
public final class ReactiveUIAdapter {
    private static final Logger LOG = Logger.getInstance(ReactiveUIAdapter.class);
    
    // 高亮颜色常量
    private static final Color SELECTION_BACKGROUND = new Color(0, 120, 215);
    private static final Color SELECTION_FOREGROUND = Color.WHITE;
    
    private final ReactiveSchedulers reactiveSchedulers;
    
    // 存储订阅引用，用于在组件销毁时取消订阅
    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    
    // 章节加载完成回调
    private Runnable onChaptersLoaded;
    // 书籍加载完成回调
    private Runnable onBooksLoaded;
    
    private final BookService bookService;
    private final ChapterService chapterService;
    private final NotificationService notificationService;
    private final ReactiveChapterPreloader chapterPreloader;
    private final Project project;
    
    public ReactiveUIAdapter(Project project) {
        this.project = project;
        this.reactiveSchedulers = ReactiveSchedulers.getInstance();
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        this.chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
        this.notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
        this.chapterPreloader = ApplicationManager.getApplication().getService(ReactiveChapterPreloader.class);
        LOG.info("初始化ReactiveUIAdapter");
        
        if (bookService == null || chapterService == null || notificationService == null) {
            LOG.error("一个或多个服务未能初始化 for ReactiveUIAdapter!");
            // Handle error appropriately, maybe throw exception?
        }
    }
    
    /**
     * 设置章节加载完成回调
     */
    public void setOnChaptersLoaded(Runnable callback) {
        this.onChaptersLoaded = callback;
    }
    
    /**
     * 设置书籍加载完成回调
     */
    public void setOnBooksLoaded(Runnable callback) {
        this.onBooksLoaded = callback;
    }
    
    /**
     * 加载所有书籍并更新UI
     *
     * @param booksListModel 书籍列表模型
     * @param loadingLabel 加载状态标签
     */
    public void loadAllBooks(DefaultListModel<Book> booksListModel, JLabel loadingLabel) {
        // 显示加载状态
        runOnUI(() -> loadingLabel.setVisible(true));
        
        // 创建并存储订阅
        Disposable subscription = bookService.getAllBooks()
            .subscribeOn(reactiveSchedulers.io())  // 使用IO调度器
            .collectList()  // 收集为列表
            .doFinally(signal -> runOnUI(() -> loadingLabel.setVisible(false)))  // 隐藏加载状态
            .subscribe(
                books -> runOnUI(() -> {
                    // 按照创建时间降序排序
                    List<Book> sortedBooks = books.stream()
                        .sorted(Comparator.comparingLong(Book::getCreateTimeMillis).reversed())
                        .collect(Collectors.toList());
                    updateBooksList(booksListModel, sortedBooks);
                    // 在书籍列表更新后调用回调
                    if (onBooksLoaded != null) {
                        LOG.info("Books loaded, calling onBooksLoaded callback.");
                        onBooksLoaded.run();
                    }
                }),
                error -> handleError("加载书籍失败", error)
            );
        
        // 存储订阅引用
        storeSubscription("loadAllBooks", subscription);
    }
    
    /**
     * 加载书籍章节并更新UI
     *
     * @param book 书籍
     * @param chaptersListModel 章节列表模型
     * @param chaptersList 章节列表组件
     * @param loadingLabel 加载状态标签
     */
    public void loadBookChapters(Book book, DefaultListModel<Chapter> chaptersListModel, JList<Chapter> chaptersList, JLabel loadingLabel) {
        // 显示加载状态
        runOnUI(() -> loadingLabel.setVisible(true));
        
        // 获取响应式结果
        Object result = chapterService.getChapterList(book);
        
        // 检查返回类型
        if (result instanceof Mono) {
            // 创建并存储订阅
            Disposable subscription = ((Mono<List<Chapter>>) result)
                .subscribeOn(reactiveSchedulers.io())  // 使用IO调度器
                .timeout(Duration.ofSeconds(30))  // 设置超时
                .retry(3)  // 失败重试
                .doFinally(signal -> runOnUI(() -> loadingLabel.setVisible(false)))  // 隐藏加载状态
                .subscribe(
                    chapters -> {
                        runOnUI(() -> {
                            updateChaptersList(chaptersListModel, chapters);
                            // 设置章节列表的高亮颜色
                            chaptersList.setSelectionBackground(SELECTION_BACKGROUND);
                            chaptersList.setSelectionForeground(SELECTION_FOREGROUND);
                            // 在章节列表加载完成后调用回调
                            if (book != null && onChaptersLoaded != null) {
                                LOG.info("Chapters loaded for book: " + book.getTitle() + ", calling onChaptersLoaded callback");
                                onChaptersLoaded.run();
                            }
                        });
                    },
                    error -> handleError("加载章节失败: " + book.getTitle(), error)
                );
            
            // 存储订阅引用
            storeSubscription("loadChapters-" + book.getId(), subscription);
        } else {
            // Not a Mono type, possibly a sync result
            LOG.warn("getChaptersReactive is not Mono type, cannot handle reactively");
            runOnUI(() -> {
                loadingLabel.setVisible(false);
                notificationService.showError("不支持的服务返回类型", "ChapterService.getChapterList 返回了不支持的类型 (非 Mono)").subscribe();
            });
        }
    }
    
    /**
     * 加载章节内容并显示
     *
     * @param book 书籍
     * @param chapter 章节
     * @param contentTextArea 内容文本区域
     * @param loadingLabel 加载状态标签
     */
    public void loadChapterContent(Book book, Chapter chapter, JTextArea contentTextArea, JLabel loadingLabel) {
        // 显示加载状态
        runOnUI(() -> {
            loadingLabel.setVisible(true);
            contentTextArea.setText("正在加载...");
        });
        
        // 获取响应式结果
        Mono<String> contentMono = getChapterContent(book, chapter.url());
        
        // 创建并存储订阅
        Disposable subscription = contentMono
            .subscribeOn(reactiveSchedulers.io())  // 使用IO调度器
            .timeout(Duration.ofSeconds(30))  // 设置超时
            .retry(2)  // 失败重试
            .doFinally(signal -> runOnUI(() -> loadingLabel.setVisible(false)))  // 隐藏加载状态
            .subscribe(
                content -> {
                    runOnUI(() -> {
                        contentTextArea.setText(content);
                        contentTextArea.setCaretPosition(0);  // 滚动到顶部
                    });
                    
                    // 预加载后续章节
                    preloadNextChapters(book, chapter);
                },
                error -> {
                    handleError("加载章节内容失败: " + chapter.title(), error);
                    runOnUI(() -> contentTextArea.setText("加载失败: " + error.getMessage()));
                }
            );
        
        // 存储订阅引用
        storeSubscription("loadContent-" + chapter.url(), subscription);
    }
    
    /**
     * 预加载后续章节
     */
    private void preloadNextChapters(Book book, Chapter currentChapter) {
        // Get reactive result
        Object chaptersResult = chapterService.getChapterList(book); // Use correct method
        
        // Check return type
        if (chaptersResult instanceof Mono) {
            // Create and store subscription
            Disposable subscription = ((Mono<List<Chapter>>) chaptersResult)
                .flatMap(chapters -> {
                    // Find index of current chapter
                    int currentIndex = -1;
                    for (int i = 0; i < chapters.size(); i++) {
                        if (chapters.get(i).equals(currentChapter)) {
                            currentIndex = i;
                            break;
                        }
                    }
                    if (currentIndex != -1) {
                        // Call renamed method in preloader
                        final int finalIndex = currentIndex; // Effectively final for lambda
                        return Mono.fromRunnable(() -> chapterPreloader.preloadChapters(book, finalIndex))
                                 .then(Mono.just(true)); // Indicate success
                    }
                    return Mono.empty(); // Chapter not found or no need to preload
                })
                .subscribeOn(reactiveSchedulers.background())  // Use background scheduler, low priority
                .subscribe(
                    result -> LOG.info("预加载后续章节成功"),
                    error -> handleError("预加载后续章节失败", error)
                );
            subscriptions.put("preloadChapters", subscription);
        } else {
            // Not a Mono type, possibly a sync result
            LOG.warn("getChapterList is not Mono type, cannot handle reactively for preloading");
        }
    }
    
    /**
     * 搜索书籍并更新UI
     *
     * @param keyword 关键词
     * @param booksListModel 书籍列表模型
     * @param loadingLabel 加载状态标签
     */
    public void searchBooks(String keyword, DefaultListModel<Book> booksListModel, JLabel loadingLabel) {
        // 显示加载状态
        runOnUI(() -> loadingLabel.setVisible(true));
        
        // 创建并存储订阅
        Disposable subscription = bookService.getAllBooks()
            .subscribeOn(reactiveSchedulers.compute())  // 使用计算调度器，因为需要过滤操作
            .filter(book -> matchesKeyword(book, keyword))  // 过滤匹配的书籍
            .collectList()  // 收集为列表
            .doFinally(signal -> runOnUI(() -> loadingLabel.setVisible(false)))  // 隐藏加载状态
            .subscribe(
                books -> runOnUI(() -> updateBooksList(booksListModel, books)),
                error -> handleError("搜索书籍失败", error)
            );
        
        // 存储订阅引用
        storeSubscription("searchBooks-" + keyword, subscription);
    }
    
    /**
     * 检查书籍是否匹配关键词
     */
    private boolean matchesKeyword(Book book, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        
        String lowerKeyword = keyword.toLowerCase();
        return book.getTitle().toLowerCase().contains(lowerKeyword) ||
               book.getAuthor().toLowerCase().contains(lowerKeyword) ||
               book.getUrl().toLowerCase().contains(lowerKeyword);
    }
    
    /**
     * 添加新书籍并更新UI
     *
     * @param url 书籍URL
     * @param booksListModel 书籍列表模型
     * @param loadingLabel 加载状态标签
     * @param onSuccess 成功回调
     */
    public void addNewBook(String url, DefaultListModel<Book> booksListModel, JLabel loadingLabel, Runnable onSuccess) {
        // 显示加载状态
        runOnUI(() -> loadingLabel.setVisible(true));
        
        // 创建并存储订阅
        Disposable subscription = bookService.addBook(new Book(generateId(url), extractTitle(url), "", url))
            .subscribeOn(reactiveSchedulers.io())  // 使用IO调度器
            .timeout(Duration.ofMinutes(1))  // 设置超时
            .doFinally(signal -> runOnUI(() -> loadingLabel.setVisible(false)))  // 隐藏加载状态
            .subscribe(
                success -> {
                    if (success) {
                        runOnUI(() -> {
                            // Workaround: Reload all books and find the new one by URL
                            LOG.info("Book added successfully, reloading list to find it...");
                            loadAllBooks(booksListModel, loadingLabel); // Reload the list
                            // Trigger onSuccess after a short delay to allow list update?
                            if (onSuccess != null) {
                                Mono.delay(Duration.ofMillis(100)).publishOn(reactiveSchedulers.ui()).subscribe(v -> onSuccess.run());
                            }
                        });
                    } else {
                        runOnUI(() -> {
                            // 显示失败通知
                            notificationService.showError("添加书籍失败", "无法添加书籍: " + url);
                        });
                    }
                },
                error -> handleError("添加书籍失败: " + url, error)
            );
        
        // 存储订阅引用
        storeSubscription("addBook-" + url, subscription);
    }
    
    /**
     * 删除书籍并更新UI
     *
     * @param book 书籍
     * @param booksListModel 书籍列表模型
     */
    public void deleteBook(Book book, DefaultListModel<Book> booksListModel) {
        // 创建并存储订阅
        Disposable subscription = bookService.removeBook(book)
            .subscribeOn(reactiveSchedulers.io())  // 使用IO调度器
            .subscribe(
                success -> {
                    if (success) {
                        runOnUI(() -> {
                            // 从列表中移除
                            booksListModel.removeElement(book);
                            // 显示成功通知
                            notificationService.showInfo("删除书籍成功", "成功删除书籍: " + book.getTitle());
                        });
                    } else {
                        notificationService.showError("删除书籍失败", "无法删除书籍: " + book.getTitle());
                    }
                },
                error -> handleError("删除书籍失败: " + book.getTitle(), error)
            );
        
        // 存储订阅引用
        storeSubscription("deleteBook-" + book.getId(), subscription);
    }
    
    /**
     * 将按钮点击事件转换为响应式流
     *
     * @param button 按钮
     * @param debounceMs 防抖时间（毫秒）
     * @return 事件流
     */
    public Flux<ActionEvent> buttonClicksToFlux(JButton button, long debounceMs) {
        return Flux.<ActionEvent>create(sink -> {
            button.addActionListener(event -> sink.next(event));
            sink.onCancel(() -> button.removeActionListener(button.getActionListeners()[0]));
        });
        // 注释掉throttleFirst调用，因为它可能在当前环境中不可用
        // .throttleFirst(Duration.ofMillis(debounceMs), reactiveSchedulers.timer());
    }
    
    /**
     * 更新书籍列表
     */
    private void updateBooksList(DefaultListModel<Book> model, List<Book> books) {
        model.clear();
        books.forEach(model::addElement);
    }
    
    /**
     * 更新章节列表
     */
    private void updateChaptersList(DefaultListModel<Chapter> model, List<Chapter> chapters) {
        model.clear();
        chapters.forEach(model::addElement);
    }
    
    /**
     * 在UI线程上执行操作
     */
    public void runOnUI(@NotNull Runnable action) {
        reactiveSchedulers.runOnUI(action);
    }
    
    /**
     * 处理错误
     *
     * @param message 错误信息
     * @param error 异常对象
     */
    private void handleError(String message, Throwable error) {
        Throwable cause = error;
        // 尝试获取根本原因
        if (error instanceof com.intellij.diagnostic.PluginException) {
            cause = error.getCause() != null ? error.getCause() : error;
        }
        
        String detailedMessage = message + ": " + cause.getMessage();
        LOG.error(detailedMessage, cause); // 记录详细错误和根本原因堆栈
        
        String userMessage = message; // 默认用户消息
        if (cause instanceof java.lang.InterruptedException) {
            userMessage = "插件初始化任务被中断，请稍后重试或重启IDE。";
        } else if (cause instanceof java.io.IOException) {
            userMessage = message + " (IO错误，请检查网络或文件权限)";
        } else if (cause instanceof com.lv.tool.privatereader.exception.PrivateReaderException) {
            userMessage = message + " (" + cause.getMessage() + ")";
        } else {
             userMessage = message + " (发生意外错误)";
        }
        
        final String finalUserMessage = userMessage;
        
        // 尝试在UI线程显示错误对话框
        runOnUI(() -> {
            try {
                 Messages.showErrorDialog(project, finalUserMessage, "错误");
            } catch (Exception uiEx) {
                 LOG.error("显示错误对话框时出错: " + uiEx.getMessage(), uiEx);
                 // 如果显示对话框失败，至少保证日志里有信息
            }
        });
        
        // // 旧的通过NotificationService显示错误的方式，可能导致问题，暂时注释
        // if (notificationService != null) {
        //     notificationService.showError("错误", finalUserMessage).subscribe();
        // } else {
        //     LOG.error("NotificationService is null, cannot show error notification");
        // }
    }
    
    /**
     * 存储订阅引用
     */
    private void storeSubscription(String key, Disposable subscription) {
        // 取消并移除旧的订阅
        Disposable oldSubscription = subscriptions.put(key, subscription);
        if (oldSubscription != null && !oldSubscription.isDisposed()) {
            oldSubscription.dispose();
        }
    }
    
    /**
     * 取消所有订阅
     */
    public void dispose() {
        LOG.info("Disposing ReactiveUIAdapter and cancelling subscriptions");
        subscriptions.forEach((key, subscription) -> {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        subscriptions.clear();
    }
    
    /**
     * 取消特定订阅
     */
    public void cancelSubscription(String key) {
        Disposable subscription = subscriptions.remove(key);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
    
    /**
     * 从URL生成唯一ID
     */
    private String generateId(String url) {
        return "book_" + url.hashCode();
    }
    
    /**
     * 从URL提取标题（临时方案，实际应该从网页内容解析）
     */
    private String extractTitle(String url) {
        // 简单地从URL中提取域名作为临时标题
        String title = url;
        if (url.startsWith("http")) {
            try {
                java.net.URL urlObj = new java.net.URL(url);
                title = urlObj.getHost();
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        return title;
    }

    /**
     * 提供一个临时实现，因为 NotificationService 现在返回 Mono<Notification>，而不是内容直接。
     * 这个方法可能需要移除或重大更改。
     * 返回空字符串，现在。UI应该直接使用解析器或通过 ChapterService 增强。
     */
    // Fix: Provide a temporary implementation using the parser
    public Mono<String> getChapterContent(@NotNull Book book, @NotNull String chapterId) {
         LOG.warn("ReactiveUIAdapter.getChapterContent is deprecated. UI should use parser directly or via ChapterService enhancement.");
         NovelParser parser = book.getParser();
         if(parser != null){
             return Mono.<String>fromCallable(() -> parser.parseChapterContent(chapterId)) // Wrap blocking call
                      .subscribeOn(reactiveSchedulers.io()); // Run on IO scheduler
         } else {
             return Mono.error(new IllegalStateException("Parser not available for book: " + book.getTitle()));
         }
     }
} 