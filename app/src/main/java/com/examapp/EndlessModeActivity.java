package com.examapp;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GestureDetectorCompat;
import com.examapp.data.QuestionManager;
import com.examapp.data.AISettingsManager;
import com.examapp.data.AICacheManager;
import com.examapp.service.AIService;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.examapp.util.DraggableFABHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import io.noties.markwon.Markwon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EndlessModeActivity extends BaseActivity implements GestureDetector.OnGestureListener {

    private QuestionManager questionManager;
    private AISettingsManager aiSettingsManager;
    private AICacheManager aiCacheManager;
    private AIService aiService;
    private Markwon markwon;
    private Subject subject;
    private List<Question> questions;
    private int currentPosition;
    private int currentStreak;
    private int bestStreak;
    private String subjectId;
    private String subjectName;
    private GestureDetectorCompat gestureDetector;
    private boolean isBinding;
    private FloatingActionButton aiAssistantButton;
    private DraggableFABHelper draggableFABHelper;

    private TextView questionNumberView;
    private TextView questionTypeView;
    private TextView questionTextView;
    private LinearLayout optionsGroup;
    private TextView currentStreakView;
    private TextView bestStreakView;
    private TextView feedbackText;
    private LinearLayout feedbackLayout;
    private Button favoriteButton;
    private Button previousButton;
    private Button nextButton;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_endless_mode);

        subjectId = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_ID);
        subjectName = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_NAME);
        questionManager = QuestionManager.getInstance(this);
        aiSettingsManager = AISettingsManager.getInstance(this);
        aiCacheManager = AICacheManager.getInstance(this);
        aiService = AIService.getInstance(this);
        markwon = Markwon.create(this);
        subject = questionManager.getSubject(subjectId);
        if ((subjectName == null || subjectName.isEmpty()) && subject != null) {
            subjectName = subject.getDisplayName();
        }
        bestStreak = questionManager.getEndlessBestStreak(subjectId);

        gestureDetector = new GestureDetectorCompat(this, this);

        initializeUI();
        loadQuestions();
        displayQuestion();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (subjectName != null && !subjectName.isEmpty()) {
            toolbar.setSubtitle(subjectName);
        }

        scrollView = findViewById(R.id.endless_scroll_view);
        scrollView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        });

        questionNumberView = findViewById(R.id.question_number);
        questionTypeView = findViewById(R.id.question_type);
        questionTextView = findViewById(R.id.question_text);
        currentStreakView = findViewById(R.id.current_streak_view);
        bestStreakView = findViewById(R.id.best_streak_view);
        optionsGroup = findViewById(R.id.options_group);
        feedbackText = findViewById(R.id.feedback_text);
        feedbackLayout = findViewById(R.id.feedback_layout);
        favoriteButton = findViewById(R.id.favorite_button);
        previousButton = findViewById(R.id.previous_button);
        nextButton = findViewById(R.id.next_button);
        aiAssistantButton = findViewById(R.id.ai_assistant_button);

        // 设置拖动功能
        draggableFABHelper = new DraggableFABHelper();
        draggableFABHelper.makeDraggable(aiAssistantButton, v -> showAIDialog());
        
        favoriteButton.setOnClickListener(v -> toggleStar());
        previousButton.setOnClickListener(v -> moveToPreviousQuestion());
        nextButton.setOnClickListener(v -> moveToNextQuestion());

        updateStreakViews();
    }

    private void loadQuestions() {
        questionManager.resetUserAnswers(subjectId); // 重置用户答案
        questions = questionManager.getPracticeQuestions(subjectId, true);
        if (questions == null || questions.isEmpty()) {
            Toast.makeText(this, "没有题目", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentPosition = 0;
    }

    private boolean isTrueFalseQuestion(Question q) {
        String cat = q.getCategory() != null ? q.getCategory().toLowerCase() : "";
        String type = q.getType() != null ? q.getType().toLowerCase() : cat;
        return type.contains("判断") || type.contains("true") || type.contains("false");
    }

    private void displayQuestion() {
        if (questions == null || questions.isEmpty()) return;

        Question question = questions.get(currentPosition);
        questionNumberView.setText(String.format("第 %d 题", currentPosition + 1));
        questionTypeView.setText(question.getType());
        questionTextView.setText(question.getQuestionText());
        updateFavoriteButtonText(question);

        optionsGroup.removeAllViews();
        isBinding = true;

        List<String> opts = question.getOptions();
        if ((opts == null || opts.isEmpty()) && isTrueFalseQuestion(question)) {
            opts = new ArrayList<>();
            opts.add("A. " + getString(R.string.option_true));
            opts.add("B. " + getString(R.string.option_false));
        }

        if (opts != null) {
            boolean isMultipleChoice = "多选题".equals(question.getType());
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
                    optionsGroup.addView(cb);
                }
                Button submitButton = new Button(this);
                submitButton.setText("确认答案");
                submitButton.setOnClickListener(v -> evaluateAnswer());
                optionsGroup.addView(submitButton);
            } else {
                RadioGroup radioGroup = new RadioGroup(this);
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
                    radioGroup.addView(rb);
                }
                radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                    if (!isBinding && checkedId != -1) {
                        evaluateAnswer();
                    }
                });
                optionsGroup.addView(radioGroup);
            }
        }
        isBinding = false;
        feedbackLayout.setVisibility(LinearLayout.GONE);
        updateNavigationButtons();
    }

    private void evaluateAnswer() {
        Question question = questions.get(currentPosition);
        if (question == null) return;

        String userAnswer = "";
        boolean isMultipleChoice = "多选题".equals(question.getType());

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
            if (userAnswer.isEmpty()) {
                Toast.makeText(this, "请选择答案", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            RadioGroup radioGroup = (RadioGroup) optionsGroup.getChildAt(0);
            if (radioGroup.getCheckedRadioButtonId() != -1) {
                RadioButton selected = findViewById(radioGroup.getCheckedRadioButtonId());
                String selectedText = selected.getText().toString();
                if (!selectedText.isEmpty()) {
                    userAnswer = selectedText.substring(0, 1);
                }
            }
        }
        
        if (userAnswer.isEmpty()) return;

        question.setUserAnswer(userAnswer);
        boolean correct = question.isAnsweredCorrectly();
        int originalIndex = getOriginalIndex(question);
        feedbackLayout.setVisibility(LinearLayout.VISIBLE);

        if (correct) {
            feedbackText.setText("✓ 正确!");
            feedbackText.setTextColor(getColor(R.color.success));
            currentStreak++;
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak;
                questionManager.updateEndlessBestStreak(subjectId, bestStreak);
            }
            updateStreakViews();
            

            if (question.isWrong()) {
                question.setWrong(false);
                questionManager.removeWrongQuestion(subjectId, originalIndex);
                updateFavoriteButtonText(question);
            }
            
            optionsGroup.postDelayed(this::moveToNextQuestion, 400);
        } else {
            feedbackText.setText("✗ 错误! 正确答案是: " + question.getAnswer());
            feedbackText.setTextColor(getColor(R.color.error));
            if (!question.isWrong()) {
                question.setWrong(true);
                questionManager.addWrongQuestion(subjectId, originalIndex);
                updateFavoriteButtonText(question);
            }
            questionManager.incrementWrongAnswerCount(question.getId());
            
            showStreakDialog();
        }
    }

    private void moveToNextQuestion() {
        if (questions == null || questions.isEmpty()) return;
        currentPosition++;
        if (currentPosition >= questions.size()) {
            Collections.shuffle(questions);
            currentPosition = 0;
        }
        displayQuestion();
    }

    private void moveToPreviousQuestion() {
        if (questions == null || questions.isEmpty()) return;
        if (currentPosition > 0) {
            currentPosition--;
            displayQuestion();
        }
    }

    private void showStreakDialog() {
        int finalStreak = currentStreak;
        currentStreak = 0;
        updateStreakViews();

        new AlertDialog.Builder(this)
                .setTitle(R.string.endless_mode)
                .setMessage(String.format(getString(R.string.streak_result), finalStreak))
                .setPositiveButton(R.string.restart, (d, w) -> {
                    Collections.shuffle(questions);
                    currentPosition = 0;
                    displayQuestion();
                })
                .setNegativeButton(R.string.quit, (d, w) -> finish())
                .setNeutralButton(R.string.streak_dialog_cancel, (d, w) -> {
                    // 取消：停留当前题，已重置 streak
                })
                .setCancelable(true)
                .show();
    }

    private void toggleStar() {
        if (questions == null || questions.isEmpty()) return;
        Question question = questions.get(currentPosition);
        int index = getOriginalIndex(question);
        if (question.isWrong()) {
            questionManager.removeWrongQuestion(subjectId, index);
            Toast.makeText(this, R.string.star_removed, Toast.LENGTH_SHORT).show();
        } else {
            questionManager.addWrongQuestion(subjectId, index);
            Toast.makeText(this, R.string.star_added, Toast.LENGTH_SHORT).show();
        }
        question.setWrong(!question.isWrong());
        updateFavoriteButtonText(question);
    }

    private void updateFavoriteButtonText(Question question) {
        favoriteButton.setText(question.isWrong() ? R.string.unstar : R.string.star);
    }

    private int getOriginalIndex(Question question) {
        if (subject == null || subject.getQuestions() == null) return currentPosition;
        int idx = subject.getQuestions().indexOf(question);
        return idx >= 0 ? idx : currentPosition;
    }

    private void updateStreakViews() {
        currentStreakView.setText(String.format(getString(R.string.current_streak), currentStreak));
        bestStreakView.setText(String.format(getString(R.string.best_streak), bestStreak));
    }

    private void updateNavigationButtons() {
        if (previousButton != null) {
            previousButton.setEnabled(currentPosition > 0);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) { return true; }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) { return false; }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) { return false; }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 != null && e2 != null) {
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (diffX < -100) {
                    moveToNextQuestion();
                    return true;
                } else if (diffX > 100) {
                    moveToPreviousQuestion();
                    return true;
                }
            }
        }
        return false;
    }


    private void showAIDialog() {
        showAIDialog(false);
    }
    
    private void showAIDialog(boolean forceRefresh) {
        if (!aiSettingsManager.isConfigured()) {
            Toast.makeText(this, R.string.ai_not_configured, Toast.LENGTH_LONG).show();
            return;
        }
        
        if (questions == null || questions.isEmpty() || currentPosition >= questions.size()) {
            Toast.makeText(this, "无法获取当前题目", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Question currentQuestion = questions.get(currentPosition);
        
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ai_answer, null);
        dialog.setContentView(dialogView);
        
        ProgressBar progressBar = dialogView.findViewById(R.id.ai_progress_bar);
        TextView thinkingText = dialogView.findViewById(R.id.ai_thinking_text);
        TextView answerText = dialogView.findViewById(R.id.ai_answer_text);
        TextView errorText = dialogView.findViewById(R.id.ai_error_text);
        ImageView modelIcon = dialogView.findViewById(R.id.ai_model_icon);
        TextView modelName = dialogView.findViewById(R.id.ai_model_name);
        ImageButton refreshButton = dialogView.findViewById(R.id.ai_refresh_button);
        Button closeButton = dialogView.findViewById(R.id.ai_close_button);
        
        // 设置模型信息
        String model = aiSettingsManager.getModel();
        modelName.setText(model != null && !model.isEmpty() ? model : "AI助手");
        modelIcon.setImageResource(getModelIconResource(model));
        
        // 刷新按钮点击事件
        refreshButton.setOnClickListener(v -> {
            loadAIResponse(currentQuestion, progressBar, thinkingText, answerText, errorText, true);
        });
        
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        // 加载AI响应
        loadAIResponse(currentQuestion, progressBar, thinkingText, answerText, errorText, forceRefresh);
        
        dialog.show();
    }
    
    private void loadAIResponse(Question question, ProgressBar progressBar,
                                TextView thinkingText, TextView answerText,
                                TextView errorText, boolean forceRefresh) {
        // 显示加载状态
        progressBar.setVisibility(View.VISIBLE);
        thinkingText.setVisibility(View.VISIBLE);
        answerText.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        
        // 调用AI服务
        aiService.askQuestion(question, new AIService.AICallback() {
            @Override
            public void onSuccess(String response) {
                progressBar.setVisibility(View.GONE);
                thinkingText.setVisibility(View.GONE);
                answerText.setVisibility(View.VISIBLE);
                // 使用Markwon渲染Markdown
                markwon.setMarkdown(answerText, response);
            }
            
            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                thinkingText.setVisibility(View.GONE);
                errorText.setVisibility(View.VISIBLE);
                errorText.setText(getString(R.string.ai_error) + ": " + error);
            }
        }, forceRefresh);
    }
    
    private int getModelIconResource(String model) {
        if (model == null || model.isEmpty()) {
            return R.drawable.ic_ai_assistant;
        }
        
        String lowerModel = model.toLowerCase();
        if (lowerModel.contains("gpt") || lowerModel.contains("openai")) {
            return R.drawable.openai;
        } else if (lowerModel.contains("gemini")) {
            return R.drawable.gemini_color;
        } else if (lowerModel.contains("claude")) {
            return R.drawable.claude_color;
        } else if (lowerModel.contains("deepseek")) {
            return R.drawable.deepseek_color;
        } else if (lowerModel.contains("glm") || lowerModel.contains("chatglm")) {
            return R.drawable.chatglm_color;
        } else if (lowerModel.contains("qwen")) {
            return R.drawable.qwen_color;
        } else if (lowerModel.contains("grok")) {
            return R.drawable.grok;
        } else if (lowerModel.contains("ollama")) {
            return R.drawable.ollama;
        } else {
            return R.drawable.ic_ai_assistant;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}