package com.lv.tool.privatereader.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 安全的HTTP请求执行器
 * 用于执行HTTP请求，避免在ForkJoinPool中执行时出现SecurityException
 */
public class SafeHttpRequestExecutor {
    private static final Logger LOG = Logger.getInstance(SafeHttpRequestExecutor.class);

    /**
     * 安全地执行HTTP请求
     *
     * @param url 请求的URL
     * @return HTTP响应内容
     * @throws IOException 如果请求失败或被中断
     */
    public static String executeGetRequest(final String url) throws IOException {
        LOG.info("安全执行HTTP请求: " + url);
        Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(
            () -> HttpRequests.request(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .connectTimeout(15000)
                    .readTimeout(15000)
                    .forceHttps(false)
                    .connect(request -> request.readString())
        );
        try {
            return future.get();
        } catch (InterruptedException e) {
            LOG.warn("执行HTTP请求时线程被中断: " + url, e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            // 抛出 IOException 以符合方法签名，但消息表明是中断
            throw new IOException("HTTP request interrupted: " + url, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            LOG.error("执行HTTP请求时发生错误: " + url + " Cause: " + (cause != null ? cause.getMessage() : "null"), e);
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof Exception) {
                 // 包装原始异常信息
                 throw new IOException("执行HTTP请求时发生内部错误: " + cause.getMessage(), cause);
            } else {
                 // 如果 cause 不是 Exception，则包装 ExecutionException
                 throw new IOException("执行HTTP请求时发生未知错误 (ExecutionException)", e);
            }
        } catch (Exception e) {
            // 处理其他可能的、非预期的异常 (例如 CancellationException)
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