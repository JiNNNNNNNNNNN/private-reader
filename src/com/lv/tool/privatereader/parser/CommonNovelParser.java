package com.lv.tool.privatereader.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用小说解析器，用于解析本地txt文件
 */
public final class CommonNovelParser implements com.lv.tool.privatereader.parser.NovelParser {
    private final String filePath;
    private String content;

    public CommonNovelParser(final String filePath) {
        this.filePath = filePath;
        try {
            this.content = Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + filePath, e);
        }
    }

    @Override
    public String getTitle() {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    @Override
    public String getAuthor() {
        return "未知作者";
    }

    @Override
    public List<Chapter> getChapterList() {
        List<Chapter> chapters = new ArrayList<>();
        String[] lines = content.split("\n");
        int chapterIndex = 1;
        
        for (String line : lines) {
            if (isChapterTitle(line)) {
                chapters.add(new Chapter(line.trim(), String.valueOf(chapterIndex++)));
            }
        }
        return chapters;
    }

    @Override
    public String getChapterContent(String chapterUrl) {
        int chapterIndex = Integer.parseInt(chapterUrl);
        List<Chapter> chapters = getChapterList();
        
        if (chapterIndex < 1 || chapterIndex > chapters.size()) {
            return "章节不存在";
        }

        String[] lines = content.split("\n");
        StringBuilder chapterContent = new StringBuilder();
        boolean isInChapter = false;
        
        for (String line : lines) {
            if (isChapterTitle(line)) {
                if (isInChapter) {
                    break;
                }
                if (line.trim().equals(chapters.get(chapterIndex - 1).title())) {
                    isInChapter = true;
                }
            } else if (isInChapter && !line.trim().isEmpty()) {
                chapterContent.append(line.trim()).append("\n");
            }
        }
        
        return chapterContent.toString();
    }

    private boolean isChapterTitle(String line) {
        line = line.trim();
        return line.matches("^第[0-9零一二三四五六七八九十百千万]+[章节卷集].*$") ||
               line.matches("^[0-9]+[、.].*$");
    }
} 