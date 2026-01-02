package com.examapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import androidx.appcompat.widget.Toolbar;
import com.examapp.data.QuestionManager;
import com.examapp.util.BackgroundApplier;

public class StudyModeActivity extends BaseActivity {

    public static final String EXTRA_SUBJECT_ID = "com.examapp.SUBJECT_ID";
    public static final String EXTRA_SUBJECT_NAME = "com.examapp.SUBJECT_NAME";

    private String subjectId;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_mode);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        subjectId = getIntent().getStringExtra(EXTRA_SUBJECT_ID);
        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT_NAME);
        setTitle(subjectName);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        BackgroundApplier.apply(this);
        setupClickListeners();
    }

    private void setupClickListeners() {
        LinearLayout studyModeCard = findViewById(R.id.study_mode_card);
        LinearLayout sequentialPracticeCard = findViewById(R.id.sequential_practice_card);
        LinearLayout randomPracticeCard = findViewById(R.id.random_practice_card);
        LinearLayout endlessModeCard = findViewById(R.id.endless_mode_card);
        LinearLayout wrongReviewCard = findViewById(R.id.wrong_review_card);
        LinearLayout mockExamCard = findViewById(R.id.mock_exam_card);
        LinearLayout wrongQuestionsCard = findViewById(R.id.wrong_questions_card);
        LinearLayout searchCard = findViewById(R.id.search_card);
        LinearLayout examHistoryCard = findViewById(R.id.exam_history_card);
        LinearLayout wrongAnalysisCard = findViewById(R.id.wrong_analysis_card);

        studyModeCard.setOnClickListener(v -> startStudyMode());
        sequentialPracticeCard.setOnClickListener(v -> startPractice(false));
        randomPracticeCard.setOnClickListener(v -> startPractice(true));
        endlessModeCard.setOnClickListener(v -> startEndlessMode());
        wrongReviewCard.setOnClickListener(v -> startWrongReview());
        mockExamCard.setOnClickListener(v -> startMockExam());
        wrongQuestionsCard.setOnClickListener(v -> startWrongQuestions());
        searchCard.setOnClickListener(v -> startSearch());
        examHistoryCard.setOnClickListener(v -> startHistory());
        wrongAnalysisCard.setOnClickListener(v -> startWrongAnalysis());
    }

    private void startStudyMode() {
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        intent.putExtra(PracticeActivity.EXTRA_MODE, PracticeActivity.MODE_REVIEW);
        startActivity(intent);
    }

    private void startPractice(boolean isRandom) {
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        intent.putExtra(PracticeActivity.EXTRA_MODE, isRandom ? PracticeActivity.MODE_RANDOM : PracticeActivity.MODE_SEQUENTIAL);
        startActivity(intent);
    }

    private void startEndlessMode() {
        Intent intent = new Intent(this, EndlessModeActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        startActivity(intent);
    }

    private void startWrongQuestions() {
        Intent intent = new Intent(this, WrongQuestionsActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        startActivity(intent);
    }

    private void startWrongReview() {
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        intent.putExtra(PracticeActivity.EXTRA_MODE, PracticeActivity.MODE_WRONG_REVIEW);
        startActivity(intent);
    }

    private void startSearch() {
        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        startActivity(intent);
    }

    private void startHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        startActivity(intent);
    }

    private void startMockExam() {
        Intent intent = new Intent(this, MockExamActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        startActivity(intent);
    }

    private void startWrongAnalysis() {
        Intent intent = new Intent(this, WrongAnalysisActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_study_mode, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_subject) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
