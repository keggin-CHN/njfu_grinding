package com.examapp.data;

import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.examapp.model.SyncData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataMerger {

    private Gson gson = new Gson();

    public Map<String, Subject> mergeSubjects(String localJson, String remoteJson) {
        Type type = new TypeToken<Map<String, Subject>>() {}.getType();
        Map<String, Subject> localSubjects = gson.fromJson(localJson, type);
        Map<String, Subject> remoteSubjects = gson.fromJson(remoteJson, type);

        if (localSubjects == null) localSubjects = new HashMap<>();
        if (remoteSubjects == null) remoteSubjects = new HashMap<>();

        Map<String, Subject> mergedSubjects = new HashMap<>(localSubjects);

        for (Map.Entry<String, Subject> remoteEntry : remoteSubjects.entrySet()) {
            String key = remoteEntry.getKey();
            Subject remoteSubject = remoteEntry.getValue();
            Subject localSubject = mergedSubjects.get(key);

            if (localSubject == null) {
                // 本地没有，直接添加远程题库
                mergedSubjects.put(key, remoteSubject);
            } else {
                // 本地有，进行详细合并
                Subject mergedSubject = mergeSingleSubject(localSubject, remoteSubject);
                mergedSubjects.put(key, mergedSubject);
            }
        }
        return mergedSubjects;
    }

    private Subject mergeSingleSubject(Subject local, Subject remote) {
        // 创建一个新的Subject对象作为合并结果，以免修改原始对象
        Subject merged = new Subject(local.getId(), local.getDisplayName());
        
        // 1. 无尽模式最高分: 取最大值
        merged.setEndlessBestStreak(Math.max(local.getEndlessBestStreak(), remote.getEndlessBestStreak()));

        // 2. 进度: 取两者中较大（更新）的值
        merged.setSequentialLastPosition(Math.max(local.getSequentialLastPosition(), remote.getSequentialLastPosition()));
        merged.setReviewLastPosition(Math.max(local.getReviewLastPosition(), remote.getReviewLastPosition()));
        // 错题回顾模式不保存进度，所以不需要合并

        // 3. 题目相关属性合并
        Map<String, Question> localQuestionsMap = local.getQuestions().stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<Question> mergedQuestions = new ArrayList<>();
        for (Question remoteQuestion : remote.getQuestions()) {
            Question localQuestion = localQuestionsMap.get(remoteQuestion.getId());
            if (localQuestion != null) {
                // 合并同一道题
                Question mergedQuestion = new Question(localQuestion); // 以本地为基础

                // 错题次数: 累加
                mergedQuestion.setWrongAnswerCount(localQuestion.getWrongAnswerCount() + remoteQuestion.getWrongAnswerCount());

                // 错题本: 取并集 (只要一个为true, 结果就为true)
                mergedQuestion.setWrong(localQuestion.isWrong() || remoteQuestion.isWrong());

                // 答题进度: 取并集 (如果一方未答，则取另一方的答案)
                if (localQuestion.getAnswerState() == Question.AnswerState.UNANSWERED) {
                    mergedQuestion.setAnswerState(remoteQuestion.getAnswerState());
                    mergedQuestion.setUserAnswer(remoteQuestion.getUserAnswer());
                }
                
                mergedQuestions.add(mergedQuestion);
                localQuestionsMap.remove(remoteQuestion.getId()); // 从map中移除已处理的题目
            } else {
                // 如果本地没有这道题，则添加
                mergedQuestions.add(remoteQuestion);
            }
        }
        // 添加本地有但远程没有的题目
        mergedQuestions.addAll(localQuestionsMap.values());
        merged.setQuestions(mergedQuestions);

        // 在合并题目后重新计算统计数据
        merged.recalculateStats();

        // 总是更新最后修改时间
        merged.setLastModified(System.currentTimeMillis());

        return merged;
    }


    public List<ExamHistoryEntry> mergeExamHistory(String localJson, String remoteJson) {
        Type type = new TypeToken<List<ExamHistoryEntry>>() {}.getType();
        List<ExamHistoryEntry> localHistory = gson.fromJson(localJson, type);
        List<ExamHistoryEntry> remoteHistory = gson.fromJson(remoteJson, type);

        if (localHistory == null) localHistory = new ArrayList<>();
        if (remoteHistory == null) remoteHistory = new ArrayList<>();
        
        // 模拟考试历史: 累加
        List<ExamHistoryEntry> mergedHistory = new ArrayList<>(localHistory);
        mergedHistory.addAll(remoteHistory);

        // 使用Set进行去重，去重标准是时间戳+设备源
        Set<String> uniqueKeys = new HashSet<>();
        List<ExamHistoryEntry> distinctHistory = new ArrayList<>();
        for (ExamHistoryEntry entry : mergedHistory) {
            String key = entry.getTimestamp() + "-" + entry.getDeviceSource();
            if (uniqueKeys.add(key)) {
                distinctHistory.add(entry);
            }
        }
        
        // 按时间戳降序排序
        distinctHistory.sort((e1, e2) -> Long.compare(e2.getTimestamp(), e1.getTimestamp()));

        return distinctHistory;
    }

    public SyncData merge(SyncData localData, SyncData remoteData) {
        // This is a simplified merge for the client side, assuming server sends the authoritative merged data.
        // For a more robust two-way merge, the logic would be similar to the desktop version.
        // Here, we just demonstrate the structure. A true merge would be more complex.

        // A simple example: remote data overwrites local data.
        // In the full implementation, we'd use the same merge logic as the desktop.
        List<Subject> mergedSubjects = remoteData.getSubjects();
        List<ExamHistoryEntry> mergedHistory = remoteData.getExamHistory();

        return new SyncData(mergedSubjects, mergedHistory);
    }
}