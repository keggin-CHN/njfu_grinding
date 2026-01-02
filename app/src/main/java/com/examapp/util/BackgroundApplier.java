package com.examapp.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.text.TextUtils;

import com.examapp.data.SettingsManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads and applies the user configured wallpaper across activities.
 */
public final class BackgroundApplier {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<String, Bitmap> CACHE = new ConcurrentHashMap<>();
    private static String lastRequestedUrl;

    private BackgroundApplier() {
    }

    public static void apply(Activity activity) {
        SettingsManager settingsManager = SettingsManager.getInstance(activity);
        String backgroundSource = settingsManager.getBackgroundUrl();
        int transparency = settingsManager.getBackgroundTransparency();

        if (TextUtils.isEmpty(backgroundSource)) {
            activity.getWindow().setBackgroundDrawableResource(android.R.color.white);
            lastRequestedUrl = null;
            return;
        }

        lastRequestedUrl = backgroundSource;

        Bitmap cached = CACHE.get(backgroundSource);
        if (cached != null) {
            setBackground(activity, cached, transparency);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = loadBitmap(activity, backgroundSource);
            if (bitmap != null) {
                CACHE.put(backgroundSource, bitmap);
                if (backgroundSource.equals(lastRequestedUrl)) {
                    activity.runOnUiThread(() -> setBackground(activity, bitmap, transparency));
                }
            }
        });
    }

    private static void setBackground(Activity activity, Bitmap bitmap, int transparency) {
        int alpha = Math.min(100, Math.max(0, transparency));
        BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
        drawable.setAlpha((int) (alpha / 100f * 255));
        activity.getWindow().setBackgroundDrawable(drawable);
    }

    private static Bitmap loadBitmap(Activity activity, String source) {
        try {
            if (source.startsWith("content://")) {
                return decodeFromContentUri(activity, source);
            } else if (source.startsWith("file://")) {
                return BitmapFactory.decodeFile(Uri.parse(source).getPath());
            } else if (source.startsWith("http://") || source.startsWith("https://")) {
                return decodeFromNetwork(source);
            } else {
                return BitmapFactory.decodeFile(source);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap decodeFromContentUri(Activity activity, String uriString) throws IOException {
        ContentResolver resolver = activity.getContentResolver();
        try (InputStream stream = resolver.openInputStream(Uri.parse(uriString))) {
            if (stream != null) {
                return BitmapFactory.decodeStream(stream);
            }
        }
        return null;
    }

    private static Bitmap decodeFromNetwork(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.connect();
        try (InputStream inputStream = connection.getInputStream()) {
            return BitmapFactory.decodeStream(inputStream);
        } finally {
            connection.disconnect();
        }
    }
}
