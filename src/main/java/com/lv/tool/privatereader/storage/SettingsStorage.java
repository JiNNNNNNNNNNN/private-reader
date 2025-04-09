package com.lv.tool.privatereader.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.settings.BaseSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 设置存储服务
 */
@Service(Service.Level.APP)
public final class SettingsStorage {
    private static final Logger LOG = Logger.getInstance(SettingsStorage.class);
    private static final String SETTINGS_DIR = "private-reader";
    private static final String SETTINGS_EXT = ".json";
    
    private final Gson gson;
    
    public SettingsStorage() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }
    
    public static SettingsStorage getInstance() {
        return ApplicationManager.getApplication().getService(SettingsStorage.class);
    }
    
    public <T extends BaseSettings<T>> void saveSettings(T settings) {
        try {
            String json = gson.toJson(settings);
            writeFile(settings.getClass(), json);
        } catch (Exception e) {
            LOG.error("保存设置失败: " + settings.getClass().getSimpleName(), e);
        }
    }
    
    public <T extends BaseSettings<T>> T loadSettings(Class<T> settingsClass) {
        try {
            String json = readFile(settingsClass);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return gson.fromJson(json, settingsClass);
        } catch (Exception e) {
            LOG.error("加载设置失败: " + settingsClass.getSimpleName(), e);
            return null;
        }
    }
    
    private <T> String readFile(Class<T> settingsClass) throws IOException {
        Path filePath = getSettingsFilePath(settingsClass);
        if (!Files.exists(filePath)) {
            return null;
        }
        try (FileReader reader = new FileReader(filePath.toFile())) {
            char[] buffer = new char[1024];
            StringBuilder content = new StringBuilder();
            int length;
            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
            return content.toString();
        }
    }
    
    private <T> void writeFile(Class<T> settingsClass, String content) throws IOException {
        Path filePath = getSettingsFilePath(settingsClass);
        Path parentDir = filePath.getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(content);
        }
    }
    
    private <T> Path getSettingsFilePath(Class<T> settingsClass) {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".private-reader", "settings", settingsClass.getSimpleName() + SETTINGS_EXT);
    }
} 