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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.GravityCompat;

import com.examapp.adapter.SubjectAdapter;
import com.examapp.data.HitokotoManager;
import com.examapp.data.QuestionManager;
import com.examapp.data.SettingsManager;
import com.examapp.model.Subject;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

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

    private void loadSubjects() {
        Map<String, Subject> subjectsMap = questionManager.getAllSubjects();
        List<Subject> subjects = new ArrayList<>(subjectsMap.values());

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
        } else if (itemId == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (itemId == R.id.nav_developer_mode) {
            settingsManager.setDeveloperMode(!settingsManager.isDeveloperMode());
            Toast.makeText(this, settingsManager.isDeveloperMode() ? "开发者模式已启用" : "开发者模式已禁用", Toast.LENGTH_SHORT).show();
        }
        drawerLayout.closeDrawers();
        return true;
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
}