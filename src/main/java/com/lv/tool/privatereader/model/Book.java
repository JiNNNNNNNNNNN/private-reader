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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.lv.tool.privatereader.parser.ParserFactory;

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
    @Tag private String id;
    /** 书籍标题 */
    @Tag private String title;
    /** 作者名称 */
    @Tag private String author;
    /** 书籍来源URL */
    @Tag private String url;
    /** 创建时间 */
    @Tag private long createTimeMillis;
    /** 最新章节标题 */
    @Tag private String lastChapter;
    /** 上次阅读的章节标题 */
    @Tag private String lastReadChapter;
    /** 上次阅读的章节ID */
    @Tag private String lastReadChapterId;
    /** 上次阅读位置（字符偏移量） */
    @Tag private int lastReadPosition;
    /** 上次阅读时间戳 */
    @Tag private long lastReadTimeMillis;
    /** 总章节数 */
    @Tag private int totalChapters;
    /** 当前阅读的章节索引 */
    @Tag private int currentChapterIndex;
    /** 是否已读完 */
    @Tag private boolean finished;
    /** 缓存的章节列表 */
    @Tag private List<Chapter> cachedChapters;
    /** 关联的项目实例 */
    @Transient private Project project;
    /** 书籍内容解析器 */
    @Transient private NovelParser parser;

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

    public void updateReadingProgress(String chapterId, String chapterTitle, int position) {
        this.lastReadChapterId = chapterId;
        this.lastReadChapter = chapterTitle;
        this.lastReadPosition = position;
        this.lastReadTimeMillis = System.currentTimeMillis();
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
            parser = ParserFactory.createParser(url);
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
} 