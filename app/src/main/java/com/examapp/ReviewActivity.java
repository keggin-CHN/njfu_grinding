package com.examapp;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.adapter.ReviewAdapter;
import com.examapp.data.QuestionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;

import java.util.ArrayList;
import java.util.List;

public class ReviewActivity extends BaseActivity implements ReviewAdapter.OnItemClickListener {

    public static final String EXTRA_HISTORY_ENTRY = "history_entry";
    public static final String EXTRA_SHOW_ONLY_WRONG = "show_only_wrong";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ExamHistoryEntry historyEntry = getIntent().getParcelableExtra(EXTRA_HISTORY_ENTRY);
        boolean showOnlyWrong = getIntent().getBooleanExtra(EXTRA_SHOW_ONLY_WRONG, false);

        if (historyEntry == null) {
            finish();
            return;
        }

        setTitle(showOnlyWrong ? "错题回顾" : "全卷回顾");

        RecyclerView recyclerView = findViewById(R.id.review_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<ExamHistoryEntry.QuestionRecord> recordsToShow = new ArrayList<>();
        if (showOnlyWrong) {
            for (ExamHistoryEntry.QuestionRecord record : historyEntry.getQuestionRecords()) {
                if (!record.isCorrect()) {
                    recordsToShow.add(record);
                }
            }
        } else {
            recordsToShow.addAll(historyEntry.getQuestionRecords());
        }

        ReviewAdapter adapter = new ReviewAdapter(recordsToShow, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onItemClick(ExamHistoryEntry.QuestionRecord record) {
        QuestionManager questionManager = QuestionManager.getInstance(this);
        Question question = questionManager.getQuestionById(record.getQuestionId());
        if (question == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_question_details, null);
        builder.setView(dialogView);

        TextView questionText = dialogView.findViewById(R.id.dialog_question_text);
        TextView optionsText = dialogView.findViewById(R.id.dialog_options_text);
        TextView userAnswerText = dialogView.findViewById(R.id.dialog_user_answer_text);
        TextView correctAnswerText = dialogView.findViewById(R.id.dialog_correct_answer_text);
        Button starButton = dialogView.findViewById(R.id.dialog_star_button);

        questionText.setText(question.getQuestionText());

        StringBuilder options = new StringBuilder();
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            for (String option : question.getOptions()) {
                options.append(option).append("\n");
            }
        }
        optionsText.setText(options.toString());

        String userAnswer = record.getUserAnswer() != null ? record.getUserAnswer() : "未作答";
        userAnswerText.setText("你的答案: " + userAnswer);
        correctAnswerText.setText("正确答案: " + question.getAnswer());

        updateStarButton(starButton, question);

        starButton.setOnClickListener(v -> {
            question.setWrong(!question.isWrong());
            questionManager.updateQuestionStarStatus(question);
            updateStarButton(starButton, question);
        });

        builder.setTitle("题目详情")
                .setPositiveButton("关闭", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateStarButton(Button starButton, Question question) {
        if (question.isWrong()) {
            starButton.setText("取消星标");
        } else {
            starButton.setText("星标");
        }
    }
}
