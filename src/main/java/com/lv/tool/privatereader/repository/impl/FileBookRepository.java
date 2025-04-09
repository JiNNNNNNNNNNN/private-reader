package com.lv.tool.privatereader.repository.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookIndex;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.StorageRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.nio.file.attribute.BasicFileAttributes;
import com.lv.tool.privatereader.service.ChapterService;

/**
 * 文件书籍仓库实现
 * 
 * 基于文件系统实现书籍仓库接口，管理书籍数据的持久化存储。
 * 采用分离存储方案：
 * - 主索引文件：存储所有书籍的基本信息，位于 private-reader/books/index.json
 * - 书籍详情文件：每本书单独存储详细信息，位于 private-reader/books/{bookId}/details.json
 */
public final class FileBookRepository implements BookRepository {
    private static final Logger LOG = Logger.getInstance(FileBookRepository.class);
    private static final int MAX_CACHE_SIZE = 100; // 最大内存缓存数量
    
    private final StorageRepository storageRepository;
    private final Gson gson;
    
    // 内存缓存，使用LRU策略
    private final Map<String, CacheEntry> bookCache = new LinkedHashMap<String, CacheEntry>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    
    // 缓存条目，包含数据和时间戳
    private static class CacheEntry {
        final Book book;
        final long timestamp;
        
        CacheEntry(Book book) {
            this.book = book;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(30);
        }
    }
    
    /**
     * 构造函数 - 通过Application初始化
     */
    public FileBookRepository(com.intellij.openapi.application.Application application) {
        this(application, application.getService(StorageRepository.class));
        
        if (storageRepository == null) {
            LOG.error("无法从Application获取StorageRepository服务");
        }
    }
    
    /**
     * 构造函数 - 通过StorageRepository初始化
     */
    public FileBookRepository(StorageRepository storageRepository) {
        this.storageRepository = storageRepository;
        this.gson = createSecureGson();
        
        // 启动后台线程，在应用启动后清理和修复损坏的书籍文件
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                LOG.info("开始自动检查和修复书籍文件...");
                // 首先清理损坏的文件
                int cleanedCount = cleanupCorruptedBooks();
                LOG.info("已清理 " + cleanedCount + " 个损坏的书籍文件");
                
                // 然后修复阅读位置和内容
                int repairedCount = repairMissingReadingContent();
                LOG.info("已修复 " + repairedCount + " 本书籍的阅读位置");
            } catch (Exception e) {
                LOG.error("自动修复过程中出错: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 构造函数 - 通过Application和StorageRepository初始化
     */
    public FileBookRepository(Application application, StorageRepository storageRepository) {
        this.storageRepository = storageRepository;
        this.gson = createSecureGson();
        
        // 启动后台线程，在应用启动后清理和修复损坏的书籍文件
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                LOG.info("开始自动检查和修复书籍文件...");
                // 首先清理损坏的文件
                int cleanedCount = cleanupCorruptedBooks();
                LOG.info("已清理 " + cleanedCount + " 个损坏的书籍文件");
                
                // 然后修复阅读位置和内容
                int repairedCount = repairMissingReadingContent();
                LOG.info("已修复 " + repairedCount + " 本书籍的阅读位置");
            } catch (Exception e) {
                LOG.error("自动修复过程中出错: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 创建安全配置的Gson实例，避免访问JDK内部类引起的模块系统限制
     * 并解决IntelliJ平台特有的序列化冲突问题
     * 
     * @return 安全配置的Gson实例
     */
    private Gson createSecureGson() {
        // 创建排除TypeAdapter工厂，处理可能导致反射错误的类型
        com.google.gson.TypeAdapterFactory excludeProblematicTypesFactory = new com.google.gson.TypeAdapterFactory() {
            @Override
            public <T> com.google.gson.TypeAdapter<T> create(com.google.gson.Gson gson, com.google.gson.reflect.TypeToken<T> type) {
                Class<? super T> rawType = type.getRawType();
                // 排除 MethodType、反射类以及IntelliJ平台特定的类
                if (rawType.getName().startsWith("java.lang.invoke.") || 
                    rawType.getName().startsWith("java.lang.reflect.") ||
                    rawType.getName().contains("MethodType") ||
                    rawType.getName().contains("com.intellij.openapi.project.impl.") ||
                    rawType.getName().contains("com.intellij.serviceContainer.") ||
                    rawType.getName().contains("com.intellij.") ||
                    rawType.getName().contains("com.jetbrains.")) {
                    
                    // 返回一个更简单的适配器，直接写入null而不尝试遍历字段
                    @SuppressWarnings("unchecked")
                    com.google.gson.TypeAdapter<T> adapter = (com.google.gson.TypeAdapter<T>) new com.google.gson.TypeAdapter<Object>() {
                        @Override
                        public void write(com.google.gson.stream.JsonWriter out, Object value) throws IOException {
                            // 简单地写null，避免遍历复杂对象的字段导致的递归问题
                            out.nullValue();
                        }
                        
                        @Override
                        public Object read(com.google.gson.stream.JsonReader in) throws IOException {
                            // 简单跳过读取
                            in.skipValue();
                            return null;
                        }
                    };
                    return adapter;
                }
                return null; // 让Gson处理其他类型
            }
        };
        
        // 更安全的禁止序列化策略
        return new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping() // 禁用HTML转义
                .disableJdkUnsafe() // 禁用不安全的JDK访问
                .excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT, 
                                           java.lang.reflect.Modifier.STATIC) // 排除transient和static字段
                .registerTypeAdapterFactory(excludeProblematicTypesFactory) // 注册我们自定义的类型适配器工厂
                // 序列化排除策略 - 更全面的字段排除
                .addSerializationExclusionStrategy(new com.google.gson.ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(com.google.gson.FieldAttributes f) {
                        String className = f.getDeclaringClass().getName();
                        String fieldName = f.getName();
                        
                        // 排除所有可能导致问题的字段
                        return className.startsWith("java.lang.invoke.") ||
                               className.startsWith("java.lang.reflect.") ||
                               className.contains("MethodType") ||
                               className.contains("com.intellij.") ||
                               className.contains("com.jetbrains.") ||
                               fieldName.equals("rtype") || 
                               fieldName.equals("ptypes") ||
                               fieldName.equals("supportedSignaturesOfLightServiceConstructors") ||
                               fieldName.equals("myContainer") ||
                               fieldName.equals("myDisposed") ||
                               fieldName.equals("myParentComponentManager") ||
                               fieldName.startsWith("my") || // 排除所有my开头的字段，这是IntelliJ常用的命名
                               fieldName.startsWith("_");   // 排除所有_开头的字段
                    }
                    
                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        String className = clazz.getName();
                        // 排除所有可能导致问题的类
                        return className.startsWith("java.lang.invoke.") ||
                               className.startsWith("java.lang.reflect.") ||
                               className.contains("MethodType") ||
                               className.contains("com.intellij.") ||
                               className.contains("com.jetbrains.");
                    }
                })
                // 反序列化排除策略 - 与序列化相同
                .addDeserializationExclusionStrategy(new com.google.gson.ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(com.google.gson.FieldAttributes f) {
                        String className = f.getDeclaringClass().getName();
                        String fieldName = f.getName();
                        
                        // 与序列化相同的排除规则
                        return className.startsWith("java.lang.invoke.") ||
                               className.startsWith("java.lang.reflect.") ||
                               className.contains("MethodType") ||
                               className.contains("com.intellij.") ||
                               className.contains("com.jetbrains.") ||
                               fieldName.equals("rtype") || 
                               fieldName.equals("ptypes") ||
                               fieldName.equals("supportedSignaturesOfLightServiceConstructors") ||
                               fieldName.equals("myContainer") ||
                               fieldName.equals("myDisposed") ||
                               fieldName.equals("myParentComponentManager") ||
                               fieldName.startsWith("my") ||
                               fieldName.startsWith("_");
                    }
                    
                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        String className = clazz.getName();
                        // 与序列化相同的排除规则
                        return className.startsWith("java.lang.invoke.") ||
                               className.startsWith("java.lang.reflect.") ||
                               className.contains("MethodType") ||
                               className.contains("com.intellij.") ||
                               className.contains("com.jetbrains.");
                    }
                })
                .setLenient() // 允许宽松解析
                .serializeNulls() // 序列化 null 值
                .create();
    }
    
    @Override
    @NotNull
    public List<Book> getAllBooks(boolean loadDetails) {
        List<Book> books = new ArrayList<>();
        
        // 添加标志位，防止递归调用
        boolean isRepairInProgress = Thread.currentThread().getStackTrace().length > 60;
        
        // 尝试清理和修复损坏的书籍文件
        try {
            // 先清理损坏的文件
            cleanupCorruptedBooks();
            // 然后修复阅读位置，但防止递归调用
            if (!isRepairInProgress) {
                repairMissingReadingContent();
            }
        } catch (Exception e) {
            LOG.warn("书籍维护过程中出错: " + e.getMessage(), e);
        }
        
        try {
            // 读取所有书籍索引
            List<BookIndex> indices = readBookIndices();
            
            for (BookIndex index : indices) {
                try {
                    if (loadDetails) {
                        // 加载完整书籍信息
                        Book book = null;
                        try {
                            book = getBook(index.getId());
                        } catch (Exception e) {
                            LOG.error("获取书籍详情失败 [" + index.getId() + "]: " + e.getMessage(), e);
                        }
                        
                        if (book != null) {
                            books.add(book);
                        } else {
                            // 如果详情获取失败，尝试从索引创建简化版本
                            LOG.warn("无法加载书籍详情: " + index.getId() + "，创建简化版本");
                            Book simpleBook = new Book(index.getId(), index.getTitle(), index.getAuthor(), index.getUrl());
                            simpleBook.setCreateTimeMillis(index.getCreateTimeMillis());
                            simpleBook.setLastChapter(index.getLastChapter());
                            simpleBook.setLastReadTimeMillis(index.getLastReadTimeMillis());
                            simpleBook.setTotalChapters(index.getTotalChapters());
                            simpleBook.setFinished(index.isFinished());
                            books.add(simpleBook);
                        }
                    } else {
                        // 只使用索引信息创建简化版本
                        Book simpleBook = new Book(index.getId(), index.getTitle(), index.getAuthor(), index.getUrl());
                        simpleBook.setCreateTimeMillis(index.getCreateTimeMillis());
                        simpleBook.setLastChapter(index.getLastChapter());
                        simpleBook.setLastReadTimeMillis(index.getLastReadTimeMillis());
                        simpleBook.setTotalChapters(index.getTotalChapters());
                        simpleBook.setFinished(index.isFinished());
                        books.add(simpleBook);
                    }
                } catch (Exception e) {
                    LOG.error("加载书籍失败 [" + index.getId() + "]: " + e.getMessage(), e);
                    // 继续处理下一本书
                }
            }
        } catch (Exception e) {
            LOG.error("获取书籍列表失败: " + e.getMessage(), e);
        }
        
        // 按最后阅读时间排序，最近阅读的排在前面
        books.sort((b1, b2) -> Long.compare(b2.getLastReadTimeMillis(), b1.getLastReadTimeMillis()));
        
        return books;
    }
    
    @Override
    @NotNull
    public List<Book> getAllBooks() {
        return getAllBooks(true);
    }
    
    @Override
    @Nullable
    public Book getBook(String bookId) {
        if (bookId == null || bookId.isEmpty()) {
            return null;
        }
        
        // 1. 尝试从缓存获取
        CacheEntry cachedEntry = bookCache.get(bookId);
        if (cachedEntry != null && !cachedEntry.isExpired()) {
            // Add Debug Log for cache hit
            LOG.debug(String.format("Cache hit for Book ID: %s. Checking completeness...", bookId));
            
            // === Check Cache Completeness Start ===
            Book cachedBook = cachedEntry.book;
            if (cachedBook.getCachedChapters() == null || cachedBook.getCachedChapters().isEmpty()) {
                LOG.warn("缓存中的书籍 [" + bookId + "] 缺少章节列表，尝试从文件重新加载...");
                try {
                     String bookDir = storageRepository.getBookDirectory(bookId);
                     File detailsFile = new File(bookDir, "details.json");
                     if (detailsFile.exists()) {
                         String jsonContent = new String(java.nio.file.Files.readAllBytes(detailsFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                         Book fileBook = parseBookFromJson(jsonContent, bookId); // Use parsing method
                         if (fileBook != null) {
                              //修复数据不一致
                              fileBook = restoreLastReadingPosition(fileBook);
                              if (fileBook.getCachedChapters() != null && !fileBook.getCachedChapters().isEmpty()) {
                                  LOG.info("成功从文件为 [" + bookId + "] 加载了章节列表，更新缓存。");
                                  bookCache.put(bookId, new CacheEntry(fileBook)); // 更新缓存
                                  return fileBook; // 返回从文件加载的完整对象
                              } else {
                                   LOG.warn("从文件重新加载 [" + bookId + "] 仍未获取到章节列表。");
                              }
                         } else {
                              LOG.warn("从文件解析书籍失败，无法更新缓存中的章节列表: " + bookId);
                         }
                     }
                } catch (Exception e) {
                     LOG.error("尝试从文件为 [" + bookId + "] 重新加载章节列表时出错: " + e.getMessage(), e);
                }
                // 如果重新加载失败或文件版也没有章节，仍然返回（可能不完整的）缓存版本
                LOG.debug("Returning potentially incomplete cached book after failed reload attempt: " + bookId);
                return cachedBook;
            } else {
                // 缓存中的书籍已有章节，直接返回
                 LOG.debug(String.format("Cache hit for Book ID: %s. Returning complete cached book: Title='%s', ChapterID=%s, Pos=%d, Page=%d",
                    bookId, cachedBook.getTitle(), cachedBook.getLastReadChapterId(),
                    cachedBook.getLastReadPosition(), cachedBook.getLastReadPage()));
                return cachedBook;
            }
            // === Check Cache Completeness End ===
        }
        
        // 2. 如果缓存未命中或已过期，从文件加载
        try {
            // 读取书籍详情文件
            String bookDir = storageRepository.getBookDirectory(bookId);
            File detailsFile = new File(bookDir, "details.json");
            if (!detailsFile.exists()) {
                LOG.warn("书籍详情文件不存在: " + detailsFile.getAbsolutePath());
                //尝试从索引恢复
                Book recoveredBook = recoverBookFromIndex(bookId);
                if (recoveredBook != null) {
                     LOG.info("从索引恢复书籍成功: " + bookId);
                     saveBookDetails(recoveredBook); // 保存恢复的数据
                     bookCache.put(bookId, new CacheEntry(recoveredBook)); // 添加到缓存
                     return recoveredBook;
                }
                return null;
            }

            Book fileBook = null;
            try {
                String jsonContent = new String(java.nio.file.Files.readAllBytes(detailsFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                fileBook = parseBookFromJson(jsonContent, bookId); //解析书籍

                // === Fetch Missing Chapters Logic Start ===
                if (fileBook != null && (fileBook.getCachedChapters() == null || fileBook.getCachedChapters().isEmpty()) &&
                    fileBook.getUrl() != null && !fileBook.getUrl().isEmpty()) {

                    LOG.warn("书籍 [" + bookId + "] details.json 文件缺少章节列表，尝试从 URL 重新获取...");
                    try {
                        // 获取章节服务实例 (假定服务接口为 ReactiveChapterService)
                        com.lv.tool.privatereader.service.ChapterService chapterService =
                            com.intellij.openapi.application.ApplicationManager.getApplication().getService(com.lv.tool.privatereader.service.ChapterService.class);

                        if (chapterService != null) {
                            // 调用服务获取章节 (使用阻塞方式获取，注意潜在性能影响)
                            LOG.debug("调用 chapterService.getChapterList for " + bookId);
                            
                            // Correctly call getChapterList which returns Mono<List<Chapter>>
                            reactor.core.publisher.Mono<List<com.lv.tool.privatereader.parser.NovelParser.Chapter>> chapterListMono = 
                                chapterService.getChapterList(fileBook);
                            List<com.lv.tool.privatereader.parser.NovelParser.Chapter> fetchedChapters =
                                chapterListMono.block(); // .block() on Mono<List<T>> returns List<T>

                            if (fetchedChapters != null && !fetchedChapters.isEmpty()) {
                                LOG.info("成功从 URL 为书籍 [" + bookId + "] 获取到 " + fetchedChapters.size() + " 个章节。");
                                fileBook.setCachedChapters(fetchedChapters);

                                // 将补充了章节的书籍信息保存回文件
                                LOG.warn("将获取到的章节列表保存回 details.json 文件: " + bookId);
                                saveBookDetails(fileBook);
                                // 保存后，缓存会在下面更新
                            } else {
                                LOG.warn("从 URL 未能获取到书籍 [" + bookId + "] 的章节列表。");
                            }
                        } else {
                             LOG.error("无法获取 ReactiveChapterService 实例，无法为 [" + bookId + "] 获取章节。");
                        }
                    } catch (Exception fetchEx) {
                        LOG.error("尝试为书籍 [" + bookId + "] 从 URL 获取章节列表时出错: " + fetchEx.getMessage(), fetchEx);
                        // 即使获取失败，也继续使用从文件解析出的（缺少章节的）book 对象
                    }
                }
                // === Fetch Missing Chapters Logic End ===

                 if (fileBook != null) {
                     // 修复可能的阅读位置数据不一致
                     fileBook = restoreLastReadingPosition(fileBook);

                     if (fileBook != null) {
                         // Add Debug Log for file load
                         LOG.debug(String.format("Cache miss for Book ID: %s. Loaded from file: Title='%s', ChapterID=%s, Pos=%d, Page=%d",
                                 bookId, fileBook.getTitle(), fileBook.getLastReadChapterId(),
                                 fileBook.getLastReadPosition(), fileBook.getLastReadPage()));
                         // 更新缓存 (使用可能已补充章节的 fileBook)
                         bookCache.put(bookId, new CacheEntry(fileBook));
                     }
                 }
            } catch (Exception parseEx) {
                 // Handle parsing error (potentially recover from index)
                  LOG.error("解析文件获取书籍失败，无法处理损坏的文件: " + parseEx.getMessage(), parseEx);
                  // (Recovery logic from index might be called here if parseBookFromJson didn't handle it)
                  Book recoveredBook = recoverBookFromIndex(bookId);
                  if (recoveredBook != null) {
                       LOG.info("从索引恢复书籍成功 (after parse error): " + bookId);
                       saveBookDetails(recoveredBook);
                       bookCache.put(bookId, new CacheEntry(recoveredBook));
                       return recoveredBook;
                  }
            }
             return fileBook; // 返回从文件加载（并可能已补充章节）的书籍

        } catch (Exception e) {
            LOG.error("获取书籍失败: " + e.getMessage(), e);
        }

        return null;
    }
    
    /**
     * 确保阅读位置数据的完整性
     * 如果主要字段丢失，尝试从备用字段恢复
     * 
     * @param book 需要检查的书籍对象
     * @return 修复后的书籍对象
     */
    private Book restoreLastReadingPosition(Book book) {
        if (book == null) {
            return null;
        }
        
        LOG.debug("检查并修复书籍阅读位置数据: " + book.getTitle());
        
        // 1. 确保lastReadChapterId不为空
        if (book.getLastReadChapterId() == null && book.getLastReadChapter() != null) {
            LOG.info("发现lastReadChapterId为空但lastReadChapter不为空，尝试恢复章节ID");
            
            // 如果有缓存的章节，尝试从标题匹配章节ID
            if (book.getCachedChapters() != null && !book.getCachedChapters().isEmpty()) {
                for (com.lv.tool.privatereader.parser.NovelParser.Chapter chapter : book.getCachedChapters()) {
                    if (book.getLastReadChapter().equals(chapter.title())) {
                        book.setLastReadChapterId(chapter.url());
                        LOG.info("成功恢复章节ID: " + chapter.url());
                        break;
                    }
                }
            }
        }
        
        // 2. 确保currentChapterIndex与lastReadChapterId一致
        if (book.getLastReadChapterId() != null && book.getCachedChapters() != null && 
            !book.getCachedChapters().isEmpty() && book.getCurrentChapterIndex() <= 0) {
            
            LOG.info("尝试根据lastReadChapterId恢复章节索引");
            
            for (int i = 0; i < book.getCachedChapters().size(); i++) {
                if (book.getLastReadChapterId().equals(book.getCachedChapters().get(i).url())) {
                    // currentChapterIndex是1-based索引
                    book.setCurrentChapterIndex(i + 1);
                    LOG.info("成功恢复章节索引: " + (i + 1));
                    break;
                }
            }
        }
        
        // 3. 确保lastReadPosition和lastReadPage有合理的值
        if (book.getLastReadPosition() < 0) {
            book.setLastReadPosition(0);
            LOG.info("重置无效的lastReadPosition");
        }
        
        if (book.getLastReadPage() <= 0) {
            book.setLastReadPage(1);
            LOG.info("重置无效的lastReadPage");
        }
        
        // 4. 确保最后阅读时间有值
        if (book.getLastReadTimeMillis() <= 0) {
            book.setLastReadTimeMillis(System.currentTimeMillis());
            LOG.info("设置缺失的lastReadTimeMillis");
        }
        
        return book;
    }
    
    /**
     * 手动解析JSON字符串为Book对象
     * 避免GSON的递归引用问题
     */
    private Book parseBookFromJson(String jsonContent, String bookId) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return null;
        }
        
        // 添加安全检查，避免处理过大的文件
        if (jsonContent.length() > 10 * 1024 * 1024) { // 10MB
            LOG.warn("书籍JSON内容过大 (" + (jsonContent.length() / (1024 * 1024)) + "MB)，可能导致解析问题");
            jsonContent = jsonContent.substring(0, 10 * 1024 * 1024); // 截断内容
        }
        
        // 检查是否包含潜在问题的HTML解析器对象
        if (jsonContent.contains("org.jsoup.parser") || 
            jsonContent.contains("org.jsoup.nodes") ||
            jsonContent.contains("parentNode\":{") ||
            jsonContent.contains("childNodes") ||
            jsonContent.contains("HtmlTreeBuilder")) {
            LOG.warn("检测到书籍JSON包含HTML解析器对象，可能导致无限递归或溢出。标记为已损坏: " + bookId);
            markCorruptedBookFile(bookId);
            return null;
        }
        
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonContent);
            if (!jsonElement.isJsonObject()) {
                LOG.warn("无效的书籍JSON格式 (不是JSON对象): " + bookId);
                return null;
            }
            
            JsonObject json = jsonElement.getAsJsonObject();
            
            Book book = new Book(
                bookId,
                getStringFromJson(json, "title", "未知书籍"),
                getStringFromJson(json, "author", "未知作者"),
                getStringFromJson(json, "url", "")
            );
            
            // 设置基本字段
            book.setSourceId(getStringFromJson(json, "sourceId", ""));
            book.setCreateTimeMillis(getLongFromJson(json, "createTimeMillis", System.currentTimeMillis()));
            
            // 设置阅读进度相关字段
            book.setLastChapter(getStringFromJson(json, "lastChapter", ""));
            book.setLastReadChapterId(getStringFromJson(json, "lastReadChapterId", null));
            book.setCurrentChapterIndex(getIntFromJson(json, "currentChapterIndex", 0));
            book.setLastReadPosition(getIntFromJson(json, "lastReadPosition", 0));
            book.setLastReadPage(getIntFromJson(json, "lastReadPage", 1));
            book.setLastReadTimeMillis(getLongFromJson(json, "lastReadTimeMillis", 0));
            book.setTotalChapters(getIntFromJson(json, "totalChapters", 0));
            book.setFinished(getBooleanFromJson(json, "finished", false));
            
            // 设置章节列表
            if (json.has("cachedChapters") && json.get("cachedChapters").isJsonArray()) {
                JsonArray chaptersArray = json.getAsJsonArray("cachedChapters");
                List<com.lv.tool.privatereader.parser.NovelParser.Chapter> chapters = new ArrayList<>();
                
                for (JsonElement chapterElement : chaptersArray) {
                    if (chapterElement.isJsonObject()) {
                        JsonObject chapterJson = chapterElement.getAsJsonObject();
                        String title = getStringFromJson(chapterJson, "title", "未命名章节");
                        String url = getStringFromJson(chapterJson, "url", "");
                        
                        chapters.add(new com.lv.tool.privatereader.parser.NovelParser.Chapter(title, url));
                    }
                }
                
                book.setCachedChapters(chapters);
            }
            
            // Add Debug Log before returning parsed book
            LOG.debug(String.format("Parsed book from JSON: ID=%s, Title='%s', Author='%s', ChapterID=%s, Chapter='%s', Pos=%d, Page=%d, Time=%d, Index=%d",
                    book.getId(), book.getTitle(), book.getAuthor(), book.getLastReadChapterId(), book.getLastReadChapter(),
                    book.getLastReadPosition(), book.getLastReadPage(), book.getLastReadTimeMillis(), book.getCurrentChapterIndex()));
            
            return book;
        } catch (Exception e) {
            LOG.error("解析书籍JSON失败: " + e.getMessage(), e);
            
            // === Recovery Logic Start ===
            LOG.warn("尝试从索引恢复书籍信息: " + bookId);
            Book recoveredBook = recoverBookFromIndex(bookId);
            
            if (recoveredBook != null) {
                LOG.info("成功从索引恢复基本书籍信息: " + bookId + " Title: " + recoveredBook.getTitle());
                LOG.warn("将使用索引恢复的基本信息覆盖损坏的 details.json 文件: " + bookId);
                saveBookDetails(recoveredBook); // Overwrite corrupted file
                return recoveredBook; // Return the basic recovered book
            } else {
                LOG.error("无法从索引恢复书籍信息: " + bookId);
                return null; // Recovery failed, return null
            }
            // === Recovery Logic End ===
        }
    }
    
    // 辅助方法，从JsonObject安全获取各种类型的值
    private String getStringFromJson(com.google.gson.JsonObject json, String key, String defaultValue) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsString();
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return defaultValue;
    }
    
    private int getIntFromJson(com.google.gson.JsonObject json, String key, int defaultValue) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsInt();
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return defaultValue;
    }
    
    private long getLongFromJson(com.google.gson.JsonObject json, String key, long defaultValue) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsLong();
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return defaultValue;
    }
    
    private boolean getBooleanFromJson(com.google.gson.JsonObject json, String key, boolean defaultValue) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsBoolean();
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return defaultValue;
    }
    
    /**
     * 从索引中恢复书籍信息
     * 当书籍详情文件损坏时，尝试从索引中获取基本信息
     */
    private Book recoverBookFromIndex(String bookId) {
        try {
            File indexFile = new File(storageRepository.getBooksFilePath());
            if (!indexFile.exists()) {
                return null;
            }
            
            // 直接读取文件内容
            String jsonContent = new String(java.nio.file.Files.readAllBytes(indexFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            
            // 手动解析JSON数组
            try {
                com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
                com.google.gson.JsonArray jsonArray = parser.parse(jsonContent).getAsJsonArray();
                
                for (int i = 0; i < jsonArray.size(); i++) {
                    com.google.gson.JsonObject indexObject = jsonArray.get(i).getAsJsonObject();
                    String id = getStringFromJson(indexObject, "id", "");
                    
                    if (id.equals(bookId)) {
                        // 找到匹配的索引项
                        String title = getStringFromJson(indexObject, "title", "未知标题");
                        String author = getStringFromJson(indexObject, "author", "未知作者");
                        String url = getStringFromJson(indexObject, "url", "");
                        
                        Book book = new Book(bookId, title, author, url);
                        book.setCreateTimeMillis(getLongFromJson(indexObject, "createTimeMillis", System.currentTimeMillis()));
                        book.setLastChapter(getStringFromJson(indexObject, "lastChapter", null));
                        book.setLastReadTimeMillis(getLongFromJson(indexObject, "lastReadTimeMillis", 0L));
                        book.setTotalChapters(getIntFromJson(indexObject, "totalChapters", 0));
                        book.setFinished(getBooleanFromJson(indexObject, "finished", false));
                        return book;
                    }
                }
            } catch (Exception e) {
                LOG.error("解析书籍索引JSON失败: " + e.getMessage(), e);
                
                // 回退到使用GSON解析
                try (FileReader reader = new FileReader(indexFile)) {
                    List<BookIndex> indices = gson.fromJson(reader, new TypeToken<List<BookIndex>>(){}.getType());
                    if (indices == null) {
                        return null;
                    }
                    
                    for (BookIndex index : indices) {
                        if (index.getId().equals(bookId)) {
                            Book book = new Book(bookId, index.getTitle(), index.getAuthor(), index.getUrl());
                            book.setCreateTimeMillis(index.getCreateTimeMillis());
                            book.setLastChapter(index.getLastChapter());
                            book.setLastReadTimeMillis(index.getLastReadTimeMillis());
                            book.setTotalChapters(index.getTotalChapters());
                            book.setFinished(index.isFinished());
                            return book;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("从索引恢复书籍信息失败: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    @Override
    public void addBook(@NotNull Book book) {
        if (book == null || book.getId() == null || book.getId().isEmpty()) {
            LOG.warn("无法添加书籍：book 或 bookId 为空");
            return;
        }
        
        try {
            // 创建书籍目录
            String bookDir = storageRepository.createBookDirectory(book.getId());
            
            // 保存书籍详情
            saveBookDetails(book);
            
            // 更新索引文件
            updateBookIndex(book);
            
            // 添加到缓存
            bookCache.put(book.getId(), new CacheEntry(book));
            
            LOG.info("添加书籍成功: " + book.getTitle());
        } catch (Exception e) {
            LOG.error("添加书籍失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void updateBook(@NotNull Book book) {
        if (book == null || book.getId() == null || book.getId().isEmpty()) {
            LOG.warn("无法更新书籍：book 或 bookId 为空");
            return;
        }

        try {
            // === Preserve Chapters Logic Start ===
            // Load existing book data to preserve chapter list if the input book doesn't have it
            Book existingBook = getBook(book.getId()); // Use getBook which handles cache/file loading
            if (existingBook != null) {
                if ((book.getCachedChapters() == null || book.getCachedChapters().isEmpty()) && 
                    (existingBook.getCachedChapters() != null && !existingBook.getCachedChapters().isEmpty())) {
                    
                    LOG.debug("Preserving existing chapter list for book: " + book.getId() + " Size: " + existingBook.getCachedChapters().size());
                    book.setCachedChapters(existingBook.getCachedChapters());
                }
                // Optionally, ensure other essential fields aren't accidentally overwritten if they exist in existingBook but not in book
                // Example: if (book.getUrl() == null && existingBook.getUrl() != null) book.setUrl(existingBook.getUrl());
                // Add similar checks if necessary based on how 'book' is constructed by the caller.
            } else {
                 LOG.warn("Update called for book ID not found in storage: " + book.getId() + ". Saving as new/overwriting.");
            }
             // === Preserve Chapters Logic End ===

            // 保存书籍详情 (now potentially with chapters preserved)
            saveBookDetails(book);

            // 更新索引文件
            updateBookIndex(book);

            // 更新缓存
            bookCache.put(book.getId(), new CacheEntry(book));

            LOG.info("更新书籍成功: " + book.getTitle());
        } catch (Exception e) {
            LOG.error("更新书籍失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void updateBooks(@NotNull List<Book> books) {
        if (books == null || books.isEmpty()) {
            return;
        }
        
        for (Book book : books) {
            updateBook(book);
        }
    }
    
    @Override
    public void removeBook(@NotNull Book book) {
        if (book == null || book.getId() == null || book.getId().isEmpty()) {
            LOG.warn("无法删除书籍：book 或 bookId 为空");
            return;
        }
        
        try {
            // 删除书籍目录
            String bookDir = storageRepository.getBookDirectory(book.getId());
            deleteDirectory(new File(bookDir));
            
            // 从索引文件中移除
            removeBookFromIndex(book.getId());
            
            // 从缓存中移除
            bookCache.remove(book.getId());
            
            LOG.info("删除书籍成功: " + book.getTitle());
        } catch (Exception e) {
            LOG.error("删除书籍失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void clearAllBooks() {
        try {
            // 清空书籍目录
            File booksDir = new File(storageRepository.getBooksPath());
            if (booksDir.exists() && booksDir.isDirectory()) {
                File[] files = booksDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteDirectory(file);
                        } else if (!file.getName().equals("index.json")) {
                            file.delete();
                        }
                    }
                }
            }
            
            // 清空索引文件
            File indexFile = new File(storageRepository.getBooksFilePath());
            if (indexFile.exists()) {
                try (FileWriter writer = new FileWriter(indexFile)) {
                    writer.write("[]");
                }
            }
            
            // 清空缓存
            bookCache.clear();
            
            LOG.info("清空所有书籍成功");
        } catch (Exception e) {
            LOG.error("清空所有书籍失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getIndexFilePath() {
        return storageRepository.getBooksFilePath();
    }
    
    /**
     * 保存书籍详情信息
     * 使用手动JSON构建，避免GSON序列化问题
     */
    public void saveBookDetails(Book book) {
        if (book == null) {
            LOG.warn("尝试保存null书籍");
            return;
        }
        
        try {
            // === Add Diagnostic Log Start ===
            List<com.lv.tool.privatereader.parser.NovelParser.Chapter> chaptersForLog = book.getCachedChapters();
            LOG.debug("准备保存书籍详情: " + book.getTitle() +
                     ", cachedChapters is null: " + (chaptersForLog == null) +
                     ", cachedChapters size: " + (chaptersForLog == null ? "N/A" : chaptersForLog.size()));
            // === Add Diagnostic Log End ===

            String bookDir = storageRepository.getBookDirectory(book.getId());
            new File(bookDir).mkdirs();
            File detailsFile = new File(bookDir, "details.json");
            
            // Add Debug Log before building JSON
            LOG.debug(String.format("Saving book details: ID=%s, Title='%s', ChapterID=%s, Chapter='%s', Pos=%d, Page=%d, Time=%d, Index=%d",
                    book.getId(), book.getTitle(), book.getLastReadChapterId(), book.getLastReadChapter(),
                    book.getLastReadPosition(), book.getLastReadPage(), book.getLastReadTimeMillis(), book.getCurrentChapterIndex()));
            
            // 手动构建JSON字符串
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            
            // 基本信息
            appendJsonStringField(json, "id", book.getId(), true);
            appendJsonStringField(json, "title", book.getTitle(), true);
            appendJsonStringField(json, "author", book.getAuthor(), true);
            appendJsonStringField(json, "url", book.getUrl(), true);
            appendJsonNumberField(json, "createTimeMillis", book.getCreateTimeMillis(), true);
            
            // 阅读进度信息 - 这些是关键信息，必须正确保存
            appendJsonStringField(json, "lastChapter", book.getLastChapter(), true);
            if (book.getLastReadChapterId() != null) {
                appendJsonStringField(json, "lastReadChapterId", book.getLastReadChapterId(), true);
            } else {
                appendJsonNullField(json, "lastReadChapterId", true);
            }
            appendJsonNumberField(json, "currentChapterIndex", book.getCurrentChapterIndex(), true);
            appendJsonNumberField(json, "lastReadPosition", book.getLastReadPosition(), true);
            appendJsonNumberField(json, "lastReadPage", book.getLastReadPage(), true);
            appendJsonNumberField(json, "lastReadTimeMillis", book.getLastReadTimeMillis(), true);
            appendJsonNumberField(json, "totalChapters", book.getTotalChapters(), true);
            appendJsonBooleanField(json, "finished", book.isFinished(), false); // Last field before potential array
            
            // 章节列表
            List<com.lv.tool.privatereader.parser.NovelParser.Chapter> chapters = book.getCachedChapters();
            if (chapters != null && !chapters.isEmpty()) {
                json.append(",\n  \"cachedChapters\": [\n"); // Add comma before array field
                
                for (int i = 0; i < chapters.size(); i++) {
                    com.lv.tool.privatereader.parser.NovelParser.Chapter chapter = chapters.get(i);
                    json.append("    {\n");
                    appendJsonStringField(json, "title", chapter.title(), true); // Comma needed after title
                    appendJsonStringField(json, "url", chapter.url(), false);   // No comma after url (last field in object)
                    json.append("    }");
                    
                    if (i < chapters.size() - 1) {
                        json.append(",");
                    }
                    json.append("\n");
                }
                
                json.append("  ]\n");
            } else {
                 // If no chapters array, ensure the last field ('finished') didn't have a trailing comma
                 // This is handled by passing 'false' to appendJsonBooleanField for 'finished'
                 json.append("\n");
            }
            
            json.append("}");
            
            // 写入文件
            // Use try-with-resources for FileWriter
            try (FileWriter writer = new FileWriter(detailsFile, java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
            
            LOG.info("已保存书籍详情: " + book.getTitle());
        } catch (Exception e) {
            LOG.error("保存书籍详情失败: " + e.getMessage(), e);
        }
    }
    
    // --- New Helper Methods Start ---

    /**
     * 辅助方法，向JSON添加字符串字段
     */
    private void appendJsonStringField(StringBuilder json, String key, String value, boolean addComma) {
        json.append("  \"").append(key).append("\": "); // Append actual quote, key, quote, colon, space
        if (value == null) {
            json.append("null");
        } else {
            json.append("\"").append(escapeJson(value)).append("\""); // Append quote, escaped value, quote
        }
        if (addComma) {
            json.append(",");
        }
        json.append("\n"); // Append actual newline
    }

    /**
     * 辅助方法，向JSON添加数字字段
     */
    private void appendJsonNumberField(StringBuilder json, String key, Number value, boolean addComma) {
        json.append("  \"").append(key).append("\": "); // Append actual quote, key, quote, colon, space
        // Output number directly without quotes
        json.append(value == null ? "null" : value);
        if (addComma) {
            json.append(",");
        }
        json.append("\n"); // Append actual newline
    }

     /**
     * 辅助方法，向JSON添加布尔字段
     */
    private void appendJsonBooleanField(StringBuilder json, String key, boolean value, boolean addComma) {
        json.append("  \"").append(key).append("\": "); // Append actual quote, key, quote, colon, space
        // Output boolean directly without quotes
        json.append(value);
        if (addComma) {
            json.append(",");
        }
        json.append("\n"); // Append actual newline
    }

    /**
     * 辅助方法，向JSON添加 null 字段
     */
     private void appendJsonNullField(StringBuilder json, String key, boolean addComma) {
         json.append("  \"").append(key).append("\": null"); // Append actual quote, key, quote, colon, null
         if (addComma) {
             json.append(",");
         }
         json.append("\n"); // Append actual newline
     }

    // --- New Helper Methods End ---

    /**
     * 转义JSON字符串中需要转义的字符
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        // Escape backslash, quote, and control characters
        return input.replace("\\\\", "\\\\\\\\") // Must escape backslash first
                   .replace("\\\"", "\\\\\\\"")
                   .replace("\\b", "\\\\b")
                   .replace("\\f", "\\\\f")
                   .replace("\\n", "\\\\n")
                   .replace("\\r", "\\\\r")
                   .replace("\\t", "\\\\t");
    }
    
    /**
     * 更新书籍索引
     */
    private void updateBookIndex(Book book) {
        try {
            // 读取当前索引
            List<BookIndex> indices = readBookIndices();
            
            // 查找并更新或添加
            boolean found = false;
            for (int i = 0; i < indices.size(); i++) {
                if (indices.get(i).getId().equals(book.getId())) {
                    indices.set(i, BookIndex.fromBook(book));
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                indices.add(BookIndex.fromBook(book));
            }
            
            // 保存更新的索引
            saveBookIndices(indices);
        } catch (Exception e) {
            LOG.error("更新书籍索引失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从索引中移除书籍
     */
    private void removeBookFromIndex(String bookId) {
        try {
            // 读取当前索引
            List<BookIndex> indices = readBookIndices();
            
            // 移除指定ID的书籍
            indices.removeIf(index -> index.getId().equals(bookId));
            
            // 保存更新的索引
            saveBookIndices(indices);
        } catch (Exception e) {
            LOG.error("从索引中移除书籍失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 读取书籍索引列表
     */
    private List<BookIndex> readBookIndices() {
        File indexFile = new File(storageRepository.getBooksFilePath());
        if (!indexFile.exists()) {
            return new ArrayList<>();
        }
        
        try {
            // 先尝试直接读取文件内容进行手动解析
            try {
                String jsonContent = new String(java.nio.file.Files.readAllBytes(indexFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
                com.google.gson.JsonArray jsonArray = parser.parse(jsonContent).getAsJsonArray();
                
                List<BookIndex> indices = new ArrayList<>();
                for (int i = 0; i < jsonArray.size(); i++) {
                    com.google.gson.JsonObject indexObject = jsonArray.get(i).getAsJsonObject();
                    
                    BookIndex index = new BookIndex();
                    index.setId(getStringFromJson(indexObject, "id", ""));
                    index.setTitle(getStringFromJson(indexObject, "title", ""));
                    index.setAuthor(getStringFromJson(indexObject, "author", ""));
                    index.setUrl(getStringFromJson(indexObject, "url", ""));
                    index.setCreateTimeMillis(getLongFromJson(indexObject, "createTimeMillis", 0L));
                    index.setLastChapter(getStringFromJson(indexObject, "lastChapter", null));
                    index.setLastReadTimeMillis(getLongFromJson(indexObject, "lastReadTimeMillis", 0L));
                    index.setTotalChapters(getIntFromJson(indexObject, "totalChapters", 0));
                    index.setFinished(getBooleanFromJson(indexObject, "finished", false));
                    
                    indices.add(index);
                }
                
                return indices;
            } catch (Exception e) {
                LOG.warn("手动解析JSON索引文件失败，尝试使用GSON: " + e.getMessage());
                
                // 作为备选，使用GSON解析
                try (FileReader reader = new FileReader(indexFile)) {
                    List<BookIndex> indices = gson.fromJson(reader, new TypeToken<List<BookIndex>>(){}.getType());
                    return indices != null ? indices : new ArrayList<>();
                }
            }
        } catch (Exception e) {
            LOG.error("读取书籍索引文件失败: " + e.getMessage(), e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 保存书籍索引列表
     */
    private void saveBookIndices(List<BookIndex> indices) {
        try {
            File indexFile = new File(storageRepository.getBooksFilePath());
            
            // 确保父目录存在
            indexFile.getParentFile().mkdirs();
            
            // 手动构建索引JSON数组
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[\n");
            
            for (int i = 0; i < indices.size(); i++) {
                BookIndex index = indices.get(i);
                jsonBuilder.append("  {\n");
                jsonBuilder.append("    \"id\": \"").append(escapeJson(index.getId())).append("\",\n");
                jsonBuilder.append("    \"title\": \"").append(escapeJson(index.getTitle())).append("\",\n");
                jsonBuilder.append("    \"author\": \"").append(escapeJson(index.getAuthor())).append("\",\n");
                jsonBuilder.append("    \"url\": \"").append(escapeJson(index.getUrl())).append("\",\n");
                jsonBuilder.append("    \"createTimeMillis\": ").append(index.getCreateTimeMillis()).append(",\n");
                jsonBuilder.append("    \"lastChapter\": ").append(index.getLastChapter() != null ? "\"" + escapeJson(index.getLastChapter()) + "\"" : "null").append(",\n");
                jsonBuilder.append("    \"lastReadTimeMillis\": ").append(index.getLastReadTimeMillis()).append(",\n");
                jsonBuilder.append("    \"totalChapters\": ").append(index.getTotalChapters()).append(",\n");
                jsonBuilder.append("    \"finished\": ").append(index.isFinished()).append("\n");
                jsonBuilder.append("  }").append(i < indices.size() - 1 ? ",\n" : "\n");
            }
            
            jsonBuilder.append("]");
            
            try (FileWriter writer = new FileWriter(indexFile)) {
                writer.write(jsonBuilder.toString());
            }
            
            LOG.info("索引文件保存成功: " + indexFile.getAbsolutePath() + " (" + indices.size() + " 条目)");
        } catch (Exception e) {
            LOG.error("保存书籍索引文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除目录及其内容
     */
    private void deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        
        directory.delete();
    }
    
    /**
     * 标记并备份损坏的书籍文件
     * @param bookId 书籍ID
     */
    private void markCorruptedBookFile(String bookId) {
        try {
            // 获取书籍目录
            String bookDir = storageRepository.getBookDirectory(bookId);
            File detailsFile = new File(bookDir, "details.json");
            
            if (detailsFile.exists()) {
                // 创建备份
                File backupFile = new File(bookDir, "details.json.corrupted." + System.currentTimeMillis());
                try {
                    java.nio.file.Files.copy(detailsFile.toPath(), backupFile.toPath());
                    LOG.info("已将损坏的文件备份到: " + backupFile.getAbsolutePath());
                } catch (Exception e) {
                    LOG.error("备份损坏文件失败: " + e.getMessage(), e);
                }
                
                // 创建最小化书籍对象并保存
                try {
                    Book minimalBook = recoverBookFromIndex(bookId);
                    if (minimalBook == null) {
                        minimalBook = new Book(bookId, "恢复的书籍 " + bookId, "未知", "");
                        minimalBook.setCreateTimeMillis(System.currentTimeMillis());
                    }
                    
                    // 保存最小化书籍对象，使用手动JSON构建
                    saveBookDetails(minimalBook);
                    LOG.info("已创建替代书籍对象: " + minimalBook.getTitle());
                    
                    // 添加到缓存
                    bookCache.put(bookId, new CacheEntry(minimalBook));
                } catch (Exception e) {
                    LOG.error("创建替代书籍对象失败: " + e.getMessage(), e);
                    
                    // 如果恢复失败，则删除原始文件以避免后续再次触发相同错误
                    try {
                        detailsFile.delete();
                        LOG.info("已删除无法修复的损坏文件: " + detailsFile.getAbsolutePath());
                    } catch (Exception ex) {
                        LOG.error("删除损坏文件失败: " + ex.getMessage(), ex);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("处理损坏书籍文件失败 [" + bookId + "]: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理损坏的书籍文件
     * 
     * @return 已清理的文件数量
     */
    public int cleanupCorruptedBooks() {
        int cleanedCount = 0;
        
        try {
            File booksDir = new File(storageRepository.getBooksPath());
            if (!booksDir.exists() || !booksDir.isDirectory()) {
                return 0;
            }
            
            File[] bookDirs = booksDir.listFiles(File::isDirectory);
            if (bookDirs == null || bookDirs.length == 0) {
                return 0;
            }
            
            for (File bookDir : bookDirs) {
                String bookId = bookDir.getName();
                if (bookId == null || bookId.isEmpty()) {
                    continue;
                }
                
                File detailsFile = new File(bookDir, "details.json");
                if (!detailsFile.exists() || !detailsFile.isFile()) {
                    continue;
                }
                
                try {
                    // 检查文件内容是否包含潜在的问题模式
                    String content = new String(java.nio.file.Files.readAllBytes(detailsFile.toPath()), 
                                              java.nio.charset.StandardCharsets.UTF_8);
                    
                    if (content.contains("org.jsoup.parser") || 
                        content.contains("org.jsoup.nodes") ||
                        content.contains("parentNode\":{") ||
                        content.contains("childNodes") ||
                        content.contains("HtmlTreeBuilder")) {
                        
                        // 备份并修复这个文件
                        markCorruptedBookFile(bookId);
                        cleanedCount++;
                    }
                } catch (Exception e) {
                    LOG.warn("检查书籍文件时出错: " + bookId + " - " + e.getMessage());
                }
            }
            
            LOG.info("已清理 " + cleanedCount + " 个损坏的书籍文件");
        } catch (Exception e) {
            LOG.error("清理损坏书籍文件失败: " + e.getMessage(), e);
        }
        
        return cleanedCount;
    }
    
    /**
     * 修复丢失的阅读内容和位置
     * 尝试检查并修复Books中可能丢失的阅读位置和内容信息
     * 
     * @return 已修复的书籍数量
     */
    public int repairMissingReadingContent() {
        int repairedCount = 0;
        
        try {
            // 读取书籍索引列表，避免递归调用getAllBooks
            List<BookIndex> indices = readBookIndices();
            List<Book> books = new ArrayList<>();
            
            // 直接从索引加载简化的书籍对象
            for (BookIndex index : indices) {
                try {
                    Book book = getBook(index.getId());
                    if (book != null) {
                        books.add(book);
                    }
                } catch (Exception e) {
                    LOG.error("加载书籍失败 [" + index.getId() + "]: " + e.getMessage(), e);
                }
            }
            
            if (books.isEmpty()) {
                return 0;
            }
            
            for (Book book : books) {
                boolean needsRepair = false;
                
                // 检查是否需要修复
                if (book.getLastReadChapterId() != null && book.getLastReadChapter() != null) {
                    if (book.getCurrentChapterIndex() <= 0) {
                        // 有上次阅读章节但章节索引无效
                        needsRepair = true;
                    }
                    
                    // 检查阅读位置是否有效
                    if (book.getLastReadPosition() < 0) {
                        needsRepair = true;
                    }
                }
                
                if (needsRepair) {
                    LOG.info("修复书籍阅读位置: " + book.getTitle());
                    
                    // 应用修复
                    book = restoreLastReadingPosition(book);
                    
                    // 保存修复后的书籍
                    saveBookDetails(book);
                    
                    // 更新书籍索引
                    updateBookIndex(book);
                    
                    repairedCount++;
                }
            }
            
            LOG.info("已修复 " + repairedCount + " 本书籍的阅读位置");
        } catch (Exception e) {
            LOG.error("修复阅读位置失败: " + e.getMessage(), e);
        }
        
        return repairedCount;
    }
}