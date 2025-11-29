package com.examapp.data;

import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 题库管理器 - 负责题库的增删改查和持久化
 */
public class QuestionManager {
    private static QuestionManager instance;
    private Map<String, Subject> subjects;
    private List<ExamHistoryEntry> examHistory;
    private Gson gson;
    private Path dataDir;
    private Path subjectsFile;
    private Path historyFile;

    private static final String DATA_DIR_NAME = ".njfu_grinding";
    private static final String SUBJECTS_FILE_NAME = "subjects.json";
    private static final String HISTORY_FILE_NAME = "exam_history.json";

    private QuestionManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.subjects = new HashMap<>();
        this.examHistory = new ArrayList<>();
        initializeDataDirectory();
        loadSubjects();
        loadExamHistory();
    }

    public static synchronized QuestionManager getInstance() {
        if (instance == null) {
            instance = new QuestionManager();
        }
        return instance;
    }

    /**
     * 初始化数据目录
     */
    private void initializeDataDirectory() {
        String userHome = System.getProperty("user.home");
        dataDir = Paths.get(userHome, DATA_DIR_NAME);
        subjectsFile = dataDir.resolve(SUBJECTS_FILE_NAME);
        historyFile = dataDir.resolve(HISTORY_FILE_NAME);

        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                System.out.println("创建数据目录: " + dataDir);
            }
        } catch (IOException e) {
            System.err.println("无法创建数据目录: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 添加题库
     */
    public void addSubject(Subject subject) {
        calculateRelativeIds(subject);
        subject.setLastModified(System.currentTimeMillis());
        subjects.put(subject.getId(), subject);
        saveSubjects();
    }

    /**
     * 获取题库
     * @deprecated Use getSubjectById instead for clarity.
     */
    @Deprecated
    public Subject getSubject(String subjectId) {
        return subjects.get(subjectId);
    }

    /**
     * 根据ID获取题库
     */
    public Subject getSubjectById(String subjectId) {
        return subjects.get(subjectId);
    }

    /**
     * 根据题目ID获取题目
     */
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

    /**
     * 获取所有题库
     */
    public Map<String, Subject> getAllSubjects() {
        return new HashMap<>(subjects);
    }

    public List<Subject> getSubjects() {
        return new ArrayList<>(subjects.values());
    }

    /**
     * 获取排序后的题库列表
     */
    public List<Subject> getAllSubjectsSorted() {
        List<Subject> subjectList = new ArrayList<>(subjects.values());
        subjectList.sort(Comparator.comparingInt(Subject::getSortOrder));
        return subjectList;
    }

    /**
     * 更新题库顺序
     */
    public void updateSubjectOrder(List<Subject> orderedSubjects) {
        for (int i = 0; i < orderedSubjects.size(); i++) {
            Subject subject = orderedSubjects.get(i);
            subject.setSortOrder(i);
            subject.setLastModified(System.currentTimeMillis());
            subjects.put(subject.getId(), subject);
        }
        saveSubjects();
    }

    /**
     * 更新题库显示名称
     */
    public void updateSubjectDisplayName(String subjectId, String displayName) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setDisplayName(displayName);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    /**
     * 更新顺序刷题进度
     */
    public void updateSequentialProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setSequentialLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    /**
     * 更新背题模式进度
     */
    public void updateReviewProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setReviewLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    /**
     * 更新错题回顾进度
     */
    public void updateWrongReviewProgress(String subjectId, int position) {
        Subject subject = subjects.get(subjectId);
        if (subject != null) {
            subject.setWrongReviewLastPosition(position);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    /**
     * 记录答题结果
     */
    public void recordAnswer(String subjectId, int questionIndex, String answer, boolean isCorrect) {
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null && 
            questionIndex >= 0 && questionIndex < subject.getQuestions().size()) {
            
            Question question = subject.getQuestions().get(questionIndex);

            // 修复BUG：持久化单道题的回答状态
            question.setUserAnswer(answer);
            question.setAnswerState(isCorrect ? Question.AnswerState.CORRECT : Question.AnswerState.WRONG);
            
            if (isCorrect) {
                subject.setCorrectCount(subject.getCorrectCount() + 1);
            } else {
                incrementWrongAnswerCount(question.getId());
            }
            subject.setAttemptedCount(subject.getAttemptedCount() + 1);
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    /**
     * 增加错题次数
     */
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

    /**
     * 添加错题
     */
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

    /**
     * 移除错题
     */
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

    /**
     * 获取错题列表
     */
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

    /**
     * 清空所有错题
     */
    public void clearAllWrongQuestions(String subjectId) {
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            for (Question q : subject.getQuestions()) {
                q.setWrong(false);
            }
            subject.setLastModified(System.currentTimeMillis());
            saveSubjects();
        }
    }

    /**
     * 更新题目收藏状态
     */
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
     * 更新题目内容（用于AI处理）
     */
    public void updateQuestion(String subjectId, Question question) {
        if (question == null || subjectId == null) return;
        
        Subject subject = subjects.get(subjectId);
        if (subject != null && subject.getQuestions() != null) {
            for (int i = 0; i < subject.getQuestions().size(); i++) {
                Question q = subject.getQuestions().get(i);
                if (q.getId().equals(question.getId())) {
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

    /**
     * 克隆题目列表
     */
    public List<Question> getClonedQuestions(List<Question> originalQuestions) {
        List<Question> clonedList = new ArrayList<>();
        if (originalQuestions != null) {
            for (Question q : originalQuestions) {
                clonedList.add(new Question(q));
            }
        }
        return clonedList;
    }

    /**
     * 重置用户答案
     */
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
     * 获取练习题目
     */
    public List<Question> getPracticeQuestions(String subjectId, boolean random) {
        Subject subject = subjects.get(subjectId);
        if (subject == null || subject.getQuestions() == null) return new ArrayList<>();

        List<Question> list = getClonedQuestions(subject.getQuestions());
        if (random) Collections.shuffle(list);
        return list;
    }

    /**
     * 获取模拟考试题目
     */
    public List<Question> getMockExamQuestions(String subjectId) {
        Subject subject = subjects.get(subjectId);
        if (subject == null || subject.getQuestions() == null) {
            return new ArrayList<>();
        }

        List<Question> singleChoice = new ArrayList<>();
        List<Question> multipleChoice = new ArrayList<>();
        List<Question> trueOrFalse = new ArrayList<>();

        for (Question question : subject.getQuestions()) {
            String type = question.getType();
            if ("单选题".equals(type)) {
                singleChoice.add(question);
            } else if ("多选题".equals(type)) {
                multipleChoice.add(question);
            } else if ("判断题".equals(type)) {
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

        return getClonedQuestions(exam);
    }

    /**
     * 按错误次数排序获取题目
     */
    public List<Question> getQuestionsSortedByWrongCount(String subjectId) {
        Subject subject = getSubject(subjectId);
        if (subject == null || subject.getQuestions() == null) {
            return new ArrayList<>();
        }
        List<Question> questions = new ArrayList<>(subject.getQuestions());
        questions.removeIf(q -> q.getWrongAnswerCount() == 0);
        questions.sort((q1, q2) -> Integer.compare(q2.getWrongAnswerCount(), q1.getWrongAnswerCount()));
        return questions;
    }

    /**
     * 题目评分
     */
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

    /**
     * 搜索题目
     */
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

    /**
     * 删除题库
     */
    public void deleteSubject(String subjectId) {
        subjects.remove(subjectId);
        saveSubjects();
    }

    /**
     * 添加考试历史记录
     */
    public void addExamHistoryEntry(ExamHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        entry.setLastModified(System.currentTimeMillis());
        examHistory.add(0, entry);
        saveExamHistory();
    }

    /**
     * 获取所有考试历史
     */
    public List<ExamHistoryEntry> getExamHistoryEntries() {
        return new ArrayList<>(examHistory);
    }

    /**
     * 获取指定题库的考试历史
     */
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

    /**
     * 清空指定题库的考试历史
     * @param subjectId 题库ID
     */
    public void clearExamHistory(String subjectId) {
        if (subjectId == null || subjectId.isEmpty()) {
            return;
        }
        examHistory.removeIf(entry -> subjectId.equals(entry.getSubjectId()));
        saveExamHistory();
    }

    /**
     * 清空所有考试历史
     */
    public void clearExamHistory() {
        examHistory.clear();
        saveExamHistory();
    }

    /**
     * 获取无尽模式最佳连击
     */
    public int getEndlessBestStreak(String subjectId) {
        Subject subject = subjects.get(subjectId);
        return subject != null ? subject.getEndlessBestStreak() : 0;
    }

    /**
     * 更新无尽模式最佳连击
     */
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

    /**
     * 保存题库数据
     */
    private void saveSubjects() {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(subjectsFile.toFile()), StandardCharsets.UTF_8)) {
            String json = gson.toJson(subjects);
            writer.write(json);
            System.out.println("题库数据已保存");
        } catch (IOException e) {
            System.err.println("保存题库数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 保存考试历史
     */
    private void saveExamHistory() {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(historyFile.toFile()), StandardCharsets.UTF_8)) {
            String json = gson.toJson(examHistory);
            writer.write(json);
            System.out.println("考试历史已保存");
        } catch (IOException e) {
            System.err.println("保存考试历史失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 加载题库数据
     */
    private void loadSubjects() {
        if (!Files.exists(subjectsFile)) {
            System.out.println("题库文件不存在，使用空题库");
            subjects = new HashMap<>();
            return;
        }

        try (Reader reader = new InputStreamReader(
                new FileInputStream(subjectsFile.toFile()), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Subject>>() {}.getType();
            subjects = gson.fromJson(reader, type);
            if (subjects == null) {
                subjects = new HashMap<>();
            }
            // 为每个加载的题库计算相对ID
            for (Subject subject : subjects.values()) {
                calculateRelativeIds(subject);
            }
            System.out.println("已加载 " + subjects.size() + " 个题库");
        } catch (Exception e) {
            System.err.println("加载题库数据失败: " + e.getMessage());
            System.err.println("JSON文件可能已损坏，将备份并使用空题库");
            
            // 备份损坏的文件
            try {
                Path backupFile = dataDir.resolve(SUBJECTS_FILE_NAME + ".corrupted." + System.currentTimeMillis());
                Files.copy(subjectsFile, backupFile);
                System.out.println("已备份损坏文件到: " + backupFile);
                Files.delete(subjectsFile);
            } catch (IOException ex) {
                System.err.println("备份失败: " + ex.getMessage());
            }
            
            subjects = new HashMap<>();
            e.printStackTrace();
        }
    }

    /**
     * 加载考试历史
     */
    private void loadExamHistory() {
        if (!Files.exists(historyFile)) {
            System.out.println("历史记录文件不存在");
            examHistory = new ArrayList<>();
            return;
        }

        try (Reader reader = new InputStreamReader(
                new FileInputStream(historyFile.toFile()), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<ExamHistoryEntry>>() {}.getType();
            examHistory = gson.fromJson(reader, type);
            if (examHistory == null) {
                examHistory = new ArrayList<>();
            }
            System.out.println("已加载 " + examHistory.size() + " 条历史记录");
        } catch (IOException e) {
            System.err.println("加载考试历史失败: " + e.getMessage());
            examHistory = new ArrayList<>();
            e.printStackTrace();
        }
    }

    /**
     * 清除缓存数据
     */
    public void clearCache() {
        // Clear in-memory data
        subjects.clear();
        examHistory.clear();
        
        // Reload from disk
        loadSubjects();
        loadExamHistory();
        
        System.out.println("题库缓存已清除");
    }

    /**
     * 获取数据目录路径
     */
    public Path getDataDirectory() {
        return dataDir;
    }

    /**
     * 计算并设置一个题库中所有题目的相对ID
     * @param subject 要处理的题库
     */
    private void calculateRelativeIds(Subject subject) {
        if (subject == null || subject.getQuestions() == null) {
            return;
        }

        Map<String, List<Question>> groupedQuestions = new LinkedHashMap<>();
        groupedQuestions.put("单选题", new ArrayList<>());
        groupedQuestions.put("多选题", new ArrayList<>());
        groupedQuestions.put("判断题", new ArrayList<>());

        // 首先按原始顺序分组
        for (Question q : subject.getQuestions()) {
            groupedQuestions.computeIfAbsent(q.getType(), k -> new ArrayList<>()).add(q);
        }

        // 为每个组内的题目设置relativeId
        for (List<Question> questionGroup : groupedQuestions.values()) {
            for (int i = 0; i < questionGroup.size(); i++) {
                questionGroup.get(i).setRelativeId(i + 1);
            }
        }
    }
}