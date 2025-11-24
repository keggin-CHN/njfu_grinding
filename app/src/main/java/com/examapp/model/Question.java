package com.examapp.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class Question implements Serializable {
    private String id;
    private String questionText;
    private List<String> options;
    private String answer;
    private String explanation;
    private String category;
    private boolean isWrong;
    private String userAnswer;
    private int index;
    private String type;
    private int relativeId;
    private int wrongAnswerCount; // 新增：错题次数统计

    // 新增：拷贝构造函数
    public Question(Question other) {
        this.id = other.id;
        this.questionText = other.questionText;
        this.options = new java.util.ArrayList<>(other.options);
        this.answer = other.answer;
        this.explanation = other.explanation;
        this.category = other.category;
        this.isWrong = other.isWrong;
        this.userAnswer = other.userAnswer; // 保留用户答案以便在侧边栏显示状态
        this.index = other.index;
        this.type = other.type;
        this.relativeId = other.relativeId;
        this.wrongAnswerCount = other.wrongAnswerCount;
        this.answerState = other.answerState; // 保留答题状态以便在侧边栏显示
    }

    public enum AnswerState {
        UNANSWERED,
        ANSWERED, // For mock exam
        CORRECT,
        WRONG
    }

    private AnswerState answerState = AnswerState.UNANSWERED;


    public Question() {
    }

    public Question(String questionText, List<String> options, String answer, String category) {
        this.questionText = questionText;
        this.options = options;
        this.answer = answer;
        this.category = category;
        this.isWrong = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getAnswer() {
        return answer;
    }

    public String getFormattedAnswer() {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        if ("多选题".equals(type) && answer.length() > 1) {
            StringBuilder formatted = new StringBuilder();
            for (char c : answer.toCharArray()) {
                formatted.append(c).append(", ");
            }
            return formatted.substring(0, formatted.length() - 2);
        }
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isWrong() {
        return isWrong;
    }

    public void setWrong(boolean wrong) {
        isWrong = wrong;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    private String sortString(String str) {
        if (str == null) return null;
        char[] chars = str.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    public boolean isAnsweredCorrectly() {
        if (answer == null || userAnswer == null) {
            return false;
        }

        if ("多选题".equals(type)) {
            return sortString(answer).equals(sortString(userAnswer));
        }

        if ("判断题".equals(type)) {
            String convertedAnswer = userAnswer;
            if ("A".equals(userAnswer)) {
                convertedAnswer = "正确";
            } else if ("B".equals(userAnswer)) {
                convertedAnswer = "错误";
            }
            return answer.equals(convertedAnswer);
        }

        return answer.equals(userAnswer);
    }

    // ★ 关键：题目类型 getter/setter，对应你代码里的 question.getType() / setType()
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getRelativeId() {
        return relativeId;
    }

    public void setRelativeId(int relativeId) {
        this.relativeId = relativeId;
    }

    public AnswerState getAnswerState() {
        return answerState;
    }

    public void setAnswerState(AnswerState answerState) {
        this.answerState = answerState;
    }

    public int getWrongAnswerCount() {
        return wrongAnswerCount;
    }

    public void setWrongAnswerCount(int wrongAnswerCount) {
        this.wrongAnswerCount = wrongAnswerCount;
    }

    public void incrementWrongAnswerCount() {
        this.wrongAnswerCount++;
    }
}
