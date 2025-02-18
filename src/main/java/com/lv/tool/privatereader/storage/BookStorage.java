package com.lv.tool.privatereader.storage;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.lv.tool.privatereader.model.Book;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.openapi.diagnostic.LogLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍存储服务
 * 
 * 负责管理书籍数据的持久化存储，提供以下功能：
 * - 保存和加载书籍列表
 * - 添加和删除书籍
 * - 更新书籍信息
 * 
 * 数据以XML格式存储在项目配置目录下的privateReader.xml文件中
 */
@State(
    name = "PrivateReaderBookStorage",
    storages = @Storage("privateReader.xml")
)
@Tag("BookStorage")
public class BookStorage implements PersistentStateComponent<BookStorage> {
    private static final Logger LOG = Logger.getInstance(BookStorage.class);
    
    static {
        LOG.setLevel(LogLevel.DEBUG);
    }
    
    @Tag("books")
    @XCollection(style = XCollection.Style.v2)
    private List<Book> books;

    public BookStorage() {
        this.books = new ArrayList<>();
        LOG.info("初始化 BookStorage");
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
        XmlSerializerUtil.copyBean(state, this);
        if (books == null) {
            LOG.warn("加载的 books 列表为 null，创建新列表");
            books = new ArrayList<>();
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
        return new ArrayList<>(books);
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
        if (!books.contains(book)) {
            books.add(book);
            LOG.info("成功添加书籍: " + book.getTitle());
        } else {
            LOG.debug("书籍已存在，跳过添加: " + book.getTitle());
        }
    }

    public void removeBook(Book book) {
        LOG.info("尝试移除书籍: " + book.getTitle());
        if (books != null) {
            boolean removed = books.remove(book);
            if (removed) {
                LOG.info("成功移除书籍: " + book.getTitle());
            } else {
                LOG.warn("未找到要移除的书籍: " + book.getTitle());
            }
        }
    }

    public void updateBook(Book book) {
        LOG.info("尝试更新书籍: " + book.getTitle());
        if (books != null) {
            int index = books.indexOf(book);
            if (index != -1) {
                books.set(index, book);
                LOG.info("成功更新书籍: " + book.getTitle());
            } else {
                LOG.warn("未找到要更新的书籍: " + book.getTitle());
            }
        }
    }
} 