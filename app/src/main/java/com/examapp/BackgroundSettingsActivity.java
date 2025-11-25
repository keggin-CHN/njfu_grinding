package com.examapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.examapp.data.SettingsManager;
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundSettingsActivity extends BaseActivity {
    private ActivityResultLauncher<CropImageContractOptions> cropImage;
    private static final int PERMISSION_REQUEST_CODE = 201;
    private SettingsManager settingsManager;
    private EditText backgroundUrlInput;
    private SeekBar transparencySeekBar;
    private TextView transparencyValueText;
    private EditText customCssInput;
    private Button saveButton;
    private Button selectBackgroundButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_settings);

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
        initializeUI();
        loadSettings();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("背景设置");

        backgroundUrlInput = findViewById(R.id.background_url_input);
        transparencySeekBar = findViewById(R.id.transparency_seekbar);
        transparencyValueText = findViewById(R.id.transparency_value_text);
        customCssInput = findViewById(R.id.custom_css_input);
        saveButton = findViewById(R.id.save_button);
        selectBackgroundButton = findViewById(R.id.select_background_button);

        saveButton.setOnClickListener(v -> saveSettings());
        selectBackgroundButton.setOnClickListener(v -> openFilePicker());

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
            cropImageOptions.fixAspectRatio = true;
            cropImageOptions.aspectRatioX = 9;
            cropImageOptions.aspectRatioY = 16;
            cropImageOptions.outputRequestWidth = 1080;
            cropImageOptions.outputRequestHeight = 1920;
            cropImageOptions.outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_FIT;
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
                        Toast.makeText(BackgroundSettingsActivity.this, "下载图片失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(BackgroundSettingsActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadSettings() {
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
        settingsManager.setBackgroundUrl(backgroundUrlInput.getText().toString());
        settingsManager.setBackgroundTransparency(transparencySeekBar.getProgress());
        settingsManager.setCustomCss(customCssInput.getText().toString());

        Toast.makeText(this, "背景设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}