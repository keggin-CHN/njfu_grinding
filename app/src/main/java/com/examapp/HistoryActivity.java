package com.examapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.examapp.adapter.HistoryAdapter;
import com.examapp.data.QuestionManager;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.util.XGBoostPredictor;
import com.examapp.widget.ScoreChartView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnHistoryEntryClickListener {
    private QuestionManager questionManager;
    private RecyclerView historyRecyclerView;
    private TextView emptyView;
    private ScoreChartView chartView;
    private TextView predictedScoreView;
    private String subjectId;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        subjectId = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_ID);
        subjectName = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_NAME);
        questionManager = QuestionManager.getInstance(this);

        initializeUI();
        loadHistory();
    }

    private void initializeUI() {
        if ((subjectName == null || subjectName.isEmpty()) && subjectId != null) {
            com.examapp.model.Subject subject = questionManager.getSubject(subjectId);
            if (subject != null) {
                subjectName = subject.getDisplayName();
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (subjectName != null && !subjectName.isEmpty()) {
            toolbar.setSubtitle(subjectName);
        }

        chartView = findViewById(R.id.score_chart_view);
        emptyView = findViewById(R.id.history_empty_view);
        historyRecyclerView = findViewById(R.id.history_recycler_view);
        predictedScoreView = findViewById(R.id.predicted_score_view);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadHistory() {
        List<ExamHistoryEntry> entries = questionManager.getExamHistoryEntries(subjectId);
        if (entries.isEmpty()) {
            emptyView.setVisibility(android.view.View.VISIBLE);
            predictedScoreView.setVisibility(android.view.View.GONE);
        } else {
            emptyView.setVisibility(android.view.View.GONE);
            predictAndShowScore(entries);
        }

        // For the chart, we want to show oldest to newest (left to right)
        List<ExamHistoryEntry> chartEntries = new ArrayList<>(entries);
        java.util.Collections.reverse(chartEntries);
        chartView.setEntries(chartEntries);

        // For the RecyclerView, we want newest on top, so we use the original list
        HistoryAdapter adapter = new HistoryAdapter(entries, this);
        historyRecyclerView.setAdapter(adapter);
    }

    private void predictAndShowScore(List<ExamHistoryEntry> entries) {
        if (entries.size() > 1) { // Need at least 2 scores to predict
            List<Double> scores = entries.stream()
                                         .map(entry -> (double) entry.getScore())
                                         .collect(Collectors.toList());
            
            double predictedScore = XGBoostPredictor.predict(scores);
            predictedScore = Math.min(predictedScore, 100.0); // Cap at 100

            predictedScoreView.setText(String.format("预期最终成绩: %.1f分", predictedScore));
            predictedScoreView.setVisibility(android.view.View.VISIBLE);
        } else {
            predictedScoreView.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public void onHistoryEntryClick(ExamHistoryEntry entry) {
        Intent intent = new Intent(this, ReviewActivity.class);
        intent.putExtra(ReviewActivity.EXTRA_HISTORY_ENTRY, (java.io.Serializable) entry);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}