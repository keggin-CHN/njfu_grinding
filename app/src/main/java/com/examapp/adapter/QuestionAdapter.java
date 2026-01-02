package com.examapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.examapp.R;
import com.examapp.model.Question;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder> {
    private List<Question> questions;
    private OnQuestionClickListener listener;

    public interface OnQuestionClickListener {
        void onQuestionClick(Question question);
    }

    public QuestionAdapter(List<Question> questions, OnQuestionClickListener listener) {
        this.questions = questions;
        this.listener = listener;
    }


    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        Question question = questions.get(position);
        holder.bind(question, position, listener, false, new HashSet<>());
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public Question getQuestionAt(int position) {
        return questions.get(position);
    }

    public void removeItem(int position) {
        questions.remove(position);
        notifyItemRemoved(position);
    }

    public void restoreItem(Question item, int position) {
        questions.add(position, item);
        notifyItemInserted(position);
    }

    public class QuestionViewHolder extends RecyclerView.ViewHolder {
        private TextView questionTextView;
        private TextView answerView;
        private TextView categoryView;
        private CheckBox checkBox;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            questionTextView = itemView.findViewById(R.id.question_text);
            answerView = itemView.findViewById(R.id.answer_text);
            categoryView = itemView.findViewById(R.id.category_text);
            checkBox = itemView.findViewById(R.id.checkbox_select);
        }

        public void bind(Question question, int position, OnQuestionClickListener listener) {
            String questionBody = question.getQuestionText();
            if (question.getType() != null && !question.getType().isEmpty() && questionBody.startsWith(question.getType())) {
                questionBody = questionBody.substring(question.getType().length()).trim();
            }
            questionTextView.setText((position + 1) + ". " + questionBody);
            answerView.setText("答案: " + question.getFormattedAnswer());

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onQuestionClick(question));
            }

            StringBuilder categoryText = new StringBuilder();
            if (question.getType() != null && !question.getType().isEmpty()) {
                categoryText.append(question.getType());
            }
            String category = question.getCategory();
            String type = question.getType();
            if (category != null && !category.isEmpty() && !category.equals(type)) {
                if (categoryText.length() > 0) {
                    categoryText.append(" ");
                }
                categoryText.append(category);
            }
            
            if (categoryText.length() > 0) {
                categoryView.setText(categoryText.toString());
                categoryView.setVisibility(View.VISIBLE);
            } else {
                categoryView.setVisibility(View.GONE);
            }
        }

        public void bind(Question question, int position, OnQuestionClickListener listener, boolean isEditMode, Set<Question> selectedItems) {
            bind(question, position, listener); // Call original bind method

            checkBox.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            checkBox.setChecked(selectedItems.contains(question));

            if (isEditMode) {
                itemView.setOnClickListener(v -> {
                    if (selectedItems.contains(question)) {
                        selectedItems.remove(question);
                        checkBox.setChecked(false);
                    } else {
                        selectedItems.add(question);
                        checkBox.setChecked(true);
                    }
                });
            }
        }
    }
}
