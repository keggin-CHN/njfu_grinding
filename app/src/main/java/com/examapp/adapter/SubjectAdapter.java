package com.examapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.examapp.R;
import com.examapp.model.Subject;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {
    private final List<Subject> subjects;
    private final OnSubjectClickListener listener;
    private OnSubjectActionListener actionListener;
    private String selectedSubjectId;

    public interface OnSubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public interface OnSubjectActionListener {
        void onDelete(String subjectId, int position);
        void onRename(String subjectId, String newName, int position);
    }

    public SubjectAdapter(List<Subject> subjects, OnSubjectClickListener listener) {
        this.subjects = subjects;
        this.listener = listener;
    }

    public void setActionListener(OnSubjectActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setSelectedSubjectId(String subjectId) {
        this.selectedSubjectId = subjectId;
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        Subject subject = subjects.remove(fromPosition);
        subjects.add(toPosition, subject);
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<Subject> getSubjects() {
        return subjects;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view, actionListener);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        holder.bind(subject, listener, selectedSubjectId);
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    public static class SubjectViewHolder extends RecyclerView.ViewHolder {
        private final TextView subjectNameView;
        private final TextView questionsCountView;
        private final TextView accuracyView;
        private final ProgressBar progressBar;
        private final ImageButton deleteButton;
        private final ImageButton renameButton;
        private final MaterialCardView cardView;
        private final OnSubjectActionListener actionListener;

        public SubjectViewHolder(@NonNull View itemView, OnSubjectActionListener actionListener) {
            super(itemView);
            this.actionListener = actionListener;
            cardView = (MaterialCardView) itemView;
            subjectNameView = itemView.findViewById(R.id.subject_name);
            questionsCountView = itemView.findViewById(R.id.questions_count);
            accuracyView = itemView.findViewById(R.id.accuracy);
            progressBar = itemView.findViewById(R.id.progress_bar);
            deleteButton = itemView.findViewById(R.id.delete_button);
            renameButton = itemView.findViewById(R.id.rename_button);
        }

        public void bind(Subject subject, OnSubjectClickListener listener, String selectedSubjectId) {
            subjectNameView.setText(subject.getDisplayName());
            questionsCountView.setText(String.format(Locale.getDefault(), "共%d题", subject.getTotalQuestions()));
            
            float accuracy = (float) subject.getAccuracy();
            accuracyView.setText(String.format(Locale.getDefault(), "正确率: %.1f%%", accuracy));
            progressBar.setProgress((int) accuracy);

            int progressColor;
            if (accuracy < 60) {
                progressColor = itemView.getContext().getColor(R.color.progress_low);
            } else if (accuracy < 85) {
                progressColor = itemView.getContext().getColor(R.color.progress_medium);
            } else {
                progressColor = itemView.getContext().getColor(R.color.progress_high);
            }
            progressBar.getProgressDrawable().setColorFilter(progressColor, android.graphics.PorterDuff.Mode.SRC_IN);

            cardView.setStrokeWidth(subject.getId().equals(selectedSubjectId) ? 4 : 0);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSubjectClick(subject);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    new AlertDialog.Builder(itemView.getContext())
                            .setTitle("删除题库")
                            .setMessage("确定要删除 \"" + subject.getDisplayName() + "\" 吗？此操作不可撤销。")
                            .setPositiveButton("删除", (dialog, which) -> actionListener.onDelete(subject.getId(), getAdapterPosition()))
                            .setNegativeButton("取消", null)
                            .show();
                }
            });

            renameButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    android.widget.EditText input = new android.widget.EditText(itemView.getContext());
                    input.setText(subject.getDisplayName());
                    input.setSelection(input.getText().length());

                    new AlertDialog.Builder(itemView.getContext())
                            .setTitle("重命名题库")
                            .setView(input)
                            .setPositiveButton("保存", (dialog, which) -> {
                                String newName = input.getText().toString().trim();
                                if (!newName.isEmpty()) {
                                    actionListener.onRename(subject.getId(), newName, getAdapterPosition());
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
            });
        }
    }
}
