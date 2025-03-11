package com.lv.tool.privatereader.repository.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.model.BookIndex;
import com.lv.tool.privatereader.repository.BookRepository;
import com.lv.tool.privatereader.repository.StorageRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 文件书籍仓库实现
 * 
 * 基于文件系统实现书籍仓库接口，管理书籍数据的持久化存储。
 * 采用分离存储方案：
 * - 主索引文件：存储所有书籍的基本信息，位于 private-reader/books/index.json
 * - 书籍详情文件：每本书单独存储详细信息，位于 private-reader/books/{bookId}/details.json
 */
@Service(Service.Level.PROJECT)
public class FileBookRepository implements BookRepository {
    private static final Logger LOG = Logger.getInstance(FileBookRepository.class);
    private static final int MAX_CACHE_SIZE = 100; // 最大内存缓存数量
    
    private final Project project;
    private final StorageRepository storageRepository;
    private final Gson gson;
    
    // 内存缓存，使用LRU策略
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
    
    public FileBookRepository(Project project, StorageRepository storageRepository) {
        this.project = project;
        this.storageRepository = storageRepository;
        
        // 配置Gson
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();
    }
    
    @Override
    @NotNull
    public List<Book> getAllBooks(boolean loadDetails) {
        List<Book> books = new ArrayList<>();
        
        try {
            // 读取索引文件
            File indexFile = new File(storageRepository.getBooksFilePath());
            if (!indexFile.exists()) {
                LOG.info("书籍索引文件不存在，返回空列表");
                return books;
            }
            
            // 解析索引文件
            try (FileReader reader = new FileReader(indexFile)) {
                List<BookIndex> indices = gson.fromJson(reader, new TypeToken<List<BookIndex>>(){}.getType());
                if (indices == null) {
                    return books;
                }
                
                // 转换为Book对象
                for (BookIndex index : indices) {
                    if (loadDetails) {
                        // 加载完整书籍信息
                        Book book = getBook(index.getId());
                        if (book != null) {
                            books.add(book);
                        }
                    } else {
                        // 只使用索引信息创建简化的Book对象
                        Book book = new Book(index.getId(), index.getTitle(), index.getAuthor(), index.getUrl());
                        book.setCreateTimeMillis(index.getCreateTimeMillis());
                        book.setLastChapter(index.getLastChapter());
                        book.setLastReadChapter(index.getLastReadChapter());
                        book.setLastReadTimeMillis(index.getLastReadTimeMillis());
                        book.setTotalChapters(index.getTotalChapters());
                        book.setFinished(index.isFinished());
                        books.add(book);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("获取书籍列表失败: " + e.getMessage(), e);
        }
        
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
        
        // 检查内存缓存
        CacheEntry entry = bookCache.get(bookId);
        if (entry != null) {
            return entry.book;
        }
        
        try {
            // 读取书籍详情文件
            String bookDir = storageRepository.getBookDirectory(bookId);
            File detailsFile = new File(bookDir, "details.json");
            if (!detailsFile.exists()) {
                LOG.warn("书籍详情文件不存在: " + detailsFile.getAbsolutePath());
                return null;
            }
            
            // 解析书籍详情
            try (FileReader reader = new FileReader(detailsFile)) {
                Book book = gson.fromJson(reader, Book.class);
                if (book != null) {
                    // 设置项目引用
                    book.setProject(project);
                    
                    // 添加到缓存
                    bookCache.put(bookId, new CacheEntry(book));
                    
                    return book;
                }
            }
        } catch (Exception e) {
            LOG.error("获取书籍失败: " + e.getMessage(), e);
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
            // 保存书籍详情
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
     * 保存书籍详情
     */
    private void saveBookDetails(Book book) throws IOException {
        String bookDir = storageRepository.getBookDirectory(book.getId());
        File detailsFile = new File(bookDir, "details.json");
        
        try (FileWriter writer = new FileWriter(detailsFile)) {
            gson.toJson(book, writer);
        }
    }
    
    /**
     * 更新书籍索引
     */
    private void updateBookIndex(Book book) throws IOException {
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
        
        // 保存更新后的索引
        saveBookIndices(indices);
    }
    
    /**
     * 从索引中移除书籍
     */
    private void removeBookFromIndex(String bookId) throws IOException {
        // 读取当前索引
        List<BookIndex> indices = readBookIndices();
        
        // 移除指定书籍
        indices = indices.stream()
            .filter(index -> !index.getId().equals(bookId))
            .collect(Collectors.toList());
        
        // 保存更新后的索引
        saveBookIndices(indices);
    }
    
    /**
     * 读取书籍索引列表
     */
    private List<BookIndex> readBookIndices() throws IOException {
        File indexFile = new File(storageRepository.getBooksFilePath());
        if (!indexFile.exists()) {
            return new ArrayList<>();
        }
        
        try (FileReader reader = new FileReader(indexFile)) {
            List<BookIndex> indices = gson.fromJson(reader, new TypeToken<List<BookIndex>>(){}.getType());
            return indices != null ? indices : new ArrayList<>();
        }
    }
    
    /**
     * 保存书籍索引列表
     */
    private void saveBookIndices(List<BookIndex> indices) throws IOException {
        File indexFile = new File(storageRepository.getBooksFilePath());
        
        // 确保父目录存在
        indexFile.getParentFile().mkdirs();
        
        try (FileWriter writer = new FileWriter(indexFile)) {
            gson.toJson(indices, writer);
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
}