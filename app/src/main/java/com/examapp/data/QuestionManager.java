package com.examapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestionManager {
    private static QuestionManager instance;
    private SharedPreferences sharedPreferences;
    private Map<String, Subject> subjects;
    private List<ExamHistoryEntry> examHistory;
    private Gson gson;

    private static final String PREFS_NAME = "exam_app_prefs";
    private static final String KEY_SUBJECTS = "subjects";
    private static final String KEY_EXAM_HISTORY = "exam_history";

    private QuestionManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.subjects = new HashMap<>();
        this.examHistory = new ArrayList<>();
        loadSubjects();
        loadExamHistory();
    }

    public static synchronized QuestionManager getInstance(Context context) {
        if (instance == null) {
            instance = new QuestionManager(context);
        }
        return instance;
    }

    public void addSubject(Subject subject) {
        subject.setLastModified(System.currentTimeMillis());
        subjects.put(subject.getId(), subject);
        saveSubjects();
    }

    public Subject getSubject(String subjectId) {
        return subjects.get(subjectId);
    }

    public Question getQuestionById(String questionId) {
        if (subjects == null || questionId == null) return null;
        for (Subject subject : subjects.values()) {
            if (subject.getQuestions() != null) {
                for (Question q : subject.getQuestions()) {
                    if (questionId.equals(q.getId())) {
                        return q;
                    }
                }
            }
        }
        return null;
    }

    public Map<String, Subject> getAllSubjects() {
        return new HashMap<>(subjects);
    }

    public List<Subject> getSubjects() {
        return new ArrayList<>(subjects.values());
    }

    public List<Subject> getAllSubjectsSorted() {
        List<Subject> subjectList = new ArrayList<>(subjects.values());
        // 按sortOrder排序
        subjectList.sort((s1, s2) -> Integer.compare(s1.getSortOrder(), s2.getSortOrder()));
        return subjectList;
    }

    public void updateSubjectOrder(List<Subject> orderedSubjects) {
        for (int i = 0; i < orderedSubjects.size(); i++) {
            Subject subject = orderedSubjects.get(i);
            subject.setSortOrder(i);
            subject.setLastModified(System.currentTimeMillis());
            subjects.put(subject.getId(), subject);
        }
        saveSubjects();
    }

    public void updateSubjectDisplayName(String subjectId, String displayName) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setDisplayName(displayName);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    public void updateSubjectProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    public void updateSequentialProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setSequentialLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    public void updateReviewProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setReviewLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    public void updateWrongReviewProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setWrongReviewLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    public void recordAnswer(String subjectId, int questionIndex, String answer, boolean isCorrect) {
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null && questionIndex >= 0 && questionIndex < subject.getQuestions().size()) {
            Question question = subject.getQuestions().get(questionIndex);
            // 保存用户答案和答题状态到原始题目
            question.setUserAnswer(answer);
            question.setAnswerState(isCorrect ? Question.AnswerState.CORRECT : Question.AnswerState.WRONG);
            
            if (isCorrect) {
                subject.setCorrectCount(subject.getCorrectCount() + 1);
            } else {
                // 如果错误，增加原始问题的错误计数
                incrementWrongAnswerCount(question.getId());
            }
            subject.setAttemptedCount(subject.getAttemptedCount() + 1);
            subject.setLastModified(System.currentTimeMillis());
            // 保存科目状态和题目的答题记录
            saveSubjects();
        }
    }

    public void incrementWrongAnswerCount(String questionId) {
        Question originalQuestion = getQuestionById(questionId);
        if (originalQuestion != null) {
            originalQuestion.incrementWrongAnswerCount();
            for (Subject subject : subjects.values()) {
                if (subject.getQuestions() != null && subject.getQuestions().contains(originalQuestion)) {
                    subject.setLastModified(System.currentTimeMillis());
                    break;
                }
            }
            saveSubjects();
        }
    }

    public void addWrongQuestion(String subjectId, int questionIndex) {
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            if (questionIndex >= 0 && questionIndex < subject.getQuestions().size()) {
                subject.getQuestions().get(questionIndex).setWrong(true);
                subject.setLastModified(System.currentTimeMillis());
                saveSubjects();
            }
        }
    }

    public void removeWrongQuestion(String subjectId, int questionIndex) {
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            if (questionIndex >= 0 && questionIndex < subject.getQuestions().size()) {
                subject.getQuestions().get(questionIndex).setWrong(false);
                subject.setLastModified(System.currentTimeMillis());
                saveSubjects();
            }
        }
    }

    public List<Question> getWrongQuestions(String subjectId) {
        List<Question> wrong = new ArrayList<>();
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            for (Question q : subject.getQuestions()) {
                if (q.isWrong()) wrong.add(q);
            }
        }
        return wrong;
    }

    public void clearAllWrongQuestions(String subjectId) {
        if (subjectId != null) {
            // 清空指定科目的错题
            Subject subject = subjects.get(subjectId);
            if (subject != null && subject.getQuestions() != null) {
                for (Question q : subject.getQuestions()) {
                    q.setWrong(false);
                }
                subject.setLastModified(System.currentTimeMillis());
            }
        } else {
            // 清空所有科目的错题
            for (Subject subject : subjects.values()) {
                if (subject.getQuestions() != null) {
                    for (Question q : subject.getQuestions()) {
                        q.setWrong(false);
                    }
                    subject.setLastModified(System.currentTimeMillis());
                }
            }
        }
        saveSubjects();
    }
    public void updateQuestionStarStatus(Question question) {
        if (question == null) return;
        Question originalQuestion = getQuestionById(question.getId());
        if (originalQuestion != null) {
            originalQuestion.setWrong(question.isWrong());
            for (Subject subject : subjects.values()) {
                if (subject.getQuestions() != null && subject.getQuestions().contains(originalQuestion)) {
                    subject.setLastModified(System.currentTimeMillis());
                    break;
                }
            }
            saveSubjects();
        }
    }
    
    /**
     * 更新题目内容(用于AI处理)
     */
    public void updateQuestion(String subjectId, Question question) {
        if (question == null || subjectId == null) return;
        
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            for (int i = 0; i < subject.getQuestions().size(); i++) {
                Question q = subject.getQuestions().get(i);
                if (q.getId().equals(question.getId())) {
                    // 更新题目文本和解析
                    q.setQuestionText(question.getQuestionText());
                    if (question.getExplanation() != null) {
                        q.setExplanation(question.getExplanation());
                    }
                    subject.setLastModified(System.currentTimeMillis());
                    saveSubjects();
                    break;
                }
            }
        }
    }


    public List<Question> getClonedQuestions(List<Question> originalQuestions) {
        List<Question> clonedList = new ArrayList<>();
        if (originalQuestions != null) {
            for (Question q : originalQuestions) {
                clonedList.add(new Question(q));
            }
        }
        return clonedList;
    }

    public void resetUserAnswers(String subjectId) {
        Subject subject = getSubject(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            for (Question q : subject.getQuestions()) {
                q.setUserAnswer(null);
                q.setAnswerState(Question.AnswerState.UNANSWERED);
            }
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }
    
    /**
     * 仅重置顺序刷题模式的用户答案（用于随机模式和错题回顾模式）
     */
    public void resetUserAnswersForSession(String subjectId) {
        // 这个方法不保存到持久化存储，仅用于会话中的临时重置
        Subject subject = getSubject(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            for (Question q : subject.getQuestions()) {
                q.setUserAnswer(null);
                q.setAnswerState(Question.AnswerState.UNANSWERED);
            }
            // 注意：不调用 saveSubjects()，这样不会影响持久化的答题记录
        }
    }

    public List<Question> getPracticeQuestions(String subjectId, boolean random) {
        Subject subject = subjects.get(subjectId);
        if (subject == null || subject.getQuestions() == null) return new ArrayList<>();

        // 返回题目的副本
        List<Question> list = getClonedQuestions(subject.getQuestions());
        if (random) Collections.shuffle(list);
        return list;
    }

    public List<Question> getMockExamQuestions(String subjectId) {
        Subject subject = subjects.get(subjectId);
        if (subject == null || subject.getQuestions() == null) {
            return new ArrayList<>();
        }

        List<Question> singleChoice = new ArrayList<>();
        List<Question> multipleChoice = new ArrayList<>();
        List<Question> trueOrFalse = new ArrayList<>();

        // Use a Set to track unique question texts to avoid duplicates if the source has them
        java.util.Set<String> seenQuestions = new java.util.HashSet<>();

        for (Question question : subject.getQuestions()) {
            // Simple de-duplication based on question text
            if (seenQuestions.contains(question.getQuestionText())) {
                continue;
            }
            seenQuestions.add(question.getQuestionText());

            String category = question.getCategory() != null ? question.getCategory().toLowerCase() : "";

            if (category.contains("单选") || category.contains("single")) {
                singleChoice.add(question);
            } else if (category.contains("多选") || category.contains("multiple")) {
                multipleChoice.add(question);
            } else if (category.contains("判断") || category.contains("true")) {
                trueOrFalse.add(question);
            }
        }

        Collections.shuffle(singleChoice);
        Collections.shuffle(multipleChoice);
        Collections.shuffle(trueOrFalse);

        List<Question> exam = new ArrayList<>();

        exam.addAll(singleChoice.subList(0, Math.min(60, singleChoice.size())));
        exam.addAll(multipleChoice.subList(0, Math.min(10, multipleChoice.size())));
        exam.addAll(trueOrFalse.subList(0, Math.min(10, trueOrFalse.size())));

        // 返回题目的副本
        return getClonedQuestions(exam);
    }

    public List<Question> getQuestionsSortedByWrongCount(String subjectId) {
        Subject subject = getSubject(subjectId);
        if (subject == null || subject.getQuestions() == null) {
            return new ArrayList<>();
        }
        List<Question> questions = new ArrayList<>(subject.getQuestions());
        // 过滤掉错误次数为0的题目
        questions.removeIf(q -> q.getWrongAnswerCount() == 0);
        // 按错误次数降序排序
        questions.sort((q1, q2) -> Integer.compare(q2.getWrongAnswerCount(), q1.getWrongAnswerCount()));
        return questions;
    }

    public int scoreQuestion(String subjectId, Question question) {
        if (question == null || question.getType() == null) {
            return 1;
        }

        String type = question.getType();
        if ("多选题".equals(type) || "判断题".equals(type)) {
            return 2;
        }
        return 1; 
    }

    public List<Question> searchQuestions(String subjectId, String keyword) {
        List<Question> results = new ArrayList<>();
        Subject subject = subjects.get(subjectId);
        if (subject == null || subject.getQuestions() == null) {
            return results;
        }

        String lowerKeyword = keyword.toLowerCase();
        for (Question question : subject.getQuestions()) {
            if (question.getQuestionText().toLowerCase().contains(lowerKeyword)) {
                results.add(question);
            }
        }
        return results;
    }

    public void deleteSubject(String subjectId) {
        subjects.remove(subjectId);
        saveSubjects();
    }

    public void addExamHistoryEntry(ExamHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        entry.setLastModified(System.currentTimeMillis());
        examHistory.add(0, entry);
        saveExamHistory();
    }
    public List<ExamHistoryEntry> getExamHistoryEntries() {
        return new ArrayList<>(examHistory);
    }
    public List<ExamHistoryEntry> getExamHistoryEntries(String subjectId) {
        if (subjectId == null || subjectId.isEmpty()) {
            return getExamHistoryEntries();
        }
        List<ExamHistoryEntry> filtered = new ArrayList<>();
        for (ExamHistoryEntry entry : examHistory) {
            if (subjectId.equals(entry.getSubjectId())) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public int getEndlessBestStreak(String subjectId) {
        Subject subject = subjects.get(subjectId);
        return subject != null ? subject.getEndlessBestStreak() : 0;
    }

    public void updateEndlessBestStreak(String subjectId, int streak) {
        Subject subject = subjects.get(subjectId);
        if (subject != null && streak > subject.getEndlessBestStreak()) {
            subject.setEndlessBestStreak(streak);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    public void replaceAllSubjects(Map<String, Subject> newSubjects) {
        this.subjects = newSubjects;
        saveSubjects();
    }

    public void replaceAllHistory(List<ExamHistoryEntry> newHistory) {
        this.examHistory = newHistory;
        saveExamHistory();
    }

    private void saveSubjects() {
        String json = gson.toJson(subjects);
        sharedPreferences.edit().putString(KEY_SUBJECTS, json).apply();
    }

    private void saveExamHistory() {
        String json = gson.toJson(examHistory);
        sharedPreferences.edit().putString(KEY_EXAM_HISTORY, json).apply();
    }

    private void loadSubjects() {
        String json = sharedPreferences.getString(KEY_SUBJECTS, null);
        if (json != null) {
            Type type = new TypeToken<Map<String, Subject>>() {}.getType();
            subjects = gson.fromJson(json, type);
        } else {
            subjects = new HashMap<>();
        }
    }

    private void loadExamHistory() {
        String json = sharedPreferences.getString(KEY_EXAM_HISTORY, null);
        if (json != null) {
            Type type = new TypeToken<List<ExamHistoryEntry>>() {}.getType();
            examHistory = gson.fromJson(json, type);
        } else {
            examHistory = new ArrayList<>();
        }
    }
}
