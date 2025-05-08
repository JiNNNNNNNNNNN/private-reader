package com.lv.tool.privatereader.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 安全的HTTP请求执行器
 * 用于执行HTTP请求，避免在ForkJoinPool中执行时出现SecurityException
 * 添加了重试机制和更详细的日志
 */
public class SafeHttpRequestExecutor {
    private static final Logger LOG = Logger.getInstance(SafeHttpRequestExecutor.class);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_RETRY_DELAY_MS = 1000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 15000;
    private static final int DEFAULT_READ_TIMEOUT = 15000;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /**
     * 安全地执行HTTP请求，带有重试机制
     *
     * @param url 请求的URL
     * @return HTTP响应内容
     * @throws IOException 如果请求失败或被中断
     */
    public static String executeGetRequest(final String url) throws IOException {
        return executeGetRequest(url, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * 安全地执行HTTP请求，带有重试机制和自定义重试参数
     *
     * @param url 请求的URL
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @return HTTP响应内容
     * @throws IOException 如果请求失败或被中断
     */
    public static String executeGetRequest(final String url, final int maxRetries, final int retryDelayMs) throws IOException {
        LOG.info("安全执行HTTP请求: " + url + "，最大重试次数: " + maxRetries);

        IOException lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                LOG.info("第 " + attempt + " 次重试请求: " + url);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("重试等待被中断", ie);
                }
            }

            try {
                return executeHttpRequest(url);
            } catch (IOException e) {
                lastException = e;

                // 根据异常类型决定是否继续重试
                if (e instanceof UnknownHostException) {
                    LOG.warn("无法解析主机名，可能是网络连接问题: " + url, e);
                } else if (e instanceof SocketTimeoutException) {
                    LOG.warn("请求超时，将重试: " + url, e);
                } else {
                    LOG.warn("请求失败，将重试: " + url + ", 错误: " + e.getMessage(), e);
                }

                // 如果已经达到最大重试次数，抛出最后一个异常
                if (attempt == maxRetries) {
                    LOG.error("达到最大重试次数 (" + maxRetries + ")，请求失败: " + url, e);
                    throw new IOException("请求失败，已重试 " + maxRetries + " 次: " + e.getMessage(), e);
                }
            }
        }

        // 这里不应该到达，但为了编译器满意
        throw lastException != null ? lastException : new IOException("未知错误");
    }

    /**
     * 执行单个HTTP请求
     *
     * @param url 请求的URL
     * @return HTTP响应内容
     * @throws IOException 如果请求失败或被中断
     */
    private static String executeHttpRequest(final String url) throws IOException {
        LOG.debug("执行HTTP请求: " + url);
        Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(
            () -> HttpRequests.request(url)
                    .userAgent(DEFAULT_USER_AGENT)
                    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                    .readTimeout(DEFAULT_READ_TIMEOUT)
                    .forceHttps(false)
                    .connect(request -> {
                        LOG.debug("连接成功，开始读取内容: " + url);
                        String content = request.readString();
                        LOG.debug("内容读取完成，长度: " + (content != null ? content.length() : 0) + " 字节: " + url);
                        return content;
                    })
        );

        try {
            String result = future.get();
            LOG.debug("HTTP请求成功完成: " + url);
            return result;
        } catch (InterruptedException e) {
            LOG.warn("执行HTTP请求时线程被中断: " + url, e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new IOException("HTTP请求被中断: " + url, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            LOG.error("执行HTTP请求时发生错误: " + url + " 原因: " + (cause != null ? cause.getMessage() : "null"), e);

            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof Exception) {
                throw new IOException("执行HTTP请求时发生内部错误: " + cause.getMessage(), cause);
            } else {
                throw new IOException("执行HTTP请求时发生未知错误", e);
            }
        } catch (Exception e) {
            LOG.error("获取HTTP请求结果时发生意外错误: " + url, e);
            throw new IOException("获取HTTP请求结果时发生意外错误: " + e.getMessage(), e);
        }
    }

    /**
     * 安全地执行HTTP请求，带有自定义配置
     *
     * @param url 请求的URL
     * @param configurator 请求配置器，可以设置超时、头信息等
     * @return HTTP响应内容
     * @throws IOException 如果请求失败或被中断
     */
    public static String executeGetRequest(final String url, final RequestConfigurator configurator) throws IOException {
        LOG.info("安全执行HTTP请求(带配置): " + url);
        Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(
            () -> {
                com.intellij.util.io.RequestBuilder builder = HttpRequests.request(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .connectTimeout(15000)
                        .readTimeout(15000)
                        .forceHttps(false);

                if (configurator != null) {
                    configurator.configure(builder);
                }

                return builder.connect(request -> request.readString());
            }
        );
        try {
            return future.get();
        } catch (InterruptedException e) {
            LOG.warn("执行HTTP请求(带配置)时线程被中断: " + url, e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new IOException("HTTP request interrupted (with config): " + url, e);
        } catch (ExecutionException e) {
             Throwable cause = e.getCause();
             LOG.error("执行HTTP请求(带配置)时发生错误: " + url + " Cause: " + (cause != null ? cause.getMessage() : "null"), e);
             if (cause instanceof IOException) {
                 throw (IOException) cause;
             } else if (cause instanceof Exception) {
                  throw new IOException("执行HTTP请求(带配置)时发生内部错误: " + cause.getMessage(), cause);
             } else {
                  throw new IOException("执行HTTP请求(带配置)时发生未知错误 (ExecutionException)", e);
             }
        } catch (Exception e) {
             LOG.error("获取HTTP请求(带配置)结果时发生意外错误: " + url, e);
             throw new IOException("获取HTTP请求(带配置)结果时发生意外错误: " + e.getMessage(), e);
        }
    }

    /**
     * HTTP请求配置器接口
     */
    public interface RequestConfigurator {
        /**
         * 配置HTTP请求
         *
         * @param builder HTTP请求构建器
         */
        void configure(com.intellij.util.io.RequestBuilder builder);
    }
}