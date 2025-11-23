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

public class SubjectExpandableAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_QUESTION = 1;

    private final List<Object> items;
    private final Map<Integer, String> answers;
    private int currentQuestionIndex;
    private final OnItemClickListener listener;
    private final boolean isReviewMode;
    private final List<Question> allQuestions;

    public interface OnItemClickListener {
        void onQuestionClick(int questionIndex);
    }

    public SubjectExpandableAdapter(List<Object> items, Map<Integer, String> answers, int currentQuestionIndex, OnItemClickListener listener, boolean isReviewMode, List<Question> allQuestions) {
        this.items = items;
        this.answers = answers;
        this.currentQuestionIndex = currentQuestionIndex;
        this.listener = listener;
        this.isReviewMode = isReviewMode;
        this.allQuestions = allQuestions;
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof String) {
            return TYPE_HEADER;
        } else {
            return TYPE_QUESTION;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_question_type_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_question_row, parent, false);
            return new QuestionViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == TYPE_HEADER) {
            HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            headerViewHolder.bind((String) items.get(position));
        } else {
            QuestionViewHolder questionViewHolder = (QuestionViewHolder) holder;
            questionViewHolder.bind((Question) items.get(position), listener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setCurrentQuestionIndex(int questionIndex) {
        int oldPosition = -1;
        int newPosition = -1;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Question) {
                Question q = (Question) items.get(i);
                if (q.getIndex() == currentQuestionIndex) {
                    oldPosition = i;
                }
                if (q.getIndex() == questionIndex) {
                    newPosition = i;
                }
            }
        }

        currentQuestionIndex = questionIndex;

        if (oldPosition != -1) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition != -1) {
            notifyItemChanged(newPosition);
        }
    }
    
    public void updateAnswers(Map<Integer, String> answers) {
        this.answers.clear();
        this.answers.putAll(answers);
        notifyDataSetChanged();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;

        HeaderViewHolder(View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.header_title);
        }

        void bind(String title) {
            headerTitle.setText(title);
        }
    }

    class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView questionNumber;

        QuestionViewHolder(View itemView) {
            super(itemView);
            questionNumber = itemView.findViewById(R.id.question_number_circle);
        }

        void bind(Question question, OnItemClickListener listener) {
            questionNumber.setText(String.valueOf(question.getRelativeId()));
            itemView.setOnClickListener(v -> listener.onQuestionClick(question.getIndex()));

            Context context = itemView.getContext();
            GradientDrawable background = (GradientDrawable) questionNumber.getBackground();

            if (question.getIndex() == currentQuestionIndex) {
                background.setColor(context.getColor(R.color.primary));
            } else if (isReviewMode) {
                int wrongCount = question.getWrongAnswerCount();
                if (wrongCount >= 5) {
                    background.setColor(context.getColor(R.color.wrong_count_high));
                } else if (wrongCount >= 3) {
                    background.setColor(context.getColor(R.color.wrong_count_medium));
                } else if (wrongCount > 0) {
                    background.setColor(context.getColor(R.color.wrong_count_low));
                } else {
                    background.setColor(context.getColor(R.color.unanswered_gray));
                }
            } else {
                switch (question.getAnswerState()) {
                    case CORRECT:
                        background.setColor(context.getColor(R.color.answered_correct));
                        break;
                    case WRONG:
                        background.setColor(context.getColor(R.color.answered_wrong));
                        break;
                    case UNANSWERED:
                    default:
                        background.setColor(context.getColor(R.color.unanswered_gray));
                        break;
                }
            }
        }
    }
}