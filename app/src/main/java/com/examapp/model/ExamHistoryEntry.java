package com.examapp.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExamHistoryEntry implements Parcelable, Serializable {
    private long timestamp;
    private String subjectId;
    private String subjectName;
    private int totalQuestions;
    private int answeredQuestions;
    private int score;
    private int maxScore;
    private List<QuestionRecord> questionRecords = new ArrayList<>();

    public ExamHistoryEntry() {}

    protected ExamHistoryEntry(Parcel in) {
        timestamp = in.readLong();
        subjectId = in.readString();
        subjectName = in.readString();
        totalQuestions = in.readInt();
        answeredQuestions = in.readInt();
        score = in.readInt();
        maxScore = in.readInt();
        questionRecords = in.createTypedArrayList(QuestionRecord.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp);
        dest.writeString(subjectId);
        dest.writeString(subjectName);
        dest.writeInt(totalQuestions);
        dest.writeInt(answeredQuestions);
        dest.writeInt(score);
        dest.writeInt(maxScore);
        dest.writeTypedList(questionRecords);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ExamHistoryEntry> CREATOR = new Creator<ExamHistoryEntry>() {
        @Override
        public ExamHistoryEntry createFromParcel(Parcel in) {
            return new ExamHistoryEntry(in);
        }

        @Override
        public ExamHistoryEntry[] newArray(int size) {
            return new ExamHistoryEntry[size];
        }
    };

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public int getAnsweredQuestions() {
        return answeredQuestions;
    }

    public void setAnsweredQuestions(int answeredQuestions) {
        this.answeredQuestions = answeredQuestions;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public List<QuestionRecord> getQuestionRecords() {
        return questionRecords;
    }

    public void setQuestionRecords(List<QuestionRecord> questionRecords) {
        this.questionRecords = questionRecords;
    }

    public int getCorrectCount() {
        int count = 0;
        for (QuestionRecord record : questionRecords) {
            if (record.isCorrect()) {
                count++;
            }
        }
        return count;
    }

    public static class QuestionRecord implements Parcelable, Serializable {
        private String questionId;
        private String questionText;
        private String correctAnswer;
        private String userAnswer;
        private boolean correct;

        public QuestionRecord() {}

        protected QuestionRecord(Parcel in) {
            questionId = in.readString();
            questionText = in.readString();
            correctAnswer = in.readString();
            userAnswer = in.readString();
            correct = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(questionId);
            dest.writeString(questionText);
            dest.writeString(correctAnswer);
            dest.writeString(userAnswer);
            dest.writeByte((byte) (correct ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<QuestionRecord> CREATOR = new Creator<QuestionRecord>() {
            @Override
            public QuestionRecord createFromParcel(Parcel in) {
                return new QuestionRecord(in);
            }

            @Override
            public QuestionRecord[] newArray(int size) {
                return new QuestionRecord[size];
            }
        };

        public String getQuestionId() { return questionId; }

        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public String getQuestionText() {
            return questionText;
        }

        public void setQuestionText(String questionText) {
            this.questionText = questionText;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public void setCorrectAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        public String getUserAnswer() {
            return userAnswer;
        }

        public void setUserAnswer(String userAnswer) {
            this.userAnswer = userAnswer;
        }

        public boolean isCorrect() {
            return correct;
        }

        public void setCorrect(boolean correct) {
            this.correct = correct;
        }
    }
}
