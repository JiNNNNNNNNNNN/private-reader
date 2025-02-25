package com.lv.tool.privatereader.storage;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.project.Project;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;
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
@State(
    name = "PrivateReaderBookStorage",
    storages = @Storage("private-reader-books.xml")
)
@Tag("BookStorage")
public class BookStorage implements PersistentStateComponent<BookStorage> {
    private static final Logger LOG = Logger.getInstance(BookStorage.class);
    
    static {
        LOG.setLevel(LogLevel.DEBUG);
    }
    
    @Tag("books")
    @XCollection(style = XCollection.Style.v2)
    private List<Book> books = new ArrayList<>();

    private Project project;
    private Gson gson;
    private final Path indexFilePath;
    private final StorageManager storageManager;
    
    // 内存缓存，用于存储已加载的书籍详情
    private final Map<String, Book> bookCache = new HashMap<>();

    public BookStorage(Project project) {
        this.project = project;
        this.storageManager = project.getService(StorageManager.class);
        
        // 创建Gson实例，添加Chapter类的类型适配器，禁用反射访问私有字段
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableJdkUnsafe() // 禁用不安全的JDK访问
            .excludeFieldsWithoutExposeAnnotation() // 只序列化带有@Expose注解的字段
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
                    Book book = new Book();
                    in.beginObject();
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
                            case "cachedChapters":
                                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                                    in.nextNull();
                                } else {
                                    List<Chapter> chapters = new ArrayList<>();
                                    in.beginArray();
                                    while (in.hasNext()) {
                                        in.beginObject();
                                        String title = "";
                                        String url = "";
                                        while (in.hasNext()) {
                                            String fieldName = in.nextName();
                                            if (fieldName.equals("title")) {
                                                title = in.nextString();
                                            } else if (fieldName.equals("url")) {
                                                url = in.nextString();
                                            } else {
                                                in.skipValue();
                                            }
                                        }
                                        chapters.add(new Chapter(title, url));
                                        in.endObject();
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
            
        // 使用StorageManager获取书籍索引文件路径
        this.indexFilePath = Path.of(storageManager.getBooksPath(), "index.json");
        loadBooks();
    }

    /**
     * 获取存储状态
     * @return 当前BookStorage实例
     */
    @Override
    public @Nullable BookStorage getState() {
        LOG.debug("获取存储状态");
        return this;
    }

    /**
     * 加载存储状态
     * 从XML文件恢复书籍列表数据
     * @param state 要加载的状态
     */
    @Override
    public void loadState(@NotNull BookStorage state) {
        LOG.info("加载存储状态");
        
        // 先尝试从JSON文件加载
        loadBooks();
        
        // 如果JSON文件没有数据，再从XML加载
        if (books == null || books.isEmpty()) {
            LOG.info("从XML加载书籍数据");
        XmlSerializerUtil.copyBean(state, this);
        if (books == null) {
            LOG.warn("加载的 books 列表为 null，创建新列表");
            books = new ArrayList<>();
        }
            // 保存到JSON文件，确保两种存储方式同步
            if (!books.isEmpty()) {
                saveBookIndex();
                
                // 保存每本书的详细信息
                for (Book book : books) {
                    saveBookDetails(book);
                }
            }
        }
        
        LOG.info("当前书籍数量: " + books.size());
    }

    /**
     * 获取所有书籍
     * @return 书籍列表的副本
     */
    public List<Book> getAllBooks() {
        if (books == null) {
            LOG.warn("books 列表为 null，创建新列表");
            books = new ArrayList<>();
        }
        LOG.debug("获取所有书籍，数量: " + books.size());
        
        // 确保所有书籍都已加载详细信息
        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            Book detailedBook = getBookDetails(book.getId());
            if (detailedBook != null) {
                books.set(i, detailedBook);
            }
        }
        
        return new ArrayList<>(books);
    }

    /**
     * 获取单本书籍
     * @param bookId 书籍ID
     * @return 书籍对象，如果不存在则返回null
     */
    public Book getBook(String bookId) {
        // 先从缓存中查找
        if (bookCache.containsKey(bookId)) {
            return bookCache.get(bookId);
        }
        
        // 再从内存中查找
        for (Book book : books) {
            if (book.getId().equals(bookId)) {
                // 加载详细信息
                Book detailedBook = getBookDetails(bookId);
                if (detailedBook != null) {
                    // 更新缓存
                    bookCache.put(bookId, detailedBook);
                    return detailedBook;
                }
                return book;
            }
        }
        
        return null;
    }

    /**
     * 添加新书籍
     * 如果书籍已存在（根据ID判断）则不会重复添加
     * @param book 要添加的书籍
     */
    public void addBook(Book book) {
        LOG.info("尝试添加书籍: " + book.getTitle());
        if (books == null) {
            books = new ArrayList<>();
        }
        
        // 检查是否已存在
        boolean exists = books.stream().anyMatch(b -> b.getId().equals(book.getId()));
        if (!exists) {
            books.add(book);
            LOG.info("成功添加书籍: " + book.getTitle());
            
            // 更新缓存
            bookCache.put(book.getId(), book);
            
            // 保存索引和详情
            saveBookIndex();
            saveBookDetails(book);
            
            // 为书籍创建专属目录
            storageManager.createBookDirectory(book.getId());
        } else {
            LOG.debug("书籍已存在，跳过添加: " + book.getTitle());
        }
    }

    /**
     * 移除书籍
     * @param book 要移除的书籍
     */
    public void removeBook(Book book) {
        LOG.info("尝试移除书籍: " + book.getTitle());
        if (books != null) {
            boolean removed = books.removeIf(b -> b.getId().equals(book.getId()));
            if (removed) {
                LOG.info("成功移除书籍: " + book.getTitle());
                
                // 从缓存中移除
                bookCache.remove(book.getId());
                
                // 保存索引
                saveBookIndex();
                
                // 删除书籍详情文件
                deleteBookDetails(book.getId());
            } else {
                LOG.debug("未找到要移除的书籍: " + book.getTitle());
            }
        }
    }

    /**
     * 更新书籍信息
     * @param book 包含更新信息的书籍对象
     */
    public void updateBook(Book book) {
        LOG.info("尝试更新书籍: " + book.getTitle());
        if (books != null) {
            boolean updated = false;
            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).getId().equals(book.getId())) {
                    books.set(i, book);
                    updated = true;
                    LOG.info("成功更新书籍: " + book.getTitle());
                    break;
                }
            }
            
            if (updated) {
                // 更新缓存
                bookCache.put(book.getId(), book);
                
                // 保存索引和详情
                saveBookIndex();
                saveBookDetails(book);
            }
        }
    }

    /**
     * 更新所有书籍
     * @param books 新的书籍列表
     */
    public void updateBooks(@NotNull List<Book> books) {
        this.books.clear();
        this.books.addAll(books);
        
        // 清空缓存
        bookCache.clear();
        
        // 保存所有书籍
        saveBookIndex();
        for (Book book : books) {
            saveBookDetails(book);
        }
    }

    /**
     * 清空所有书籍数据
     * 删除内存中的书籍列表并保存空列表到存储
     */
    public void clearAllBooks() {
        LOG.info("清空所有书籍数据");
        if (books != null) {
            // 删除所有书籍详情文件
            for (Book book : books) {
                deleteBookDetails(book.getId());
            }
            
            books.clear();
            bookCache.clear();
            saveBookIndex();
        } else {
            books = new ArrayList<>();
        }
        LOG.info("所有书籍数据已清空");
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
            // 创建书籍索引列表
            List<BookIndex> bookIndices = books.stream()
                .map(BookIndex::fromBook)
                .collect(Collectors.toList());
            
            String json = gson.toJson(bookIndices);
            File file = indexFilePath.toFile();
            
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
                LOG.info("创建书籍索引目录: " + parentDir.getAbsolutePath());
            }
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
            }
            LOG.info("书籍索引已保存到: " + indexFilePath);
            LOG.info("书籍数量: " + books.size());
        } catch (Exception e) {
            LOG.error("保存书籍索引失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 保存书籍详细信息到专属目录
     * @param book 要保存的书籍
     */
    private void saveBookDetails(Book book) {
        try {
            Path bookDir = Path.of(storageManager.getBookDirectory(book.getId()));
            Path detailsPath = bookDir.resolve("details.json");
            
            try (FileWriter writer = new FileWriter(detailsPath.toFile())) {
                writer.write(gson.toJson(book));
            }
            LOG.info("书籍详细信息已保存: " + book.getTitle() + " 到 " + detailsPath);
        } catch (Exception e) {
            LOG.error("保存书籍详细信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载书籍详细信息
     * @param bookId 书籍ID
     * @return 书籍详细信息，如果不存在则返回null
     */
    private Book getBookDetails(String bookId) {
        // 先从缓存中查找
        if (bookCache.containsKey(bookId)) {
            return bookCache.get(bookId);
        }
        
        try {
            Path bookDir = Path.of(storageManager.getBookDirectory(bookId));
            Path detailsPath = bookDir.resolve("details.json");
            
            if (!Files.exists(detailsPath)) {
                LOG.warn("书籍详细信息文件不存在: " + detailsPath);
                return null;
            }
            
            try (FileReader reader = new FileReader(detailsPath.toFile())) {
                Book book = gson.fromJson(reader, Book.class);
                if (book != null) {
                    // 更新缓存
                    bookCache.put(bookId, book);
                }
                return book;
            }
        } catch (Exception e) {
            LOG.error("加载书籍详细信息失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 删除书籍详细信息文件
     * @param bookId 书籍ID
     */
    private void deleteBookDetails(String bookId) {
        try {
            Path bookDir = Path.of(storageManager.getBookDirectory(bookId));
            Path detailsPath = bookDir.resolve("details.json");
            
            if (Files.exists(detailsPath)) {
                Files.delete(detailsPath);
                LOG.info("已删除书籍详细信息文件: " + detailsPath);
            }
        } catch (Exception e) {
            LOG.error("删除书籍详细信息文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从持久化存储加载书籍列表
     */
    private void loadBooks() {
        // 清空缓存
        bookCache.clear();
        
        // 加载书籍索引
        File indexFile = indexFilePath.toFile();
        if (!indexFile.exists()) {
            LOG.info("书籍索引文件不存在: " + indexFilePath);
            
            // 尝试从旧的books.json文件加载
            File oldBooksFile = Path.of(storageManager.getBooksFilePath()).toFile();
            if (oldBooksFile.exists()) {
                LOG.info("尝试从旧的books.json文件加载数据");
                loadFromLegacyFile(oldBooksFile);
            } else {
                books = new ArrayList<>();
            }
            return;
        }
        
        try (FileReader reader = new FileReader(indexFile)) {
            Type bookIndexListType = new TypeToken<ArrayList<BookIndex>>(){}.getType();
            List<BookIndex> bookIndices = gson.fromJson(reader, bookIndexListType);
            
            if (bookIndices == null) {
                LOG.warn("加载的书籍索引为null，创建新列表");
                books = new ArrayList<>();
                return;
            }
            
            // 将索引转换为书籍对象
            books = new ArrayList<>();
            for (BookIndex index : bookIndices) {
                // 尝试加载详细信息
                Book book = getBookDetails(index.getId());
                if (book == null) {
                    // 如果详细信息不存在，创建一个基本的Book对象
                    book = new Book();
                    book.setId(index.getId());
                    book.setTitle(index.getTitle());
                    book.setAuthor(index.getAuthor());
                    book.setUrl(index.getUrl());
                    book.setCreateTimeMillis(index.getCreateTimeMillis());
                    book.setLastChapter(index.getLastChapter());
                    book.setLastReadChapter(index.getLastReadChapter());
                    book.setLastReadTimeMillis(index.getLastReadTimeMillis());
                    book.setTotalChapters(index.getTotalChapters());
                    book.setFinished(index.isFinished());
                }
                books.add(book);
                
                // 更新缓存
                bookCache.put(book.getId(), book);
            }
            
            LOG.info("从索引文件加载了 " + books.size() + " 本书");
        } catch (Exception e) {
            LOG.error("加载书籍索引失败: " + e.getMessage(), e);
            books = new ArrayList<>();
        }
    }
    
    /**
     * 从旧版本的books.json文件加载数据
     * @param oldBooksFile 旧版本的books.json文件
     */
    private void loadFromLegacyFile(File oldBooksFile) {
        try (FileReader reader = new FileReader(oldBooksFile)) {
            Type bookListType = new TypeToken<ArrayList<Book>>(){}.getType();
            books = gson.fromJson(reader, bookListType);
            
            if (books == null) {
                books = new ArrayList<>();
                return;
            }
            
            LOG.info("从旧版本文件加载了 " + books.size() + " 本书");
            
            // 将数据迁移到新的存储结构
            for (Book book : books) {
                saveBookDetails(book);
                bookCache.put(book.getId(), book);
            }
            
            // 保存索引
            saveBookIndex();
            
            LOG.info("已将数据迁移到新的存储结构");
        } catch (Exception e) {
            LOG.error("从旧版本文件加载失败: " + e.getMessage(), e);
            books = new ArrayList<>();
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