package com.examapp.model;

import java.io.Serializable;
import java.util.List;

public class SyncData implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Subject> subjects;
    private List<ExamHistoryEntry> examHistory;

    public SyncData(List<Subject> subjects, List<ExamHistoryEntry> examHistory) {
        this.subjects = subjects;
        this.examHistory = examHistory;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
    }

    public List<ExamHistoryEntry> getExamHistory() {
        return examHistory;
    }

    public void setExamHistory(List<ExamHistoryEntry> examHistory) {
        this.examHistory = examHistory;
    }
}