package com.lv.tool.privatereader.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

/**
 * 网络工具类
 */
public class NetworkUtils {
    private static final String[] CHECK_HOSTS = {
        "www.baidu.com",
        "www.qq.com",
        "www.aliyun.com"
    };
    private static final int TIMEOUT = 3000;

    /**
     * 检查网络是否可用
     */
    public static boolean isNetworkAvailable() {
        for (String host : CHECK_HOSTS) {
            if (isHostReachable(host)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查指定主机是否可达
     */
    public static boolean isHostReachable(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 80), TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查网络连接速度
     * @return 连接速度（毫秒），-1表示连接失败
     */
    public static long checkNetworkSpeed(String url) {
        try {
            long start = System.currentTimeMillis();
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.connect();
            return System.currentTimeMillis() - start;
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * 获取最佳可用主机
     * @return 最佳主机，如果都不可用则返回null
     */
    public static String getBestHost() {
        String bestHost = null;
        long bestSpeed = Long.MAX_VALUE;

        for (String host : CHECK_HOSTS) {
            long speed = checkNetworkSpeed("http://" + host);
            if (speed > 0 && speed < bestSpeed) {
                bestSpeed = speed;
                bestHost = host;
            }
        }

        return bestHost;
    }
} 