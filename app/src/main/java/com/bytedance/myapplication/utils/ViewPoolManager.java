package com.bytedance.myapplication.utils;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bytedance.myapplication.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * View 预加载池管理器
 * 用于预加载和复用 View 对象，减少 inflate 开销
 */
public class ViewPoolManager {
    private static final String TAG = "ViewPoolManager";
    private static ViewPoolManager instance;
    
    // View 池：key 为 layoutId，value 为 View 队列
    private Map<Integer, Queue<View>> viewPools = new HashMap<>();
    
    // 每个布局的最大缓存数量
    private static final int MAX_POOL_SIZE = 10;
    
    // 预加载的布局 ID 列表
    private Map<Integer, Integer> preloadLayouts = new HashMap<>();
    
    private Context applicationContext;
    
    private ViewPoolManager() {
    }
    
    public static synchronized ViewPoolManager getInstance() {
        if (instance == null) {
            instance = new ViewPoolManager();
        }
        return instance;
    }
    
    /**
     * 初始化 View 池管理器
     * @param context Application 上下文
     */
    public void init(Context context) {
        this.applicationContext = context.getApplicationContext();
    }
    
    /**
     * 注册需要预加载的布局
     * @param layoutId 布局资源 ID
     * @param preloadCount 预加载数量
     */
    public void registerPreloadLayout(int layoutId, int preloadCount) {
        preloadLayouts.put(layoutId, preloadCount);
    }
    
    /**
     * 预加载指定布局的 View
     * @param layoutId 布局资源 ID
     * @param parent 父容器（可以为 null）
     * @param count 预加载数量
     */
    public void preloadViews(int layoutId, ViewGroup parent, int count) {
        if (applicationContext == null) {
            return;
        }
        
        LayoutInflater inflater = LayoutInflater.from(applicationContext);
        Queue<View> pool = viewPools.get(layoutId);
        if (pool == null) {
            pool = new LinkedList<>();
            viewPools.put(layoutId, pool);
        }
        
        // 预加载指定数量的 View
        int currentSize = pool.size();
        int needLoad = Math.min(count, MAX_POOL_SIZE - currentSize);
        
        for (int i = 0; i < needLoad; i++) {
            try {
                // 注意：parent 为 null 时，inflate 的 View 可能没有正确的布局参数
                // 但这是正常的，因为布局参数会在使用时由 GridView/ListView 设置
                View view = inflater.inflate(layoutId, parent, false);
                // 标记为预加载的 View（使用 R.id 作为 key）
                view.setTag(R.id.view_pool_tag, true);
                // 注意：不要在这里清除布局参数，因为 setLayoutParams(null) 会抛出异常
                // 布局参数会在从池中获取时由 Adapter 处理
                pool.offer(view);
            } catch (Exception e) {
                Log.e(TAG, "预加载 View 失败: layoutId=" + layoutId, e);
            }
        }
    }
    
    /**
     * 预加载所有注册的布局
     */
    public void preloadAll() {
        if (applicationContext == null) {
            return;
        }
        
        for (Map.Entry<Integer, Integer> entry : preloadLayouts.entrySet()) {
            int layoutId = entry.getKey();
            int count = entry.getValue();
            preloadViews(layoutId, null, count);
        }
    }
    
    /**
     * 分批预加载所有注册的布局，避免阻塞主线程
     * @param handler 主线程 Handler
     * @param onComplete 完成回调
     */
    public void preloadAllInBatches(android.os.Handler handler, Runnable onComplete) {
        if (applicationContext == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        // 收集所有需要预加载的布局和数量
        List<PreloadTask> tasks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : preloadLayouts.entrySet()) {
            int layoutId = entry.getKey();
            int count = entry.getValue();
            tasks.add(new PreloadTask(layoutId, count));
        }
        
        // 分批预加载，每次 2 个，间隔 50ms
        preloadBatch(handler, tasks, 0, onComplete);
    }
    
    private void preloadBatch(android.os.Handler handler, List<PreloadTask> tasks, int taskIndex, Runnable onComplete) {
        if (taskIndex >= tasks.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        PreloadTask task = tasks.get(taskIndex);
        int layoutId = task.layoutId;
        int remaining = task.remaining;
        
        // 每次只预加载 2 个 View
        int batchSize = Math.min(2, remaining);
        preloadViews(layoutId, null, batchSize);
        task.remaining -= batchSize;
        
        if (task.remaining > 0) {
            // 继续预加载当前布局
            handler.postDelayed(() -> preloadBatch(handler, tasks, taskIndex, onComplete), 50);
        } else {
            // 当前布局预加载完成，继续下一个
            handler.postDelayed(() -> preloadBatch(handler, tasks, taskIndex + 1, onComplete), 50);
        }
    }
    
    private static class PreloadTask {
        int layoutId;
        int remaining;
        
        PreloadTask(int layoutId, int count) {
            this.layoutId = layoutId;
            this.remaining = count;
        }
    }
    
    /**
     * 从池中获取 View（如果池中有的话）
     * @param layoutId 布局资源 ID
     * @return View 对象，如果池中没有则返回 null
     */
    public View getViewFromPool(int layoutId) {
        Queue<View> pool = viewPools.get(layoutId);
        if (pool != null && !pool.isEmpty()) {
            View view = pool.poll();
            return view;
        }
        return null;
    }
    
    /**
     * 将 View 回收到池中（如果池未满）
     * @param layoutId 布局资源 ID
     * @param view View 对象
     */
    public void recycleView(int layoutId, View view) {
        if (view == null) {
            return;
        }
        
        Queue<View> pool = viewPools.get(layoutId);
        if (pool == null) {
            pool = new LinkedList<>();
            viewPools.put(layoutId, pool);
        }
        
        // 如果池未满，回收 View
        if (pool.size() < MAX_POOL_SIZE) {
            // 清理 View 状态
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).removeAllViews();
            }
            view.setTag(R.id.view_pool_tag, null);
            pool.offer(view);
        }
    }
    
    /**
     * 获取指定布局的池大小
     * @param layoutId 布局资源 ID
     * @return 池中 View 的数量
     */
    public int getPoolSize(int layoutId) {
        Queue<View> pool = viewPools.get(layoutId);
        return pool != null ? pool.size() : 0;
    }
    
    /**
     * 清空指定布局的池
     * @param layoutId 布局资源 ID
     */
    public void clearPool(int layoutId) {
        Queue<View> pool = viewPools.remove(layoutId);
        if (pool != null) {
            for (View view : pool) {
                if (view instanceof ViewGroup) {
                    ((ViewGroup) view).removeAllViews();
                }
            }
            pool.clear();
        }
    }
    
    /**
     * 清空所有池
     */
    public void clearAllPools() {
        for (Map.Entry<Integer, Queue<View>> entry : viewPools.entrySet()) {
            Queue<View> pool = entry.getValue();
            for (View view : pool) {
                if (view instanceof ViewGroup) {
                    ((ViewGroup) view).removeAllViews();
                }
            }
            pool.clear();
        }
        viewPools.clear();
    }
}

