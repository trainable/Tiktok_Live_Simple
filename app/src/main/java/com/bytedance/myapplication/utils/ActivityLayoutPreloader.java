package com.bytedance.myapplication.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity 布局预渲染管理器
 * 用于提前渲染 Activity 布局，减少首次打开时的渲染时间
 */
public class ActivityLayoutPreloader {
    private static final String TAG = "ActivityLayoutPreloader";
    private static ActivityLayoutPreloader instance;
    
    // 预渲染的布局缓存：key 为 layoutId，value 为预渲染的 View
    private Map<Integer, View> preloadedLayouts = new HashMap<>();
    
    private Context applicationContext;
    private Handler mainHandler;
    
    private ActivityLayoutPreloader() {
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized ActivityLayoutPreloader getInstance() {
        if (instance == null) {
            instance = new ActivityLayoutPreloader();
        }
        return instance;
    }
    
    /**
     * 初始化布局预渲染管理器
     * @param context Application 上下文
     */
    public void init(Context context) {
        this.applicationContext = context.getApplicationContext();
    }
    
    /**
     * 预渲染指定布局
     * @param layoutId 布局资源 ID
     * @param parent 父容器（可以为 null）
     */
    public void preloadLayout(int layoutId, ViewGroup parent) {
        if (applicationContext == null) {
            return;
        }
        
        // 如果已经预渲染过，跳过
        if (preloadedLayouts.containsKey(layoutId)) {
            return;
        }
        
        // 在主线程预渲染（inflate 必须在主线程执行）
        mainHandler.post(() -> {
            try {
                LayoutInflater inflater = LayoutInflater.from(applicationContext);
                View view = inflater.inflate(layoutId, parent, false);
                
                // 执行一次 measure 和 layout，触发预渲染
                if (parent != null) {
                    int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                            parent.getWidth(), View.MeasureSpec.EXACTLY);
                    int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                            parent.getHeight(), View.MeasureSpec.EXACTLY);
                    view.measure(widthMeasureSpec, heightMeasureSpec);
                    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                }
                
                preloadedLayouts.put(layoutId, view);
            } catch (Exception e) {
                Log.e(TAG, "布局预渲染失败: layoutId=" + layoutId, e);
            }
        });
    }
    
    /**
     * 获取预渲染的布局（如果存在）
     * @param layoutId 布局资源 ID
     * @return 预渲染的 View，如果不存在则返回 null
     * 注意：使用后会从缓存中移除
     */
    public View getPreloadedLayout(int layoutId) {
        View view = preloadedLayouts.get(layoutId);
        if (view != null) {
            // 从缓存中移除（因为 View 只能有一个父容器）
            preloadedLayouts.remove(layoutId);
        }
        return view;
    }
    
    /**
     * 获取预渲染的布局并自动重新预加载（用于频繁使用的布局，如直播间）
     * @param layoutId 布局资源 ID
     * @return 预渲染的 View，如果不存在则返回 null
     */
    public View getPreloadedLayoutAndReload(int layoutId) {
        View view = preloadedLayouts.get(layoutId);
        if (view != null) {
            // 从缓存中移除（因为 View 只能有一个父容器）
            preloadedLayouts.remove(layoutId);
            // 优化：立即重新预加载，为下次使用做准备（不延迟，尽快预加载）
            mainHandler.post(() -> {
                preloadLayout(layoutId, null);
            });
        }
        return view;
    }
    
    /**
     * 检查布局是否已预渲染
     * @param layoutId 布局资源 ID
     * @return 是否已预渲染
     */
    public boolean isPreloaded(int layoutId) {
        return preloadedLayouts.containsKey(layoutId);
    }
    
    /**
     * 注册需要预渲染的布局
     * @param layoutId 布局资源 ID
     */
    public void registerLayout(int layoutId) {
        // 延迟预渲染，避免在注册时立即执行
        mainHandler.postDelayed(() -> {
            preloadLayout(layoutId, null);
        }, 500);
    }
    
    /**
     * 预加载所有已注册的布局
     */
    public void preloadAllLayouts() {
        // 这个方法主要用于批量预加载，但当前实现是注册时自动预加载
        // 如果需要批量预加载，可以在这里实现
    }
    
    /**
     * 清空所有预渲染的布局
     * 注意：View 对象会被垃圾回收，但需要确保没有其他引用
     */
    public void clearAll() {
        // 清理 View 引用，帮助 GC
        for (View view : preloadedLayouts.values()) {
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).removeAllViews();
            }
        }
        preloadedLayouts.clear();
    }
}

