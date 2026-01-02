package com.examapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import com.examapp.model.ExamHistoryEntry;

import androidx.appcompat.widget.Toolbar;

public class ResultActivity extends BaseActivity {

    private ExamHistoryEntry historyEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        historyEntry = (ExamHistoryEntry) getIntent().getSerializableExtra(ReviewActivity.EXTRA_HISTORY_ENTRY);
        if (historyEntry == null) {
            // Handle error, maybe finish the activity
            finish();
            return;
        }

        initializeUI();
        displayResults();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        Button viewAllButton = findViewById(R.id.view_all_button);
        viewAllButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, ReviewActivity.class);
            intent.putExtra(ReviewActivity.EXTRA_HISTORY_ENTRY, (java.io.Serializable) historyEntry);
            intent.putExtra(ReviewActivity.EXTRA_SHOW_ONLY_WRONG, false);
            startActivity(intent);
        });

        Button viewWrongButton = findViewById(R.id.view_wrong_button);
        viewWrongButton.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, ReviewActivity.class);
            intent.putExtra(ReviewActivity.EXTRA_HISTORY_ENTRY, (java.io.Serializable) historyEntry);
            intent.putExtra(ReviewActivity.EXTRA_SHOW_ONLY_WRONG, true);
            startActivity(intent);
        });
    }

    private void displayResults() {
        int totalQuestions = historyEntry.getTotalQuestions();
        int correctCount = historyEntry.getCorrectCount();
        int totalScore = historyEntry.getScore();
        int maxScore = historyEntry.getMaxScore();

        TextView totalQuestionsView = findViewById(R.id.total_questions);
        TextView correctCountView = findViewById(R.id.correct_count);
        TextView scoreView = findViewById(R.id.score);
        TextView accuracyView = findViewById(R.id.accuracy);

        totalQuestionsView.setText("总题数: " + totalQuestions);
        correctCountView.setText("正确: " + correctCount);
        scoreView.setText("得分: " + totalScore + " / " + maxScore);

        double accuracy = totalQuestions > 0 ? (double) correctCount / totalQuestions * 100 : 0;
        accuracyView.setText(String.format("准确度: %.1f%%", accuracy));
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
