package com.examapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineQuestionBankActivity extends BaseActivity {

    private static final String GITHUB_API_BASE = "https://api.github.com/repos/keggin-CHN/njfu_grinding/contents/";
    private static final String BASE_PATH = "题库收集";
    private static final String PREFS_NAME = "online_question_bank_prefs";
    private static final String KEY_IMPORTED_FILES = "imported_files";
    private static final String EXTRA_REPO_PATH = "EXTRA_REPO_PATH";

    private ProgressBar progressBar;
    private LinearLayout errorLayout;
    private TextView errorText;
    private Button retryButton;
    private TextView readmeText;
    private View readmeContainer;
    private TextView readmeTitle;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private TextView currentPathText;
    private Button upButton;
    private NestedScrollView contentScrollView;

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<Intent> importLauncher;
    private SharedPreferences preferences;

    private String currentPath = BASE_PATH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_question_bank);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("在线题库");
        }

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        progressBar = findViewById(R.id.progress_bar);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);
        retryButton = findViewById(R.id.retry_button);
        readmeText = findViewById(R.id.readme_text);
        readmeContainer = findViewById(R.id.readme_container);
        readmeTitle = findViewById(R.id.readme_title);
        fileRecyclerView = findViewById(R.id.file_recycler_view);
        currentPathText = findViewById(R.id.current_path_text);
        upButton = findViewById(R.id.up_button);
        contentScrollView = findViewById(R.id.content_scroll_view);

        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter();
        fileRecyclerView.setAdapter(fileAdapter);

        retryButton.setOnClickListener(v -> loadData());
        upButton.setOnClickListener(v -> navigateUp());

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String repoPath = result.getData().getStringExtra(EXTRA_REPO_PATH);
                        if (repoPath != null) {
                            updateImportedState(repoPath, true);
                            fileAdapter.markImported(repoPath);
                            Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        loadData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        loadDirectory(currentPath);
    }

    private void loadDirectory(String path) {
        currentPath = path;
        updatePathHeader();

        progressBar.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.GONE);
        readmeContainer.setVisibility(View.GONE);
        readmeTitle.setVisibility(View.GONE);

        executorService.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(buildApiUrl(path))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }

                    String jsonData = response.body().string();
                    List<GitHubFile> files = gson.fromJson(jsonData, new TypeToken<List<GitHubFile>>(){}.getType());

                    List<GitHubFile> directories = new ArrayList<>();
                    List<GitHubFile> jsonFiles = new ArrayList<>();
                    String readmeUrl = null;
                    Set<String> importedFiles = getImportedFiles();

                    if (files != null) {
                        for (GitHubFile file : files) {
                            if ("dir".equals(file.type)) {
                                directories.add(file);
                            } else if ("file".equals(file.type)) {
                                if ("README.md".equalsIgnoreCase(file.name)) {
                                    readmeUrl = file.download_url;
                                } else if (file.name != null && file.name.toLowerCase(Locale.ROOT).endsWith(".json")) {
                                    File cached = new File(getCacheDir(), file.name);
                                    if (cached.exists()) {
                                        file.localPath = cached.getAbsolutePath();
                                    }
                                    file.isImported = importedFiles.contains(file.path);
                                    jsonFiles.add(file);
                                }
                            }
                        }
                    }

                    List<GitHubFile> displayFiles = new ArrayList<>();
                    displayFiles.addAll(directories);
                    displayFiles.addAll(jsonFiles);

                    String finalReadmeUrl = readmeUrl;
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        contentScrollView.setVisibility(View.VISIBLE);
                        fileRecyclerView.setVisibility(View.VISIBLE);
                        fileAdapter.setFiles(displayFiles);

                        if (finalReadmeUrl != null) {
                            loadReadme(finalReadmeUrl);
                        } else {
                            readmeContainer.setVisibility(View.GONE);
                            readmeTitle.setVisibility(View.GONE);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = e.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = e.getClass().getSimpleName();
                }
                String finalErrorMessage = errorMessage;
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    errorLayout.setVisibility(View.VISIBLE);
                    errorText.setText("加载失败: " + finalErrorMessage + "\n\n请检查网络连接");
                });
            }
        });
    }

    private void updatePathHeader() {
        if (currentPathText != null) {
            currentPathText.setText("当前目录: " + currentPath);
        }
        if (upButton != null) {
            upButton.setVisibility(BASE_PATH.equals(currentPath) ? View.GONE : View.VISIBLE);
        }
    }

    private void navigateUp() {
        if (BASE_PATH.equals(currentPath)) {
            return;
        }
        int lastSlash = currentPath.lastIndexOf('/');
        String parent = lastSlash > 0 ? currentPath.substring(0, lastSlash) : BASE_PATH;
        loadDirectory(parent);
    }

    private String buildApiUrl(String path) {
        String[] segments = path.split("/");
        StringBuilder builder = new StringBuilder(GITHUB_API_BASE);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(Uri.encode(segments[i]));
        }
        return builder.toString();
    }

    private void loadReadme(String url) {
        readmeTitle.setVisibility(View.VISIBLE);
        readmeContainer.setVisibility(View.VISIBLE);
        readmeText.setText("正在加载说明文档...");

        executorService.execute(() -> {
            try {
                Request request = new Request.Builder().url(url).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String content = response.body().string();
                        mainHandler.post(() -> {
                            Markwon markwon = Markwon.create(this);
                            markwon.setMarkdown(readmeText, content);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> readmeText.setText("说明文档加载失败"));
            }
        });
    }

    private void downloadFile(GitHubFile file, int position) {
        file.isDownloading = true;
        if (file.isImported) {
            file.isImported = false;
            updateImportedState(file.path, false);
        }
        fileAdapter.notifyItemChanged(position);

        executorService.execute(() -> {
            try {
                Request request = new Request.Builder().url(file.download_url).build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("Download failed");

                    File cacheDir = getCacheDir();
                    File outputFile = new File(cacheDir, file.name);

                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    mainHandler.post(() -> {
                        file.isDownloading = false;
                        file.localPath = outputFile.getAbsolutePath();
                        fileAdapter.notifyItemChanged(position);
                        Toast.makeText(this, "下载完成: " + file.name, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    file.isDownloading = false;
                    fileAdapter.notifyItemChanged(position);
                    Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void importFile(GitHubFile file) {
        Intent intent = new Intent(this, ImportActivity.class);
        intent.putExtra("EXTRA_FILE_PATH", file.localPath);
        intent.putExtra(EXTRA_REPO_PATH, file.path);
        importLauncher.launch(intent);
    }

    private Set<String> getImportedFiles() {
        return new HashSet<>(preferences.getStringSet(KEY_IMPORTED_FILES, new HashSet<>()));
    }

    private void updateImportedState(String path, boolean imported) {
        Set<String> importedFiles = getImportedFiles();
        if (imported) {
            importedFiles.add(path);
        } else {
            importedFiles.remove(path);
        }
        preferences.edit().putStringSet(KEY_IMPORTED_FILES, importedFiles).apply();
    }

    private String formatFileSize(long size) {
        if (size <= 0) {
            return "未知大小";
        }
        double kb = size / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.getDefault(), "%.1f MB", mb);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<GitHubFile> files = new ArrayList<>();

        public void setFiles(List<GitHubFile> files) {
            this.files = files;
            notifyDataSetChanged();
        }

        public void markImported(String repoPath) {
            for (int i = 0; i < files.size(); i++) {
                GitHubFile file = files.get(i);
                if (repoPath.equals(file.path)) {
                    file.isImported = true;
                    file.localPath = null;
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_online_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GitHubFile file = files.get(position);
            holder.fileName.setText(file.name);

            if (file.isDirectory()) {
                holder.fileIcon.setImageResource(R.drawable.ic_folder);
                holder.fileMeta.setText("文件夹");
                holder.progressBar.setVisibility(View.GONE);
                holder.actionButton.setVisibility(View.VISIBLE);
                holder.actionButton.setText("进入");
                holder.actionButton.setOnClickListener(v -> loadDirectory(file.path));
                return;
            }

            holder.fileIcon.setImageResource(R.drawable.ic_link);
            holder.fileMeta.setText("JSON · " + formatFileSize(file.size));

            if (file.isDownloading) {
                holder.actionButton.setVisibility(View.INVISIBLE);
                holder.progressBar.setVisibility(View.VISIBLE);
            } else {
                holder.progressBar.setVisibility(View.GONE);
                holder.actionButton.setVisibility(View.VISIBLE);

                if (file.isImported) {
                    holder.actionButton.setText("重新下载");
                    holder.actionButton.setOnClickListener(v -> downloadFile(file, position));
                } else if (file.localPath != null) {
                    holder.actionButton.setText("导入");
                    holder.actionButton.setOnClickListener(v -> importFile(file));
                } else {
                    holder.actionButton.setText("下载");
                    holder.actionButton.setOnClickListener(v -> downloadFile(file, position));
                }
            }
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView fileName;
            TextView fileMeta;
            Button actionButton;
            ProgressBar progressBar;
            ImageView fileIcon;

            ViewHolder(View itemView) {
                super(itemView);
                fileName = itemView.findViewById(R.id.file_name);
                fileMeta = itemView.findViewById(R.id.file_meta);
                actionButton = itemView.findViewById(R.id.action_button);
                progressBar = itemView.findViewById(R.id.download_progress);
                fileIcon = itemView.findViewById(R.id.file_icon);
            }
        }
    }

    private static class GitHubFile {
        String name;
        String path;
        String type;
        long size;
        String download_url;

        boolean isDownloading;
        boolean isImported;
        String localPath;

        boolean isDirectory() {
            return "dir".equals(type);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
