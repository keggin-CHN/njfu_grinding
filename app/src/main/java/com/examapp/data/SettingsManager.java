package com.examapp.data;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static SettingsManager instance;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "exam_app_settings";
    private static final String KEY_BACKGROUND_URL = "background_url";
    private static final String KEY_BACKGROUND_TRANSPARENCY = "background_transparency";
    private static final String KEY_DEVELOPER_MODE = "developer_mode";
    private static final String KEY_CUSTOM_CSS = "custom_css";

    private SettingsManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    public String getBackgroundUrl() {
        return sharedPreferences.getString(KEY_BACKGROUND_URL, null);
    }

    public void setBackgroundUrl(String url) {
        sharedPreferences.edit().putString(KEY_BACKGROUND_URL, url).apply();
    }

    public int getBackgroundTransparency() {
        return sharedPreferences.getInt(KEY_BACKGROUND_TRANSPARENCY, 100);
    }

    public void setBackgroundTransparency(int transparency) {
        sharedPreferences.edit().putInt(KEY_BACKGROUND_TRANSPARENCY, transparency).apply();
    }

    public boolean isDeveloperMode() {
        return sharedPreferences.getBoolean(KEY_DEVELOPER_MODE, false);
    }

    public void setDeveloperMode(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
    }

    public String getCustomCss() {
        return sharedPreferences.getString(KEY_CUSTOM_CSS, "");
    }

    public void setCustomCss(String css) {
        sharedPreferences.edit().putString(KEY_CUSTOM_CSS, css).apply();
    }
}
