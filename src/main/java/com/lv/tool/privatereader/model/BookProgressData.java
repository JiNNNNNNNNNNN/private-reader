package com.lv.tool.privatereader.model;

import org.jetbrains.annotations.Nullable;

/**
 * Represents the reading progress data retrieved from or saved to the persistent store (e.g., SQLite).
 *
 * @param bookId               The unique identifier of the book.
 * @param lastReadChapterId    The ID (e.g., URL) of the last read chapter. Can be null if no progress.
 * @param lastReadChapterTitle The title of the last read chapter. Can be null.
 * @param lastReadPosition     The scroll position within the last read chapter.
 * @param lastReadPage         The page number within the last read chapter (if applicable).
 * @param isFinished           Whether the book is marked as finished.
 * @param lastReadTimestamp    The timestamp (epoch millis) when the progress was last updated.
 */
public record BookProgressData(
        String bookId,
        @Nullable String lastReadChapterId,
        @Nullable String lastReadChapterTitle,
        int lastReadPosition,
        int lastReadPage,
        boolean isFinished, // Mapped from INTEGER (0/1) in DB
        long lastReadTimestamp // Mapped from INTEGER in DB
) {
    // No additional methods needed for a simple data carrier record
} 