package com.examapp;

import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.adapter.ReviewAdapter;
import com.examapp.data.QuestionManager;
import com.examapp.data.AISettingsManager;
import com.examapp.data.AICacheManager;
import com.examapp.service.AIService;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import io.noties.markwon.Markwon;

import java.util.ArrayList;
import java.util.List;

public class ReviewActivity extends BaseActivity implements ReviewAdapter.OnItemClickListener {

    public static final String EXTRA_HISTORY_ENTRY = "history_entry";
    public static final String EXTRA_SHOW_ONLY_WRONG = "show_only_wrong";
    
    private AISettingsManager aiSettingsManager;
    private AICacheManager aiCacheManager;
    private AIService aiService;
    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        aiSettingsManager = AISettingsManager.getInstance(this);
        aiCacheManager = AICacheManager.getInstance(this);
        aiService = AIService.getInstance(this);
        markwon = Markwon.create(this);
        
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
        Button aiButton = dialogView.findViewById(R.id.dialog_ai_button);

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
        // 注意:由于Android资源文件名限制,图标文件名中的连字符需要改为下划线
        // 例如: chatglm-color.png 应重命名为 chatglm_color.png
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

    private void updateStarButton(Button starButton, Question question) {
        if (question.isWrong()) {
            starButton.setText("取消星标");
        } else {
            starButton.setText("星标");
        }
    }
}
