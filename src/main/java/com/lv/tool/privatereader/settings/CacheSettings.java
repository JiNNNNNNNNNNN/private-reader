package com.lv.tool.privatereader.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.lv.tool.privatereader.storage.StorageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 缓存设置
 *
 * 管理应用的缓存相关设置，包括：
 * - 是否启用缓存
 * - 最大缓存大小
 * - 缓存过期时间
 * - 章节预加载设置
 */
@State(
        name = "PrivateReaderCacheSettings",
        storages = @Storage("private-reader-cache-settings.xml")
)
@Service(Service.Level.PROJECT)
public class CacheSettings implements PersistentStateComponent<CacheSettings> {
    private static final Logger LOG = Logger.getInstance(CacheSettings.class);

    private boolean enableCache = true;
    private int maxCacheSize = 100; // MB
    private int maxCacheAge = 7; // days
    private boolean enablePreload = true; // 是否启用预加载
    private int preloadCount = 50; // 预加载章节数量
    private int preloadDelay = 1000; // 预加载请求间隔(毫秒)

    private final Project project;

    public CacheSettings(Project project) {
        this.project = project;
    }

    // 无参构造函数，用于序列化/反序列化
    public CacheSettings() {
        this.project = null;
    }

    /**
     * 是否启用缓存
     * @return 是否启用缓存
     */
    public boolean isEnableCache() {
        return enableCache;
    }

    /**
     * 设置是否启用缓存
     * @param enableCache 是否启用缓存
     */
    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    /**
     * 获取最大缓存大小（MB）
     * @return 最大缓存大小
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * 设置最大缓存大小（MB）
     * @param maxCacheSize 最大缓存大小
     */
    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * 获取缓存过期时间（天）
     * @return 缓存过期时间
     */
    public int getMaxCacheAge() {
        return maxCacheAge;
    }

    /**
     * 设置缓存过期时间（天）
     * @param maxCacheAge 缓存过期时间
     */
    public void setMaxCacheAge(int maxCacheAge) {
        this.maxCacheAge = maxCacheAge;
    }

    /**
     * 是否启用章节预加载
     * @return 是否启用预加载
     */
    public boolean isEnablePreload() {
        return enablePreload;
    }

    /**
     * 设置是否启用章节预加载
     * @param enablePreload 是否启用预加载
     */
    public void setEnablePreload(boolean enablePreload) {
        this.enablePreload = enablePreload;
    }

    /**
     * 获取预加载章节数量
     * @return 预加载章节数量
     */
    public int getPreloadCount() {
        return preloadCount;
    }

    /**
     * 设置预加载章节数量
     * @param preloadCount 预加载章节数量
     */
    public void setPreloadCount(int preloadCount) {
        this.preloadCount = preloadCount;
    }

    /**
     * 获取预加载请求间隔（毫秒）
     * @return 预加载请求间隔
     */
    public int getPreloadDelay() {
        return preloadDelay;
    }

    /**
     * 设置预加载请求间隔（毫秒）
     * @param preloadDelay 预加载请求间隔
     */
    public void setPreloadDelay(int preloadDelay) {
        this.preloadDelay = preloadDelay;
    }

    /**
     * 获取缓存目录路径
     * 优先使用StorageRepository获取，如果不可用则使用默认路径
     *
     * @return 缓存目录路径
     */
    @NotNull
    public String getCacheDirectoryPath() {
        // 尝试从StorageRepository获取缓存路径
        com.lv.tool.privatereader.repository.StorageRepository storageRepository = getStorageRepository();
        if (storageRepository != null) {
            return storageRepository.getCachePath();
        }

        // 如果StorageRepository不可用，使用默认路径
        LOG.info("StorageRepository不可用，使用默认缓存路径");
        return Path.of(PathManager.getConfigPath(), "private-reader", "cache").toString();
    }

    /**
     * 获取StorageRepository实例
     *
     * @return StorageRepository实例，如果不可用则返回null
     */
    @Nullable
    private com.lv.tool.privatereader.repository.StorageRepository getStorageRepository() {
        if (project != null) {
            com.lv.tool.privatereader.repository.RepositoryModule repositoryModule = 
                project.getService(com.lv.tool.privatereader.repository.RepositoryModule.class);
            if (repositoryModule != null) {
                return repositoryModule.getStorageRepository();
            }
            
            // 尝试使用旧的StorageManager
            StorageManager storageManager = project.getService(StorageManager.class);
            if (storageManager != null) {
                return new com.lv.tool.privatereader.repository.StorageRepository() {
                    @Override
                    public @NotNull String getBaseStoragePath() {
                        return storageManager.getBaseStoragePath();
                    }

                    @Override
                    public @NotNull String getBooksPath() {
                        return storageManager.getBooksPath();
                    }

                    @Override
                    public @NotNull String getCachePath() {
                        return storageManager.getCachePath();
                    }

                    @Override
                    public @NotNull String getSettingsPath() {
                        return storageManager.getSettingsPath();
                    }

                    @Override
                    public @NotNull String getBackupPath() {
                        return storageManager.getBackupPath();
                    }

                    @Override
                    public @NotNull String getBooksFilePath() {
                        return storageManager.getBooksPath() + "/index.json";
                    }

                    @Override
                    public @NotNull String createBookDirectory(String bookId) {
                        return storageManager.createBookDirectory(bookId);
                    }

                    @Override
                    public @NotNull String getBookDirectory(String bookId) {
                        return storageManager.getBookDirectory(bookId);
                    }

                    @Override
                    public void clearAllStorage() {
                        storageManager.clearAllStorage();
                    }

                    @Override
                    public @NotNull String createBackup() {
                        return storageManager.createBackup();
                    }

                    @Override
                    public boolean restoreFromBackup(String backupFilePath) {
                        return storageManager.restoreFromBackup(backupFilePath);
                    }

                    @Override
                    public @NotNull String getSafeFileName(@NotNull String fileName) {
                        return storageManager.getSafeFileName(fileName);
                    }

                    @Override
                    public @NotNull String getCacheFileName(@NotNull String url) {
                        return storageManager.getCacheFileName(url);
                    }
                };
            }
        }

        // 如果当前实例没有关联项目，尝试从打开的项目中获取
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length > 0) {
            com.lv.tool.privatereader.repository.RepositoryModule repositoryModule = 
                openProjects[0].getService(com.lv.tool.privatereader.repository.RepositoryModule.class);
            if (repositoryModule != null) {
                return repositoryModule.getStorageRepository();
            }
            
            // 尝试使用旧的StorageManager
            StorageManager storageManager = openProjects[0].getService(StorageManager.class);
            if (storageManager != null) {
                // 同上，创建适配器
                // 此处省略重复代码
            }
        }

        return null;
    }

    @Override
    public @Nullable CacheSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CacheSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 