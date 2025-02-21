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
import com.intellij.openapi.project.Project;
import com.intellij.ide.util.PropertiesComponent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

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
    private static final String STORAGE_KEY = "privateReaderBooks";

    public BookStorage(Project project) {
        this.project = project;
        this.gson = new Gson();
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
            books.removeIf(b -> b.getId().equals(book.getId()));
        }
    }

    public void updateBook(Book book) {
        LOG.info("尝试更新书籍: " + book.getTitle());
        if (books != null) {
            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).getId().equals(book.getId())) {
                    books.set(i, book);
                    LOG.info("成功更新书籍: " + book.getTitle());
                    break;
                }
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
        saveBooks();
    }

    /**
     * 保存书籍列表到持久化存储
     */
    private void saveBooks() {
        try {
            String json = gson.toJson(books);
            PropertiesComponent.getInstance(project).setValue(STORAGE_KEY, json);
        } catch (Exception e) {
            LOG.error("保存书籍列表失败", e);
        }
    }

    /**
     * 从持久化存储加载书籍列表
     */
    private void loadBooks() {
        try {
            String json = PropertiesComponent.getInstance(project).getValue(STORAGE_KEY);
            if (json != null && !json.isEmpty()) {
                Type listType = new TypeToken<ArrayList<Book>>(){}.getType();
                books = gson.fromJson(json, listType);
            }
        } catch (Exception e) {
            LOG.error("加载书籍列表失败", e);
        }
    }
} 