package com.examapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.R;
import com.examapp.data.QuestionManager;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Question;
import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final List<ExamHistoryEntry.QuestionRecord> questionRecords;
    private final QuestionManager questionManager;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ExamHistoryEntry.QuestionRecord record);
    }

    public ReviewAdapter(List<ExamHistoryEntry.QuestionRecord> questionRecords, Context context) {
        this.questionRecords = questionRecords;
        this.questionManager = QuestionManager.getInstance(context);
        if (context instanceof OnItemClickListener) {
            this.listener = (OnItemClickListener) context;
        } else {
            this.listener = null;
        }
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review_question, parent, false);
        return new ReviewViewHolder(view, questionManager);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        ExamHistoryEntry.QuestionRecord record = questionRecords.get(position);
        holder.bind(record, listener);
    }

    @Override
    public int getItemCount() {
        return questionRecords.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        private final TextView questionText;
        private final TextView userAnswerText;
        private final TextView correctAnswerText;
        private final TextView resultText;
        private final QuestionManager questionManager;

        public ReviewViewHolder(@NonNull View itemView, QuestionManager manager) {
            super(itemView);
            questionText = itemView.findViewById(R.id.review_question_text);
            userAnswerText = itemView.findViewById(R.id.review_user_answer);
            correctAnswerText = itemView.findViewById(R.id.review_correct_answer);
            resultText = itemView.findViewById(R.id.review_result);
            this.questionManager = manager;
        }

        public void bind(ExamHistoryEntry.QuestionRecord record, OnItemClickListener listener) {
            // 通过ID从QuestionManager获取完整的Question对象
            Question question = questionManager.getQuestionById(record.getQuestionId());
            if (question == null) return;

            String questionDetails = getAdapterPosition() + 1 + ". " + question.getQuestionText();
            questionText.setText(questionDetails);

            String userAnswer = record.getUserAnswer() != null ? record.getUserAnswer() : "未作答";
            userAnswerText.setText("你的答案: " + userAnswer);
            correctAnswerText.setText("正确答案: " + question.getAnswer());

            if (record.isCorrect()) {
                resultText.setText("正确");
                resultText.setTextColor(Color.GREEN);
            } else {
                resultText.setText("错误");
                resultText.setTextColor(Color.RED);
            }

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onItemClick(record));
            }
        }
    }
}