package com.examapp;

import androidx.appcompat.app.AppCompatActivity;

import com.examapp.util.BackgroundApplier;

/**
 * Provides a single place to apply the user-selected background wallpaper
 * across every screen in the app without duplicating logic.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
        BackgroundApplier.apply(this);
    }
}
