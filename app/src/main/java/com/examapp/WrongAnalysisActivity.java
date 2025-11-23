package com.examapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.adapter.WrongQuestionAdapter;
import com.examapp.data.QuestionManager;
import com.examapp.model.Question;
import java.util.List;

public class WrongAnalysisActivity extends BaseActivity implements WrongQuestionAdapter.OnQuestionClickListener {
    private String subjectId;
    private String subjectName;
    private QuestionManager questionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_analysis);

        subjectId = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_ID);
        subjectName = getIntent().getStringExtra(StudyModeActivity.EXTRA_SUBJECT_NAME);
        questionManager = QuestionManager.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (subjectName != null && !subjectName.isEmpty()) {
            getSupportActionBar().setTitle(subjectName + " - 错题分析");
        }

        RecyclerView recyclerView = findViewById(R.id.wrong_questions_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        List<Question> wrongQuestions = questionManager.getQuestionsSortedByWrongCount(subjectId);

        WrongQuestionAdapter adapter = new WrongQuestionAdapter(wrongQuestions, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onQuestionClick(Question question) {
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

        userAnswerText.setVisibility(View.GONE); // No user answer in this context
        correctAnswerText.setText("正确答案: " + question.getFormattedAnswer());

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

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}