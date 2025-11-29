package com.examapp.data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * AI设置管理器 - 负责AI助手相关设置
 */
public class AISettingsManager {
    private static AISettingsManager instance;
    private Properties properties;
    private Path settingsFile;

    private static final String DATA_DIR_NAME = ".njfu_grinding";
    private static final String AI_SETTINGS_FILE_NAME = "ai_settings.properties";

    // AI设置键
    private static final String KEY_PROVIDER = "ai_provider";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_MODEL = "model";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_TOP_P = "top_p";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_MAX_RETRY = "max_retry";
    private static final String KEY_ENABLED = "enabled";

    // 固定的AI提示词 - 要求紧凑格式输出
    private static final String FIXED_PROMPT = "你是一位专业的题目解析助手。请按照以下格式简洁地分析这道题:\n\n" +
            "题目:{question}\n" +
            "选项:{options}\n" +
            "正确答案:{answer}\n\n" +
            "请严格按照以下格式输出(不要添加额外的标题或说明):\n\n" +
            "本题考察[具体知识点]。答案为{answer}。\n\n" +
            "然后逐个分析每个选项(每个选项单独一行):\n" +
            "A. [选项内容]\n" +
            "[简要讲解A选项的知识点]\n\n" +
            "B. [选项内容]\n" +
            "[简要讲解B选项的知识点]\n\n" +
            "(以此类推)\n\n" +
            "要求:1.紧凑简洁 2.直接输出内容 3.不要添加\"解析:\"等额外标题";

    // 支持的AI提供商
    public static final String PROVIDER_OPENAI = "OpenAI";
    public static final String PROVIDER_CLAUDE = "Claude";
    public static final String PROVIDER_GEMINI = "Gemini";
    public static final String PROVIDER_DEEPSEEK = "DeepSeek";
    public static final String PROVIDER_CHATGLM = "ChatGLM";
    public static final String PROVIDER_QWEN = "Qwen";
    public static final String PROVIDER_GROK = "Grok";
    public static final String PROVIDER_OLLAMA = "Ollama";

    private AISettingsManager() {
        this.properties = new Properties();
        initializeSettingsFile();
        loadSettings();
    }

    public static synchronized AISettingsManager getInstance() {
        if (instance == null) {
            instance = new AISettingsManager();
        }
        return instance;
    }

    /**
     * 初始化设置文件
     */
    private void initializeSettingsFile() {
        String userHome = System.getProperty("user.home");
        Path dataDir = Paths.get(userHome, DATA_DIR_NAME);
        settingsFile = dataDir.resolve(AI_SETTINGS_FILE_NAME);

        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            if (!Files.exists(settingsFile)) {
                Files.createFile(settingsFile);
                setDefaultSettings();
            }
        } catch (IOException e) {
            System.err.println("无法创建AI设置文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 设置默认值
     */
    private void setDefaultSettings() {
        properties.setProperty(KEY_PROVIDER, PROVIDER_OPENAI);
        properties.setProperty(KEY_API_KEY, "");
        properties.setProperty(KEY_BASE_URL, "https://api.openai.com");
        properties.setProperty(KEY_API_URL, "https://api.openai.com/v1/chat/completions");
        properties.setProperty(KEY_MODEL, "gpt-3.5-turbo");
        properties.setProperty(KEY_TEMPERATURE, "0.7");
        properties.setProperty(KEY_TOP_P, "0.9");
        properties.setProperty(KEY_MAX_TOKENS, "1000");
        properties.setProperty(KEY_MAX_RETRY, "3");
        properties.setProperty(KEY_ENABLED, "false");
        saveSettings();
    }

    /**
     * 加载设置
     */
    private void loadSettings() {
        try (InputStream input = new FileInputStream(settingsFile.toFile())) {
            properties.load(input);
            System.out.println("AI设置已加载");
        } catch (IOException e) {
            System.err.println("加载AI设置失败: " + e.getMessage());
            setDefaultSettings();
        }
    }

    /**
     * 保存设置
     */
    private void saveSettings() {
        try (OutputStream output = new FileOutputStream(settingsFile.toFile())) {
            properties.store(output, "NJFU刷题助手AI设置");
            System.out.println("AI设置已保存");
        } catch (IOException e) {
            System.err.println("保存AI设置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // AI提供商
    public String getProvider() {
        return properties.getProperty(KEY_PROVIDER, PROVIDER_OPENAI);
    }

    public void setProvider(String provider) {
        properties.setProperty(KEY_PROVIDER, provider);
        saveSettings();
    }

    // API密钥
    public String getApiKey() {
        return properties.getProperty(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        properties.setProperty(KEY_API_KEY, apiKey);
        saveSettings();
    }

    // Base URL
    public String getBaseUrl() {
        return properties.getProperty(KEY_BASE_URL, "https://api.openai.com");
    }

    public void setBaseUrl(String baseUrl) {
        properties.setProperty(KEY_BASE_URL, baseUrl);
        saveSettings();
    }

    // API URL
    public String getApiUrl() {
        return properties.getProperty(KEY_API_URL, "https://api.openai.com/v1/chat/completions");
    }

    public void setApiUrl(String apiUrl) {
        properties.setProperty(KEY_API_URL, apiUrl);
        saveSettings();
    }

    // 模型
    public String getModel() {
        return properties.getProperty(KEY_MODEL, "gpt-3.5-turbo");
    }

    public void setModel(String model) {
        properties.setProperty(KEY_MODEL, model);
        saveSettings();
    }

    // 温度参数
    public double getTemperature() {
        return Double.parseDouble(properties.getProperty(KEY_TEMPERATURE, "0.7"));
    }

    public void setTemperature(double temperature) {
        properties.setProperty(KEY_TEMPERATURE, String.valueOf(temperature));
        saveSettings();
    }

    // Top-P参数
    public float getTopP() {
        return Float.parseFloat(properties.getProperty(KEY_TOP_P, "0.9"));
    }

    public void setTopP(float topP) {
        properties.setProperty(KEY_TOP_P, String.valueOf(topP));
        saveSettings();
    }

    // 最大重试次数
    public int getMaxRetry() {
        return Integer.parseInt(properties.getProperty(KEY_MAX_RETRY, "3"));
    }

    public void setMaxRetry(int maxRetry) {
        properties.setProperty(KEY_MAX_RETRY, String.valueOf(maxRetry));
        saveSettings();
    }

    // 最大令牌数
    public int getMaxTokens() {
        return Integer.parseInt(properties.getProperty(KEY_MAX_TOKENS, "1000"));
    }

    public void setMaxTokens(int maxTokens) {
        properties.setProperty(KEY_MAX_TOKENS, String.valueOf(maxTokens));
        saveSettings();
    }

    // 是否启用
    public boolean isEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, "false"));
    }

    public void setEnabled(boolean enabled) {
        properties.setProperty(KEY_ENABLED, String.valueOf(enabled));
        saveSettings();
    }

    /**
     * 检查是否已配置
     */
    public boolean isConfigured() {
        String apiKey = getApiKey();
        String apiUrl = getApiUrl();
        return apiKey != null && !apiKey.trim().isEmpty() && 
               apiUrl != null && !apiUrl.trim().isEmpty();
    }

    /**
     * 获取默认API URL（根据提供商）
     */
    public String getDefaultApiUrl(String provider) {
        switch (provider) {
            case PROVIDER_OPENAI:
                return "https://api.openai.com/v1/chat/completions";
            case PROVIDER_CLAUDE:
                return "https://api.anthropic.com/v1/messages";
            case PROVIDER_GEMINI:
                return "https://generativelanguage.googleapis.com/v1/models/";
            case PROVIDER_DEEPSEEK:
                return "https://api.deepseek.com/v1/chat/completions";
            case PROVIDER_CHATGLM:
                return "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case PROVIDER_QWEN:
                return "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
            case PROVIDER_GROK:
                return "https://api.x.ai/v1/chat/completions";
            case PROVIDER_OLLAMA:
                return "http://localhost:11434/api/chat";
            default:
                return "";
        }
    }

    /**
     * 获取默认模型（根据提供商）
     */
    public String getDefaultModel(String provider) {
        switch (provider) {
            case PROVIDER_OPENAI:
                return "gpt-3.5-turbo";
            case PROVIDER_CLAUDE:
                return "claude-3-sonnet-20240229";
            case PROVIDER_GEMINI:
                return "gemini-pro";
            case PROVIDER_DEEPSEEK:
                return "deepseek-chat";
            case PROVIDER_CHATGLM:
                return "glm-4";
            case PROVIDER_QWEN:
                return "qwen-turbo";
            case PROVIDER_GROK:
                return "grok-beta";
            case PROVIDER_OLLAMA:
                return "llama2";
            default:
                return "";
        }
    }

    /**
     * 重置参数到默认值
     */
    public void resetParametersToDefault() {
        properties.setProperty(KEY_TEMPERATURE, "0.7");
        properties.setProperty(KEY_TOP_P, "0.9");
        properties.setProperty(KEY_MAX_RETRY, "3");
        saveSettings();
    }

    /**
     * 重置AI设置
     */
    public void resetSettings() {
        properties.clear();
        setDefaultSettings();
    }

    /**
     * 获取答疑提示词(固定,不可修改)
     */
    public String getAnswerPrompt() {
        return FIXED_PROMPT;
    }

    /**
     * 获取设置文件路径
     */
    public Path getSettingsFile() {
        return settingsFile;
    }
}