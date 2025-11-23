package com.examapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Menu;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.adapter.SubjectExpandableAdapter;
import com.examapp.data.QuestionManager;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockExamActivity extends BaseActivity implements GestureDetector.OnGestureListener {
    private QuestionManager questionManager;
    private Subject subject;
    private List<Question> examQuestions = new ArrayList<>();
    private Map<Integer, String> answers = new HashMap<>();
    private int currentPosition;
    private String subjectId;
    private String subjectName;
    private GestureDetectorCompat gestureDetector;
    private boolean isBindingOptions;

    private DrawerLayout drawerLayout;
    private RecyclerView questionNavRecyclerView;
    private SubjectExpandableAdapter subjectExpandableAdapter;
    private ScrollView mockScrollView;
    private TextView questionNumberView;
    private TextView questionTextView;
    private TextView answeredProgressView;
    private RadioGroup optionsGroup;
    private Button previousButton;
    private Button nextButton;
    private Button submitButton;
    private Button favoriteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_exam);

        questionManager = QuestionManager.getInstance(this);
        subjectId = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_ID);
        subject = questionManager.getSubject(subjectId);
        subjectName = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_NAME);
        if ((subjectName == null || subjectName.isEmpty()) && subject != null) {
            subjectName = subject.getDisplayName();
        }
        gestureDetector = new GestureDetectorCompat(this, this);

        initializeUI();
        loadExamQuestions();
        displayCurrentQuestion();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        drawerLayout = findViewById(R.id.mock_drawer_layout);
        questionNavRecyclerView = findViewById(R.id.question_nav_recycler_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (subjectName != null && !subjectName.isEmpty()) {
            getSupportActionBar().setSubtitle(subjectName);
            TextView navHeaderTitle = findViewById(R.id.nav_header_title);
            TextView navHeaderSubtitle = findViewById(R.id.nav_header_subtitle);
            if (navHeaderTitle != null) {
                navHeaderTitle.setText(String.format("%s模拟考试", subjectName));
            }
            if (navHeaderSubtitle != null) {
                navHeaderSubtitle.setVisibility(View.GONE);
            }
        }

        mockScrollView = findViewById(R.id.mock_scroll_view);
        mockScrollView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        questionNumberView = findViewById(R.id.question_number);
        questionTextView = findViewById(R.id.question_text);
        answeredProgressView = findViewById(R.id.answered_progress);
        optionsGroup = findViewById(R.id.options_group);
        previousButton = findViewById(R.id.previous_button);
        nextButton = findViewById(R.id.next_button);
        submitButton = findViewById(R.id.submit_button);
        favoriteButton = findViewById(R.id.favorite_button);

        previousButton.setOnClickListener(v -> moveToPreviousQuestion());
        nextButton.setOnClickListener(v -> moveToNextQuestion());
        submitButton.setOnClickListener(v -> attemptSubmit());
        favoriteButton.setOnClickListener(v -> toggleStar());

        optionsGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!isBindingOptions) {
                captureCurrentAnswer();
            }
        });
    }

    private void loadExamQuestions() {
        questionManager.resetUserAnswers(subjectId); 
        if (subject != null) {
            examQuestions = questionManager.getMockExamQuestions(subjectId);
        } else {
            examQuestions = new ArrayList<>();
        }
        answers = new HashMap<>();
        currentPosition = 0;
    }

    private void displayCurrentQuestion() {
        if (examQuestions == null || examQuestions.isEmpty()) {
            Toast.makeText(this, "没有题目", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Question question = examQuestions.get(currentPosition);
        String typeText = question.getType() != null ? question.getType() : "未知";
        questionNumberView.setText(String.format("【%s】 第 %d / %d 题", typeText, currentPosition + 1, examQuestions.size()));
        questionTextView.setText(question.getQuestionText());

        isBindingOptions = true;
        optionsGroup.removeAllViews();

        List<String> opts = question.getOptions();
        boolean isMultipleChoice = "多选题".equals(question.getType());

        if ((opts == null || opts.isEmpty()) && isTrueFalseQuestion(question)) {
            opts = new ArrayList<>();
            opts.add("A. " + getString(R.string.option_true));
            opts.add("B. " + getString(R.string.option_false));
        }

        if (opts != null) {
            if (isMultipleChoice) {
                for (String option : opts) {
                    CheckBox cb = new CheckBox(this);
                    cb.setText(option);
                    cb.setTextSize(16);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMargins(0, 16, 0, 16);
                    cb.setLayoutParams(lp);
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (!isBindingOptions) {
                            captureCurrentAnswer();
                        }
                    });
                    optionsGroup.addView(cb);
                }
            } else {
                for (String option : opts) {
                    RadioButton rb = new RadioButton(this);
                    rb.setText(option);
                    rb.setTextSize(16);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMargins(0, 16, 0, 16);
                    rb.setLayoutParams(lp);
                    optionsGroup.addView(rb);
                }
            }
        }

        String savedAnswer = answers.get(currentPosition);
        if (savedAnswer != null) {
            if (isMultipleChoice) {
                for (int i = 0; i < optionsGroup.getChildCount(); i++) {
                    android.view.View child = optionsGroup.getChildAt(i);
                    if (child instanceof CheckBox) {
                        CheckBox cb = (CheckBox) child;
                        String optionLetter = cb.getText().toString().substring(0, 1);
                        if (savedAnswer.contains(optionLetter)) {
                            cb.setChecked(true);
                        }
                    }
                }
            } else {
                for (int i = 0; i < optionsGroup.getChildCount(); i++) {
                    RadioButton rb = (RadioButton) optionsGroup.getChildAt(i);
                    if (rb.getText().toString().startsWith(savedAnswer)) {
                        rb.setChecked(true);
                        break;
                    }
                }
            }
        } else {
            if (!isMultipleChoice) {
                optionsGroup.clearCheck();
            }
        }
        isBindingOptions = false;

        updateFavoriteButtonLabel(question);
        updateAnsweredProgress();
        updateNavigationButtons();
        updateQuestionNavigation();
    }

    private void updateQuestionNavigation() {
        if (subjectExpandableAdapter == null) {
            List<Object> items = groupQuestionsByType(examQuestions);
            subjectExpandableAdapter = new SubjectExpandableAdapter(items, answers, currentPosition, position -> {
                currentPosition = position;
                displayCurrentQuestion();
                drawerLayout.closeDrawer(GravityCompat.START);
            }, false, examQuestions); // isReviewMode is false for mock exam

            GridLayoutManager layoutManager = new GridLayoutManager(this, 5);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return subjectExpandableAdapter.getItemViewType(position) == SubjectExpandableAdapter.TYPE_HEADER ? 5 : 1;
                }
            });
            questionNavRecyclerView.setLayoutManager(layoutManager);
            questionNavRecyclerView.setAdapter(subjectExpandableAdapter);
        } else {
            subjectExpandableAdapter.setCurrentQuestionIndex(currentPosition);
            subjectExpandableAdapter.updateAnswers(new HashMap<>(answers));
        }
    }

    private List<Object> groupQuestionsByType(List<Question> questions) {
        List<Object> items = new ArrayList<>();
        Map<String, List<Question>> groupedQuestions = new LinkedHashMap<>();
        groupedQuestions.put("单选题", new ArrayList<>());
        groupedQuestions.put("多选题", new ArrayList<>());
        groupedQuestions.put("判断题", new ArrayList<>());

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.setIndex(i);
            if ("多选题".equals(q.getType())) {
                groupedQuestions.get("多选题").add(q);
            } else if ("判断题".equals(q.getType())) {
                groupedQuestions.get("判断题").add(q);
            } else {
                groupedQuestions.get("单选题").add(q);
            }
        }

        for (Map.Entry<String, List<Question>> entry : groupedQuestions.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                items.add(entry.getKey());
                int relativeIdCounter = 1;
                for (Question q : entry.getValue()) {
                    q.setRelativeId(relativeIdCounter++);
                }
                items.addAll(entry.getValue());
            }
        }
        return items;
    }

    private void updateFavoriteButtonLabel(Question question) {
        favoriteButton.setText(question.isWrong() ? R.string.unstar : R.string.star);
    }

    private void updateAnsweredProgress() {
        int answered = 0;
        for (int i = 0; i < examQuestions.size(); i++) {
            if (answers.containsKey(i)) {
                answered++;
            }
        }
        answeredProgressView.setText(getString(R.string.answered_progress, answered, examQuestions.size()));
    }

    private void updateNavigationButtons() {
        previousButton.setEnabled(currentPosition > 0);
        nextButton.setEnabled(currentPosition < examQuestions.size() - 1);
    }

    private void moveToNextQuestion() {
        captureCurrentAnswer();
        if (currentPosition < examQuestions.size() - 1) {
            currentPosition++;
            displayCurrentQuestion();
        }
    }

    private void moveToPreviousQuestion() {
        captureCurrentAnswer();
        if (currentPosition > 0) {
            currentPosition--;
            displayCurrentQuestion();
        }
    }

    private void captureCurrentAnswer() {
        Question question = examQuestions.get(currentPosition);
        boolean isMultipleChoice = "多选题".equals(question.getType());
        String userAnswer = "";

        if (isMultipleChoice) {
            StringBuilder userAnswerBuilder = new StringBuilder();
            for (int i = 0; i < optionsGroup.getChildCount(); i++) {
                View child = optionsGroup.getChildAt(i);
                if (child instanceof CheckBox && ((CheckBox) child).isChecked()) {
                    String optionText = ((CheckBox) child).getText().toString();
                    userAnswerBuilder.append(optionText.substring(0, 1));
                }
            }
            userAnswer = userAnswerBuilder.toString();
        } else {
            int selectedId = optionsGroup.getCheckedRadioButtonId();
            if (selectedId != -1) {
                RadioButton selectedButton = findViewById(selectedId);
                String selectedText = selectedButton.getText().toString();
                if (!selectedText.isEmpty()) {
                    userAnswer = selectedText.substring(0, 1);
                }
            }
        }

        if (userAnswer.isEmpty()) {
            answers.remove(currentPosition);
        } else {
            answers.put(currentPosition, userAnswer);
        }
        updateAnsweredProgress();
        updateQuestionNavigation();
    }

    private void toggleStar() {
        if (examQuestions == null || examQuestions.isEmpty()) {
            return;
        }
        Question question = examQuestions.get(currentPosition);
        int originalIndex = getOriginalQuestionIndex(currentPosition);
        if (question.isWrong()) {
            questionManager.removeWrongQuestion(subjectId, originalIndex);
            Toast.makeText(this, R.string.star_removed, Toast.LENGTH_SHORT).show();
        } else {
            questionManager.addWrongQuestion(subjectId, originalIndex);
            Toast.makeText(this, R.string.star_added, Toast.LENGTH_SHORT).show();
        }
        question.setWrong(!question.isWrong());
        updateFavoriteButtonLabel(question);
    }

    private void attemptSubmit() {
        captureCurrentAnswer();
        List<Integer> unanswered = findUnansweredQuestions();
        String message;
        if (!unanswered.isEmpty()) {
            message = getString(R.string.confirm_submit_exam_with_count, unanswered.size());
        } else {
            message = getString(R.string.confirm_submit_exam_all_answered);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.submit_exam_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.submit_exam, (dialog, which) -> finalizeExam())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private List<Integer> findUnansweredQuestions() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < examQuestions.size(); i++) {
            if (!answers.containsKey(i)) {
                result.add(i);
            }
        }
        return result;
    }

    private void finalizeExam() {
        int totalScore = 0;
        int correctCount = 0;
        int answeredQuestions = 0;
        int maxScore = calculateMaxScore();
        List<ExamHistoryEntry.QuestionRecord> records = new ArrayList<>();

        for (int i = 0; i < examQuestions.size(); i++) {
            Question question = examQuestions.get(i);
            String userAnswer = answers.get(i);
            question.setUserAnswer(userAnswer); // Set user answer for correct evaluation
            if (userAnswer != null && !userAnswer.isEmpty()) {
                answeredQuestions++;
            }
            boolean isCorrect = question.isAnsweredCorrectly();
            int originalIndex = getOriginalQuestionIndex(i);

            if (isCorrect) {
                totalScore += questionManager.scoreQuestion(subjectId, question);
                correctCount++;
                if (question.isWrong()) {
                    questionManager.removeWrongQuestion(subjectId, originalIndex);
                }
            } else {
                if (!question.isWrong()) {
                    questionManager.addWrongQuestion(subjectId, originalIndex);
                }
                questionManager.incrementWrongAnswerCount(question.getId());
            }

            ExamHistoryEntry.QuestionRecord record = new ExamHistoryEntry.QuestionRecord();
            record.setQuestionId(question.getId());
            record.setQuestionText(question.getQuestionText());
            record.setCorrectAnswer(question.getAnswer());
            record.setUserAnswer(userAnswer);
            record.setCorrect(isCorrect);
            records.add(record);
        }

        ExamHistoryEntry entry = new ExamHistoryEntry();
        entry.setTimestamp(System.currentTimeMillis());
        entry.setSubjectId(subjectId);
        entry.setSubjectName(subjectName != null ? subjectName : getString(R.string.app_name));
        entry.setTotalQuestions(examQuestions.size());
        entry.setAnsweredQuestions(answeredQuestions);
        entry.setScore(totalScore);
        entry.setMaxScore(maxScore);
        entry.setQuestionRecords(records);
        questionManager.addExamHistoryEntry(entry);

        Intent resultIntent = new Intent(this, ResultActivity.class);
        resultIntent.putExtra(ReviewActivity.EXTRA_HISTORY_ENTRY, (java.io.Serializable) entry);
        startActivity(resultIntent);
        finish();
    }

    private int calculateMaxScore() {
        int maxScore = 0;
        for (Question question : examQuestions) {
            maxScore += questionManager.scoreQuestion(subjectId, question);
        }
        return maxScore;
    }

    private int getOriginalQuestionIndex(int position) {
        if (subject == null || subject.getQuestions() == null || position < 0 || position >= examQuestions.size()) {
            return position;
        }
        Question question = examQuestions.get(position);
        int index = subject.getQuestions().indexOf(question);
        return index >= 0 ? index : position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 != null && e2 != null) {
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (diffX > 100) {
                    moveToPreviousQuestion();
                    return true;
                } else if (diffX < -100) {
                    moveToNextQuestion();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            onBackPressed();
        }
        return true;
    }

    private boolean isTrueFalseQuestion(Question q) {
        String cat = q.getCategory() != null ? q.getCategory().toLowerCase() : "";
        String type = q.getType() != null ? q.getType().toLowerCase() : cat;
        return type.contains("判断") || type.contains("true") || type.contains("false");
    }
}
