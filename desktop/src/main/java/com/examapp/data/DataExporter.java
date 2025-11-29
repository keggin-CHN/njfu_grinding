package com.examapp.data;

import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据导出器 - 负责导出应用数据
 */
public class DataExporter {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * 导出所有数据到指定文件
     */
    public static void exportAllData(File file) throws IOException {
        QuestionManager questionManager = QuestionManager.getInstance();
        SettingsManager settingsManager = SettingsManager.getInstance();
        
        Map<String, Object> allData = new HashMap<>();
        
        // Export subjects
        allData.put("subjects", questionManager.getAllSubjects());
        
        // Export exam history
        allData.put("examHistory", questionManager.getExamHistoryEntries());
        
        // Export settings (excluding sensitive data)
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", settingsManager.getTheme());
        settings.put("fontSize", settingsManager.getFontSize());
        settings.put("autoSave", settingsManager.isAutoSave());
        settings.put("showExplanation", settingsManager.isShowExplanation());
        settings.put("developerMode", settingsManager.isDeveloperMode());
        allData.put("settings", settings);
        
        // Export metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exportTime", System.currentTimeMillis());
        metadata.put("exportVersion", "1.0");
        metadata.put("totalSubjects", questionManager.getAllSubjects().size());
        metadata.put("totalExamHistory", questionManager.getExamHistoryEntries().size());
        allData.put("metadata", metadata);
        
        // Write to file
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(allData, writer);
            writer.flush();
        }
        
        System.out.println("数据已导出到: " + file.getAbsolutePath());
    }
    
    /**
     * 导出单个题库
     */
    public static void exportSubject(Subject subject, File file) throws IOException {
        if (subject == null) {
            throw new IllegalArgumentException("Subject cannot be null");
        }
        
        // Convert to Android format for compatibility
        Map<String, Object> exportData = new HashMap<>();
        
        // Group questions by type
        Map<String, Map<String, Object>> questionsByType = new HashMap<>();
        questionsByType.put("单选题", new HashMap<>());
        questionsByType.put("多选题", new HashMap<>());
        questionsByType.put("判断题", new HashMap<>());
        
        for (Question question : subject.getQuestions()) {
            Map<String, Object> questionData = new HashMap<>();
            
            if (question.getAnswer() != null) {
                questionData.put("answer", question.getAnswer());
            }
            
            if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                questionData.put("options", question.getOptions());
            }
            
            if (question.getExplanation() != null && !question.getExplanation().trim().isEmpty()) {
                questionData.put("explanation", question.getExplanation());
            }
            
            questionsByType.get(question.getType()).put(question.getQuestionText(), questionData);
        }
        
        // Add non-empty question types to export
        for (Map.Entry<String, Map<String, Object>> entry : questionsByType.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                exportData.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", subject.getName());
        metadata.put("displayName", subject.getDisplayName());
        metadata.put("totalQuestions", subject.getTotalQuestions());
        metadata.put("singleChoiceCount", subject.getSingleChoiceCount());
        metadata.put("multipleChoiceCount", subject.getMultipleChoiceCount());
        metadata.put("trueFalseCount", subject.getTrueFalseCount());
        metadata.put("exportTime", System.currentTimeMillis());
        metadata.put("exportVersion", "1.0");
        
        exportData.put("_metadata", metadata);
        
        // Write to file
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(exportData, writer);
            writer.flush();
        }
        
        System.out.println("题库已导出到: " + file.getAbsolutePath());
    }
    
    /**
     * 导出考试历史
     */
    public static void exportExamHistory(File file) throws IOException {
        QuestionManager questionManager = QuestionManager.getInstance();
        
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("examHistory", questionManager.getExamHistoryEntries());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exportTime", System.currentTimeMillis());
        metadata.put("totalEntries", questionManager.getExamHistoryEntries().size());
        metadata.put("exportVersion", "1.0");
        
        exportData.put("metadata", metadata);
        
        // Write to file
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(exportData, writer);
            writer.flush();
        }
        
        System.out.println("考试历史已导出到: " + file.getAbsolutePath());
    }
}