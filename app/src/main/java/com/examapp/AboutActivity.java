package com.examapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        initializeUI();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("关于");

        TextView authorInfoTextView = findViewById(R.id.author_info_textview);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            authorInfoTextView.setText(Html.fromHtml(getString(R.string.author_info), Html.FROM_HTML_MODE_LEGACY));
        } else {
            authorInfoTextView.setText(Html.fromHtml(getString(R.string.author_info)));
        }
        authorInfoTextView.setMovementMethod(LinkMovementMethod.getInstance());
        
        // 设置付款码点击事件
        ImageView donateImage = findViewById(R.id.donate_image);
        donateImage.setOnClickListener(v -> showFullScreenDonateDialog());
    }
    
    private void showFullScreenDonateDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_fullscreen_image);
        
        // 设置透明背景
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#CC000000")));
        }
        
        ImageView fullscreenImage = dialog.findViewById(R.id.fullscreen_image);
        TextView welcomeText = dialog.findViewById(R.id.welcome_text);
        
        fullscreenImage.setImageResource(R.drawable.donate_qr);
        welcomeText.setText("💝 感谢您的支持，欢迎打赏！");
        
        // 点击任意位置关闭
        dialog.findViewById(R.id.dialog_container).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
        
        // 显示欢迎Toast
        Toast.makeText(this, "感谢您的支持！🎉", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}