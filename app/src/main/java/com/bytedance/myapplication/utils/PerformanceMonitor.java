package com.bytedance.myapplication.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceMonitor {
    private static final String TAG = "PerformanceMonitor";
    
    private static long appStartTime = 0;
    private static final Map<String, Long> pageStartTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> pageRenderTimes = new ConcurrentHashMap<>();
    private static final Map<String, ImageLoadCounter> imageLoadCounters = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> videoRenderStatus = new ConcurrentHashMap<>();
    // 视频性能指标
    private static final Map<String, VideoPerformanceMetrics> videoMetrics = new ConcurrentHashMap<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public static void recordAppStartTime() {
        if (appStartTime == 0) {
            appStartTime = System.currentTimeMillis();
        }
    }
    
    public static void recordPageStartTime(String pageId) {
        long clickTime = System.currentTimeMillis();
        pageStartTimes.put(pageId, clickTime);
    }
    
    public static void recordPageRenderTime(String pageId) {
        mainHandler.post(() -> {
            long renderTime = System.currentTimeMillis();
            pageRenderTimes.put(pageId, renderTime);
        });
    }
    
    public static void initImageLoadMonitor(String pageId, int totalImageCount) {
        if (totalImageCount <= 0) {
            recordPageRenderTime(pageId);
            return;
        }
        
        imageLoadCounters.put(pageId, new ImageLoadCounter(totalImageCount, pageId));
    }
    
    public static void recordImageLoadComplete(String pageId) {
        ImageLoadCounter counter = imageLoadCounters.get(pageId);
        if (counter != null) {
            int loadedCount = counter.incrementAndGet();
            
            if (loadedCount >= counter.getTotalCount()) {
                mainHandler.post(() -> {
                    long renderTime = System.currentTimeMillis();
                    pageRenderTimes.put(pageId, renderTime);
                    imageLoadCounters.remove(pageId);
                });
            }
        }
    }
    
    public static void initVideoRenderMonitor(String pageId) {
        videoRenderStatus.put(pageId, false);
    }
    
    public static void recordVideoRenderComplete(String pageId) {
        if (videoRenderStatus.containsKey(pageId) && !videoRenderStatus.get(pageId)) {
            videoRenderStatus.put(pageId, true);
            
            mainHandler.post(() -> {
                long renderTime = System.currentTimeMillis();
                pageRenderTimes.put(pageId, renderTime);
            });
        }
    }
    
    public static long getPageLaunchCost(String pageId) {
        Long startTime = pageStartTimes.get(pageId);
        Long renderTime = pageRenderTimes.get(pageId);
        
        if (startTime != null && renderTime != null) {
            return renderTime - startTime;
        }
        
        if (appStartTime > 0 && renderTime != null) {
            return renderTime - appStartTime;
        }
        
        return -1;
    }
    
    /**
     * 打印最终性能报告（在退出界面时调用）
     */
    public static void printFinalReport(String pageId) {
        Long startTime = pageStartTimes.get(pageId);
        Long renderTime = pageRenderTimes.get(pageId);
        VideoPerformanceMetrics videoMetrics = getVideoPerformanceMetrics(pageId);
        
        StringBuilder report = new StringBuilder();
        report.append(String.format("=== 最终性能报告 [%s] ===\n", pageId));
        
        if (startTime != null && renderTime != null) {
            long cost = renderTime - startTime;
            report.append(String.format("页面启动耗时: %dms\n", cost));
            report.append(String.format("启动时间: %d\n", startTime));
            report.append(String.format("渲染完成时间: %d\n", renderTime));
        } else if (appStartTime > 0 && renderTime != null) {
            long cost = renderTime - appStartTime;
            report.append(String.format("APP启动到页面渲染耗时: %dms\n", cost));
            report.append(String.format("APP启动时间: %d\n", appStartTime));
            report.append(String.format("渲染完成时间: %d\n", renderTime));
        }
        
        // 添加视频性能指标
        if (videoMetrics != null) {
            report.append("\n--- 视频性能指标 ---\n");
            report.append(String.format("播放器初始化时间: %dms\n", videoMetrics.playerInitTime));
            report.append(String.format("连接建立时间: %dms (从初始化到loadstart)\n", 
                videoMetrics.loadStartTime - videoMetrics.playerInitTime));
            report.append(String.format("首帧数据加载时间: %dms (从初始化到loadeddata)\n", 
                videoMetrics.loadedDataTime - videoMetrics.playerInitTime));
            report.append(String.format("首帧渲染时间: %dms (从初始化到playing)\n", 
                videoMetrics.playingTime - videoMetrics.playerInitTime));
            report.append(String.format("首帧渲染总耗时: %dms\n", videoMetrics.firstFrameDuration));
            report.append(String.format("数据加载耗时: %dms (loadstart到loadeddata)\n", 
                videoMetrics.loadedDataTime - videoMetrics.loadStartTime));
            report.append(String.format("渲染耗时: %dms (loadeddata到playing)\n", 
                videoMetrics.playingTime - videoMetrics.loadedDataTime));
        }
        
        report.append("=========================");
        Log.d(TAG, report.toString());
    }
    
    /**
     * 视频性能指标数据类
     */
    public static class VideoPerformanceMetrics {
        public long playerInitTime;      // 播放器初始化时间（ms）
        public long loadStartTime;       // 开始加载时间（ms）
        public long loadedDataTime;      // 首帧数据加载完成时间（ms）
        public long playingTime;         // 首帧渲染时间（ms）
        public long firstFrameDuration;  // 首帧渲染总耗时（ms）
        
        public VideoPerformanceMetrics() {
        }
        
        public VideoPerformanceMetrics(long playerInitTime, long loadStartTime, 
                                      long loadedDataTime, long playingTime, long firstFrameDuration) {
            this.playerInitTime = playerInitTime;
            this.loadStartTime = loadStartTime;
            this.loadedDataTime = loadedDataTime;
            this.playingTime = playingTime;
            this.firstFrameDuration = firstFrameDuration;
        }
    }
    
    /**
     * 记录视频性能指标
     */
    public static void recordVideoPerformanceMetrics(String pageId, VideoPerformanceMetrics metrics) {
        videoMetrics.put(pageId, metrics);
    }
    
    /**
     * 获取视频性能指标
     */
    public static VideoPerformanceMetrics getVideoPerformanceMetrics(String pageId) {
        return videoMetrics.get(pageId);
    }
    
    public static void clearPageData(String pageId) {
        pageStartTimes.remove(pageId);
        pageRenderTimes.remove(pageId);
        imageLoadCounters.remove(pageId);
        videoRenderStatus.remove(pageId);
        videoMetrics.remove(pageId);
    }
    
    private static class ImageLoadCounter {
        private final int totalCount;
        private final String pageId;
        private final AtomicInteger loadedCount = new AtomicInteger(0);
        
        public ImageLoadCounter(int totalCount, String pageId) {
            this.totalCount = totalCount;
            this.pageId = pageId;
        }
        
        public int incrementAndGet() {
            return loadedCount.incrementAndGet();
        }
        
        public int getTotalCount() {
            return totalCount;
        }
    }
}

