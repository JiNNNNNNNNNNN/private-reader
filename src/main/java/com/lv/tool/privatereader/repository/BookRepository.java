package com.lv.tool.privatereader.repository;

import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 书籍仓库接口
 * 
 * 定义书籍数据的存储和检索操作，提供统一的数据访问接口。
 * 实现类负责具体的存储实现细节，如文件存储、数据库存储等。
 */
public interface BookRepository {
    
    /**
     * 获取所有书籍
     * 
     * @param loadDetails 是否加载详细信息
     * @return 书籍列表
     */
    @NotNull
    List<Book> getAllBooks(boolean loadDetails);
    
    /**
     * 获取所有书籍（默认加载详细信息）
     * 
     * @return 书籍列表
     */
    @NotNull
    List<Book> getAllBooks();
    
    /**
     * 根据ID获取书籍
     * 
     * @param bookId 书籍ID
     * @return 书籍对象，如果不存在则返回null
     */
    @Nullable
    Book getBook(String bookId);
    
    /**
     * 添加书籍
     * 
     * @param book 要添加的书籍
     */
    void addBook(@NotNull Book book);
    
    /**
     * 更新书籍
     * 
     * @param book 要更新的书籍
     */
    void updateBook(@NotNull Book book);
    
    /**
     * 批量更新书籍
     * 
     * @param books 要更新的书籍列表
     */
    void updateBooks(@NotNull List<Book> books);
    
    /**
     * 删除书籍
     * 
     * @param book 要删除的书籍
     */
    void removeBook(@NotNull Book book);
    
    /**
     * 清空所有书籍
     */
    void clearAllBooks();
    
    /**
     * 获取书籍索引文件路径
     * 
     * @return 索引文件路径
     */
    String getIndexFilePath();
} 