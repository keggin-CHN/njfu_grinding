package com.examapp.data;

import android.content.Context;
import android.content.SharedPreferences;

public class AISettingsManager {
    private static AISettingsManager instance;
    private SharedPreferences preferences;
    
    private static final String PREF_NAME = "ai_settings";
    private static final String KEY_BASE_URL = "ai_base_url";
    private static final String KEY_API_KEY = "ai_api_key";
    private static final String KEY_MODEL = "ai_model";
    private static final String KEY_TEMPERATURE = "ai_temperature";
    private static final String KEY_TOP_P = "ai_top_p";
    private static final String KEY_MAX_RETRY = "ai_max_retry";
    
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
    
    // 用于题目修复的提示词(初次尝试)
    private static final String FIX_BRACKET_PROMPT = "请检查以下题目是否缺少填空标记括号\"( )\"。" +
            "如果题目中应该有填空位置但缺少括号,请在正确的位置添加\"( )\"。\n\n" +
            "题目:{question}\n" +
            "正确答案:{answer}\n\n" +
            "重要要求:\n" +
            "1. 只能添加一个括号\"( )\"\n" +
            "2. 不要修改题目的其他任何内容\n" +
            "3. 只返回修复后的题目文本,不要添加任何解释\n" +
            "4. 如果题目不需要修复,请返回原题目";
    
    // 用于题目修复的严格提示词(重试时使用)
    private static final String FIX_BRACKET_STRICT_PROMPT = "【严格要求】请为以下题目添加填空括号\"( )\":\n\n" +
            "题目:{question}\n" +
            "正确答案:{answer}\n\n" +
            "【必须遵守的规则】:\n" +
            "1. 必须且只能添加一个括号\"( )\",不能多也不能少\n" +
            "2. 除了添加括号外,不得修改题目的任何其他文字、标点或格式\n" +
            "3. 只返回修复后的完整题目文本,不要有任何额外的说明、解释或标记\n" +
            "4. 括号的位置应该在答案对应的填空位置\n\n" +
            "【示例】:\n" +
            "原题目:\"是实现中华民族伟大复兴的根本保证。\"\n" +
            "修复后:\"( )是实现中华民族伟大复兴的根本保证。\"\n\n" +
            "现在请修复上面的题目:";
    
    // 用于生成解析的提示词
    private static final String GENERATE_EXPLANATION_PROMPT = "请为以下题目生成简洁的解析:\n\n" +
            "题目:{question}\n" +
            "选项:{options}\n" +
            "正确答案:{answer}\n\n" +
            "请用2-3句话简要说明:\n" +
            "1. 本题考察的知识点\n" +
            "2. 为什么选择这个答案\n" +
            "3. 其他选项的主要问题(可选)\n\n" +
            "要求简洁明了,不超过100字。";
    
    private AISettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized AISettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new AISettingsManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public String getBaseUrl() {
        return preferences.getString(KEY_BASE_URL, "");
    }
    
    public void setBaseUrl(String baseUrl) {
        preferences.edit().putString(KEY_BASE_URL, baseUrl).apply();
    }
    
    public String getApiKey() {
        return preferences.getString(KEY_API_KEY, "");
    }
    
    public void setApiKey(String apiKey) {
        preferences.edit().putString(KEY_API_KEY, apiKey).apply();
    }
    
    public String getModel() {
        return preferences.getString(KEY_MODEL, "");
    }
    
    public void setModel(String model) {
        preferences.edit().putString(KEY_MODEL, model).apply();
    }
    
    /**
     * 获取答疑提示词(固定,不可修改)
     */
    public String getAnswerPrompt() {
        return FIXED_PROMPT;
    }
    
    /**
     * 获取题目修复提示词(初次尝试)
     */
    public String getFixBracketPrompt() {
        return FIX_BRACKET_PROMPT;
    }
    
    /**
     * 获取题目修复严格提示词(重试时使用)
     */
    public String getFixBracketStrictPrompt() {
        return FIX_BRACKET_STRICT_PROMPT;
    }
    
    /**
     * 获取生成解析提示词
     */
    public String getGenerateExplanationPrompt() {
        return GENERATE_EXPLANATION_PROMPT;
    }
    
    public float getTemperature() {
        return preferences.getFloat(KEY_TEMPERATURE, 0.6f);
    }
    
    public void setTemperature(float temperature) {
        preferences.edit().putFloat(KEY_TEMPERATURE, temperature).apply();
    }
    
    public float getTopP() {
        return preferences.getFloat(KEY_TOP_P, 1.0f);
    }
    
    public void setTopP(float topP) {
        preferences.edit().putFloat(KEY_TOP_P, topP).apply();
    }
    
    public String getFullApiUrl() {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty()) {
            return "";
        }
        
        // 如果以#结尾,表示使用自定义完整路径,移除#后直接返回
        if (baseUrl.endsWith("#")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // 移除末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/v1/chat/completions";
    }
    
    public String getModelsApiUrl() {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty()) {
            return "";
        }
        
        // 如果以#结尾,表示使用自定义完整路径
        // 对于models接口,尝试将chat/completions替换为models
        if (baseUrl.endsWith("#")) {
            String customUrl = baseUrl.substring(0, baseUrl.length() - 1);
            // 尝试智能替换
            if (customUrl.contains("chat/completions")) {
                return customUrl.replace("chat/completions", "models");
            }
            // 如果无法替换,返回原路径(可能会失败,但至少尝试)
            return customUrl;
        }
        
        // 移除末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/v1/models";
    }
    
    public boolean isConfigured() {
        return !getBaseUrl().isEmpty() && !getApiKey().isEmpty() && !getModel().isEmpty();
    }
    
    public int getMaxRetry() {
        return preferences.getInt(KEY_MAX_RETRY, 10);
    }
    
    public void setMaxRetry(int maxRetry) {
        // 限制在1-10之间
        if (maxRetry < 1) maxRetry = 1;
        if (maxRetry > 10) maxRetry = 10;
        preferences.edit().putInt(KEY_MAX_RETRY, maxRetry).apply();
    }
    
    /**
     * 重置Temperature和Top-P为默认值
     */
    public void resetParametersToDefault() {
        setTemperature(0.6f);
        setTopP(1.0f);
    }
}