package com.lv.tool.privatereader.storage;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import org.apache.commons.io.FileUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 书籍存储服务
 *
 * 负责管理书籍数据的持久化存储，提供以下功能：
 * - 保存和加载书籍列表
 * - 添加和删除书籍
 * - 更新书籍信息
 *
 * 采用分离存储方案：
 * - 主索引文件：存储所有书籍的基本信息，位于 private-reader/books/index.json
 * - 书籍详情文件：每本书单独存储详细信息，位于 private-reader/books/{bookId}/details.json
 */
@Tag("BookStorage")
public class BookStorage {
    private static final Logger LOG = Logger.getInstance(BookStorage.class);
    private static final int MAX_CACHE_SIZE = 100; // 最大内存缓存数量
    private static final long CLEANUP_INTERVAL = TimeUnit.HOURS.toMillis(1); // 清理间隔
    private long lastCleanupTime = 0;

    static {
        LOG.setLevel(LogLevel.DEBUG);
    }

    @Tag("books")
    @XCollection(style = XCollection.Style.v2)
    private List<Book> books = new ArrayList<>();

    @Transient
    private Project project;
    @Transient
    private Gson gson;
    @Transient
    private final Path indexFilePath;
    @Transient
    private StorageManager storageManager;

    // 内存缓存，使用LRU策略
    @Transient
    private final Map<String, CacheEntry> bookCache = new LinkedHashMap<String, CacheEntry>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    // 缓存条目，包含时间戳
    private static class CacheEntry {
        final Book book;
        final long timestamp;

        CacheEntry(Book book) {
            this.book = book;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public BookStorage() {
        this.indexFilePath = null;
        this.storageManager = null;
    }

    public BookStorage(Project project) {
        this.project = project;
        this.storageManager = project.getService(StorageManager.class);

        // 创建Gson实例，添加压缩选项
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableJdkUnsafe()
            .excludeFieldsWithoutExposeAnnotation()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    // 排除不需要序列化的字段
                    return f.getName().equals("parser") ||
                           f.getName().equals("project") ||
                           f.getName().equals("cachedChapters");
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .registerTypeAdapter(Chapter.class, new TypeAdapter<Chapter>() {
                @Override
                public void write(JsonWriter out, Chapter value) throws IOException {
                    out.beginObject();
                    out.name("title").value(value.title());
                    out.name("url").value(value.url());
                    out.endObject();
                }

                @Override
                public Chapter read(JsonReader in) throws IOException {
                    in.beginObject();
                    String title = "";
                    String url = "";
                    while (in.hasNext()) {
                        String name = in.nextName();
                        if (name.equals("title")) {
                            title = in.nextString();
                        } else if (name.equals("url")) {
                            url = in.nextString();
                        } else {
                            in.skipValue();
                        }
                    }
                    in.endObject();
                    return new Chapter(title, url);
                }
            })
            .registerTypeAdapter(Book.class, new TypeAdapter<Book>() {
                @Override
                public void write(JsonWriter out, Book book) throws IOException {
                    out.beginObject();
                    out.name("id").value(book.getId());
                    out.name("title").value(book.getTitle());
                    out.name("author").value(book.getAuthor());
                    out.name("url").value(book.getUrl());
                    out.name("createTimeMillis").value(book.getCreateTimeMillis());
                    out.name("lastChapter").value(book.getLastChapter());
                    out.name("lastReadChapter").value(book.getLastReadChapter());
                    out.name("lastReadChapterId").value(book.getLastReadChapterId());
                    out.name("lastReadPosition").value(book.getLastReadPosition());
                    out.name("lastReadTimeMillis").value(book.getLastReadTimeMillis());
                    out.name("totalChapters").value(book.getTotalChapters());
                    out.name("currentChapterIndex").value(book.getCurrentChapterIndex());
                    out.name("finished").value(book.isFinished());
                    out.name("lastReadPage").value(book.getLastReadPage());

                    // 手动序列化章节列表
                    List<Chapter> chapters = book.getCachedChapters();
                    out.name("cachedChapters");
                    if (chapters == null) {
                        out.nullValue();
                    } else {
                        out.beginArray();
                        for (Chapter chapter : chapters) {
                            out.beginObject();
                            out.name("title").value(chapter.title());
                            out.name("url").value(chapter.url());
                            out.endObject();
                        }
                        out.endArray();
                    }

                    out.endObject();
                }

                @Override
                public Book read(JsonReader in) throws IOException {
                    in.beginObject();
                    Book book = new Book();
                    while (in.hasNext()) {
                        String name = in.nextName();
                        switch (name) {
                            case "id":
                                book.setId(in.nextString());
                                break;
                            case "title":
                                book.setTitle(in.nextString());
                                break;
                            case "author":
                                book.setAuthor(in.nextString());
                                break;
                            case "url":
                                book.setUrl(in.nextString());
                                break;
                            case "createTimeMillis":
                                book.setCreateTimeMillis(in.nextLong());
                                break;
                            case "lastChapter":
                                book.setLastChapter(in.nextString());
                                break;
                            case "lastReadChapter":
                                book.setLastReadChapter(in.nextString());
                                break;
                            case "lastReadChapterId":
                                book.setLastReadChapterId(in.nextString());
                                break;
                            case "lastReadPosition":
                                book.setLastReadPosition(in.nextInt());
                                break;
                            case "lastReadTimeMillis":
                                book.setLastReadTimeMillis(in.nextLong());
                                break;
                            case "totalChapters":
                                book.setTotalChapters(in.nextInt());
                                break;
                            case "currentChapterIndex":
                                book.setCurrentChapterIndex(in.nextInt());
                                break;
                            case "finished":
                                book.setFinished(in.nextBoolean());
                                break;
                            case "lastReadPage":
                                book.setLastReadPage(in.nextInt());
                                break;
                            case "cachedChapters":
                                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                                    in.nextNull();
                                    book.setCachedChapters(null);
                                } else {
                                    List<Chapter> chapters = new ArrayList<>();
                                    in.beginArray();
                                    while (in.hasNext()) {
                                        in.beginObject();
                                        String title = "";
                                        String url = "";
                                        while (in.hasNext()) {
                                            String chapterName = in.nextName();
                                            if (chapterName.equals("title")) {
                                                title = in.nextString();
                                            } else if (chapterName.equals("url")) {
                                                url = in.nextString();
                                            } else {
                                                in.skipValue();
                                            }
                                        }
                                        in.endObject();
                                        chapters.add(new Chapter(title, url));
                                    }
                                    in.endArray();
                                    book.setCachedChapters(chapters);
                                }
                                break;
                            default:
                                in.skipValue();
                                break;
                        }
                    }
                    in.endObject();
                    return book;
                }
            })
            .create();

        this.indexFilePath = Path.of(storageManager.getBooksPath(), "index.json");
        loadBooks();
    }

    /**
     * 定期清理过期缓存
     */
    private void cleanupIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime >= CLEANUP_INTERVAL) {
            // 清理过期的缓存条目
            bookCache.entrySet().removeIf(entry ->
                currentTime - entry.getValue().timestamp >= CLEANUP_INTERVAL
            );
            lastCleanupTime = currentTime;
        }
    }

    /**
     * 压缩索引文件
     * 删除无效的书籍记录
     */
    private void compressIndex() {
        try {
            // 读取当前索引文件
            String json = FileUtils.readFileToString(indexFilePath.toFile(), StandardCharsets.UTF_8);

            // 解析 JSON
            Type listType = new TypeToken<List<Book>>(){}.getType();
            List<Book> books = gson.fromJson(json, listType);

            // 按最后阅读时间排序
            books.sort(Comparator.comparingLong(Book::getLastReadTimeMillis).reversed());

            // 只保留前 100 本书
            if (books.size() > 100) {
                books = books.subList(0, 100);
            }

            // 保存压缩后的索引
            String compressedJson = gson.toJson(books);
            FileUtils.writeStringToFile(indexFilePath.toFile(), compressedJson, StandardCharsets.UTF_8);

            LOG.debug("已压缩索引文件，当前书籍数量: " + books.size());
        } catch (IOException e) {
            LOG.error("压缩索引文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查并清理旧的 XML 配置文件
     */
    private void checkAndCleanLegacyXmlFiles() {
        try {
            // 检查是否存在旧的 XML 文件
            Path xmlPath = Path.of(PathManager.getSystemPath(), "private-reader-books.xml");
            if (Files.exists(xmlPath)) {
                // 读取 XML 文件
                Element root = JDOMUtil.load(xmlPath.toFile());
                if (root != null) {
                    // 将 XML 数据转换为 JSON 格式
                    List<Book> books = new ArrayList<>();
                    for (Element bookElement : root.getChildren("book")) {
                        Book book = new Book();
                        book.setId(bookElement.getChildText("id"));
                        book.setTitle(bookElement.getChildText("title"));
                        book.setAuthor(bookElement.getChildText("author"));
                        book.setUrl(bookElement.getChildText("url"));
                        book.setCreateTimeMillis(Long.parseLong(bookElement.getChildText("createTimeMillis")));
                        book.setLastChapter(bookElement.getChildText("lastChapter"));
                        book.setLastReadChapter(bookElement.getChildText("lastReadChapter"));
                        book.setLastReadChapterId(bookElement.getChildText("lastReadChapterId"));
                        book.setLastReadPosition(Integer.parseInt(bookElement.getChildText("lastReadPosition")));
                        book.setLastReadTimeMillis(Long.parseLong(bookElement.getChildText("lastReadTimeMillis")));
                        book.setTotalChapters(Integer.parseInt(bookElement.getChildText("totalChapters")));
                        book.setCurrentChapterIndex(Integer.parseInt(bookElement.getChildText("currentChapterIndex")));
                        book.setFinished(Boolean.parseBoolean(bookElement.getChildText("finished")));
                        book.setLastReadPage(Integer.parseInt(bookElement.getChildText("lastReadPage")));
                        books.add(book);
                    }

                    // 保存为 JSON 格式
                    saveBookIndex();

                    // 删除旧的 XML 文件
                    Files.delete(xmlPath);
                    LOG.debug("已迁移旧版 XML 数据到 JSON 格式");
                }
            }
        } catch (IOException | JDOMException e) {
            LOG.error("迁移旧版 XML 数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化Gson实例
     */
    private void initializeGson() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableJdkUnsafe()
            .excludeFieldsWithoutExposeAnnotation()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return f.getName().equals("parser") ||
                           f.getName().equals("project") ||
                           f.getName().equals("cachedChapters");
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();
    }

    /**
     * 获取所有书籍
     * @param loadDetails 是否加载详细信息
     * @return 书籍列表的副本
     */
    public List<Book> getAllBooks(boolean loadDetails) {
        cleanupIfNeeded();

        if (loadDetails) {
            return books.stream()
                .map(book -> {
                    Book detailedBook = getBookDetails(book.getId());
                    return detailedBook != null ? detailedBook : book;
                })
                .collect(Collectors.toList());
        }

        return new ArrayList<>(books);
    }

    /**
     * 获取所有书籍（包含详细信息）
     * @return 书籍列表的副本
     */
    public List<Book> getAllBooks() {
        return getAllBooks(false);
    }

    /**
     * 获取单本书籍
     * @param bookId 书籍ID
     * @return 书籍对象，如果不存在则返回null
     */
    public Book getBook(String bookId) {
        cleanupIfNeeded();

        // 检查缓存
        CacheEntry cached = bookCache.get(bookId);
        if (cached != null) {
            return cached.book;
        }

        // 从内存中查找
        Book book = books.stream()
            .filter(b -> b.getId().equals(bookId))
            .findFirst()
            .orElse(null);

        if (book == null) {
            return null;
        }

        // 加载详细信息
        Book detailedBook = getBookDetails(bookId);
        if (detailedBook != null) {
            // 更新缓存
            bookCache.put(bookId, new CacheEntry(detailedBook));
            return detailedBook;
        }

        return book;
    }

    /**
     * 添加新书籍
     * 如果书籍已存在（根据ID判断）则不会重复添加
     * @param book 要添加的书籍
     */
    public void addBook(Book book) {
        books.add(book);
        saveBookIndex();
        saveBookDetails(book);
    }

    /**
     * 移除书籍
     * @param book 要移除的书籍
     */
    public void removeBook(Book book) {
        books.remove(book);
        saveBookIndex();
        deleteBookDetails(book.getId());
    }

    /**
     * 更新书籍信息
     * @param book 包含更新信息的书籍对象
     */
    public void updateBook(Book book) {
        saveBookDetails(book);
        bookCache.put(book.getId(), new CacheEntry(book));
    }

    /**
     * 更新所有书籍
     * @param books 新的书籍列表
     */
    public void updateBooks(@NotNull List<Book> books) {
        for (Book book : books) {
            updateBook(book);
        }
    }

    /**
     * 清空所有书籍数据
     * 删除内存中的书籍列表并保存空列表到存储
     */
    public void clearAllBooks() {
        books.clear();
        bookCache.clear();
        saveBookIndex();

        // 删除所有书籍详情文件
        try {
            Path booksPath = Path.of(PathManager.getSystemPath(), "private-reader", "books");
            if (Files.exists(booksPath)) {
                FileUtils.deleteDirectory(booksPath.toFile());
            }
        } catch (IOException e) {
            LOG.error("清除所有书籍失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取书籍索引文件路径
     * @return 书籍索引文件的完整路径
     */
    public String getIndexFilePath() {
        return indexFilePath.toString();
    }

    /**
     * 保存书籍索引到持久化存储
     */
    private void saveBookIndex() {
        try {
            // 确保目录存在
            Path booksPath = Path.of(PathManager.getSystemPath(), "private-reader", "books");
            Files.createDirectories(booksPath);

            // 保存索引文件
            Path indexPath = booksPath.resolve("index.json");
            try (FileWriter writer = new FileWriter(indexPath.toFile())) {
                gson.toJson(books, writer);
            }

            LOG.debug("已保存书籍索引到: " + indexPath);
        } catch (IOException e) {
            LOG.error("保存书籍索引失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存书籍详细信息到专属目录
     * @param book 要保存的书籍
     */
    private void saveBookDetails(Book book) {
        try {
            // 确保目录存在
            Path bookPath = Path.of(PathManager.getSystemPath(), "private-reader", "books", book.getId());
            Files.createDirectories(bookPath);

            // 保存详情文件
            Path detailsPath = bookPath.resolve("details.json");
            try (FileWriter writer = new FileWriter(detailsPath.toFile())) {
                gson.toJson(book, writer);
            }

            LOG.debug("已保存书籍详情到: " + detailsPath);
        } catch (IOException e) {
            LOG.error("保存书籍详情失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载书籍详细信息
     * @param bookId 书籍ID
     * @return 书籍详细信息，如果不存在则返回null
     */
    private Book getBookDetails(String bookId) {
        try {
            Path detailsPath = Path.of(PathManager.getSystemPath(), "private-reader", "books", bookId, "details.json");
            if (!Files.exists(detailsPath)) {
                return null;
            }

            try (FileReader reader = new FileReader(detailsPath.toFile())) {
                return gson.fromJson(reader, Book.class);
            }
        } catch (IOException e) {
            LOG.error("加载书籍详情失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除书籍详细信息文件
     * @param bookId 书籍ID
     */
    private void deleteBookDetails(String bookId) {
        try {
            Path bookPath = Path.of(PathManager.getSystemPath(), "private-reader", "books", bookId);
            if (Files.exists(bookPath)) {
                FileUtils.deleteDirectory(bookPath.toFile());
            }
        } catch (IOException e) {
            LOG.error("删除书籍详情失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从持久化存储加载书籍列表
     */
    private void loadBooks() {
        try {
            Path indexPath = Path.of(PathManager.getSystemPath(), "private-reader", "books", "index.json");
            if (!Files.exists(indexPath)) {
                return;
            }

            try (FileReader reader = new FileReader(indexPath.toFile())) {
                Type listType = new TypeToken<List<Book>>(){}.getType();
                List<Book> loadedBooks = gson.fromJson(reader, listType);
                if (loadedBooks != null) {
                    books = loadedBooks;
                }
            }
        } catch (IOException e) {
            LOG.error("加载书籍列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取书籍数据文件路径（兼容旧版本）
     * @return 书籍数据文件的完整路径
     */
    public String getBooksFilePath() {
        return storageManager.getBooksFilePath();
    }
}