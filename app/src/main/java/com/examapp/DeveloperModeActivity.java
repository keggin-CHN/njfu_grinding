package com.examapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.examapp.data.SettingsManager;

public class DeveloperModeActivity extends BaseActivity {
    private SettingsManager settingsManager;
    private Switch developerModeSwitch;
    private EditText customCssInput;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_mode);

        settingsManager = SettingsManager.getInstance(this);
        initializeUI();
        loadSettings();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("开发者模式");

        developerModeSwitch = findViewById(R.id.developer_mode_switch);
        customCssInput = findViewById(R.id.custom_css_input);
        saveButton = findViewById(R.id.save_button);

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        developerModeSwitch.setChecked(settingsManager.isDeveloperMode());
        customCssInput.setText(settingsManager.getCustomCss());
    }

    private void saveSettings() {
        settingsManager.setDeveloperMode(developerModeSwitch.isChecked());
        settingsManager.setCustomCss(customCssInput.getText().toString());

        Toast.makeText(this, "开发者设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}