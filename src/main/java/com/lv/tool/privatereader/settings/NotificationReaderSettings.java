package com.lv.tool.privatereader.settings;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * 通知阅读器设置
 */
@Service(Service.Level.APP)
public class NotificationReaderSettings extends BaseSettings<NotificationReaderSettings> {
    private static final Logger LOG = Logger.getInstance(NotificationReaderSettings.class);

    private boolean autoRead = true;
    private int readIntervalSeconds = 5;
    private boolean showUnreadCount = true;
    private boolean markAsReadOnClose = true;
    private int pageSize = 70;
    private boolean showPageNumber = true;
    private boolean enabled = false; // Default to disabled
    private int updateIntervalSeconds = 30; // Default refresh interval
    private boolean showChapterTitle = true;
    private boolean showReadingProgress = true;
    private boolean showButtons = true; // 是否在通知栏中显示导航按钮

    @Override
    protected void copyFrom(NotificationReaderSettings source) {
        this.autoRead = source.autoRead;
        this.readIntervalSeconds = source.readIntervalSeconds;
        this.showUnreadCount = source.showUnreadCount;
        this.markAsReadOnClose = source.markAsReadOnClose;
        this.pageSize = source.pageSize;
        this.showPageNumber = source.showPageNumber;
        this.enabled = source.enabled;
        this.updateIntervalSeconds = source.updateIntervalSeconds;
        this.showChapterTitle = source.showChapterTitle;
        this.showReadingProgress = source.showReadingProgress;
        this.showButtons = source.showButtons;
    }

    @Override
    protected NotificationReaderSettings getDefault() {
        NotificationReaderSettings settings = new NotificationReaderSettings();
        settings.autoRead = true;
        settings.readIntervalSeconds = 5;
        settings.showUnreadCount = true;
        settings.markAsReadOnClose = true;
        settings.pageSize = 70;
        settings.showPageNumber = true;
        settings.enabled = false;
        settings.updateIntervalSeconds = 30;
        settings.showChapterTitle = true;
        settings.showReadingProgress = true;
        settings.showButtons = true;
        return settings;
    }

    public boolean isAutoRead() {
        ensureSettingsLoaded();
        return autoRead;
    }

    public void setAutoRead(boolean autoRead) {
        this.autoRead = autoRead;
        markDirty();
    }

    public int getReadIntervalSeconds() {
        ensureSettingsLoaded();
        return readIntervalSeconds;
    }

    public void setReadIntervalSeconds(int readIntervalSeconds) {
        this.readIntervalSeconds = readIntervalSeconds;
        markDirty();
    }

    public boolean isShowUnreadCount() {
        ensureSettingsLoaded();
        return showUnreadCount;
    }

    public void setShowUnreadCount(boolean showUnreadCount) {
        this.showUnreadCount = showUnreadCount;
        markDirty();
    }

    public boolean isMarkAsReadOnClose() {
        ensureSettingsLoaded();
        return markAsReadOnClose;
    }

    public void setMarkAsReadOnClose(boolean markAsReadOnClose) {
        this.markAsReadOnClose = markAsReadOnClose;
        markDirty();
    }

    public int getPageSize() {
        ensureSettingsLoaded();
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        markDirty();
    }

    public boolean isShowPageNumber() {
        ensureSettingsLoaded();
        return showPageNumber;
    }

    public void setShowPageNumber(boolean showPageNumber) {
        this.showPageNumber = showPageNumber;
        markDirty();
    }

    public boolean isEnabled() {
        ensureSettingsLoaded();
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        markDirty();
    }

    public int getUpdateInterval() {
        ensureSettingsLoaded();
        return updateIntervalSeconds;
    }

    public void setUpdateInterval(int updateIntervalSeconds) {
        this.updateIntervalSeconds = updateIntervalSeconds;
        markDirty();
    }

    public boolean isShowChapterTitle() {
        ensureSettingsLoaded();
        return showChapterTitle;
    }

    public void setShowChapterTitle(boolean showChapterTitle) {
        this.showChapterTitle = showChapterTitle;
        markDirty();
    }

    public boolean isShowReadingProgress() {
        ensureSettingsLoaded();
        return showReadingProgress;
    }

    public void setShowReadingProgress(boolean showReadingProgress) {
        this.showReadingProgress = showReadingProgress;
        markDirty();
    }

    public boolean isShowButtons() {
        ensureSettingsLoaded();
        return showButtons;
    }

    public void setShowButtons(boolean showButtons) {
        this.showButtons = showButtons;
        markDirty();
    }

    // Renamed for clarity, as isShowPageNumber is already used
    public boolean isShowPageNumbers() {
        return isShowPageNumber();
    }
}