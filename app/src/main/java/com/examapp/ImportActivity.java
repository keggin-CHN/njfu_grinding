package com.examapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.examapp.data.QuestionImporter;
import com.examapp.data.QuestionManager;
import com.examapp.model.Subject;
import com.examapp.service.AIProcessingService;

import java.io.InputStream;

public class ImportActivity extends BaseActivity {
    private static final int FILE_PICKER_REQUEST_CODE = 100;
    private static final String EXTRA_REPO_PATH = "EXTRA_REPO_PATH";
    
    // 预定义的标签颜色
    private static final int[] TAG_COLORS = {
        0xFFE57373, // 红色
        0xFFFF8A65, // 橙色
        0xFFFFD54F, // 黄色
        0xFF81C784, // 绿色
        0xFF4FC3F7, // 浅蓝
        0xFF64B5F6, // 蓝色
        0xFF9575CD, // 紫色
        0xFFF06292, // 粉色
        0xFFA1887F, // 棕色
        0xFF90A4AE  // 灰蓝
    };

    private EditText subjectNameInput;
    private EditText imageUrlInput;
    private Button selectFileButton;
    private Button importButton;
    private ProgressBar progressBar;
    private RadioGroup aiProcessRadioGroup;
    private RadioButton radioNoAI;
    private SeekBar concurrencySeekBar;
    private TextView concurrencyLabel;
    private TextView aiProcessHint;
    private LinearLayout colorPickerContainer;
    private QuestionImporter questionImporter;
    private Uri selectedFileUri;
    private int selectedTagColor = -1; // -1表示随机颜色
    private View selectedColorView = null;
    private String repoPath;

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

        // Check for file path passed from OnlineQuestionBankActivity
        if (getIntent().hasExtra("EXTRA_FILE_PATH")) {
            String filePath = getIntent().getStringExtra("EXTRA_FILE_PATH");
            repoPath = getIntent().getStringExtra(EXTRA_REPO_PATH);
            if (filePath != null) {
                selectedFileUri = Uri.fromFile(new java.io.File(filePath));
                selectFileButton.setText("文件已选择: " + selectedFileUri.getLastPathSegment());

                // Auto-fill subject name from filename (remove extension)
                String fileName = selectedFileUri.getLastPathSegment();
                if (fileName != null && fileName.toLowerCase().endsWith(".json")) {
                    subjectNameInput.setText(fileName.substring(0, fileName.length() - 5));
                }
            }
        }
        
        // AI处理选项
        aiProcessRadioGroup = findViewById(R.id.ai_process_radio_group);
        radioNoAI = findViewById(R.id.radio_no_ai);
        RadioButton radioGenerateExplanations = findViewById(R.id.radio_generate_explanations);
        concurrencySeekBar = findViewById(R.id.concurrency_seekbar);
        concurrencyLabel = findViewById(R.id.concurrency_label);
        aiProcessHint = findViewById(R.id.ai_process_hint);

        selectFileButton.setOnClickListener(v -> openFilePicker());
        importButton.setOnClickListener(v -> importQuestions());
        
        // AI处理选项变化监听
        aiProcessRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean aiEnabled = checkedId != R.id.radio_no_ai;
            concurrencySeekBar.setVisibility(aiEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
            concurrencyLabel.setVisibility(aiEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
            aiProcessHint.setVisibility(aiEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        
        // 并发数SeekBar监听
        concurrencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int concurrency = Math.max(1, progress); // 最小为1
                concurrencyLabel.setText("并发处理数: " + concurrency);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 颜色选择器
        colorPickerContainer = findViewById(R.id.color_picker_container);
        setupColorPicker();
    }
    
    private void setupColorPicker() {
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        
        // 添加"随机"颜色选项
        View randomColorView = createColorView(size, margin, -1, true);
        colorPickerContainer.addView(randomColorView);
        selectedColorView = randomColorView; // 默认选中随机
        
        // 添加预定义颜色选项
        for (int color : TAG_COLORS) {
            View colorView = createColorView(size, margin, color, false);
            colorPickerContainer.addView(colorView);
        }
    }
    
    private View createColorView(int size, int margin, int color, boolean isRandom) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(android.view.Gravity.CENTER);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(margin, 0, margin, 0);
        container.setLayoutParams(containerParams);
        
        // 颜色圆圈
        View colorCircle = new View(this);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(size, size);
        colorCircle.setLayoutParams(circleParams);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        
        if (isRandom) {
            // 随机颜色显示为彩虹渐变
            drawable.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            drawable.setColors(new int[]{
                0xFFE57373, 0xFFFFD54F, 0xFF81C784,
                0xFF64B5F6, 0xFF9575CD, 0xFFE57373
            });
        } else {
            drawable.setColor(color);
        }
        
        colorCircle.setBackground(drawable);
        container.addView(colorCircle);
        
        // 选中指示器
        ImageView checkMark = new ImageView(this);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
            (int) (20 * getResources().getDisplayMetrics().density),
            (int) (20 * getResources().getDisplayMetrics().density)
        );
        checkParams.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        checkMark.setLayoutParams(checkParams);
        checkMark.setImageResource(R.drawable.ic_check);
        checkMark.setColorFilter(0xFF4CAF50);
        checkMark.setVisibility(isRandom ? View.VISIBLE : View.INVISIBLE); // 默认选中随机
        container.addView(checkMark);
        
        container.setOnClickListener(v -> {
            // 取消之前的选中状态
            if (selectedColorView != null) {
                ImageView prevCheck = (ImageView) ((LinearLayout) selectedColorView).getChildAt(1);
                prevCheck.setVisibility(View.INVISIBLE);
            }
            
            // 设置新的选中状态
            selectedColorView = container;
            checkMark.setVisibility(View.VISIBLE);
            selectedTagColor = isRandom ? -1 : color;
        });
        
        return container;
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
                InputStream inputStream;
                if ("file".equals(selectedFileUri.getScheme())) {
                    inputStream = new java.io.FileInputStream(new java.io.File(selectedFileUri.getPath()));
                } else {
                    inputStream = getContentResolver().openInputStream(selectedFileUri);
                }
                
                Subject subject = questionImporter.importFromJson(inputStream, subjectName, selectedTagColor);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    importButton.setEnabled(true);

                    // 检查是否需要AI处理
                    int checkedId = aiProcessRadioGroup.getCheckedRadioButtonId();
                    if (checkedId == R.id.radio_generate_explanations) {
                        // 需要生成AI解析,启动后台服务
                        int concurrency = Math.max(1, concurrencySeekBar.getProgress());

                        // 启动AI处理服务 - 只生成解析,不修复题目
                        Intent serviceIntent = new Intent(this, AIProcessingService.class);
                        serviceIntent.putExtra(AIProcessingService.EXTRA_SUBJECT_ID, subject.getId());
                        serviceIntent.putExtra(AIProcessingService.EXTRA_FIX_QUESTIONS, false);
                        serviceIntent.putExtra(AIProcessingService.EXTRA_GENERATE_EXPLANATIONS, true);
                        serviceIntent.putExtra(AIProcessingService.EXTRA_CONCURRENCY, concurrency);

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }

                        if (repoPath != null) {
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra(EXTRA_REPO_PATH, repoPath);
                            setResult(RESULT_OK, resultIntent);
                        }

                        Toast.makeText(this, "题库已导入,AI解析生成将在后台进行", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        showImportSuccessDialog(subject.getTotalQuestions());
                    }
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
                    if (repoPath != null) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_REPO_PATH, repoPath);
                        setResult(RESULT_OK, resultIntent);
                    }
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
