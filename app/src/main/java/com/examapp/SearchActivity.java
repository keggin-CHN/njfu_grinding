package com.examapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.examapp.adapter.QuestionAdapter;
import com.examapp.data.QuestionManager;
import com.examapp.model.Question;
import com.examapp.model.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchActivity extends BaseActivity {
    private QuestionManager questionManager;
    private EditText searchInput;
    private RecyclerView searchResultsRecyclerView;
    private LinearLayout emptyStateLayout;
    private QuestionAdapter questionAdapter;
    private List<Question> allQuestions;
    private List<Question> filteredQuestions;
    private String subjectId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        questionManager = QuestionManager.getInstance(this);
        subjectId = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_ID);
        initializeUI();
        loadAllQuestions();
        setupSearch();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        searchInput = findViewById(R.id.search_input);
        searchResultsRecyclerView = findViewById(R.id.search_results_recycler_view);
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        emptyStateLayout = findViewById(R.id.empty_state_layout);
    }

    private void loadAllQuestions() {
        allQuestions = new ArrayList<>();
        
        if (subjectId != null) {
            Subject subject = questionManager.getSubject(subjectId);
            if (subject != null && subject.getQuestions() != null) {
                allQuestions.addAll(subject.getQuestions());
            }
        } else {
            Map<String, Subject> subjects = questionManager.getAllSubjects();
            for (Subject subject : subjects.values()) {
                if (subject.getQuestions() != null) {
                    allQuestions.addAll(subject.getQuestions());
                }
            }
        }
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void performSearch(String keyword) {
        filteredQuestions = new ArrayList<>();
        
        if (keyword.isEmpty()) {
            showEmptyState();
            return;
        }

        String lowerKeyword = keyword.toLowerCase();
        for (Question question : allQuestions) {
            if (question.getQuestionText().toLowerCase().contains(lowerKeyword)) {
                filteredQuestions.add(question);
            }
        }

        if (filteredQuestions.isEmpty()) {
            showEmptyState();
        } else {
            searchResultsRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);

            questionAdapter = new QuestionAdapter(filteredQuestions, null); // No click listener needed here
            searchResultsRecyclerView.setAdapter(questionAdapter);
        }
    }

    private void showEmptyState() {
        searchResultsRecyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
