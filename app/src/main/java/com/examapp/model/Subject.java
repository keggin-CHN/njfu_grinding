package com.examapp.model;

import java.io.Serializable;
import java.util.List;

public class Subject implements Serializable {
    private String id;
    private String name;
    private String displayName;
    private int totalQuestions;
    private List<Question> questions;
    private int lastPosition;
    private int correctCount;
    private int attemptedCount;
    private int endlessBestStreak;
    private int sequentialLastPosition;
    private int reviewLastPosition;
    private int wrongReviewLastPosition;
    private int sortOrder; // 排序顺序
    private long lastModified; // 新增：最后修改时间

    public Subject() {
        this.sortOrder = 0;
        this.lastModified = System.currentTimeMillis();
    }

    public Subject(String id, String name) {
        this.id = id;
        this.name = name;
        this.displayName = name;
        this.lastPosition = 0;
        this.lastModified = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
        this.totalQuestions = questions != null ? questions.size() : 0;
    }

    public int getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(int lastPosition) {
        this.lastPosition = lastPosition;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getAttemptedCount() {
        return attemptedCount;
    }

    public void setAttemptedCount(int attemptedCount) {
        this.attemptedCount = attemptedCount;
    }

    public int getEndlessBestStreak() {
        return endlessBestStreak;
    }

    public void setEndlessBestStreak(int endlessBestStreak) {
        this.endlessBestStreak = endlessBestStreak;
    }

    public double getAccuracy() {
        if (attemptedCount == 0) return 0;
        return (double) correctCount / attemptedCount * 100;
    }

    public int getSequentialLastPosition() {
        return sequentialLastPosition;
    }

    public void setSequentialLastPosition(int sequentialLastPosition) {
        this.sequentialLastPosition = sequentialLastPosition;
    }

    public int getReviewLastPosition() {
        return reviewLastPosition;
    }

    public void setReviewLastPosition(int reviewLastPosition) {
        this.reviewLastPosition = reviewLastPosition;
    }

    public int getWrongReviewLastPosition() {
        return wrongReviewLastPosition;
    }

    public void setWrongReviewLastPosition(int wrongReviewLastPosition) {
        this.wrongReviewLastPosition = wrongReviewLastPosition;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * 根据题目列表重新计算统计数据，例如正确数、已答数等。
     */
    public void recalculateStats() {
        if (questions == null) {
            this.attemptedCount = 0;
            this.correctCount = 0;
            this.totalQuestions = 0;
            return;
        }

        this.totalQuestions = questions.size();
        int attempted = 0;
        int correct = 0;
        for (Question q : questions) {
            if (q.getAnswerState() != Question.AnswerState.UNANSWERED) {
                attempted++;
                if (q.getAnswerState() == Question.AnswerState.CORRECT) {
                    correct++;
                }
            }
        }
        this.attemptedCount = attempted;
        this.correctCount = correct;
    }
}
