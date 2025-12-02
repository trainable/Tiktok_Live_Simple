package com.bytedance.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.myapplication.R;
import com.bytedance.myapplication.utils.PerformanceMonitor;
import com.bytedance.myapplication.utils.PreloadManager;
import com.bytedance.myapplication.utils.SmoothnessMonitor;

public class SplashActivity extends AppCompatActivity {
    private static final String PAGE_ID = "SplashActivity";
    private static final long MIN_DISPLAY_TIME_MS = 1500;  // 最小显示时间1.5秒，让用户看到启动页
    private static final long MAX_WAIT_TIME_MS = 5000;     // 最大等待时间5秒
    private static final long CHECK_INTERVAL_MS = 100;
    
    private SmoothnessMonitor smoothnessMonitor;
    private Handler mainHandler;
    private Runnable checkPreloadRunnable;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 记录启动时间
        startTime = System.currentTimeMillis();
        PerformanceMonitor.recordPageStartTime(PAGE_ID);
        
        // 初始化流畅度监控
        smoothnessMonitor = new SmoothnessMonitor(PAGE_ID);
        
        // 设置布局
        setContentView(R.layout.activity_splash);
        
        // 隐藏系统状态栏和导航栏，实现全屏效果
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        
        // 隐藏ActionBar（如果存在）
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 记录页面渲染时间
        mainHandler.post(() -> {
            PerformanceMonitor.recordPageRenderTime(PAGE_ID);
        });
        
        // 启动预加载
        PreloadManager.getInstance().startPreload(this);
        
        // 开始检查预加载状态
        startCheckingPreload();
    }
    
    private void startCheckingPreload() {
        checkPreloadRunnable = new Runnable() {
            @Override
            public void run() {
                PreloadManager preloadManager = PreloadManager.getInstance();
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                // 检查条件：
                // 1. 至少显示最小时间（让用户看到启动页）
                // 2. 预加载完成（WebView和WebSocketManager已创建）
                // 3. 房间信息预加载完成（JSON数据已请求完成）
                // 4. 或者超时（最多等待5秒）
                boolean minTimePassed = elapsedTime >= MIN_DISPLAY_TIME_MS;
                boolean preloadComplete = preloadManager.isPreloaded();
                boolean roomInfoPreloaded = preloadManager.isRoomInfoPreloaded();
                boolean timeout = elapsedTime >= MAX_WAIT_TIME_MS;
                
                if (minTimePassed && preloadComplete && (roomInfoPreloaded || timeout)) {
                    navigateToRoomList();
                } else {
                    // 继续检查
                    mainHandler.postDelayed(checkPreloadRunnable, CHECK_INTERVAL_MS);
                }
            }
        };
        
        mainHandler.postDelayed(checkPreloadRunnable, CHECK_INTERVAL_MS);
    }
    
    private void navigateToRoomList() {
        Intent intent = new Intent(SplashActivity.this, RoomListActivity.class);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (smoothnessMonitor != null) {
            smoothnessMonitor.startMonitoring();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (smoothnessMonitor != null) {
            smoothnessMonitor.stopMonitoring();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 取消检查任务
        if (checkPreloadRunnable != null && mainHandler != null) {
            mainHandler.removeCallbacks(checkPreloadRunnable);
        }
        
        // 清理监控
        if (smoothnessMonitor != null) {
            smoothnessMonitor.stopMonitoring();
            smoothnessMonitor = null;
        }
        
        PerformanceMonitor.clearPageData(PAGE_ID);
    }
}

