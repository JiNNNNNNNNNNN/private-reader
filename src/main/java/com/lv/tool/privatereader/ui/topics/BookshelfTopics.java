package com.lv.tool.privatereader.ui.topics;

import com.intellij.util.messages.Topic;
import com.lv.tool.privatereader.model.Book;

public interface BookshelfTopics {
    Topic<BookUpdateListener> BOOK_UPDATED = Topic.create("book_updated", BookUpdateListener.class);

    interface BookUpdateListener {
        void bookUpdated(Book book);
    }
} 