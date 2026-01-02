package com.examapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.examapp.R;
import com.examapp.model.ExamHistoryEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private final List<ExamHistoryEntry> entries;
    private final OnHistoryEntryClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public interface OnHistoryEntryClickListener {
        void onHistoryEntryClick(ExamHistoryEntry entry);
    }

    public HistoryAdapter(List<ExamHistoryEntry> entries, OnHistoryEntryClickListener listener) {
        this.entries = entries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_entry, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        ExamHistoryEntry entry = entries.get(position);
        String subjectName = entry.getSubjectName() != null ? entry.getSubjectName() : holder.itemView.getContext().getString(R.string.app_name);
        holder.subjectName.setText(subjectName);
        holder.timestamp.setText(dateFormat.format(new Date(entry.getTimestamp())));
        holder.score.setText(holder.itemView.getContext().getString(R.string.history_score_format, entry.getScore(), entry.getMaxScore()));
        holder.answered.setText(holder.itemView.getContext().getString(R.string.history_answered_format,
                entry.getAnsweredQuestions(), entry.getTotalQuestions()));

        if (entry.getDeviceSource() != null && !entry.getDeviceSource().isEmpty()) {
            holder.deviceSource.setText(holder.itemView.getContext().getString(R.string.device_source_format, entry.getDeviceSource()));
            holder.deviceSource.setVisibility(View.VISIBLE);
        } else {
            holder.deviceSource.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onHistoryEntryClick(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final TextView subjectName;
        final TextView timestamp;
        final TextView score;
        final TextView answered;
        final TextView deviceSource; // 新增

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            subjectName = itemView.findViewById(R.id.history_subject);
            timestamp = itemView.findViewById(R.id.history_timestamp);
            score = itemView.findViewById(R.id.history_score);
            answered = itemView.findViewById(R.id.history_answered);
            deviceSource = itemView.findViewById(R.id.history_device_source); // 新增
        }
    }
}
