package com.examapp;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.examapp.data.AISettingsManager;
import com.examapp.service.AIService;

public class AISettingsActivity extends BaseActivity {
    private AISettingsManager aiSettingsManager;
    private AIService aiService;
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
    private Button saveButton;
    private ArrayAdapter<String> modelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);

        aiSettingsManager = AISettingsManager.getInstance(this);
        aiService = AIService.getInstance(this);
        initializeUI();
        loadSettings();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("API接口设置");

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
        saveButton = findViewById(R.id.save_button);

        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aiModelSpinner.setAdapter(modelAdapter);

        saveButton.setOnClickListener(v -> saveSettings());
        aiRefreshModelsButton.setOnClickListener(v -> refreshModels());
        aiResetParamsButton.setOnClickListener(v -> resetAIParameters());

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

        aiMaxRetrySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int maxRetry = progress + 1;
                aiMaxRetryValueText.setText(String.valueOf(maxRetry));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateFullApiPath() {
        String baseUrl = aiBaseUrlInput.getText().toString().trim();
        if (!baseUrl.isEmpty()) {
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
        aiSettingsManager.resetParametersToDefault();
        
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
                
                String savedModel = aiSettingsManager.getModel();
                if (!savedModel.isEmpty() && models.contains(savedModel)) {
                    int position = modelAdapter.getPosition(savedModel);
                    aiModelSpinner.setSelection(position);
                }
                
                Toast.makeText(AISettingsActivity.this, "获取到 " + models.size() + " 个模型", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                Toast.makeText(AISettingsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadSettings() {
        aiBaseUrlInput.setText(aiSettingsManager.getBaseUrl());
        aiApiKeyInput.setText(aiSettingsManager.getApiKey());
        
        float temperature = aiSettingsManager.getTemperature();
        aiTemperatureSeekBar.setProgress((int)(temperature * 100));
        aiTemperatureValueText.setText(String.format("%.2f", temperature));
        
        float topP = aiSettingsManager.getTopP();
        aiTopPSeekBar.setProgress((int)(topP * 100));
        aiTopPValueText.setText(String.format("%.2f", topP));
        
        int maxRetry = aiSettingsManager.getMaxRetry();
        aiMaxRetrySeekBar.setProgress(maxRetry - 1);
        aiMaxRetryValueText.setText(String.valueOf(maxRetry));
        
        updateFullApiPath();
        
        String savedModel = aiSettingsManager.getModel();
        if (!savedModel.isEmpty()) {
            modelAdapter.clear();
            modelAdapter.add(savedModel);
            modelAdapter.notifyDataSetChanged();
            aiModelSpinner.setSelection(0);
        }
    }

    private void saveSettings() {
        aiSettingsManager.setBaseUrl(aiBaseUrlInput.getText().toString().trim());
        aiSettingsManager.setApiKey(aiApiKeyInput.getText().toString().trim());
        
        if (aiModelSpinner.getSelectedItem() != null) {
            aiSettingsManager.setModel(aiModelSpinner.getSelectedItem().toString());
        }
        
        aiSettingsManager.setTemperature(aiTemperatureSeekBar.getProgress() / 100.0f);
        aiSettingsManager.setTopP(aiTopPSeekBar.getProgress() / 100.0f);
        aiSettingsManager.setMaxRetry(aiMaxRetrySeekBar.getProgress() + 1);

        Toast.makeText(this, "AI设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}