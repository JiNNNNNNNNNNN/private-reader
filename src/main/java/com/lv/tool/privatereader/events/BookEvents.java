package com.lv.tool.privatereader.events;

import com.intellij.util.messages.Topic;
import java.util.EventListener;

/**
 * Contains events related to book data.
 */
public class BookEvents {

    /**
     * Listener for book data loading events.
     */
    public interface BookDataListener extends EventListener {
        /**
         * Topic for BookDataListener events.
         */
        Topic<BookDataListener> BOOK_DATA_TOPIC = Topic.create("Book Data Loaded", BookDataListener.class);

        /**
         * Fired when initial book data (e.g., from storage) has been loaded.
         */
        void bookDataLoaded();
    }

    // Add other book-related events or listeners here if needed
} 