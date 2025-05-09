package com.lv.tool.privatereader.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.repository.StorageRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断工具类
 *
 * 用于检查和修复项目中的问题，特别是与存储相关的问题。
 * 提供了检查存储目录、检查JSON文件格式、修复损坏文件等功能。
 */
public class DiagnosticTool {
    private static final Logger LOG = Logger.getInstance(DiagnosticTool.class);
    private final StorageRepository storageRepository;
    private final Gson gson;

    /**
     * 诊断结果类
     */
    public static class DiagnosticResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
            LOG.error(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
            LOG.warn(warning);
        }

        public void addInfo(String info) {
            infos.add(info);
            LOG.debug(info);
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getInfos() {
            return infos;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasIssues() {
            return hasErrors() || hasWarnings();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (!errors.isEmpty()) {
                sb.append("错误:\n");
                for (String error : errors) {
                    sb.append("- ").append(error).append("\n");
                }
                sb.append("\n");
            }

            if (!warnings.isEmpty()) {
                sb.append("警告:\n");
                for (String warning : warnings) {
                    sb.append("- ").append(warning).append("\n");
                }
                sb.append("\n");
            }

            if (!infos.isEmpty()) {
                sb.append("信息:\n");
                for (String info : infos) {
                    sb.append("- ").append(info).append("\n");
                }
            }

            return sb.toString();
        }
    }

    /**
     * 构造函数
     *
     * @param storageRepository 存储仓库
     */
    public DiagnosticTool(@NotNull StorageRepository storageRepository) {
        this.storageRepository = storageRepository;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        LOG.debug("DiagnosticTool 初始化完成");
    }

    /**
     * 运行完整诊断
     *
     * @return 诊断结果
     */
    public DiagnosticResult runDiagnostic() {
        DiagnosticResult result = new DiagnosticResult();

        // 检查存储目录
        checkStorageDirectory(result);

        // 检查JSON文件
        checkJsonFiles(result);

        return result;
    }

    /**
     * 检查存储目录
     *
     * @param result 诊断结果
     */
    public void checkStorageDirectory(DiagnosticResult result) {
        LOG.debug("开始检查存储目录...");

        // 检查基础存储目录
        String baseStoragePath = storageRepository.getBaseStoragePath();
        File baseStorageDir = new File(baseStoragePath);

        if (!baseStorageDir.exists()) {
            result.addError("基础存储目录不存在: " + baseStoragePath);
            return;
        }

        if (!baseStorageDir.isDirectory()) {
            result.addError("基础存储路径不是目录: " + baseStoragePath);
            return;
        }

        if (!baseStorageDir.canRead() || !baseStorageDir.canWrite()) {
            result.addError("基础存储目录权限不足: " + baseStoragePath);
            return;
        }

        result.addInfo("基础存储目录正常: " + baseStoragePath);

        // 检查书籍存储目录
        String booksPath = storageRepository.getBooksPath();
        File booksDir = new File(booksPath);

        if (!booksDir.exists()) {
            result.addWarning("书籍存储目录不存在: " + booksPath);
            return;
        }

        if (!booksDir.isDirectory()) {
            result.addError("书籍存储路径不是目录: " + booksPath);
            return;
        }

        if (!booksDir.canRead() || !booksDir.canWrite()) {
            result.addError("书籍存储目录权限不足: " + booksPath);
            return;
        }

        result.addInfo("书籍存储目录正常: " + booksPath);

        // 检查书籍索引文件
        String booksFilePath = storageRepository.getBooksFilePath();
        File booksFile = new File(booksFilePath);

        if (!booksFile.exists()) {
            result.addWarning("书籍索引文件不存在: " + booksFilePath);
            return;
        }

        if (!booksFile.isFile()) {
            result.addError("书籍索引路径不是文件: " + booksFilePath);
            return;
        }

        if (!booksFile.canRead() || !booksFile.canWrite()) {
            result.addError("书籍索引文件权限不足: " + booksFilePath);
            return;
        }

        result.addInfo("书籍索引文件正常: " + booksFilePath);

        // 检查缓存目录
        String cachePath = storageRepository.getCachePath();
        File cacheDir = new File(cachePath);

        if (!cacheDir.exists()) {
            result.addWarning("缓存目录不存在: " + cachePath);
            return;
        }

        if (!cacheDir.isDirectory()) {
            result.addError("缓存路径不是目录: " + cachePath);
            return;
        }

        if (!cacheDir.canRead() || !cacheDir.canWrite()) {
            result.addError("缓存目录权限不足: " + cachePath);
            return;
        }

        result.addInfo("缓存目录正常: " + cachePath);

        LOG.debug("存储目录检查完成");
    }

    /**
     * 检查JSON文件
     *
     * @param result 诊断结果
     */
    public void checkJsonFiles(DiagnosticResult result) {
        LOG.debug("开始检查JSON文件...");

        // 检查书籍索引文件
        String booksFilePath = storageRepository.getBooksFilePath();
        File booksFile = new File(booksFilePath);

        if (booksFile.exists() && booksFile.isFile()) {
            try {
                String jsonContent = new String(Files.readAllBytes(booksFile.toPath()), StandardCharsets.UTF_8);
                validateJson(jsonContent, "书籍索引文件", result);
            } catch (IOException e) {
                result.addError("读取书籍索引文件失败: " + e.getMessage());
            }
        }

        // 检查书籍详情文件
        String booksPath = storageRepository.getBooksPath();
        File booksDir = new File(booksPath);

        if (booksDir.exists() && booksDir.isDirectory()) {
            File[] bookDirs = booksDir.listFiles(File::isDirectory);

            if (bookDirs != null) {
                for (File bookDir : bookDirs) {
                    File detailsFile = new File(bookDir, "details.json");

                    if (detailsFile.exists() && detailsFile.isFile()) {
                        try {
                            String jsonContent = new String(Files.readAllBytes(detailsFile.toPath()), StandardCharsets.UTF_8);
                            validateJson(jsonContent, "书籍详情文件 (" + bookDir.getName() + ")", result);
                        } catch (IOException e) {
                            result.addError("读取书籍详情文件失败 (" + bookDir.getName() + "): " + e.getMessage());
                        }
                    }
                }
            }
        }

        LOG.debug("JSON文件检查完成");
    }

    /**
     * 验证JSON格式
     *
     * @param jsonContent JSON内容
     * @param fileType 文件类型
     * @param result 诊断结果
     */
    private void validateJson(String jsonContent, String fileType, DiagnosticResult result) {
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonContent);

            if (jsonElement.isJsonNull()) {
                result.addWarning(fileType + " 内容为 null");
            } else if (jsonElement.isJsonArray() && jsonElement.getAsJsonArray().size() == 0) {
                result.addInfo(fileType + " 是空数组");
            } else if (jsonElement.isJsonObject() && jsonElement.getAsJsonObject().size() == 0) {
                result.addWarning(fileType + " 是空对象");
            } else {
                result.addInfo(fileType + " 格式正确");
            }
        } catch (JsonSyntaxException e) {
            result.addError(fileType + " 格式错误: " + e.getMessage());
        }
    }

    /**
     * 修复损坏的JSON文件
     *
     * @param file 文件
     * @param result 诊断结果
     * @return 是否修复成功
     */
    public boolean repairCorruptedJsonFile(File file, DiagnosticResult result) {
        LOG.debug("开始修复损坏的JSON文件: " + file.getAbsolutePath());

        if (!file.exists() || !file.isFile()) {
            result.addError("文件不存在或不是文件: " + file.getAbsolutePath());
            return false;
        }

        try {
            // 读取文件内容
            String jsonContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            // 尝试解析JSON
            try {
                JsonElement jsonElement = JsonParser.parseString(jsonContent);
                result.addInfo("文件格式正确，无需修复: " + file.getAbsolutePath());
                return true;
            } catch (JsonSyntaxException e) {
                // JSON格式错误，尝试修复
                result.addWarning("文件格式错误，尝试修复: " + file.getAbsolutePath());

                // 创建备份
                File backupFile = new File(file.getParentFile(), file.getName() + ".bak." + System.currentTimeMillis());
                Files.copy(file.toPath(), backupFile.toPath());
                result.addInfo("已创建备份: " + backupFile.getAbsolutePath());

                // 根据文件类型进行修复
                if (file.getName().equals("index.json")) {
                    // 修复索引文件
                    Files.write(file.toPath(), "[]".getBytes(StandardCharsets.UTF_8));
                    result.addInfo("已重置索引文件为空数组");
                    return true;
                } else if (file.getName().equals("details.json")) {
                    // 修复详情文件
                    String bookId = file.getParentFile().getName();
                    String minimalJson = "{\n" +
                            "  \"id\": \"" + bookId + "\",\n" +
                            "  \"title\": \"恢复的书籍 " + bookId + "\",\n" +
                            "  \"author\": \"未知作者\",\n" +
                            "  \"url\": null,\n" +
                            "  \"sourceId\": null,\n" +
                            "  \"createTimeMillis\": " + System.currentTimeMillis() + ",\n" +
                            "  \"lastChapter\": null,\n" +
                            "  \"totalChapters\": 0,\n" +
                            "  \"cachedChapters\": []\n" +
                            "}";
                    Files.write(file.toPath(), minimalJson.getBytes(StandardCharsets.UTF_8));
                    result.addInfo("已重置详情文件为最小化书籍对象");
                    return true;
                } else {
                    result.addError("未知文件类型，无法修复: " + file.getName());
                    return false;
                }
            }
        } catch (IOException e) {
            result.addError("修复文件时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查并修复所有损坏的JSON文件
     *
     * @param result 诊断结果
     * @return 修复的文件数量
     */
    public int repairAllCorruptedJsonFiles(DiagnosticResult result) {
        LOG.debug("开始检查并修复所有损坏的JSON文件...");

        int repairedCount = 0;

        // 修复书籍索引文件
        String booksFilePath = storageRepository.getBooksFilePath();
        File booksFile = new File(booksFilePath);

        if (booksFile.exists() && booksFile.isFile()) {
            try {
                String jsonContent = new String(Files.readAllBytes(booksFile.toPath()), StandardCharsets.UTF_8);
                try {
                    JsonParser.parseString(jsonContent);
                } catch (JsonSyntaxException e) {
                    if (repairCorruptedJsonFile(booksFile, result)) {
                        repairedCount++;
                    }
                }
            } catch (IOException e) {
                result.addError("读取书籍索引文件失败: " + e.getMessage());
            }
        }

        // 修复书籍详情文件
        String booksPath = storageRepository.getBooksPath();
        File booksDir = new File(booksPath);

        if (booksDir.exists() && booksDir.isDirectory()) {
            File[] bookDirs = booksDir.listFiles(File::isDirectory);

            if (bookDirs != null) {
                for (File bookDir : bookDirs) {
                    File detailsFile = new File(bookDir, "details.json");

                    if (detailsFile.exists() && detailsFile.isFile()) {
                        try {
                            String jsonContent = new String(Files.readAllBytes(detailsFile.toPath()), StandardCharsets.UTF_8);
                            try {
                                JsonParser.parseString(jsonContent);
                            } catch (JsonSyntaxException e) {
                                if (repairCorruptedJsonFile(detailsFile, result)) {
                                    repairedCount++;
                                }
                            }
                        } catch (IOException e) {
                            result.addError("读取书籍详情文件失败 (" + bookDir.getName() + "): " + e.getMessage());
                        }
                    }
                }
            }
        }

        LOG.debug("修复完成，共修复 " + repairedCount + " 个文件");
        return repairedCount;
    }
}
