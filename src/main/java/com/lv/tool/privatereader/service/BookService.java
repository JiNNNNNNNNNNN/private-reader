package com.lv.tool.privatereader.service;

import com.lv.tool.privatereader.model.Book;
import java.util.List;

/**
 * 书籍服务接口
 * 处理书籍相关的业务逻辑
 */
public interface BookService {
    /**
     * 获取所有书籍
     * @return 书籍列表
     */
    List<Book> getAllBooks();
    
    /**
     * 添加书籍
     * @param book 书籍对象
     * @return 添加成功返回true，否则返回false
     */
    boolean addBook(Book book);
    
    /**
     * 删除书籍
     * @param book 书籍对象
     * @return 删除成功返回true，否则返回false
     */
    boolean removeBook(Book book);
    
    /**
     * 更新书籍信息
     * @param book 书籍对象
     * @return 更新成功返回true，否则返回false
     */
    boolean updateBook(Book book);
    
    /**
     * 获取最近阅读的书籍
     * @return 最近阅读的书籍，如果没有则返回null
     */
    Book getLastReadBook();
    
    /**
     * 保存阅读进度
     * @param book 书籍对象
     * @param chapterId 章节ID
     * @param position 阅读位置
     */
    void saveReadingProgress(Book book, String chapterId, int position);
} 