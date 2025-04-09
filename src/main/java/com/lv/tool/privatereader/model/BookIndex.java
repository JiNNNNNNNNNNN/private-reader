package com.lv.tool.privatereader.model;

import com.google.gson.annotations.Expose;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import java.util.Objects;

/**
 * 书籍索引类
 * 
 * 用于存储书籍的基本索引信息，作为主索引文件的数据结构。
 * 只包含必要的元数据，不包含完整的书籍内容和章节信息。
 */
@Tag("BookIndex")
public class BookIndex {
    /** 书籍唯一标识符 */
    @Tag @Expose private String id;
    /** 书籍标题 */
    @Tag @Expose private String title;
    /** 作者名称 */
    @Tag @Expose private String author;
    /** 书籍来源URL */
    @Tag @Expose private String url;
    /** 创建时间 */
    @Tag @Expose private long createTimeMillis;
    /** 最新章节标题 */
    @Tag @Expose private String lastChapter;
    /** 上次阅读时间戳 */
    @Tag @Expose private long lastReadTimeMillis;
    /** 总章节数 */
    @Tag @Expose private int totalChapters;
    /** 是否已读完 */
    @Tag @Expose private boolean finished;

    public BookIndex() {
        this.createTimeMillis = System.currentTimeMillis();
        this.lastReadTimeMillis = System.currentTimeMillis();
    }

    /**
     * 从完整的Book对象创建索引
     * @param book 完整的书籍对象
     * @return 书籍索引对象
     */
    public static BookIndex fromBook(@NotNull Book book) {
        BookIndex index = new BookIndex();
        index.id = book.getId();
        index.title = book.getTitle();
        index.author = book.getAuthor();
        index.url = book.getUrl();
        index.createTimeMillis = book.getCreateTimeMillis();
        index.lastChapter = book.getLastChapter();
        index.lastReadTimeMillis = book.getLastReadTimeMillis();
        index.totalChapters = book.getTotalChapters();
        index.finished = book.isFinished();
        return index;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getCreateTimeMillis() {
        return createTimeMillis;
    }

    public void setCreateTimeMillis(long createTimeMillis) {
        this.createTimeMillis = createTimeMillis;
    }

    public String getLastChapter() {
        return lastChapter;
    }

    public void setLastChapter(String lastChapter) {
        this.lastChapter = lastChapter;
    }

    public long getLastReadTimeMillis() {
        return lastReadTimeMillis;
    }

    public void setLastReadTimeMillis(long lastReadTimeMillis) {
        this.lastReadTimeMillis = lastReadTimeMillis;
    }

    public int getTotalChapters() {
        return totalChapters;
    }

    public void setTotalChapters(int totalChapters) {
        this.totalChapters = totalChapters;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookIndex that = (BookIndex) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
} 