package com.examapp.widget;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.examapp.R;

public class SyncProgressDialog extends DialogFragment {

    private ProgressBar progressBar;
    private TextView statusText;
    private TextView detailsText;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_sync_progress, null);

        progressBar = view.findViewById(R.id.sync_progress_bar);
        statusText = view.findViewById(R.id.sync_status_text);
        detailsText = view.findViewById(R.id.sync_details_text);

        builder.setView(view)
                .setTitle("正在同步")
                .setNegativeButton("取消", (dialog, id) -> {
                    // Notify the activity that sync was cancelled
                    if (getActivity() instanceof SyncDialogListener) {
                        ((SyncDialogListener) getActivity()).onSyncCancelled();
                    }
                });
        
        setCancelable(false); // Prevent dialog dismissal on outside touch
        return builder.create();
    }

    public void updateProgress(String status, String details) {
        if (statusText != null) {
            statusText.setText(status);
        }
        if (detailsText != null) {
            detailsText.setText(details);
        }
    }
    
    public void showCompletion(String message) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (statusText != null) {
            statusText.setText(message);
        }
        if (detailsText != null) {
            detailsText.setText("");
        }
        // Allow cancellation and add a close button
        setCancelable(true);
        ((AlertDialog) getDialog()).getButton(Dialog.BUTTON_NEGATIVE).setText("关闭");
    }
    
    public interface SyncDialogListener {
        void onSyncCancelled();
    }
}