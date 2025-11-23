package com.examapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.examapp.data.QuestionImporter;
import com.examapp.data.QuestionManager;
import com.examapp.model.Subject;

import java.io.InputStream;

public class ImportActivity extends BaseActivity {
    private static final int FILE_PICKER_REQUEST_CODE = 100;

    private EditText subjectNameInput;
    private EditText imageUrlInput;
    private Button selectFileButton;
    private Button importButton;
    private ProgressBar progressBar;
    private QuestionImporter questionImporter;
    private Uri selectedFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        questionImporter = new QuestionImporter(this);
        initializeUI();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        subjectNameInput = findViewById(R.id.subject_name_input);
        imageUrlInput = findViewById(R.id.image_url_input);
        selectFileButton = findViewById(R.id.select_file_button);
        importButton = findViewById(R.id.import_button);
        progressBar = findViewById(R.id.progress_bar);

        selectFileButton.setOnClickListener(v -> openFilePicker());
        importButton.setOnClickListener(v -> importQuestions());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                selectedFileUri = data.getData();
                selectFileButton.setText("文件已选择: " + (selectedFileUri != null ? selectedFileUri.getLastPathSegment() : "未知"));
            }
        }
    }

    private void importQuestions() {
        String subjectName = subjectNameInput.getText().toString().trim();

        if (TextUtils.isEmpty(subjectName)) {
            Toast.makeText(this, "请输入科目名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFileUri == null) {
            Toast.makeText(this, "请选择题库文件", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        importButton.setEnabled(false);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedFileUri);
                Subject subject = questionImporter.importFromJson(inputStream, subjectName);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    importButton.setEnabled(true);
                    showImportSuccessDialog(subject.getTotalQuestions());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    importButton.setEnabled(true);
                    showImportFailureDialog(e.getMessage());
                });
            }
        }).start();
    }

    private void showImportSuccessDialog(int totalQuestions) {
        new AlertDialog.Builder(this)
                .setTitle("导入成功")
                .setMessage("成功导入 " + totalQuestions + " 道题目")
                .setPositiveButton("确定", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showImportFailureDialog(String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle("导入失败")
                .setMessage("错误信息: " + errorMessage)
                .setPositiveButton("确定", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
