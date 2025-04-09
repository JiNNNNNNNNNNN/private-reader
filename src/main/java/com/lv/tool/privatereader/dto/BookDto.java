package com.lv.tool.privatereader.dto;

import com.intellij.openapi.project.Project;
import com.lv.tool.privatereader.model.Book;

/**
 * 书籍数据传输对象
 * 用于在不同层之间传递书籍数据
 */
public class BookDto {
    private String id;
    private String name;
    private String author;
    private String url;
    private Project project;
    
    /**
     * 从Book实体创建BookDto
     * @param book Book实体
     * @return BookDto对象
     */
    public static BookDto fromBook(Book book) {
        if (book == null) {
            return null;
        }
        
        BookDto dto = new BookDto();
        dto.id = book.getId();
        dto.name = book.getTitle();
        dto.author = book.getAuthor();
        dto.url = book.getUrl();
        dto.project = book.getProject();
        return dto;
    }
    
    /**
     * 获取书籍ID
     * @return 书籍ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 设置书籍ID
     * @param id 书籍ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * 获取书籍名称
     * @return 书籍名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 设置书籍名称
     * @param name 书籍名称
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * 获取作者
     * @return 作者
     */
    public String getAuthor() {
        return author;
    }
    
    /**
     * 设置作者
     * @param author 作者
     */
    public void setAuthor(String author) {
        this.author = author;
    }
    
    /**
     * 获取URL
     * @return URL
     */
    public String getUrl() {
        return url;
    }
    
    /**
     * 设置URL
     * @param url URL
     */
    public void setUrl(String url) {
        this.url = url;
    }
    
    /**
     * 获取项目
     * @return 项目
     */
    public Project getProject() {
        return project;
    }
    
    /**
     * 设置项目
     * @param project 项目
     */
    public void setProject(Project project) {
        this.project = project;
    }
} 