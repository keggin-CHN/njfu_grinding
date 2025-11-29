package com.examapp.data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 设置管理器 - 负责应用设置的读写
 */
public class SettingsManager {
    private static SettingsManager instance;
    private Properties properties;
    private Path settingsFile;

    private static final String DATA_DIR_NAME = ".njfu_grinding";
    private static final String SETTINGS_FILE_NAME = "settings.properties";

    // 设置键
    private static final String KEY_DEVELOPER_MODE = "developer_mode";
    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_AUTO_SAVE = "auto_save";
    private static final String KEY_SHOW_EXPLANATION = "show_explanation";
    private static final String KEY_CUSTOM_CSS = "custom_css";

    private SettingsManager() {
        this.properties = new Properties();
        initializeSettingsFile();
        loadSettings();
    }

    public static synchronized SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    /**
     * 初始化设置文件
     */
    private void initializeSettingsFile() {
        String userHome = System.getProperty("user.home");
        Path dataDir = Paths.get(userHome, DATA_DIR_NAME);
        settingsFile = dataDir.resolve(SETTINGS_FILE_NAME);

        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            if (!Files.exists(settingsFile)) {
                Files.createFile(settingsFile);
                setDefaultSettings();
            }
        } catch (IOException e) {
            System.err.println("无法创建设置文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 设置默认值
     */
    private void setDefaultSettings() {
        properties.setProperty(KEY_DEVELOPER_MODE, "false");
        properties.setProperty(KEY_THEME, "light");
        properties.setProperty(KEY_FONT_SIZE, "14");
        properties.setProperty(KEY_AUTO_SAVE, "true");
        properties.setProperty(KEY_SHOW_EXPLANATION, "true");
        saveSettings();
    }

    /**
     * 加载设置
     */
    private void loadSettings() {
        try (InputStream input = new FileInputStream(settingsFile.toFile())) {
            properties.load(input);
            System.out.println("设置已加载");
        } catch (IOException e) {
            System.err.println("加载设置失败: " + e.getMessage());
            setDefaultSettings();
        }
    }

    /**
     * 保存设置
     */
    private void saveSettings() {
        try (OutputStream output = new FileOutputStream(settingsFile.toFile())) {
            properties.store(output, "NJFU刷题助手设置");
            System.out.println("设置已保存");
        } catch (IOException e) {
            System.err.println("保存设置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 开发者模式
    public boolean isDeveloperMode() {
        return Boolean.parseBoolean(properties.getProperty(KEY_DEVELOPER_MODE, "false"));
    }

    public void setDeveloperMode(boolean enabled) {
        properties.setProperty(KEY_DEVELOPER_MODE, String.valueOf(enabled));
        saveSettings();
    }

    // 主题
    public String getTheme() {
        return properties.getProperty(KEY_THEME, "light");
    }

    public void setTheme(String theme) {
        properties.setProperty(KEY_THEME, theme);
        saveSettings();
    }

    // 字体大小
    public int getFontSize() {
        return Integer.parseInt(properties.getProperty(KEY_FONT_SIZE, "14"));
    }

    public void setFontSize(int size) {
        properties.setProperty(KEY_FONT_SIZE, String.valueOf(size));
        saveSettings();
    }

    // 自动保存
    public boolean isAutoSave() {
        return Boolean.parseBoolean(properties.getProperty(KEY_AUTO_SAVE, "true"));
    }

    public void setAutoSave(boolean enabled) {
        properties.setProperty(KEY_AUTO_SAVE, String.valueOf(enabled));
        saveSettings();
    }

    // 显示解析
    public boolean isShowExplanation() {
        return Boolean.parseBoolean(properties.getProperty(KEY_SHOW_EXPLANATION, "true"));
    }

    public void setShowExplanation(boolean show) {
        properties.setProperty(KEY_SHOW_EXPLANATION, String.valueOf(show));
        saveSettings();
    }

    /**
     * 获取自定义设置
     */
    public String getSetting(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * 设置自定义设置
     */
    public void setSetting(String key, String value) {
        properties.setProperty(key, value);
        saveSettings();
    }

    // 自定义CSS
    public String getCustomCss() {
        return properties.getProperty(KEY_CUSTOM_CSS, "");
    }

    public void setCustomCss(String css) {
        properties.setProperty(KEY_CUSTOM_CSS, css != null ? css : "");
        saveSettings();
    }

    /**
     * 重置开发者设置
     */
    public void resetDeveloperSettings() {
        properties.setProperty(KEY_DEVELOPER_MODE, "false");
        properties.setProperty(KEY_CUSTOM_CSS, "");
        saveSettings();
    }

    /**
     * 重置所有设置
     */
    public void resetSettings() {
        properties.clear();
        setDefaultSettings();
    }

    /**
     * 获取设置文件路径
     */
    public Path getSettingsFile() {
        return settingsFile;
    }
}