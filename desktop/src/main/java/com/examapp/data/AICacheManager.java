package com.examapp.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

/**
 * AI响应缓存管理器。
 * 将AI的响应缓存到本地文件，以减少不必要的API调用。
 */
public class AICacheManager {
    private static AICacheManager instance;
    private final Path cacheDir;

    private AICacheManager() {
        // 使用QuestionManager来获取统一的数据目录
        Path dataDir = QuestionManager.getInstance().getDataDirectory();
        this.cacheDir = dataDir.resolve("cache");
        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                System.out.println("创建AI缓存目录: " + cacheDir);
            }
        } catch (IOException e) {
            System.err.println("无法创建AI缓存目录: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized AICacheManager getInstance() {
        if (instance == null) {
            instance = new AICacheManager();
        }
        return instance;
    }

    /**
     * 根据给定的prompt生成一个安全的缓存键（文件名）。
     * @param prompt 用于生成AI响应的原始文本。
     * @return SHA-256哈希字符串。
     */
    public String generateKey(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // 理论上不应该发生
            e.printStackTrace();
            // 备用方案：使用Java内置的hashCode，虽然可能碰撞
            return String.valueOf(prompt.hashCode());
        }
    }

    /**
     * 从缓存中获取响应。
     * @param key 通过 generateKey 生成的缓存键。
     * @return 缓存的响应内容，如果不存在则返回 null。
     */
    public String getCache(String key) {
        Path cacheFile = cacheDir.resolve(key + ".cache");
        if (Files.exists(cacheFile)) {
            try {
                System.out.println("命中AI缓存: " + key);
                return new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("读取AI缓存失败: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 将响应保存到缓存。
     * @param key 通过 generateKey 生成的缓存键。
     * @param content 要缓存的AI响应内容。
     */
    public void saveCache(String key, String content) {
        Path cacheFile = cacheDir.resolve(key + ".cache");
        try {
            Files.write(cacheFile, content.getBytes(StandardCharsets.UTF_8));
            System.out.println("已保存AI缓存: " + key);
        } catch (IOException e) {
            System.err.println("保存AI缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清除所有AI缓存
     */
    public void clearCache() {
        try {
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                     .filter(path -> path.toString().endsWith(".cache"))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                             System.out.println("已删除缓存文件: " + path.getFileName());
                         } catch (IOException e) {
                             System.err.println("删除缓存文件失败: " + path + " - " + e.getMessage());
                         }
                     });
                System.out.println("AI缓存已清除");
            }
        } catch (IOException e) {
            System.err.println("清除AI缓存失败: " + e.getMessage());
        }
    }

    /**
     * 将字节数组转换为十六进制字符串。
     */
    private static String bytesToHex(byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
}