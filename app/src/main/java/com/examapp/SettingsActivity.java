package com.examapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.app.ProgressDialog;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.examapp.data.SettingsManager;
import com.examapp.data.AISettingsManager;
import com.examapp.service.AIService;
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import androidx.activity.result.ActivityResultLauncher;

/**
 * @deprecated 此Activity已被拆分为多个独立的设置页面：
 * - AISettingsActivity: AI答疑设置
 * - BackgroundSettingsActivity: 背景设置
 * - DeveloperModeActivity: 开发者模式
 * - AboutActivity: 关于
 *
 * 保留此文件仅为兼容性考虑，建议使用上述独立的Activity。
 */
@Deprecated
public class SettingsActivity extends BaseActivity {
    private ActivityResultLauncher<CropImageContractOptions> cropImage;
    private static final int PERMISSION_REQUEST_CODE = 201;
    private SettingsManager settingsManager;
    private AISettingsManager aiSettingsManager;
    private AIService aiService;
    private EditText backgroundUrlInput;
    private SeekBar transparencySeekBar;
    private Button saveButton;
    private Button selectBackgroundButton;
    private Switch developerModeSwitch;
    private TextView transparencyValueText;
    private EditText customCssInput;
    
    // AI Settings
    private EditText aiBaseUrlInput;
    private TextView aiFullPathText;
    private EditText aiApiKeyInput;
    private Spinner aiModelSpinner;
    private Button aiRefreshModelsButton;
    private SeekBar aiTemperatureSeekBar;
    private TextView aiTemperatureValueText;
    private SeekBar aiTopPSeekBar;
    private TextView aiTopPValueText;
    private Button aiResetParamsButton;
    private SeekBar aiMaxRetrySeekBar;
    private TextView aiMaxRetryValueText;
    private ArrayAdapter<String> modelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        cropImage = registerForActivityResult(new CropImageContract(), result -> {
            if (result.isSuccessful()) {
                Uri resultUri = result.getUriContent();
                backgroundUrlInput.setText(resultUri.toString());
                Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show();
            } else {
                Exception error = result.getError();
                Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        settingsManager = SettingsManager.getInstance(this);
        aiSettingsManager = AISettingsManager.getInstance(this);
        aiService = AIService.getInstance(this);
        initializeUI();
        loadSettings();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // AI Settings
        aiBaseUrlInput = findViewById(R.id.ai_base_url_input);
        aiFullPathText = findViewById(R.id.ai_full_path_text);
        aiApiKeyInput = findViewById(R.id.ai_api_key_input);
        aiModelSpinner = findViewById(R.id.ai_model_spinner);
        aiRefreshModelsButton = findViewById(R.id.ai_refresh_models_button);
        aiTemperatureSeekBar = findViewById(R.id.ai_temperature_seekbar);
        aiTemperatureValueText = findViewById(R.id.ai_temperature_value_text);
        aiTopPSeekBar = findViewById(R.id.ai_top_p_seekbar);
        aiTopPValueText = findViewById(R.id.ai_top_p_value_text);
        aiResetParamsButton = findViewById(R.id.ai_reset_params_button);
        aiMaxRetrySeekBar = findViewById(R.id.ai_max_retry_seekbar);
        aiMaxRetryValueText = findViewById(R.id.ai_max_retry_value_text);
        
        backgroundUrlInput = findViewById(R.id.background_url_input);
        transparencySeekBar = findViewById(R.id.transparency_seekbar);
        transparencyValueText = findViewById(R.id.transparency_value_text);
        saveButton = findViewById(R.id.save_button);
        selectBackgroundButton = findViewById(R.id.select_background_button);
        developerModeSwitch = findViewById(R.id.developer_mode_switch);
        customCssInput = findViewById(R.id.custom_css_input);
        
        // Setup AI model spinner
        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aiModelSpinner.setAdapter(modelAdapter);

        TextView authorInfoTextView = findViewById(R.id.author_info_textview);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            authorInfoTextView.setText(Html.fromHtml(getString(R.string.author_info), Html.FROM_HTML_MODE_LEGACY));
        } else {
            authorInfoTextView.setText(Html.fromHtml(getString(R.string.author_info)));
        }
        authorInfoTextView.setMovementMethod(LinkMovementMethod.getInstance());

        saveButton.setOnClickListener(v -> saveSettings());
        selectBackgroundButton.setOnClickListener(v -> openFilePicker());
        aiRefreshModelsButton.setOnClickListener(v -> refreshModels());
        aiResetParamsButton.setOnClickListener(v -> resetAIParameters());
        
        // AI Base URL change listener
        aiBaseUrlInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateFullApiPath();
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        // AI Temperature SeekBar
        aiTemperatureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float temperature = progress / 100.0f;
                aiTemperatureValueText.setText(String.format("%.2f", temperature));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // AI Top-P SeekBar
        aiTopPSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float topP = progress / 100.0f;
                aiTopPValueText.setText(String.format("%.2f", topP));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // AI Max Retry SeekBar
        aiMaxRetrySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // SeekBar range is 0-9, we need 1-10
                int maxRetry = progress + 1;
                aiMaxRetryValueText.setText(String.valueOf(maxRetry));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        transparencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (transparencyValueText != null) {
                    transparencyValueText.setText(progress + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void openFilePicker() {
        final CharSequence[] options = { "相机", "图库", "图床链接" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择图片来源");
        builder.setItems(options, (dialog, item) -> {
            CropImageOptions cropImageOptions = new CropImageOptions();
            cropImageOptions.activityTitle = "裁剪图片";
            cropImageOptions.cropMenuCropButtonTitle = "确定";
            cropImageOptions.cropMenuCropButtonIcon = R.drawable.ic_check;
            cropImageOptions.guidelines = CropImageView.Guidelines.ON;
            
            // --- New cropping restrictions ---
            // Fix the aspect ratio to 9:16 for a typical phone screen
            cropImageOptions.fixAspectRatio = true;
            cropImageOptions.aspectRatioX = 9;
            cropImageOptions.aspectRatioY = 16;
            
            // The fixAspectRatio property already restricts resizing capabilities.
            
            // Set a reasonable output size to avoid overly large images
            cropImageOptions.outputRequestWidth = 1080;
            cropImageOptions.outputRequestHeight = 1920;
            cropImageOptions.outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_FIT;

            // Core fix: Prevent image compression
            cropImageOptions.outputCompressFormat = Bitmap.CompressFormat.PNG;
            cropImageOptions.outputCompressQuality = 100;


            if (options[item].equals("相机")) {
                cropImageOptions.imageSourceIncludeCamera = true;
                cropImageOptions.imageSourceIncludeGallery = false;
                cropImage.launch(new CropImageContractOptions(null, cropImageOptions));
            } else if (options[item].equals("图库")) {
                cropImageOptions.imageSourceIncludeCamera = false;
                cropImageOptions.imageSourceIncludeGallery = true;
                cropImage.launch(new CropImageContractOptions(null, cropImageOptions));
            } else if (options[item].equals("图床链接")) {
                showUrlInputDialog();
            }
        });
        builder.show();
    }

    private void showUrlInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入图片链接");

        final EditText input = new EditText(this);
        input.setText(backgroundUrlInput.getText().toString());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                downloadAndCropImage(url);
            } else {
                Toast.makeText(this, "链接不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void updateFullApiPath() {
        String baseUrl = aiBaseUrlInput.getText().toString().trim();
        if (!baseUrl.isEmpty()) {
            // 如果以#结尾,表示使用自定义完整路径
            if (baseUrl.endsWith("#")) {
                String customUrl = baseUrl.substring(0, baseUrl.length() - 1);
                aiFullPathText.setText("完整API路径: " + customUrl + " (自定义完整路径)");
            } else {
                String displayUrl = baseUrl;
                if (displayUrl.endsWith("/")) {
                    displayUrl = displayUrl.substring(0, displayUrl.length() - 1);
                }
                aiFullPathText.setText("完整API路径: " + displayUrl + "/v1/chat/completions");
            }
        } else {
            aiFullPathText.setText("完整API路径: ");
        }
    }
    
    private void resetAIParameters() {
        // 重置为默认值
        aiSettingsManager.resetParametersToDefault();
        
        // 更新UI
        float temperature = aiSettingsManager.getTemperature();
        aiTemperatureSeekBar.setProgress((int)(temperature * 100));
        aiTemperatureValueText.setText(String.format("%.2f", temperature));
        
        float topP = aiSettingsManager.getTopP();
        aiTopPSeekBar.setProgress((int)(topP * 100));
        aiTopPValueText.setText(String.format("%.2f", topP));
        
        Toast.makeText(this, "已恢复默认值: Temperature=0.6, Top-P=1.0", Toast.LENGTH_SHORT).show();
    }
    
    private void refreshModels() {
        String baseUrl = aiBaseUrlInput.getText().toString().trim();
        String apiKey = aiApiKeyInput.getText().toString().trim();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "请先填写Base URL和API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 临时保存以便AIService可以使用
        aiSettingsManager.setBaseUrl(baseUrl);
        aiSettingsManager.setApiKey(apiKey);
        
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.ai_loading_models));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        aiService.fetchModels(new AIService.ModelsCallback() {
            @Override
            public void onSuccess(java.util.List<String> models) {
                progressDialog.dismiss();
                modelAdapter.clear();
                modelAdapter.addAll(models);
                modelAdapter.notifyDataSetChanged();
                
                // 如果之前有选择的模型,尝试恢复
                String savedModel = aiSettingsManager.getModel();
                if (!savedModel.isEmpty() && models.contains(savedModel)) {
                    int position = modelAdapter.getPosition(savedModel);
                    aiModelSpinner.setSelection(position);
                }
                
                Toast.makeText(SettingsActivity.this, "获取到 " + models.size() + " 个模型", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadSettings() {
        // Load AI Settings
        aiBaseUrlInput.setText(aiSettingsManager.getBaseUrl());
        aiApiKeyInput.setText(aiSettingsManager.getApiKey());
        
        float temperature = aiSettingsManager.getTemperature();
        aiTemperatureSeekBar.setProgress((int)(temperature * 100));
        aiTemperatureValueText.setText(String.format("%.2f", temperature));
        
        float topP = aiSettingsManager.getTopP();
        aiTopPSeekBar.setProgress((int)(topP * 100));
        aiTopPValueText.setText(String.format("%.2f", topP));
        
        int maxRetry = aiSettingsManager.getMaxRetry();
        aiMaxRetrySeekBar.setProgress(maxRetry - 1); // SeekBar is 0-9, value is 1-10
        aiMaxRetryValueText.setText(String.valueOf(maxRetry));
        
        updateFullApiPath();
        
        // Load saved model if exists
        String savedModel = aiSettingsManager.getModel();
        if (!savedModel.isEmpty()) {
            modelAdapter.clear();
            modelAdapter.add(savedModel);
            modelAdapter.notifyDataSetChanged();
            aiModelSpinner.setSelection(0);
        }
        
        developerModeSwitch.setChecked(settingsManager.isDeveloperMode());

        String backgroundUrl = settingsManager.getBackgroundUrl();
        if (backgroundUrl != null) {
            backgroundUrlInput.setText(backgroundUrl);
        }

        int transparency = settingsManager.getBackgroundTransparency();
        transparencySeekBar.setProgress(transparency);
        if (transparencyValueText != null) {
            transparencyValueText.setText(transparency + "%");
        }

        customCssInput.setText(settingsManager.getCustomCss());
    }

    private void saveSettings() {
        // Save AI Settings
        aiSettingsManager.setBaseUrl(aiBaseUrlInput.getText().toString().trim());
        aiSettingsManager.setApiKey(aiApiKeyInput.getText().toString().trim());
        
        if (aiModelSpinner.getSelectedItem() != null) {
            aiSettingsManager.setModel(aiModelSpinner.getSelectedItem().toString());
        }
        
        aiSettingsManager.setTemperature(aiTemperatureSeekBar.getProgress() / 100.0f);
        aiSettingsManager.setTopP(aiTopPSeekBar.getProgress() / 100.0f);
        aiSettingsManager.setMaxRetry(aiMaxRetrySeekBar.getProgress() + 1); // SeekBar is 0-9, value is 1-10
        
        settingsManager.setDeveloperMode(developerModeSwitch.isChecked());
        settingsManager.setBackgroundUrl(backgroundUrlInput.getText().toString());
        settingsManager.setBackgroundTransparency(transparencySeekBar.getProgress());
        settingsManager.setCustomCss(customCssInput.getText().toString());

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void downloadAndCropImage(String urlString) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在下载图片...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                if (bitmap == null) {
                    throw new Exception("无法解码图片");
                }

                File cachePath = new File(getCacheDir(), "images");
                cachePath.mkdirs();
                File tempFile = new File(cachePath, "temp_image.png");
                FileOutputStream stream = new FileOutputStream(tempFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
                
                Uri localUri = Uri.fromFile(tempFile);

                handler.post(() -> {
                    progressDialog.dismiss();
                    if (localUri != null) {
                        CropImageOptions cropImageOptions = new CropImageOptions();
                        cropImageOptions.activityTitle = "裁剪图片";
                        cropImageOptions.cropMenuCropButtonTitle = "确定";
                        cropImageOptions.cropMenuCropButtonIcon = R.drawable.ic_check;
                        cropImageOptions.guidelines = CropImageView.Guidelines.ON;
                        cropImageOptions.fixAspectRatio = true;
                        cropImageOptions.aspectRatioX = 9;
                        cropImageOptions.aspectRatioY = 16;
                        cropImageOptions.outputRequestWidth = 1080;
                        cropImageOptions.outputRequestHeight = 1920;
                        cropImageOptions.outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_FIT;
                        cropImageOptions.outputCompressFormat = Bitmap.CompressFormat.PNG;
                        cropImageOptions.outputCompressQuality = 100;
                        cropImage.launch(new CropImageContractOptions(localUri, cropImageOptions));
                    } else {
                        Toast.makeText(SettingsActivity.this, "下载图片失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(SettingsActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
