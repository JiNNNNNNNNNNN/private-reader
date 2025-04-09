package com.lv.tool.privatereader.settings;

import com.google.gson.Gson;
import com.lv.tool.privatereader.settings.BaseSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsStorage {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsStorage.class);
    private Gson gson = new Gson();

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

    private <T extends BaseSettings<T>> String readFile(Class<T> settingsClass) {
        File file = new File(getSettingsFilePath(settingsClass));
        if (!file.exists()) {
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            return sb.toString();
        } catch (IOException e) {
            LOG.error("读取设置文件失败: " + settingsClass.getSimpleName(), e);
            return null;
        }
    }

    private <T extends BaseSettings<T>> String getSettingsFilePath(Class<T> settingsClass) {
        return System.getProperty("user.home") + "/.private-reader/" + settingsClass.getSimpleName() + ".json";
    }
} 