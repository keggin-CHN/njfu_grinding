package com.examapp.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.examapp.R;
import com.examapp.model.Question;
import java.util.List;
import java.util.Map;

public class QuestionCircleAdapter extends RecyclerView.Adapter<QuestionCircleAdapter.ViewHolder> {

    private final List<Question> questions;
    private final Map<Integer, String> answers;
    private int currentPosition;
    private final OnQuestionClickListener listener;
    private final boolean isReviewMode;

    public interface OnQuestionClickListener {
        void onQuestionClick(int position);
    }

    public QuestionCircleAdapter(List<Question> questions, Map<Integer, String> answers, int currentPosition, OnQuestionClickListener listener, boolean isReviewMode) {
        this.questions = questions;
        this.answers = answers;
        this.currentPosition = currentPosition;
        this.listener = listener;
        this.isReviewMode = isReviewMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_question_circle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position, listener);
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public void setCurrentPosition(int position) {
        int oldPosition = currentPosition;
        currentPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(currentPosition);
    }
    
    public void updateAnswers(Map<Integer, String> newAnswers) {
        this.answers.clear();
        this.answers.putAll(newAnswers);
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView questionCircle;

        ViewHolder(View itemView) {
            super(itemView);
            questionCircle = itemView.findViewById(R.id.question_circle);
        }

        void bind(int position, OnQuestionClickListener listener) {
            questionCircle.setText(String.valueOf(position + 1));
            itemView.setOnClickListener(v -> listener.onQuestionClick(position));

            Context context = itemView.getContext();
            GradientDrawable background = (GradientDrawable) questionCircle.getBackground();
            Question question = questions.get(position);

            if (position == currentPosition) {
                background.setColor(context.getColor(R.color.primary));
            } else if (answers.containsKey(position)) {
                background.setColor(context.getColor(R.color.answered_blue));
            } else if (isReviewMode) {
                int wrongCount = question.getWrongAnswerCount();
                if (wrongCount >= 5) {
                    background.setColor(context.getColor(R.color.wrong_count_high));
                } else if (wrongCount >= 3) {
                    background.setColor(context.getColor(R.color.wrong_count_medium));
                } else if (wrongCount > 0) {
                    background.setColor(context.getColor(R.color.wrong_count_low));
                } else {
                    background.setColor(context.getColor(R.color.gray));
                }
            } else {
                background.setColor(context.getColor(R.color.gray));
            }
        }
    }
}