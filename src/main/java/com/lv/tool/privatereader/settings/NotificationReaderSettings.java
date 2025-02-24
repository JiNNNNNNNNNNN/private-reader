package com.lv.tool.privatereader.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
@State(
    name = "NotificationReaderSettings",
    storages = @Storage("private-reader-notification-settings.xml")
)
public final class NotificationReaderSettings implements PersistentStateComponent<NotificationReaderSettings> {
    private static final int DEFAULT_PAGE_SIZE = 80;
    
    private int pageSize = DEFAULT_PAGE_SIZE;
    private boolean showPageNumber = true; // 是否显示页码

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isShowPageNumber() {
        return showPageNumber;
    }

    public void setShowPageNumber(boolean showPageNumber) {
        this.showPageNumber = showPageNumber;
    }

    @Override
    public @Nullable NotificationReaderSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull NotificationReaderSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 