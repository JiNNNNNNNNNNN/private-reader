package com.lv.tool.privatereader.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 阅读模式设置
 * 用于持久化存储阅读模式（通知栏模式或阅读器模式）
 */
@State(
    name = "PrivateReaderModeSettings",
    storages = @Storage("private-reader-mode-settings.xml")
)
public class ReaderModeSettings implements PersistentStateComponent<ReaderModeSettings> {
    private boolean notificationMode = false; // 默认为阅读器模式

    public static final Topic<ReaderModeSettingsListener> TOPIC = 
            Topic.create("PrivateReaderModeSettings", ReaderModeSettingsListener.class);

    public boolean isNotificationMode() {
        return notificationMode;
    }

    public void setNotificationMode(boolean notificationMode) {
        this.notificationMode = notificationMode;
    }

    @Override
    public @Nullable ReaderModeSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ReaderModeSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 