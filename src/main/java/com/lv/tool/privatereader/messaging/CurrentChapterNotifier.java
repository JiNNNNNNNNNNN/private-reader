package com.lv.tool.privatereader.messaging;

import com.intellij.util.messages.Topic;
import com.lv.tool.privatereader.model.Book;
import com.lv.tool.privatereader.parser.NovelParser; // 确保 NovelParser 被导入以使用其内部类 Chapter

public interface CurrentChapterNotifier {
    Topic<CurrentChapterNotifier> TOPIC = Topic.create("PrivateReader.CurrentChapterChanged", CurrentChapterNotifier.class);

    /**
     * 当阅读器当前章节发生变化时调用
     * @param book 当前阅读的书籍
     * @param newChapter 新的当前章节 (确保使用 NovelParser.Chapter)
     */
    void currentChapterChanged(Book book, NovelParser.Chapter newChapter);
} 