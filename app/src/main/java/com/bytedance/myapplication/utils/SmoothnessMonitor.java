package com.bytedance.myapplication.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UI流畅性监控工具类
 * 使用Choreographer.FrameCallback监听每一帧绘制时机
 * 
 * 指标定义：
 * 1. 帧率（FPS）= 总帧数 / 统计时间
 * 2. 丢帧率 = 丢N帧的次数 / 统计时长
 *    - 丢帧率分为不同level：丢3帧率、丢5帧率、丢7帧率等
 *    - 可以选择适当的N值作为严重卡顿率
 * 
 * 采集原理：
 * Android View渲染体系存在FrameCallback接口，可以监听到每一帧绘制的时机
 */
public class SmoothnessMonitor {
    private static final String TAG = "SmoothnessMonitor";
    
    // 标准帧间隔时间（60FPS，每帧约16.67ms）
    private static final long FRAME_INTERVAL_NS = 16_666_666L; // 16.67ms in nanoseconds
    
    // 丢帧阈值（毫秒）
    private static final long DROP_FRAME_3_THRESHOLD_MS = 50;   // 丢3帧阈值（约3帧时间）
    private static final long DROP_FRAME_5_THRESHOLD_MS = 83;  // 丢5帧阈值（约5帧时间）
    private static final long DROP_FRAME_7_THRESHOLD_MS = 117; // 丢7帧阈值（约7帧时间）
    
    // 统计时间窗口（毫秒）
    private static final long STATISTICS_WINDOW_MS = 1000; // 每秒统计一次
    
    private Choreographer choreographer;
    private Choreographer.FrameCallback frameCallback;
    private Handler mainHandler;
    
    // 统计数据
    private long startTimeNs;           // 开始统计时间（纳秒）
    private long lastFrameTimeNs;       // 上一帧时间（纳秒）
    private AtomicInteger totalFrames = new AtomicInteger(0);  // 总帧数（累计）
    private AtomicInteger dropFrame3Count = new AtomicInteger(0); // 丢3帧次数（累计）
    private AtomicInteger dropFrame5Count = new AtomicInteger(0); // 丢5帧次数（累计）
    private AtomicInteger dropFrame7Count = new AtomicInteger(0); // 丢7帧次数（累计）
    private AtomicLong lastReportTimeNs = new AtomicLong(0); // 上次报告时间（纳秒）
    private AtomicInteger framesSinceLastReport = new AtomicInteger(0); // 上次报告后的帧数
    private AtomicInteger dropFrame3SinceLastReport = new AtomicInteger(0); // 上次报告后丢3帧次数
    private AtomicInteger dropFrame5SinceLastReport = new AtomicInteger(0); // 上次报告后丢5帧次数
    private AtomicInteger dropFrame7SinceLastReport = new AtomicInteger(0); // 上次报告后丢7帧次数
    
    private boolean isMonitoring = false;
    private String pageId;
    
    // 统计报告回调接口
    public interface SmoothnessReportCallback {
        void onReport(String pageId, float fps, float dropFrame3Rate, 
                     float dropFrame5Rate, float dropFrame7Rate);
    }
    
    private SmoothnessReportCallback reportCallback;
    
    public SmoothnessMonitor(String pageId) {
        this.pageId = pageId;
        this.choreographer = Choreographer.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        this.frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNs) {
                if (isMonitoring) {
                    onFrame(frameTimeNs);
                    // 继续监听下一帧
                    choreographer.postFrameCallback(this);
                }
            }
        };
    }
    
    /**
     * 开始监控
     */
    public void startMonitoring() {
        if (isMonitoring) {
            return;
        }
        
        isMonitoring = true;
        startTimeNs = System.nanoTime();
        lastFrameTimeNs = startTimeNs;
        lastReportTimeNs.set(startTimeNs);
        totalFrames.set(0);
        dropFrame3Count.set(0);
        dropFrame5Count.set(0);
        dropFrame7Count.set(0);
        framesSinceLastReport.set(0);
        dropFrame3SinceLastReport.set(0);
        dropFrame5SinceLastReport.set(0);
        dropFrame7SinceLastReport.set(0);
        
        choreographer.postFrameCallback(frameCallback);
    }
    
    /**
     * 停止监控
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        isMonitoring = false;
        choreographer.removeFrameCallback(frameCallback);
        
        // 生成最终报告
        generateReport();
    }
    
    /**
     * 处理每一帧
     * 优化：减少不必要的变量计算，提升性能
     */
    private void onFrame(long frameTimeNs) {
        if (lastFrameTimeNs == 0) {
            lastFrameTimeNs = frameTimeNs;
            return;
        }
        
        // 计算帧间隔（纳秒转毫秒）
        long frameIntervalMs = (frameTimeNs - lastFrameTimeNs) / 1_000_000;
        
        // 统计总帧数（累计）
        totalFrames.incrementAndGet();
        framesSinceLastReport.incrementAndGet();
        
        // 统计丢帧（累计）- 优化：使用级联判断，减少重复计算
        if (frameIntervalMs > DROP_FRAME_7_THRESHOLD_MS) {
            dropFrame7Count.incrementAndGet();
            dropFrame7SinceLastReport.incrementAndGet();
            dropFrame5Count.incrementAndGet();
            dropFrame5SinceLastReport.incrementAndGet();
            dropFrame3Count.incrementAndGet();
            dropFrame3SinceLastReport.incrementAndGet();
        } else if (frameIntervalMs > DROP_FRAME_5_THRESHOLD_MS) {
            dropFrame5Count.incrementAndGet();
            dropFrame5SinceLastReport.incrementAndGet();
            dropFrame3Count.incrementAndGet();
            dropFrame3SinceLastReport.incrementAndGet();
        } else if (frameIntervalMs > DROP_FRAME_3_THRESHOLD_MS) {
            dropFrame3Count.incrementAndGet();
            dropFrame3SinceLastReport.incrementAndGet();
        }
        
        // 更新上一帧时间
        lastFrameTimeNs = frameTimeNs;
        
        // 定期生成报告（每秒一次）- 优化：减少不必要的 get() 调用
        long lastReportNs = lastReportTimeNs.get();
        long timeSinceLastReportMs = (frameTimeNs - lastReportNs) / 1_000_000;
        if (timeSinceLastReportMs >= STATISTICS_WINDOW_MS) {
            generatePeriodicReport(frameTimeNs);
            lastReportTimeNs.set(frameTimeNs);
            // 重置窗口统计数据
            framesSinceLastReport.set(0);
            dropFrame3SinceLastReport.set(0);
            dropFrame5SinceLastReport.set(0);
            dropFrame7SinceLastReport.set(0);
        }
    }
    
    /**
     * 生成定期报告（每秒一次）
     * 使用滑动窗口，只统计最近1秒的数据
     * 优化：减少重复的 get() 调用，提升性能
     */
    private void generatePeriodicReport(long currentTimeNs) {
        long lastReportNs = lastReportTimeNs.get();
        long windowTimeMs = (currentTimeNs - lastReportNs) / 1_000_000;
        if (windowTimeMs <= 0) {
            windowTimeMs = STATISTICS_WINDOW_MS;
        }
        
        // 使用窗口内的统计数据 - 优化：一次性获取所有值
        int frames = framesSinceLastReport.get();
        int drop3 = dropFrame3SinceLastReport.get();
        int drop5 = dropFrame5SinceLastReport.get();
        int drop7 = dropFrame7SinceLastReport.get();
        
        // 计算 FPS 和丢帧率（不输出日志，只用于回调）
        float fps = (frames * 1000.0f) / windowTimeMs;
        float dropFrame3Rate = (drop3 * 1000.0f) / windowTimeMs;
        float dropFrame5Rate = (drop5 * 1000.0f) / windowTimeMs;
        float dropFrame7Rate = (drop7 * 1000.0f) / windowTimeMs;
        
        if (reportCallback != null) {
            reportCallback.onReport(pageId, fps, dropFrame3Rate, dropFrame5Rate, dropFrame7Rate);
        }
    }
    
    /**
     * 生成最终报告
     */
    private void generateReport() {
        long totalTimeMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        if (totalTimeMs <= 0) {
            totalTimeMs = 1; // 避免除零
        }
        
        int frames = totalFrames.get();
        float fps = (frames * 1000.0f) / totalTimeMs;
        
        float dropFrame3Rate = (dropFrame3Count.get() * 1000.0f) / totalTimeMs;
        float dropFrame5Rate = (dropFrame5Count.get() * 1000.0f) / totalTimeMs;
        float dropFrame7Rate = (dropFrame7Count.get() * 1000.0f) / totalTimeMs;
        
        Log.d(TAG, String.format(
            "=== 最终流畅性报告 [%s] ===\n" +
            "统计时长: %dms\n" +
            "总帧数: %d\n" +
            "平均FPS: %.2f\n" +
            "丢3帧率: %.2f次/s (严重卡顿)\n" +
            "丢5帧率: %.2f次/s (严重卡顿)\n" +
            "丢7帧率: %.2f次/s (严重卡顿)\n" +
            "=========================",
            pageId, totalTimeMs, frames, fps, dropFrame3Rate, dropFrame5Rate, dropFrame7Rate
        ));
        
        if (reportCallback != null) {
            reportCallback.onReport(pageId, fps, dropFrame3Rate, dropFrame5Rate, dropFrame7Rate);
        }
    }
    
    /**
     * 设置报告回调
     */
    public void setReportCallback(SmoothnessReportCallback callback) {
        this.reportCallback = callback;
    }
    
    /**
     * 获取当前FPS
     */
    public float getCurrentFPS() {
        if (!isMonitoring) {
            return 0;
        }
        
        long totalTimeMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        if (totalTimeMs <= 0) {
            return 0;
        }
        
        int frames = totalFrames.get();
        return (frames * 1000.0f) / totalTimeMs;
    }
    
    /**
     * 获取丢帧率
     * @param level 丢帧级别（3、5、7）
     */
    public float getDropFrameRate(int level) {
        if (!isMonitoring) {
            return 0;
        }
        
        long totalTimeMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        if (totalTimeMs <= 0) {
            return 0;
        }
        
        AtomicInteger count;
        switch (level) {
            case 3:
                count = dropFrame3Count;
                break;
            case 5:
                count = dropFrame5Count;
                break;
            case 7:
                count = dropFrame7Count;
                break;
            default:
                return 0;
        }
        
        return (count.get() * 1000.0f) / totalTimeMs;
    }
    
    /**
     * 重置统计数据
     */
    public void reset() {
        totalFrames.set(0);
        dropFrame3Count.set(0);
        dropFrame5Count.set(0);
        dropFrame7Count.set(0);
        framesSinceLastReport.set(0);
        dropFrame3SinceLastReport.set(0);
        dropFrame5SinceLastReport.set(0);
        dropFrame7SinceLastReport.set(0);
        startTimeNs = System.nanoTime();
        lastFrameTimeNs = startTimeNs;
        lastReportTimeNs.set(startTimeNs);
    }
}

