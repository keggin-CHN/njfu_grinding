package com.examapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * AI答疑缓存管理器
 * 用于缓存AI对题目的解析,避免重复请求
 */
public class AICacheManager {
    private static AICacheManager instance;
    private SharedPreferences preferences;
    private Gson gson;
    
    private static final String PREF_NAME = "ai_cache";
    private static final String KEY_CACHE_MAP = "cache_map";
    
    private Map<String, String> cacheMap;
    
    private AICacheManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadCache();
    }
    
    public static synchronized AICacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new AICacheManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 从SharedPreferences加载缓存
     */
    private void loadCache() {
        String json = preferences.getString(KEY_CACHE_MAP, "{}");
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        cacheMap = gson.fromJson(json, type);
        if (cacheMap == null) {
            cacheMap = new HashMap<>();
        }
    }
    
    /**
     * 保存缓存到SharedPreferences
     */
    private void saveCache() {
        String json = gson.toJson(cacheMap);
        preferences.edit().putString(KEY_CACHE_MAP, json).apply();
    }
    
    /**
     * 生成题目的唯一键
     * 使用题目文本的hash值作为键
     */
    private String generateKey(String questionText, String answer) {
        return String.valueOf((questionText + answer).hashCode());
    }
    
    /**
     * 获取缓存的AI回复
     * @param questionText 题目文本
     * @param answer 正确答案
     * @return 缓存的AI回复,如果不存在则返回null
     */
    public String getCachedResponse(String questionText, String answer) {
        String key = generateKey(questionText, answer);
        return cacheMap.get(key);
    }
    
    /**
     * 缓存AI回复
     * @param questionText 题目文本
     * @param answer 正确答案
     * @param response AI的回复
     */
    public void cacheResponse(String questionText, String answer, String response) {
        String key = generateKey(questionText, answer);
        cacheMap.put(key, response);
        saveCache();
    }
    
    /**
     * 检查是否有缓存
     * @param questionText 题目文本
     * @param answer 正确答案
     * @return 是否存在缓存
     */
    public boolean hasCachedResponse(String questionText, String answer) {
        String key = generateKey(questionText, answer);
        return cacheMap.containsKey(key);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        cacheMap.clear();
        saveCache();
    }
    
    /**
     * 获取缓存大小
     * @return 缓存的条目数量
     */
    public int getCacheSize() {
        return cacheMap.size();
    }
}