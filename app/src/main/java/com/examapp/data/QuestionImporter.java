package com.examapp.data;

import android.content.Context;

import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestionImporter {
    private Context context;
    private QuestionManager questionManager;

    public QuestionImporter(Context context) {
        this.context = context;
        this.questionManager = QuestionManager.getInstance(context);
    }

    public Subject importFromJson(InputStream inputStream, String subjectName) throws Exception {
        String json = readInputStream(inputStream);
        JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);

        String subjectId = UUID.randomUUID().toString();
        Subject subject = new Subject(subjectId, subjectName);

        List<Question> questions = new ArrayList<>();
        for (String typeKey : jsonObject.keySet()) {
            JsonObject typeObj = jsonObject.getAsJsonObject(typeKey);
            String questionType = determineQuestionType(typeKey);

            for (String questionKey : typeObj.keySet()) {
                JsonObject questionObj = typeObj.getAsJsonObject(questionKey);

                Question question = new Question();
                question.setId(UUID.randomUUID().toString());
                question.setQuestionText(questionKey);
                question.setCategory(typeKey);
                question.setType(questionType);

                if (questionObj.has("answer")) {
                    String answer = questionObj.get("answer").getAsString();
                    question.setAnswer(extractAnswer(answer));
                }

                if (questionObj.has("options")) {
                    List<String> options = new ArrayList<>();
                    JsonElement optionsElement = questionObj.get("options");
                    if (optionsElement.isJsonArray()) {
                        for (JsonElement option : optionsElement.getAsJsonArray()) {
                            options.add(option.getAsString());
                        }
                    }
                    question.setOptions(options);
                } else if ("判断题".equals(questionType)) {
                    List<String> options = new ArrayList<>();
                    options.add("A. 正确");
                    options.add("B. 错误");
                    question.setOptions(options);
                }

                if (questionObj.has("explanation")) {
                    question.setExplanation(questionObj.get("explanation").getAsString());
                }

                questions.add(question);
            }
        }

        subject.setQuestions(questions);
        questionManager.addSubject(subject);
        return subject;
    }

    public Subject importFromAsset(String assetFileName, String subjectName) throws Exception {
        InputStream inputStream = context.getAssets().open(assetFileName);
        return importFromJson(inputStream, subjectName);
    }

    private String readInputStream(InputStream inputStream) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }
        return stringBuilder.toString();
    }

    private String extractAnswer(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        StringBuilder extracted = new StringBuilder();
        for (char c : answer.toCharArray()) {
            if (Character.isUpperCase(c) && Character.isLetter(c)) {
                extracted.append(c);
            }
        }
        if (extracted.length() > 0) {
            return extracted.toString();
        }
        return answer;
    }

    private String determineQuestionType(String typeKey) {
        if (typeKey.contains("单选")) {
            return "单选题";
        } else if (typeKey.contains("多选")) {
            return "多选题";
        } else if (typeKey.contains("判断")) {
            return "判断题";
        } else {
            return "单选题";
        }
    }
}
