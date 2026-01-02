package com.examapp;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.core.view.GravityCompat;

import com.examapp.adapter.SubjectAdapter;
import com.examapp.data.HitokotoManager;
import com.examapp.data.QuestionManager;
import com.examapp.data.SettingsManager;
import com.examapp.model.Subject;
import com.examapp.service.SyncClient;
import com.examapp.service.SyncCallback;
import com.examapp.widget.SyncProgressDialog;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, SyncCallback, SyncProgressDialog.SyncDialogListener {

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    private RecyclerView subjectRecyclerView;
    private SubjectAdapter subjectAdapter;
    private LinearLayout emptyStateLayout;
    private QuestionManager questionManager;
    private SettingsManager settingsManager;
    private TextView hitokotoText;
    private Button studyModeButton;
    private Button mockExamButton;
    private String selectedSubjectId;
    private String lastHitokoto = "";
    private SyncClient syncClient;
    private SyncProgressDialog syncProgressDialog;

    private final Handler hitokotoHandler = new Handler(Looper.getMainLooper());
    private final Runnable hitokotoRefreshRunnable = this::loadHitokoto;

    private static final long HITOKOTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L;

    // Drawer 手势相关
    private float drawerGestureStartX;
    private boolean drawerGestureEligible;
    private static final int OPEN_THRESHOLD_PX = 60; // 右滑位移阈值

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        questionManager = QuestionManager.getInstance(this);
        settingsManager = SettingsManager.getInstance(this);

        setContentView(R.layout.activity_main);

        initializeUI();
        // 每次启动强制刷新
        forceRefreshHitokoto();
        loadSubjects();
    }

    private void initializeUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(this);

        // 设置底部题库链接点击事件
        TextView navFooterLink = navigationView.findViewById(R.id.nav_footer_link);
        if (navFooterLink != null) {
            navFooterLink.setOnClickListener(v -> openQuestionBankLink());
        }

        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.app_name,
                R.string.app_name
        );
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // 半屏区域右滑打开抽屉
        drawerLayout.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drawerGestureStartX = event.getX();
                    drawerGestureEligible = drawerGestureStartX < v.getWidth() / 2 && !drawerLayout.isDrawerOpen(GravityCompat.START);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (drawerGestureEligible) {
                        float diff = event.getX() - drawerGestureStartX;
                        if (diff > OPEN_THRESHOLD_PX) {
                            drawerLayout.openDrawer(GravityCompat.START);
                            drawerGestureEligible = false;
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    drawerGestureEligible = false;
                    break;
            }
            return false;
        });

        subjectRecyclerView = findViewById(R.id.subject_recycler_view);
        subjectRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        setupItemTouchHelper();
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        hitokotoText = findViewById(R.id.hitokoto_text);
        studyModeButton = findViewById(R.id.study_mode_button);
        mockExamButton = findViewById(R.id.mock_exam_button);
        mockExamButton.setVisibility(View.GONE); // Hide mock exam button

        studyModeButton.setOnClickListener(v -> openStudyMode());
        hitokotoText.setOnClickListener(v -> forceRefreshHitokoto());

        updateActionButtonsState();
    }

    private void forceRefreshHitokoto() {
        lastHitokoto = ""; // 清空保证不会被重复过滤
        loadHitokoto();
    }

    private void loadHitokoto() {
        hitokotoHandler.removeCallbacks(hitokotoRefreshRunnable);
        if (hitokotoText != null) {
            hitokotoText.setText(getString(R.string.loading));
        }
        new Thread(() -> {
            String hitokoto = fetchUniqueHitokoto();
            runOnUiThread(() -> {
                if (hitokotoText != null) {
                    hitokotoText.setText(hitokoto);
                }
                lastHitokoto = hitokoto;
                hitokotoHandler.postDelayed(hitokotoRefreshRunnable, HITOKOTO_REFRESH_INTERVAL_MS);
            });
        }).start();
    }

    private String fetchUniqueHitokoto() {
        String result = HitokotoManager.getHitokoto();
        int retry = 0;
        while (result.equals(lastHitokoto) && retry < 2) {
            result = HitokotoManager.getHitokoto();
            retry++;
        }
        return result;
    }

    private void startHitokotoRefresh() {
        stopHitokotoRefresh();
        // 每次 resume 强制再刷一次
        forceRefreshHitokoto();
    }

    private void stopHitokotoRefresh() {
        hitokotoHandler.removeCallbacks(hitokotoRefreshRunnable);
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder,
                                @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                if (subjectAdapter != null) {
                    subjectAdapter.moveItem(fromPosition, toPosition);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 不处理滑动
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 拖动结束后保存新的顺序
                if (subjectAdapter != null) {
                    questionManager.updateSubjectOrder(subjectAdapter.getSubjects());
                }
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true; // 启用长按拖动
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(subjectRecyclerView);
    }

    private void loadSubjects() {
        // 使用排序后的题库列表
        List<Subject> subjects = questionManager.getAllSubjectsSorted();
        Map<String, Subject> subjectsMap = questionManager.getAllSubjects();

        if (selectedSubjectId != null && !subjectsMap.containsKey(selectedSubjectId)) {
            selectedSubjectId = null;
        }

        if (subjects.isEmpty()) {
            subjectRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            subjectRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);

            subjectAdapter = new SubjectAdapter(subjects, this::onSubjectClick);
            subjectAdapter.setActionListener(new SubjectAdapter.OnSubjectActionListener() {
                @Override
                public void onDelete(String subjectId, int position) {
                    questionManager.deleteSubject(subjectId);
                    subjects.remove(position);
                    subjectAdapter.notifyItemRemoved(position);
                    if (subjectId.equals(selectedSubjectId)) {
                        selectedSubjectId = null;
                        updateActionButtonsState();
                    }
                    Toast.makeText(MainActivity.this, "题库已删除", Toast.LENGTH_SHORT).show();
                    if (subjects.isEmpty()) {
                        subjectRecyclerView.setVisibility(View.GONE);
                        emptyStateLayout.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onRename(String subjectId, String newName, int position) {
                    questionManager.updateSubjectDisplayName(subjectId, newName);
                    subjects.get(position).setDisplayName(newName);
                    subjectAdapter.notifyItemChanged(position);
                    Toast.makeText(MainActivity.this, "题库已重命名", Toast.LENGTH_SHORT).show();
                }
            });
            subjectRecyclerView.setAdapter(subjectAdapter);
            subjectAdapter.setSelectedSubjectId(selectedSubjectId);
        }
        updateActionButtonsState();
    }

    private void onSubjectClick(Subject subject) {
        selectedSubjectId = subject.getId();
        if (subjectAdapter != null) {
            subjectAdapter.setSelectedSubjectId(selectedSubjectId);
        }
        updateActionButtonsState();
        Toast.makeText(this, "已选择 " + subject.getDisplayName(), Toast.LENGTH_SHORT).show();
    }

    private void openStudyMode() {
        Subject subject = requireSelectedSubject();
        if (subject == null) return;

        Intent intent = new Intent(this, StudyModeActivity.class);
        intent.putExtra(StudyModeActivity.EXTRA_SUBJECT_ID, subject.getId());
        intent.putExtra(StudyModeActivity.EXTRA_SUBJECT_NAME, subject.getDisplayName());
        startActivity(intent);
    }

    private void openMockExam() {
        // This method is no longer needed here, as the button is in StudyModeActivity.
        // Kept for compatibility, but should not be called.
        Subject subject = requireSelectedSubject();
        if (subject == null) return;

        Intent intent = new Intent(this, MockExamActivity.class);
        intent.putExtra(StudyModeActivity.EXTRA_SUBJECT_ID, subject.getId());
        intent.putExtra(StudyModeActivity.EXTRA_SUBJECT_NAME, subject.getDisplayName());
        startActivity(intent);
    }

    private Subject requireSelectedSubject() {
        if (selectedSubjectId == null) {
            Toast.makeText(this, R.string.select_subject_prompt, Toast.LENGTH_SHORT).show();
            return null;
        }
        Subject subject = questionManager.getSubject(selectedSubjectId);
        if (subject == null) {
            selectedSubjectId = null;
            updateActionButtonsState();
            Toast.makeText(this, R.string.select_subject_prompt, Toast.LENGTH_SHORT).show();
        }
        return subject;
    }

    private void updateActionButtonsState() {
        boolean enabled = selectedSubjectId != null;
        if (studyModeButton != null) {
            studyModeButton.setEnabled(enabled);
            studyModeButton.setAlpha(enabled ? 1f : 0.4f);
        }
        if (mockExamButton != null) {
            // The button is now hidden, but we keep the logic just in case.
            mockExamButton.setEnabled(enabled);
            mockExamButton.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_import) {
            startActivity(new Intent(this, ImportActivity.class));
        } else if (itemId == R.id.nav_sync) {
            startSyncProcess();
        } else if (itemId == R.id.nav_ai_settings) {
            startActivity(new Intent(this, AISettingsActivity.class));
        } else if (itemId == R.id.nav_background_settings) {
            startActivity(new Intent(this, BackgroundSettingsActivity.class));
        } else if (itemId == R.id.nav_developer_mode) {
            settingsManager.setDeveloperMode(!settingsManager.isDeveloperMode());
            Toast.makeText(this, settingsManager.isDeveloperMode() ? "调试日志输出已开启" : "调试日志输出已关闭", Toast.LENGTH_SHORT).show();
        } else if (itemId == R.id.nav_share) {
            shareApp();
        } else if (itemId == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }
        drawerLayout.closeDrawers();
        return true;
    }

    private void shareApp() {
        try {
            // 获取APK文件路径
            String packageName = getPackageName();
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String apkPath = appInfo.sourceDir;
            
            // 创建文件Uri
            java.io.File apkFile = new java.io.File(apkPath);
            android.net.Uri apkUri;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // Android 7.0及以上使用FileProvider
                apkUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    packageName + ".fileprovider",
                    apkFile
                );
            } else {
                apkUri = android.net.Uri.fromFile(apkFile);
            }
            
            // 创建分享Intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.android.package-archive");
            shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "NJFU刷题助手");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "分享NJFU刷题助手APK");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // 启动分享选择器
            startActivity(Intent.createChooser(shareIntent, "分享APK"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void openQuestionBankLink() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse("https://github.com/keggin-CHN/njfu_grinding/tree/main/%E9%A2%98%E5%BA%93%E6%94%B6%E9%9B%86"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubjects();
        startHitokotoRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHitokotoRefresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        
        // The sync button is now in the navigation drawer, so we can remove this.
        // int itemId = item.getItemId();
        // if (itemId == R.id.action_sync) {
        //     startSyncProcess();
        //     return true;
        // }
        
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerToggle != null) drawerToggle.onConfigurationChanged(newConfig);
    }

    private void startSyncProcess() {
        // Create a new client for each sync attempt to ensure a clean state.
        syncClient = new SyncClient(this, this);
        
        syncProgressDialog = new SyncProgressDialog();
        syncProgressDialog.show(getSupportFragmentManager(), "SyncProgressDialog");
        syncClient.startDiscovery();
    }

    @Override
    public void onDiscoveryStarted() {
        if (syncProgressDialog != null) {
            syncProgressDialog.updateProgress("正在查找服务...", "请确保桌面端已开启同步服务。");
        }
    }

    @Override
    public void onServiceFound(String serviceName, String ipAddress, int port) {
        if (syncProgressDialog != null) {
            syncProgressDialog.updateProgress("已找到服务", "正在连接到 " + serviceName + "...");
        }
    }

    @Override
    public void onServiceLost() {
        if (syncProgressDialog != null) {
            syncProgressDialog.showCompletion("错误：连接已断开");
        }
    }

    @Override
    public void onConnectionEstablished() {
        if (syncProgressDialog != null) {
            syncProgressDialog.updateProgress("连接成功", "等待授权...");
        }
    }

    @Override
    public void onAuthRequired(java.util.function.Consumer<String> authCodeConsumer) {
        runOnUiThread(() -> showAuthCodeDialog(authCodeConsumer));
    }

    private void showAuthCodeDialog(java.util.function.Consumer<String> authCodeConsumer) {
        if (isFinishing() || isDestroyed()) {
            return; // Avoid showing dialog on a finished activity
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入授权码");
        builder.setMessage("请在电脑端查看6位授权码并在此输入：");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("6位数字");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String code = input.getText().toString();
            authCodeConsumer.accept(code);
        });
        builder.setNegativeButton("取消", (dialog, which) -> {
            dialog.cancel();
            authCodeConsumer.accept(null); // Pass null to indicate cancellation
        });

        builder.setCancelable(false);
        builder.show();
    }

    @Override
    public void onSyncProgress(String message) {
        if (syncProgressDialog != null) {
            syncProgressDialog.updateProgress("同步进行中", message);
        }
    }

    @Override
    public void onSyncCompleted() {
        if (syncProgressDialog != null) {
            // Dismiss the dialog after a short delay to allow the user to see the message.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (syncProgressDialog != null && syncProgressDialog.getDialog() != null && syncProgressDialog.getDialog().isShowing()) {
                    syncProgressDialog.dismiss();
                }
                // Recreate the activity to ensure all data is reloaded and UI is refreshed.
                recreate();
            }, 1500); // 1.5 second delay
        } else {
            // If dialog is null, just recreate immediately.
            recreate();
        }
        Toast.makeText(this, "同步成功！", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onError(String errorMessage) {
        if (syncProgressDialog != null) {
            syncProgressDialog.showCompletion("同步失败: " + errorMessage);
        }
    }
    
    @Override
    public void onSyncCancelled() {
        // The SyncClient now handles its own cleanup internally when auth is cancelled.
        // We just need to inform the user.
        Toast.makeText(this, "同步已取消", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure the client is cleaned up when the activity is destroyed
        // to prevent leaks and lingering background tasks.
        if (syncClient != null) {
            syncClient.cleanup();
        }
    }
}