package com.examapp;

import android.content.DialogInterface;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
import com.examapp.model.Question;
import com.examapp.model.Subject;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class WrongQuestionsActivity extends BaseActivity implements QuestionAdapter.OnQuestionClickListener {
    private QuestionManager questionManager;
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
        builder.setTitle(question.getType());

        StringBuilder details = new StringBuilder();
        details.append(question.getQuestionText()).append("\n\n");

        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            for (String option : question.getOptions()) {
                details.append(option).append("\n");
            }
            details.append("\n");
        }

        details.append("正确答案: ").append(question.getAnswer());

        builder.setMessage(details.toString());
        builder.setPositiveButton("关闭", (dialog, which) -> dialog.dismiss());
        builder.setNegativeButton("取消星标", (dialog, which) -> {
            String questionSubjectId = findSubjectIdForQuestion(question);
            if (questionSubjectId != null) {
                int originalIndex = findOriginalIndex(question, questionSubjectId);
                if (originalIndex != -1) {
                    questionManager.removeWrongQuestion(questionSubjectId, originalIndex);
                    loadWrongQuestions(); // 重新加载列表
                    Toast.makeText(this, "已取消星标", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.create().show();
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
}
