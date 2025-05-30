package com.lv.tool.privatereader.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.time.temporal.ChronoUnit;
import com.lv.tool.privatereader.parser.NovelParser.Chapter;
import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.parser.NovelParser;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.lv.tool.privatereader.parser.ParserFactory;
import com.google.gson.annotations.Expose;

/**
 * 书籍模型类
 *
 * 用于存储和管理书籍的基本信息、阅读进度和章节数据。
 * 包含书籍的元数据（标题、作者等）、阅读状态（当前章节、进度等）
 * 以及章节缓存等功能。
 */
@Tag("Book")
public class Book {
    /** 书籍唯一标识符 */
    @Tag @Expose private String id;
    /** 书籍标题 */
    @Tag @Expose private String title;
    /** 作者名称 */
    @Tag @Expose private String author;
    /** 书籍来源URL */
    @Tag @Expose private String url;
    /** 书籍来源ID */
    @Tag @Expose private String sourceId;
    /** 创建时间 */
    @Tag @Expose private long createTimeMillis;
    /** 最新章节标题 */
    @Tag @Expose private String lastChapter;
    /** 上次阅读的章节标题 */
    @Tag @Expose private String lastReadChapter;
    /** 上次阅读的章节ID */
    @Tag @Expose private String lastReadChapterId;
    /** 上次阅读位置（字符偏移量） */
    @Tag @Expose private int lastReadPosition;
    /** 上次阅读时间戳 */
    @Tag("lastReadTimeMillis")
    @Expose
    private long lastReadTimeMillis = 0;
    /** 总章节数 */
    @Tag @Expose private int totalChapters;
    /** 当前阅读的章节索引 */
    @Tag @Expose private int currentChapterIndex;
    /** 是否已读完 */
    @Tag @Expose private boolean finished;
    /** 缓存的章节列表 */
    @Tag
    @XCollection(style = XCollection.Style.v2)
    @Expose
    private List<Chapter> cachedChapters;
    /** 关联的项目实例 */
    @Transient private Project project;
    /** 书籍内容解析器 */
    @Transient private NovelParser parser;
    /** 上次阅读页数 */
    @Tag("lastReadPage")
    @Expose
    private int lastReadPage = 1;

    public Book() {
        this.lastReadPosition = 0;
        this.totalChapters = 0;
        this.currentChapterIndex = 0;
        this.finished = false;
        this.lastReadTimeMillis = System.currentTimeMillis();
        this.createTimeMillis = System.currentTimeMillis();
    }

    public Book(String id, String title, String author, String url) {
        this();
        this.id = id;
        this.title = title;
        this.author = author;
        this.url = url;
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

    public String getLastChapter() {
        return lastChapter;
    }

    public void setLastChapter(String lastChapter) {
        this.lastChapter = lastChapter;
    }

    public String getLastReadChapter() {
        return lastReadChapter;
    }

    public void setLastReadChapter(String lastReadChapter) {
        this.lastReadChapter = lastReadChapter;
    }

    public String getLastReadChapterId() {
        return lastReadChapterId;
    }

    public void setLastReadChapterId(String lastReadChapterId) {
        this.lastReadChapterId = lastReadChapterId;
    }

    public int getLastReadPosition() {
        return lastReadPosition;
    }

    public void setLastReadPosition(int lastReadPosition) {
        this.lastReadPosition = lastReadPosition;
    }

    public int getTotalChapters() {
        return totalChapters;
    }

    public void setTotalChapters(int totalChapters) {
        this.totalChapters = totalChapters;
    }

    public int getCurrentChapterIndex() {
        return currentChapterIndex;
    }

    public void setCurrentChapterIndex(int currentChapterIndex) {
        this.currentChapterIndex = currentChapterIndex;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public double getReadingProgress() {
        if (totalChapters == 0) return 0.0;
        return (double) currentChapterIndex / totalChapters;
    }

    public void updateReadingProgress(String chapterId, int position, int page) {
        this.lastReadChapterId = chapterId;
        this.lastReadPosition = position;
        this.lastReadTimeMillis = System.currentTimeMillis();
        this.lastReadPage = page;
    }

    @Override
    public String toString() {
        return String.format("%s - %s [%d%%]",
            title,
            author,
            (int) (getReadingProgress() * 100));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(id, book.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public List<Chapter> getCachedChapters() {
        return cachedChapters;
    }

    public void setCachedChapters(List<Chapter> chapters) {
        this.cachedChapters = chapters;
        if (chapters != null) {
            this.totalChapters = chapters.size();
        }
    }

    @Transient
    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @Transient
    public NovelParser getParser() {
        if (parser == null && url != null && !url.isEmpty()) {
            try {
                com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(Book.class);
                LOG.info("创建书籍解析器: " + url);
                parser = ParserFactory.createParser(url);
                if (parser != null) {
                    LOG.info("成功创建书籍解析器: " + url);
                } else {
                    LOG.error("创建书籍解析器失败: " + url + "，ParserFactory返回null");
                }
            } catch (Exception e) {
                com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(Book.class);
                LOG.error("创建书籍解析器时发生错误: " + url + ", 错误: " + e.getMessage(), e);
                // 不抛出异常，而是返回null，让调用者处理
            }
        }
        return parser;
    }

    public void setParser(NovelParser parser) {
        this.parser = parser;
    }

    public long getLastReadTimeMillis() {
        return lastReadTimeMillis;
    }

    public void setLastReadTimeMillis(long lastReadTimeMillis) {
        this.lastReadTimeMillis = lastReadTimeMillis;
    }

    public long getCreateTimeMillis() {
        return createTimeMillis;
    }

    public void setCreateTimeMillis(long createTimeMillis) {
        this.createTimeMillis = createTimeMillis;
    }

    public int getLastReadPage() {
        return lastReadPage;
    }

    /**
     * Gets the last read page number, returning a default value if the stored page is invalid (e.g., <= 0).
     * @param defaultValue The value to return if lastReadPage is not positive.
     * @return The last read page number or the default value.
     */
    public int getLastReadPageOrDefault(int defaultValue) {
        return this.lastReadPage > 0 ? this.lastReadPage : defaultValue;
    }

    public void setLastReadPage(int lastReadPage) {
        int originalValue = this.lastReadPage;
        if (lastReadPage > 0) { // 确保页码大于0
            this.lastReadPage = lastReadPage;
        } else {
            // 如果传入的页码无效（例如0或负数），则将其设置为1
            this.lastReadPage = 1;
            com.intellij.openapi.diagnostic.Logger.getInstance(Book.class).warn("[Book.setLastReadPage] Invalid page number passed: " + lastReadPage + ". Defaulting to 1. Original value was: " + originalValue);
        }
        if (originalValue != this.lastReadPage) {
             com.intellij.openapi.diagnostic.Logger.getInstance(Book.class).info("[Book.setLastReadPage] lastReadPage changed from " + originalValue + " to " + this.lastReadPage + " (input was " + lastReadPage + ")");
        }
    }

    /**
     * 获取书籍名称
     *
     * @return 书籍标题
     */
    public String getName() {
        return this.title;
    }

    /**
     * 获取书籍来源ID
     *
     * @return 书籍来源ID
     */
    public String getSourceId() {
        if (sourceId != null && !sourceId.isEmpty()) {
            return sourceId;
        }

        if (url == null || url.isEmpty()) {
            return "";
        }

        // 从URL中提取源ID
        // 假设URL格式为：http://example.com/book/123
        // 我们提取域名作为源ID
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost();
        } catch (Exception e) {
            // 如果URL解析失败，返回空字符串
            return "";
        }
    }

    /**
     * 设置书籍来源ID
     *
     * @param sourceId 书籍来源ID
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
}