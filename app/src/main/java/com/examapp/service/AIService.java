package com.examapp.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.examapp.data.AISettingsManager;
import com.examapp.data.AICacheManager;
import com.examapp.model.Question;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AIService {
    private static AIService instance;
    private AISettingsManager settingsManager;
    private AICacheManager cacheManager;
    private OkHttpClient client;
    private Handler mainHandler;
    
    public interface AICallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(String error);
    }
    
    private AIService(Context context) {
        settingsManager = AISettingsManager.getInstance(context);
        cacheManager = AICacheManager.getInstance(context);
        mainHandler = new Handler(Looper.getMainLooper());
        
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    public static synchronized AIService getInstance(Context context) {
        if (instance == null) {
            instance = new AIService(context.getApplicationContext());
        }
        return instance;
    }
    
    public void fetchModels(ModelsCallback callback) {
        String url = settingsManager.getModelsApiUrl();
        String apiKey = settingsManager.getApiKey();
        
        if (url.isEmpty() || apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError("请先配置Base URL和API Key"));
            return;
        }
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("网络请求失败: " + e.getMessage()));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String errorMsg = "API错误: " + response.code();
                        if (response.body() != null) {
                            errorMsg += " - " + response.body().string();
                        }
                        String finalErrorMsg = errorMsg;
                        mainHandler.post(() -> callback.onError(finalErrorMsg));
                        return;
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray dataArray = jsonResponse.getJSONArray("data");
                    
                    java.util.List<String> models = new java.util.ArrayList<>();
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject modelObj = dataArray.getJSONObject(i);
                        String modelId = modelObj.getString("id");
                        models.add(modelId);
                    }
                    
                    mainHandler.post(() -> callback.onSuccess(models));
                    
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("解析响应失败: " + e.getMessage()));
                }
            }
        });
    }
    
    public void askQuestion(Question question, AICallback callback) {
        askQuestion(question, callback, false);
    }
    
    public void askQuestion(Question question, AICallback callback, boolean forceRefresh) {
        if (!settingsManager.isConfigured()) {
            mainHandler.post(() -> callback.onError("请先在设置中配置AI参数"));
            return;
        }
        
        // 检查缓存(除非强制刷新)
        if (!forceRefresh) {
            String cachedResponse = cacheManager.getCachedResponse(
                question.getQuestionText(),
                question.getFormattedAnswer()
            );
            if (cachedResponse != null) {
                mainHandler.post(() -> callback.onSuccess(cachedResponse));
                return;
            }
        }
        
        String url = settingsManager.getFullApiUrl();
        String apiKey = settingsManager.getApiKey();
        String model = settingsManager.getModel();
        String promptTemplate = settingsManager.getAnswerPrompt();
        float temperature = settingsManager.getTemperature();
        float topP = settingsManager.getTopP();
        
        // 构建选项文本
        StringBuilder optionsText = new StringBuilder();
        List<String> options = question.getOptions();
        if (options != null && !options.isEmpty()) {
            for (String option : options) {
                optionsText.append(option).append("\n");
            }
        } else {
            optionsText.append("无选项(判断题)");
        }
        
        // 替换提示词中的占位符
        String prompt = promptTemplate
                .replace("{question}", question.getQuestionText())
                .replace("{options}", optionsText.toString())
                .replace("{answer}", question.getFormattedAnswer());
        
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", model);
            requestJson.put("temperature", temperature);
            requestJson.put("top_p", topP);
            
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            
            requestJson.put("messages", messages);
            
            RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("网络请求失败: " + e.getMessage()));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorMsg = "API错误: " + response.code();
                            if (response.body() != null) {
                                errorMsg += " - " + response.body().string();
                            }
                            String finalErrorMsg = errorMsg;
                            mainHandler.post(() -> callback.onError(finalErrorMsg));
                            return;
                        }
                        
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        
                        if (choices.length() > 0) {
                            JSONObject firstChoice = choices.getJSONObject(0);
                            JSONObject messageObj = firstChoice.getJSONObject("message");
                            String content = messageObj.getString("content");
                            
                            // 缓存响应
                            cacheManager.cacheResponse(
                                question.getQuestionText(),
                                question.getFormattedAnswer(),
                                content
                            );
                            
                            mainHandler.post(() -> callback.onSuccess(content));
                        } else {
                            mainHandler.post(() -> callback.onError("AI返回了空响应"));
                        }
                        
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("解析响应失败: " + e.getMessage()));
                    }
                }
            });
            
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("构建请求失败: " + e.getMessage()));
        }
    }
}