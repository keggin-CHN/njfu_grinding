package com.examapp;

import android.content.DialogInterface;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.examapp.adapter.QuestionAdapter;
import com.examapp.data.QuestionManager;
import com.examapp.data.AISettingsManager;
import com.examapp.data.AICacheManager;
import com.examapp.service.AIService;
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import io.noties.markwon.Markwon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class WrongQuestionsActivity extends BaseActivity implements QuestionAdapter.OnQuestionClickListener {
    private QuestionManager questionManager;
    private AISettingsManager aiSettingsManager;
    private AICacheManager aiCacheManager;
    private AIService aiService;
    private Markwon markwon;
    private RecyclerView wrongQuestionsRecyclerView;
    private LinearLayout emptyStateLayout;
    private QuestionAdapter questionAdapter;
    private List<Question> allWrongQuestions;
    private String subjectId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_questions);

        questionManager = QuestionManager.getInstance(this);
        aiSettingsManager = AISettingsManager.getInstance(this);
        aiCacheManager = AICacheManager.getInstance(this);
        aiService = AIService.getInstance(this);
        markwon = Markwon.create(this);
        subjectId = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_ID);
        initializeUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWrongQuestions();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        wrongQuestionsRecyclerView = findViewById(R.id.wrong_questions_recycler_view);
        wrongQuestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        Button deleteAllButton = findViewById(R.id.delete_all_button);
        deleteAllButton.setOnClickListener(v -> clearAllQuestions());

        setupSwipeToDelete();
    }

    private void loadWrongQuestions() {
        allWrongQuestions = new ArrayList<>();
        
        if (subjectId != null) {
            List<Question> wrongQuestions = questionManager.getWrongQuestions(subjectId);
            allWrongQuestions.addAll(wrongQuestions);
        } else {
            Map<String, Subject> subjects = questionManager.getAllSubjects();
            for (Subject subject : subjects.values()) {
                List<Question> wrongQuestions = questionManager.getWrongQuestions(subject.getId());
                allWrongQuestions.addAll(wrongQuestions);
            }
        }

        if (allWrongQuestions.isEmpty()) {
            wrongQuestionsRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            wrongQuestionsRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);

            questionAdapter = new QuestionAdapter(allWrongQuestions, this);
            wrongQuestionsRecyclerView.setAdapter(questionAdapter);
        }
    }

    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Question question = questionAdapter.getQuestionAt(position);
                
                // 找到题目的原始 subjectId
                String questionSubjectId = findSubjectIdForQuestion(question);
                if (questionSubjectId == null) return;

                int originalIndex = findOriginalIndex(question, questionSubjectId);
                if (originalIndex != -1) {
                    questionManager.removeWrongQuestion(questionSubjectId, originalIndex);
                    questionAdapter.removeItem(position);

                    Snackbar.make(wrongQuestionsRecyclerView, "已删除", Snackbar.LENGTH_LONG)
                            .setAction("撤销", v -> {
                                questionManager.addWrongQuestion(questionSubjectId, originalIndex);
                                questionAdapter.restoreItem(question, position);
                            }).show();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                new RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        .addBackgroundColor(ContextCompat.getColor(WrongQuestionsActivity.this, R.color.red))
                        .addActionIcon(R.drawable.ic_delete)
                        .create()
                        .decorate();
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        }).attachToRecyclerView(wrongQuestionsRecyclerView);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onQuestionClick(Question question) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_question_details, null);
        builder.setView(dialogView);

        TextView questionText = dialogView.findViewById(R.id.dialog_question_text);
        TextView optionsText = dialogView.findViewById(R.id.dialog_options_text);
        TextView userAnswerText = dialogView.findViewById(R.id.dialog_user_answer_text);
        TextView correctAnswerText = dialogView.findViewById(R.id.dialog_correct_answer_text);
        Button starButton = dialogView.findViewById(R.id.dialog_star_button);
        Button aiButton = dialogView.findViewById(R.id.dialog_ai_button);

        questionText.setText(question.getQuestionText());
        
        StringBuilder options = new StringBuilder();
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            for (String option : question.getOptions()) {
                options.append(option).append("\n");
            }
        }
        optionsText.setText(options.toString());

        userAnswerText.setVisibility(View.GONE);
        correctAnswerText.setText("正确答案: " + question.getAnswer());

        starButton.setText("取消星标");
        starButton.setOnClickListener(v -> {
            String questionSubjectId = findSubjectIdForQuestion(question);
            if (questionSubjectId != null) {
                int originalIndex = findOriginalIndex(question, questionSubjectId);
                if (originalIndex != -1) {
                    questionManager.removeWrongQuestion(questionSubjectId, originalIndex);
                    loadWrongQuestions();
                    Toast.makeText(this, "已取消星标", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        // AI答疑按钮
        if (aiSettingsManager.isConfigured()) {
            aiButton.setVisibility(View.VISIBLE);
            aiButton.setOnClickListener(v -> showAIDialog(question));
        } else {
            aiButton.setVisibility(View.GONE);
        }

        builder.setTitle("题目详情")
                .setPositiveButton("关闭", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void clearAllQuestions() {
        new AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空所有错题吗？此操作不可撤销。")
                .setPositiveButton("清空", (dialog, which) -> {
                    questionManager.clearAllWrongQuestions(subjectId);
                    loadWrongQuestions();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int findOriginalIndex(Question questionToFind, String subjectId) {
        if (subjectId != null) {
            Subject subject = questionManager.getSubject(subjectId);
            if (subject != null && subject.getQuestions() != null) {
                return subject.getQuestions().indexOf(questionToFind);
            }
        }
        return -1;
    }

    private String findSubjectIdForQuestion(Question questionToFind) {
        if (subjectId != null) {
            return subjectId;
        }
        Map<String, Subject> subjects = questionManager.getAllSubjects();
        for (Map.Entry<String, Subject> entry : subjects.entrySet()) {
            if (entry.getValue().getQuestions().contains(questionToFind)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private void showAIDialog(Question question) {
        showAIDialog(question, false);
    }
    
    private void showAIDialog(Question question, boolean forceRefresh) {
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
            loadAIResponse(question, progressBar, thinkingText, answerText, errorText, true);
        });
        
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        // 加载AI响应
        loadAIResponse(question, progressBar, thinkingText, answerText, errorText, forceRefresh);
        
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
}
