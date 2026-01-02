package com.examapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.R;
import com.examapp.model.Question;
import java.util.List;

public class WrongQuestionAdapter extends RecyclerView.Adapter<WrongQuestionAdapter.ViewHolder> {

    private final List<Question> wrongQuestions;
    private final OnQuestionClickListener listener;

    public interface OnQuestionClickListener {
        void onQuestionClick(Question question);
    }

    public WrongQuestionAdapter(List<Question> wrongQuestions, OnQuestionClickListener listener) {
        this.wrongQuestions = wrongQuestions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wrong_question, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Question question = wrongQuestions.get(position);
        holder.bind(question, listener);
    }

    @Override
    public int getItemCount() {
        return wrongQuestions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView questionText;
        public final TextView wrongCount;

        public ViewHolder(View view) {
            super(view);
            questionText = view.findViewById(R.id.question_text);
            wrongCount = view.findViewById(R.id.wrong_count);
        }

        public void bind(final Question question, final OnQuestionClickListener listener) {
            questionText.setText(question.getQuestionText());
            wrongCount.setText("错误次数: " + question.getWrongAnswerCount());
            itemView.setOnClickListener(v -> listener.onQuestionClick(question));
        }
    }
}