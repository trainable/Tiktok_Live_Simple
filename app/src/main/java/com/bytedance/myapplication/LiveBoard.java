package com.bytedance.myapplication;

import android.app.Application;
import android.util.Log;

import com.bytedance.myapplication.plugin.PluginManager;
import com.bytedance.myapplication.utils.PerformanceMonitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveBoard extends Application {
    private static final String TAG = "LiveBoard";
    private static final ExecutorService applicationExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    @Override
    public void onCreate() {
        super.onCreate();
        
        PerformanceMonitor.recordAppStartTime();
        PluginManager.getInstance().init(this);
        com.bytedance.myapplication.utils.ViewPoolManager.getInstance().init(this);
        
        com.bytedance.myapplication.utils.ActivityLayoutPreloader activityLayoutPreloader = 
            com.bytedance.myapplication.utils.ActivityLayoutPreloader.getInstance();
        activityLayoutPreloader.init(this);
        activityLayoutPreloader.registerLayout(R.layout.activity_live_room);
        
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            activityLayoutPreloader::preloadAllLayouts, 1000);
        
        com.bytedance.myapplication.utils.PreloadManager.getInstance().startPreload(this);
        registerPlugins();
        
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            Log.e(TAG, "未捕获的异常", ex);
            ex.printStackTrace();
            System.exit(1);
        });
    }
    
    private void registerPlugins() {
        PluginManager pluginManager = PluginManager.getInstance();
        pluginManager.registerPlugin(new com.bytedance.myapplication.plugin.example.LikePlugin());
    }
    
    public static ExecutorService getApplicationExecutor() {
        return applicationExecutor;
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        
        try {
            com.bytedance.myapplication.utils.ViewPoolManager.getInstance().clearAllPools();
            com.bytedance.myapplication.utils.ActivityLayoutPreloader.getInstance().clearAll();
            com.bytedance.myapplication.utils.PreloadManager.getInstance().cleanup();
        } catch (Exception e) {
            Log.e(TAG, "清理预加载资源失败", e);
        }
        
        if (applicationExecutor != null && !applicationExecutor.isShutdown()) {
            applicationExecutor.shutdown();
        }
    }
}

