package com.lv.tool.privatereader.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用网络小说解析器，用于解析网络小说网站
 */
public final class UniversalParser implements NovelParser {
    private final String url;
    private Document document;

    public UniversalParser(final String url) {
        this.url = url;
        try {
            this.document = Jsoup.connect(url).get();
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to URL: " + url, e);
        }
    }

    @Override
    public String getTitle() {
        Element titleElement = document.selectFirst("h1");
        return titleElement != null ? titleElement.text() : "未知标题";
    }

    @Override
    public String getAuthor() {
        Element authorElement = document.selectFirst("meta[name=author]");
        return authorElement != null ? authorElement.attr("content") : "未知作者";
    }

    @Override
    public List<Chapter> getChapterList() {
        List<Chapter> chapters = new ArrayList<>();
        Elements links = document.select("a[href]");
        
        for (Element link : links) {
            String href = link.attr("abs:href");
            String title = link.text();
            
            if (isChapterLink(href, title)) {
                chapters.add(new Chapter(title, href));
            }
        }
        
        return chapters;
    }

    @Override
    public String getChapterContent(String chapterUrl) {
        try {
            Document chapterDoc = Jsoup.connect(chapterUrl).get();
            Element content = chapterDoc.selectFirst("div.chapter-content");
            if (content != null) {
                return TextFormatter.format(content.text());
            }
            return "章节内容不存在";
        } catch (IOException e) {
            return "获取章节内容失败：" + e.getMessage();
        }
    }

    private boolean isChapterLink(String href, String title) {
        return href.contains("/chapter/") || 
               title.matches("^第[0-9零一二三四五六七八九十百千万]+[章节卷集].*$") ||
               title.matches("^[0-9]+[、.].*$");
    }
} 