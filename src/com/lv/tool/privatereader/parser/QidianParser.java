package com.lv.tool.privatereader.parser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 起点中文网解析器
 */
public final class QidianParser implements NovelParser {
    private final String url;
    private final HttpClient client;
    private String pageContent;

    public QidianParser(final String url) {
        this.url = url;
        this.client = HttpClient.newHttpClient();
        fetchPage();
    }

    private void fetchPage() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .build();
            
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            this.pageContent = response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch page: " + url, e);
        }
    }

    @Override
    public String getTitle() {
        Pattern pattern = Pattern.compile("<h1 class=\"book-title\">(.*?)</h1>");
        Matcher matcher = pattern.matcher(pageContent);
        return matcher.find() ? matcher.group(1) : "未知标题";
    }

    @Override
    public String getAuthor() {
        Pattern pattern = Pattern.compile("<a class=\"writer-name\".*?>(.*?)</a>");
        Matcher matcher = pattern.matcher(pageContent);
        return matcher.find() ? matcher.group(1) : "未知作者";
    }

    @Override
    public List<Chapter> getChapterList() {
        List<Chapter> chapters = new ArrayList<>();
        Pattern pattern = Pattern.compile("<li.*?><a href=\"(.*?)\".*?>(.*?)</a></li>");
        Matcher matcher = pattern.matcher(pageContent);
        
        while (matcher.find()) {
            String chapterUrl = matcher.group(1);
            String title = matcher.group(2);
            if (!chapterUrl.startsWith("http")) {
                chapterUrl = "https://book.qidian.com" + chapterUrl;
            }
            chapters.add(new Chapter(title, chapterUrl));
        }
        
        return chapters;
    }

    @Override
    public String getChapterContent(String chapterUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chapterUrl))
                .header("User-Agent", "Mozilla/5.0")
                .build();
            
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            String content = response.body();
            Pattern pattern = Pattern.compile("<div class=\"read-content\">(.*?)</div>");
            Matcher matcher = pattern.matcher(content);
            
            return matcher.find() ? 
                matcher.group(1)
                    .replaceAll("<p>", "\n")
                    .replaceAll("</p>", "")
                    .replaceAll("<.*?>", "") 
                : "获取章节内容失败";
                
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch chapter: " + chapterUrl, e);
        }
    }
} 