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
import com.lv.tool.privatereader.repository.BookRepository;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

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
import java.net.URI;
import java.net.URISyntaxException;
import com.lv.tool.privatereader.messaging.CurrentChapterNotifier;

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
    private final ReaderModeSettings readerModeSettings;
    private final Project project;

    public ReactiveUIAdapter(Project project) {
        this.project = project;
        this.reactiveSchedulers = ReactiveSchedulers.getInstance();
        this.bookService = ApplicationManager.getApplication().getService(BookService.class);
        this.chapterService = ApplicationManager.getApplication().getService(ChapterService.class);
        this.notificationService = ApplicationManager.getApplication().getService(NotificationService.class);
        this.chapterPreloader = ApplicationManager.getApplication().getService(ReactiveChapterPreloader.class);
        this.readerModeSettings = ApplicationManager.getApplication().getService(ReaderModeSettings.class);
        LOG.info("初始化ReactiveUIAdapter");

        if (bookService == null || chapterService == null || notificationService == null) {
            LOG.error("一个或多个服务未能初始化 for ReactiveUIAdapter!");
            // Handle error appropriately, maybe throw exception?
        }

        if (readerModeSettings == null) {
            LOG.warn("ReaderModeSettings 未能初始化，通知栏模式可能无法正常工作");
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
     * @param chapterTitleLabel 用于显示当前章节标题的标签 (新增)
     */
    public void loadChapterContent(Book book, Chapter chapter, JTextArea contentTextArea, JLabel loadingLabel, JLabel chapterTitleLabel) {
        // 显示加载状态
        runOnUI(() -> {
            loadingLabel.setVisible(true);
            contentTextArea.setText("正在加载...");
            if (chapterTitleLabel != null) {
                chapterTitleLabel.setText("加载中..."); // 初始时显示加载中
            }
        });

        // 记录详细的日志
        LOG.info("[调试] 开始加载章节内容: 书籍=" + book.getTitle() + ", 章节=" + chapter.title() + ", URL=" + chapter.url());

        // 检查书籍和章节参数
        if (book == null) {
            LOG.error("[调试] 加载章节内容失败: 书籍参数为 null");
            runOnUI(() -> {
                contentTextArea.setText("加载失败: 书籍参数无效");
                if (chapterTitleLabel != null) chapterTitleLabel.setText(" ");
            });
            return;
        }

        if (chapter == null || chapter.url() == null || chapter.url().isEmpty()) {
            LOG.error("[调试] 加载章节内容失败: 章节参数无效");
            runOnUI(() -> {
                contentTextArea.setText("加载失败: 章节参数无效");
                if (chapterTitleLabel != null) chapterTitleLabel.setText(" ");
            });
            return;
        }

        // 检查解析器
        NovelParser parser = book.getParser();
        if (parser == null) {
            LOG.error("[调试] 加载章节内容失败: 无法获取解析器, 书籍 URL=" + book.getUrl());
            runOnUI(() -> {
                contentTextArea.setText("加载失败: 无法获取解析器。\n\n请检查书籍 URL 是否有效: " + book.getUrl());
                if (chapterTitleLabel != null) chapterTitleLabel.setText("解析器错误");
            });
            return;
        }

        // 获取响应式结果
        LOG.info("[调试] 调用 getChapterContent 获取章节内容: 书籍=" + book.getTitle() + ", 章节 URL=" + chapter.url());
        Mono<String> contentMono = getChapterContent(book, chapter.url());

        // 创建并存储订阅
        Disposable subscription = contentMono
            .subscribeOn(reactiveSchedulers.io())  // 使用IO调度器
            .timeout(Duration.ofSeconds(30))  // 设置超时
            .retry(2)  // 失败重试
            .doFinally(signal -> {
                LOG.info("[调试] 章节内容加载完成或失败: signal=" + signal);
                runOnUI(() -> loadingLabel.setVisible(false));
            })  // 隐藏加载状态
            .subscribe(
                content -> {
                    LOG.info("[调试] 章节内容加载成功: 书籍=" + book.getTitle() + ", 章节=" + chapter.title() + ", 内容长度=" + (content != null ? content.length() : 0));
                    runOnUI(() -> {
                        if (content != null && !content.isEmpty()) {
                            contentTextArea.setText(content);
                            contentTextArea.setCaretPosition(0);  // 滚动到顶部
                            if (chapterTitleLabel != null) {
                                chapterTitleLabel.setText("当前章节: " + chapter.title());
                            }
                            // 发布章节变更事件
                            // 确保 chapter 是 NovelParser.Chapter 类型
                            if (chapter instanceof com.lv.tool.privatereader.parser.NovelParser.Chapter) {
                                CurrentChapterNotifier publisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(CurrentChapterNotifier.TOPIC);
                                publisher.currentChapterChanged(book, (com.lv.tool.privatereader.parser.NovelParser.Chapter) chapter);
                                LOG.debug("ReactiveUIAdapter published currentChapterChanged event for chapter: " + chapter.title());
                            } else {
                                LOG.warn("Cannot publish event from ReactiveUIAdapter: chapter is not NovelParser.Chapter. Actual: " + chapter.getClass().getName());
                            }
                        } else {
                            contentTextArea.setText("章节内容为空。请检查章节 URL 是否有效: " + chapter.url());
                            if (chapterTitleLabel != null) {
                                chapterTitleLabel.setText("内容为空");
                            }
                        }
                    });

                    // 预加载后续章节
                    preloadNextChapters(book, chapter);
                },
                error -> {
                    LOG.error("[调试] 处理章节内容加载错误: " + error.getMessage(), error);
                    runOnUI(() -> {
                        contentTextArea.setText("加载章节内容时出错: " + error.getMessage());
                        if (chapterTitleLabel != null) {
                            chapterTitleLabel.setText("加载失败");
                        }
                        handleError("加载章节 '" + chapter.title() + "' 失败", error); // 调用已有的错误处理
                    });
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

        // 先获取书籍信息，再添加书籍
        Disposable subscription = fetchBookInfo(url)
            .flatMap(book -> {
                LOG.info("开始添加书籍: " + book.getTitle());
                return bookService.addBook(book);
            })
            .timeout(Duration.ofMinutes(1))  // 设置超时
            .doFinally(signal -> runOnUI(() -> loadingLabel.setVisible(false)))  // 隐藏加载状态
            .subscribe(
                success -> {
                    if (success) {
                        runOnUI(() -> {
                            // 重新加载书籍列表以显示新添加的书籍
                            LOG.info("书籍添加成功，正在重新加载列表...");
                            loadAllBooks(booksListModel, loadingLabel); // 重新加载列表
                            // 延迟一小段时间后触发成功回调，以确保列表更新完成
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
                java.net.URL urlObj = new java.net.URI(url).toURL();
                title = urlObj.getHost();
            } catch (URISyntaxException | java.net.MalformedURLException e) {
                // 忽略解析错误
            }
        }
        return title;
    }

    /**
     * 从URL获取书籍信息（标题和作者）
     *
     * @param url 书籍URL
     * @return 包含书籍信息的Mono对象
     */
    private Mono<Book> fetchBookInfo(String url) {
        LOG.info("开始从URL获取书籍信息: " + url);

        return Mono.fromCallable(() -> {
            try {
                // 创建解析器
                com.lv.tool.privatereader.parser.NovelParser parser =
                    new com.lv.tool.privatereader.parser.site.UniversalParser(url);

                // 获取书籍标题和作者
                String title = parser.getTitle();
                String author = parser.getAuthor();

                LOG.info("成功获取书籍信息: 标题=" + title + ", 作者=" + author);

                // 创建Book对象
                Book book = new Book(generateId(url), title, author, url);
                return book;
            } catch (Exception e) {
                LOG.warn("获取书籍信息失败，将使用临时标题: " + e.getMessage(), e);

                // 创建使用临时标题的Book对象
                Book book = new Book(generateId(url), extractTitle(url), "", url);
                return book;
            }
        })
        .subscribeOn(reactiveSchedulers.io())
        .doOnError(e -> LOG.error("获取书籍信息时发生错误: " + e.getMessage(), e));
    }

    /**
     * 获取章节内容
     *
     * @param book 书籍
     * @param chapterId 章节ID
     * @return 章节内容的Mono对象
     */
    public Mono<String> getChapterContent(@NotNull Book book, @NotNull String chapterId) {
         LOG.info("[调试] ReactiveUIAdapter.getChapterContent 被调用: 书籍=" + book.getTitle() + ", 章节 ID=" + chapterId);

         // 检查参数
         if (book == null) {
             LOG.error("[调试] getChapterContent 失败: 书籍参数为 null");
             return Mono.error(new IllegalArgumentException("书籍参数为 null"));
         }

         if (chapterId == null || chapterId.isEmpty()) {
             LOG.error("[调试] getChapterContent 失败: 章节 ID 为 null 或空");
             return Mono.error(new IllegalArgumentException("章节 ID 为 null 或空"));
         }

         // 获取解析器
         NovelParser parser = book.getParser();
         if (parser == null) {
             LOG.error("[调试] getChapterContent 失败: 无法获取解析器, 书籍 URL=" + book.getUrl());
             return Mono.error(new IllegalStateException("无法获取解析器。请检查书籍 URL 是否有效: " + book.getUrl()));
         }

         LOG.info("[调试] 开始解析章节内容: 书籍=" + book.getTitle() + ", 章节 ID=" + chapterId + ", 解析器类型=" + parser.getClass().getSimpleName());

         // 使用 fromCallable 包装阻塞调用
         return Mono.<String>fromCallable(() -> {
                 try {
                     LOG.info("[调试] 调用 parser.parseChapterContent: 章节 ID=" + chapterId);
                     String content = parser.parseChapterContent(chapterId);
                     LOG.info("[调试] 解析章节内容成功: 章节 ID=" + chapterId + ", 内容长度=" + (content != null ? content.length() : 0));
                     return content;
                 } catch (Exception e) {
                     LOG.error("[调试] 解析章节内容失败: 章节 ID=" + chapterId + ", 错误=" + e.getMessage(), e);
                     throw e; // 重新抛出异常，使其被 Mono 捕获
                 }
             })
             .subscribeOn(reactiveSchedulers.io()) // 在 IO 调度器上运行
             .doOnError(error -> LOG.error("[调试] getChapterContent 返回错误: " + error.getMessage(), error));
     }
}