package com.lv.tool.privatereader.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;

import java.io.IOException;
import java.util.concurrent.Callable;
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
     * @throws IOException 如果请求失败
     */
    public static String executeGetRequest(final String url) throws IOException {
        LOG.info("安全执行HTTP请求: " + url);
        try {
            // 使用ApplicationManager的线程池执行，避免在ForkJoinPool中执行
            Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return HttpRequests.request(url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .connectTimeout(15000)
                            .readTimeout(15000)
                            .forceHttps(false)
                            .connect(request -> request.readString());
                    }
                }
            );
            return future.get();
        } catch (Exception e) {
            LOG.error("执行HTTP请求失败: " + e.getMessage(), e);
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("执行HTTP请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 安全地执行HTTP请求，带有自定义配置
     *
     * @param url 请求的URL
     * @param configurator 请求配置器，可以设置超时、头信息等
     * @return HTTP响应内容
     * @throws IOException 如果请求失败
     */
    public static String executeGetRequest(final String url, final RequestConfigurator configurator) throws IOException {
        LOG.info("安全执行HTTP请求(带配置): " + url);
        try {
            // 使用ApplicationManager的线程池执行，避免在ForkJoinPool中执行
            Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        com.intellij.util.io.RequestBuilder builder = HttpRequests.request(url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .connectTimeout(15000)
                            .readTimeout(15000)
                            .forceHttps(false);
                        
                        // 应用配置
                        if (configurator != null) {
                            configurator.configure(builder);
                        }
                        
                        return builder.connect(request -> request.readString());
                    }
                }
            );
            return future.get();
        } catch (Exception e) {
            LOG.error("执行HTTP请求失败: " + e.getMessage(), e);
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("执行HTTP请求失败: " + e.getMessage(), e);
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