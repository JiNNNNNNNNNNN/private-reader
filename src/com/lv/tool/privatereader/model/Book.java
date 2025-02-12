package com.lv.tool.privatereader.model;

import java.util.Objects;

/**
 * 书籍模型
 */
public final class Book {
    private final String title;
    private final String author;
    private final String url;
    private int lastChapter;

    public Book(String title, String author, String url) {
        this.title = title;
        this.author = author;
        this.url = url;
        this.lastChapter = 1;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getUrl() {
        return url;
    }

    public int getLastChapter() {
        return lastChapter;
    }

    public void setLastChapter(int lastChapter) {
        this.lastChapter = lastChapter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(url, book.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return title + " - " + author;
    }
} 