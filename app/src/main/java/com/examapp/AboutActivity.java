package com.examapp;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

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
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}