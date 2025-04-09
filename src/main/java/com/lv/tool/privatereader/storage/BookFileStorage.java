package com.lv.tool.privatereader.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.model.Book;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BookFileStorage {
    private static final Logger LOG = Logger.getInstance(BookFileStorage.class);
    private static final String BOOKS_FILE = "books.json";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableJdkUnsafe()
        .create();

    public static File getStorageFile() {
        File configDir = new File(PathManager.getConfigPath(), "PrivateReader");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, BOOKS_FILE);
    }

    public static synchronized void saveBook(Book book) throws IOException {
        File storageFile = getStorageFile();
        List<Book> books = loadBooks();
        
        // 更新或添加书籍
        boolean found = false;
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).getUrl().equals(book.getUrl())) {
                books.set(i, book);
                found = true;
                break;
            }
        }
        if (!found) {
            books.add(book);
        }
        
        // 保存到文件
        String json = GSON.toJson(books);
        FileUtils.writeStringToFile(storageFile, json, StandardCharsets.UTF_8);
    }

    public static synchronized List<Book> loadBooks() throws IOException {
        File storageFile = getStorageFile();
        if (!storageFile.exists()) {
            return new ArrayList<>();
        }
        
        String json = FileUtils.readFileToString(storageFile, StandardCharsets.UTF_8);
        Book[] books = GSON.fromJson(json, Book[].class);
        return books != null ? new ArrayList<>(List.of(books)) : new ArrayList<>();
    }

    public static synchronized void deleteBook(Book book) throws IOException {
        File storageFile = getStorageFile();
        List<Book> books = loadBooks();
        
        books.removeIf(b -> b.getUrl().equals(book.getUrl()));
        
        String json = GSON.toJson(books);
        FileUtils.writeStringToFile(storageFile, json, StandardCharsets.UTF_8);
    }
    
    /**
     * 清理项目中旧的配置文件
     * 检查并删除.idea目录下的private-reader-books.xml文件
     * 
     * @param projectBasePath 项目根目录路径
     * @return 是否成功清理
     */
    public static boolean cleanLegacyProjectFile(String projectBasePath) {
        if (projectBasePath == null || projectBasePath.isEmpty()) {
            LOG.warn("项目路径为空，无法清理旧配置文件");
            return false;
        }
        
        try {
            File ideaDir = new File(projectBasePath, ".idea");
            if (!ideaDir.exists() || !ideaDir.isDirectory()) {
                LOG.info("项目中不存在.idea目录，无需清理");
                return true;
            }
            
            File legacyFile = new File(ideaDir, "private-reader-books.xml");
            if (legacyFile.exists() && legacyFile.isFile()) {
                LOG.info("发现旧的配置文件: " + legacyFile.getAbsolutePath());
                boolean deleted = legacyFile.delete();
                if (deleted) {
                    LOG.info("成功删除旧的配置文件: " + legacyFile.getAbsolutePath());
                    return true;
                } else {
                    LOG.warn("无法删除旧的配置文件: " + legacyFile.getAbsolutePath());
                    return false;
                }
            } else {
                LOG.info("未发现旧的配置文件，无需清理");
                return true;
            }
        } catch (Exception e) {
            LOG.error("清理旧配置文件时发生异常", e);
            return false;
        }
    }
} 