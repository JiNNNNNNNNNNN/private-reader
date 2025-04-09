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
    
    @Override
    protected void copyFrom(NotificationReaderSettings source) {
        this.autoRead = source.autoRead;
        this.readIntervalSeconds = source.readIntervalSeconds;
        this.showUnreadCount = source.showUnreadCount;
        this.markAsReadOnClose = source.markAsReadOnClose;
        this.pageSize = source.pageSize;
        this.showPageNumber = source.showPageNumber;
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
} 