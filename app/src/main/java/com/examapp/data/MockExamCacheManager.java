package com.examapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.examapp.model.Question;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟考试缓存管理器
 * 用于保存未完成的模拟考试状态，支持意外退出后恢复
 */
public class MockExamCacheManager {
    private static MockExamCacheManager instance;
    private SharedPreferences preferences;
    private Gson gson;
    
    private static final String PREF_NAME = "mock_exam_cache";
    private static final String KEY_HAS_CACHE = "has_cache";
    private static final String KEY_SUBJECT_ID = "subject_id";
    private static final String KEY_SUBJECT_NAME = "subject_name";
    private static final String KEY_QUESTIONS = "questions";
    private static final String KEY_ANSWERS = "answers";
    private static final String KEY_CURRENT_POSITION = "current_position";
    private static final String KEY_TIMESTAMP = "timestamp";
    
    private MockExamCacheManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized MockExamCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new MockExamCacheManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 检查是否有未完成的考试缓存
     * @param subjectId 科目ID
     * @return 是否存在缓存
     */
    public boolean hasCachedExam(String subjectId) {
        if (!preferences.getBoolean(KEY_HAS_CACHE, false)) {
            return false;
        }
        String cachedSubjectId = preferences.getString(KEY_SUBJECT_ID, null);
        return subjectId != null && subjectId.equals(cachedSubjectId);
    }
    
    /**
     * 保存模拟考试状态
     * @param subjectId 科目ID
     * @param subjectName 科目名称
     * @param questions 题目列表
     * @param answers 答案Map
     * @param currentPosition 当前题目位置
     */
    public void saveExamState(String subjectId, String subjectName, 
                              List<Question> questions, Map<Integer, String> answers, 
                              int currentPosition) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_HAS_CACHE, true);
        editor.putString(KEY_SUBJECT_ID, subjectId);
        editor.putString(KEY_SUBJECT_NAME, subjectName);
        editor.putString(KEY_QUESTIONS, gson.toJson(questions));
        editor.putString(KEY_ANSWERS, gson.toJson(answers));
        editor.putInt(KEY_CURRENT_POSITION, currentPosition);
        editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
        editor.apply();
    }
    
    /**
     * 获取缓存的科目名称
     * @return 科目名称
     */
    public String getCachedSubjectName() {
        return preferences.getString(KEY_SUBJECT_NAME, null);
    }
    
    /**
     * 获取缓存的题目列表
     * @return 题目列表
     */
    public List<Question> getCachedQuestions() {
        String json = preferences.getString(KEY_QUESTIONS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Question>>(){}.getType();
        List<Question> questions = gson.fromJson(json, type);
        return questions != null ? questions : new ArrayList<>();
    }
    
    /**
     * 获取缓存的答案Map
     * @return 答案Map
     */
    public Map<Integer, String> getCachedAnswers() {
        String json = preferences.getString(KEY_ANSWERS, null);
        if (json == null) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<Integer, String>>(){}.getType();
        Map<Integer, String> answers = gson.fromJson(json, type);
        return answers != null ? answers : new HashMap<>();
    }
    
    /**
     * 获取缓存的当前位置
     * @return 当前题目位置
     */
    public int getCachedPosition() {
        return preferences.getInt(KEY_CURRENT_POSITION, 0);
    }
    
    /**
     * 获取缓存的时间戳
     * @return 缓存时间戳
     */
    public long getCachedTimestamp() {
        return preferences.getLong(KEY_TIMESTAMP, 0);
    }
    
    /**
     * 获取已答题数量
     * @return 已答题数量
     */
    public int getAnsweredCount() {
        return getCachedAnswers().size();
    }
    
    /**
     * 获取总题目数量
     * @return 总题目数量
     */
    public int getTotalCount() {
        return getCachedQuestions().size();
    }
    
    /**
     * 清除考试缓存（交卷后调用）
     */
    public void clearCache() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }
    
    /**
     * 格式化缓存时间为可读字符串
     * @return 格式化的时间字符串
     */
    public String getFormattedCacheTime() {
        long timestamp = getCachedTimestamp();
        if (timestamp == 0) {
            return "未知时间";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}