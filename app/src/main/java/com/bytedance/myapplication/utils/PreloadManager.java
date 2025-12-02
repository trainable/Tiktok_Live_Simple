package com.bytedance.myapplication.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.bumptech.glide.Glide;
import com.bytedance.myapplication.LiveBoard;
import com.bytedance.myapplication.R;
import com.bytedance.myapplication.model.Comment;
import com.bytedance.myapplication.model.Host;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PreloadManager {
    private static final String TAG = "PreloadManager";
    private static PreloadManager instance;
    private static final long DEFAULT_TTL_MS = 30000;
    private static final int ROOM_COUNT = 10;
    
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isPreloading = new AtomicBoolean(false);
    private final AtomicBoolean isPreloaded = new AtomicBoolean(false);
    private final AtomicBoolean isRoomInfoPreloading = new AtomicBoolean(false);
    private final AtomicBoolean isRoomInfoPreloaded = new AtomicBoolean(false);
    
    private WebView preloadedWebView;
    private WebView reusableWebView;
    private WebSocketManager preloadedWebSocketManager;
    private final Map<String, Host> preloadedRoomInfoCache = new HashMap<>();
    private final Map<String, WebView> preloadedStreamWebViews = new HashMap<>();
    private final Map<String, Boolean> preloadedManifests = new HashMap<>();
    private final Map<String, TTLRoomCache> ttlCache = new HashMap<>();
    
    public static class TTLRoomCache {
        public final WebView webView;
        public final Host host;
        public final List<Comment> comments;
        public final Integer onlineCount;
        public final int commentScrollPosition; // 评论列表滚动位置
        public final long expireTime;
        
        public TTLRoomCache(WebView webView, Host host, List<Comment> comments, Integer onlineCount, int commentScrollPosition, long ttlMs) {
            this.webView = webView;
            this.host = host;
            this.comments = comments != null ? new ArrayList<>(comments) : null;
            this.onlineCount = onlineCount;
            this.commentScrollPosition = commentScrollPosition;
            this.expireTime = System.currentTimeMillis() + ttlMs;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() >= expireTime;
        }
    }
    
    private PreloadManager() {
        executor = LiveBoard.getApplicationExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized PreloadManager getInstance() {
        if (instance == null) {
            instance = new PreloadManager();
        }
        return instance;
    }
    
    public void startPreload(Context context) {
        if (isPreloading.get() || isPreloaded.get()) {
            return;
        }
        
        isPreloading.set(true);
        executor.execute(() -> {
            try {
                mainHandler.post(() -> {
                    preloadedWebView = new WebView(context.getApplicationContext());
                    WebViewConfigHelper.configureForPerformance(preloadedWebView);
                });
                
                preloadedWebSocketManager = new WebSocketManager();
                preloadRoomInfo(context);
                preloadViews(context);
                mainHandler.post(() -> preloadPreferredRoom(context, "1"));
                
                isPreloading.set(false);
                isPreloaded.set(true);
            } catch (Exception e) {
                Log.e(TAG, "预加载失败", e);
                isPreloading.set(false);
            }
        });
    }
    
    private void preloadViews(Context context) {
        try {
            ViewPoolManager viewPoolManager = ViewPoolManager.getInstance();
            viewPoolManager.init(context);
            viewPoolManager.registerPreloadLayout(R.layout.item_comment, 5);
            viewPoolManager.registerPreloadLayout(R.layout.item_room, 10);
            mainHandler.postDelayed(() -> viewPoolManager.preloadAllInBatches(mainHandler, null), 500);
        } catch (Exception e) {
            Log.e(TAG, "初始化 ViewPoolManager 失败", e);
        }
    }
    
    private void preloadRoomInfo(Context context) {
        if (isRoomInfoPreloading.get()) {
            return;
        }
        
        isRoomInfoPreloading.set(true);
        
        List<String> roomIds = new ArrayList<>();
        for (int i = 1; i <= ROOM_COUNT; i++) {
            roomIds.add(String.valueOf(i));
        }
        
        final int totalRooms = roomIds.size();
        final AtomicInteger completedCount = new AtomicInteger(0);
        
        for (String roomId : roomIds) {
            ApiService.getHostInfo(roomId, new ApiService.ApiCallback<Host>() {
                @Override
                public void onSuccess(Host host) {
                    synchronized (preloadedRoomInfoCache) {
                        preloadedRoomInfoCache.put(roomId, host);
                        int count = completedCount.incrementAndGet();
                        
                        if (host.getAvatar() != null && !host.getAvatar().isEmpty()) {
                            mainHandler.post(() -> Glide.with(context.getApplicationContext())
                                    .load(host.getAvatar())
                                    .preload());
                        }
                        
                        if (count >= totalRooms) {
                            isRoomInfoPreloaded.set(true);
                        }
                    }
                }

                @Override
                public void onFailure(String error) {
                    int count = completedCount.incrementAndGet();
                    if (count >= totalRooms) {
                        isRoomInfoPreloaded.set(true);
                    }
                }
            });
        }
    }
    
    public Map<String, Host> getPreloadedRoomInfoCache() {
        synchronized (preloadedRoomInfoCache) {
            return new HashMap<>(preloadedRoomInfoCache);
        }
    }
    
    private String getStreamUrlForRoom(String roomId) {
        return "https://akamaibroadcasteruseast.akamaized.net/cmaf/live/657078/akasource/out.mpd";
    }
    
    private void preloadPreferredRoom(Context context, String roomId) {
        if (roomId == null || roomId.isEmpty() || !"1".equals(roomId)) {
            return;
        }
        
        synchronized (preloadedStreamWebViews) {
            if (preloadedStreamWebViews.containsKey(roomId)) {
                return;
            }
        }
        
        preloadStreamForRoom(context, roomId);
    }
    
    private void preloadManifest(String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            return;
        }
        
        synchronized (preloadedManifests) {
            if (preloadedManifests.containsKey(streamUrl)) {
                return;
            }
            preloadedManifests.put(streamUrl, true);
        }
        
        executor.execute(() -> {
            try {
                URL url = new URL(streamUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    byte[] buffer = new byte[8192];
                    while (inputStream.read(buffer) != -1) {
                        if (inputStream.available() <= 0) {
                            break;
                        }
                    }
                    inputStream.close();
                }
                connection.disconnect();
            } catch (Exception e) {
                // Ignore
            }
        });
    }
    
    public void preloadStreamForRoom(Context context, String roomId) {
        if (roomId == null || roomId.isEmpty() || !"1".equals(roomId)) {
            return;
        }
        
        synchronized (ttlCache) {
            if (ttlCache.containsKey(roomId)) {
                return;
            }
        }
        
        synchronized (preloadedStreamWebViews) {
            if (preloadedStreamWebViews.containsKey(roomId)) {
                return;
            }
        }
        
        String streamUrl = getStreamUrlForRoom(roomId);
        if (streamUrl == null || streamUrl.isEmpty()) {
            return;
        }
        
        executor.execute(() -> {
            preloadManifest(streamUrl);
            
            mainHandler.post(() -> {
                try {
                    WebView bufferWebView = new WebView(context.getApplicationContext());
                    WebViewConfigHelper.configureForPerformance(bufferWebView);
                    
                    if (context instanceof android.app.Activity) {
                        android.app.Activity activity = (android.app.Activity) context;
                        android.view.ViewGroup rootView = activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            android.widget.FrameLayout hiddenContainer = new android.widget.FrameLayout(context);
                            hiddenContainer.setLayoutParams(new android.view.ViewGroup.LayoutParams(1, 1));
                            hiddenContainer.setVisibility(android.view.View.INVISIBLE);
                            hiddenContainer.setAlpha(0.0f);
                            hiddenContainer.addView(bufferWebView, new android.view.ViewGroup.LayoutParams(1, 1));
                            rootView.addView(hiddenContainer);
                        }
                    }
                    
                    bufferWebView.setVisibility(android.view.View.INVISIBLE);
                    bufferWebView.setAlpha(0.0f);
                    bufferWebView.onResume();
                    bufferWebView.resumeTimers();
                    
                    synchronized (preloadedStreamWebViews) {
                        preloadedStreamWebViews.put(roomId, bufferWebView);
                    }
                    
                    String htmlUrl = "file:///android_asset/player.html?url=" + Uri.encode(streamUrl) + "&preload=true";
                    bufferWebView.loadUrl(htmlUrl);
                } catch (Exception e) {
                    Log.e(TAG, "预缓冲房间 " + roomId + " 的流失败", e);
                    synchronized (preloadedStreamWebViews) {
                        preloadedStreamWebViews.remove(roomId);
                    }
                }
            });
        });
    }
    
    private boolean isWebViewDestroyed(WebView webView) {
        if (webView == null) {
            return true;
        }
        try {
            webView.getUrl();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
    
    private void restoreWebViewState(WebView webView, String roomId) {
        if (isWebViewDestroyed(webView)) {
            Log.w(TAG, "无法恢复已销毁的 WebView: roomId=" + roomId);
            return;
        }
        
        try {
            android.view.ViewParent parent = webView.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(webView);
                android.view.ViewParent grandParent = ((android.view.ViewGroup) parent).getParent();
                if (grandParent instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) grandParent).removeView((android.view.View) parent);
                }
            }
            
            webView.onResume();
            webView.resumeTimers();
            webView.setVisibility(android.view.View.VISIBLE);
            webView.setAlpha(1.0f);
            webView.requestFocus();
            
            String currentUrl = null;
            try {
                currentUrl = webView.getUrl();
            } catch (Exception e) {
                Log.w(TAG, "获取 WebView URL 失败，WebView 可能已销毁", e);
                return;
            }
            
            if (currentUrl == null || currentUrl.isEmpty() || !currentUrl.contains("player.html")) {
                String streamUrl = getStreamUrlForRoom(roomId);
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    String htmlUrl = "file:///android_asset/player.html?url=" + Uri.encode(streamUrl);
                    webView.loadUrl(htmlUrl);
                }
            } else {
                String streamUrl = Uri.encode(getStreamUrlForRoom(roomId));
                String jsCode = "(function(){" +
                               "try{" +
                               "if(window.player&&window.player.video){" +
                               "var video=window.player.video;" +
                               "var readyState=video.readyState;" +
                               "var paused=video.paused;" +
                               "if(readyState>=3){" +
                               "video.muted=false;" +
                               "if(paused){video.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(err){});}" +
                               "}else{" +
                               "video.addEventListener('canplay',function(){video.muted=false;if(video.paused){video.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(err){});}},{once:true});" +
                               "}" +
                               "}else{" +
                               "var checkCount=0;" +
                               "var checkInterval=setInterval(function(){checkCount++;if(window.player&&window.player.video){clearInterval(checkInterval);var v=window.player.video;if(v.readyState>=3){v.muted=false;if(v.paused){v.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(err){});}}else{v.addEventListener('canplay',function(){v.muted=false;if(v.paused){v.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(err){});}},{once:true});}}else if(checkCount>20){clearInterval(checkInterval);window.location.href='file:///android_asset/player.html?url=" + streamUrl + "';}},200);" +
                               "}" +
                               "}catch(e){" +
                               "window.location.href='file:///android_asset/player.html?url=" + streamUrl + "';" +
                               "}" +
                               "})();";
                webView.evaluateJavascript(jsCode, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "恢复 WebView 状态失败: roomId=" + roomId, e);
        }
    }
    
    public WebView getTTLCachedWebView(String roomId) {
        TTLRoomCache cache = getTTLRoomCache(roomId);
        return cache != null ? cache.webView : null;
    }
    
    public TTLRoomCache getTTLRoomCache(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        
        synchronized (ttlCache) {
            TTLRoomCache cache = ttlCache.get(roomId);
            if (cache != null) {
                // 先检查是否过期
                if (cache.isExpired()) {
                    Log.d(TAG, "TTL缓存已过期: roomId=" + roomId);
                    if (!isWebViewDestroyed(cache.webView)) {
                        cleanupTTLWebView(cache.webView);
                    }
                    ttlCache.remove(roomId);
                    return null;
                }
                
                // 检查WebView是否被销毁（使用更宽松的检查，避免误判）
                boolean isDestroyed = false;
                if (cache.webView == null) {
                    isDestroyed = true;
                } else {
                    try {
                        cache.webView.getUrl();
                    } catch (Exception e) {
                        isDestroyed = true;
                    }
                }
                
                if (isDestroyed) {
                    Log.d(TAG, "TTL缓存中的WebView已销毁: roomId=" + roomId);
                    ttlCache.remove(roomId);
                    return null;
                }
                
                // 缓存有效，返回并移除（避免重复使用）
                TTLRoomCache result = cache;
                ttlCache.remove(roomId);
                Log.d(TAG, "TTL缓存恢复成功: roomId=" + roomId + ", host=" + (result.host != null) + ", comments=" + (result.comments != null ? result.comments.size() : 0));
                // 注意：不在这里调用 restoreWebViewState，让 LiveRoomActivity 自己处理
                // 因为需要先添加到容器，再恢复状态
                return result;
            }
        }
        return null;
    }
    
    public WebView getPreloadedStreamWebView(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        
        synchronized (preloadedStreamWebViews) {
            WebView webView = preloadedStreamWebViews.get(roomId);
            if (webView != null) {
                preloadedStreamWebViews.remove(roomId);
                
                try {
                    restoreWebViewState(webView, roomId);
                } catch (Exception e) {
                    Log.e(TAG, "恢复预加载流 WebView 状态失败: roomId=" + roomId, e);
                }
                
                return webView;
            }
        }
        return null;
    }
    
    public void returnWebViewWithTTL(String roomId, WebView webView, long ttlMs) {
        returnRoomWithTTL(roomId, webView, null, null, null, -1, ttlMs);
    }
    
    public void returnRoomWithTTL(String roomId, WebView webView, Host host, List<Comment> comments, Integer onlineCount, int commentScrollPosition, long ttlMs) {
        if (roomId == null || roomId.isEmpty() || webView == null) {
            return;
        }
        
        if (ttlMs <= 0) {
            ttlMs = DEFAULT_TTL_MS;
        }
        
        try {
            if (!isWebViewDestroyed(webView)) {
                // 停止加载（如果正在加载中）
                try {
                    webView.stopLoading();
                } catch (Exception e) {
                    // Ignore
                }
                
                // 只静音播放，不暂停（参考item1的实现，保持WebView活跃）
                // 这样切回来时WebView还在播放，能正常显示
                String jsCode = "(function(){try{if(window.player&&window.player.video){window.player.video.muted=true;if(window.player.video.paused){window.player.video.play();}}}catch(e){}})();";
                webView.evaluateJavascript(jsCode, null);
            }
            
            if (webView.getParent() != null) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            
            webView.setVisibility(android.view.View.INVISIBLE);
            webView.setAlpha(0.0f);
            
            // 重要：不要调用onPause()和pauseTimers()，保持WebView活跃（参考item1的实现）
            // 这样WebView在隐藏容器中继续播放，切回来时能正常显示
            // try {
            //     webView.onPause();
            //     webView.pauseTimers();
            // } catch (Exception e) {
            //     // Ignore
            // }
            
            // 重要：TTL缓存需要添加到隐藏容器中（参考item1的实现）
            // 确保WebView在隐藏容器中保持活跃，这样切回来时能正常显示
            android.view.ViewParent parent = webView.getParent();
            if (parent == null) {
                // 尝试从WebView的Context获取Activity
                android.app.Activity activity = null;
                android.content.Context context = webView.getContext();
                
                if (context instanceof android.app.Activity) {
                    activity = (android.app.Activity) context;
                    // 检查Activity是否正在被销毁
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        Log.w(TAG, "Activity正在被销毁，无法添加到隐藏容器: roomId=" + roomId);
                        // Activity正在被销毁，不添加到隐藏容器，但仍然保存到TTL缓存
                        // WebView会在新Activity创建时恢复
                    } else {
                        android.view.ViewGroup rootView = activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            android.widget.FrameLayout hiddenContainer = new android.widget.FrameLayout(context);
                            hiddenContainer.setLayoutParams(new android.view.ViewGroup.LayoutParams(1, 1));
                            hiddenContainer.setVisibility(android.view.View.INVISIBLE);
                            hiddenContainer.setAlpha(0.0f);
                            hiddenContainer.addView(webView, new android.view.ViewGroup.LayoutParams(1, 1));
                            rootView.addView(hiddenContainer);
                            
                            // 重要：保持WebView活跃（参考item1的实现）
                            try {
                                webView.onResume();
                                webView.resumeTimers();
                            } catch (Exception e) {
                                // Ignore
                            }
                            Log.d(TAG, "TTL缓存WebView已添加到隐藏容器: roomId=" + roomId);
                        }
                    }
                } else {
                    Log.w(TAG, "WebView的Context不是Activity，无法添加到隐藏容器: roomId=" + roomId);
                }
            } else {
                // WebView已经在某个容器中，确保它保持活跃
                try {
                    webView.onResume();
                    webView.resumeTimers();
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            synchronized (ttlCache) {
                TTLRoomCache oldCache = ttlCache.remove(roomId);
                if (oldCache != null && oldCache.webView != webView) {
                    cleanupTTLWebView(oldCache.webView);
                }
                
                ttlCache.put(roomId, new TTLRoomCache(webView, host, comments, onlineCount, commentScrollPosition, ttlMs));
                Log.d(TAG, "TTL缓存已保存: roomId=" + roomId + ", host=" + (host != null) + ", comments=" + (comments != null ? comments.size() : 0) + ", scrollPosition=" + commentScrollPosition);
            }
            
            final String finalRoomId = roomId;
            final WebView finalWebView = webView;
            Runnable cleanupTask = () -> {
                synchronized (ttlCache) {
                    TTLRoomCache cache = ttlCache.get(finalRoomId);
                    if (cache != null && cache.webView == finalWebView && cache.isExpired()) {
                        cleanupTTLWebView(finalWebView);
                        ttlCache.remove(finalRoomId);
                    }
                }
            };
            
            mainHandler.postDelayed(cleanupTask, ttlMs);
            
        } catch (Exception e) {
            Log.e(TAG, "放入 TTL 缓存失败: roomId=" + roomId, e);
            returnWebViewForReuse(webView);
        }
    }
    
    private void cleanupTTLWebView(WebView webView) {
        if (webView == null) {
            return;
        }
        
        try {
            String jsCode = "(function(){try{if(window.player){window.player.destroy();}}catch(e){}})();";
            webView.evaluateJavascript(jsCode, null);
            
            android.view.ViewParent parent = webView.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(webView);
                android.view.ViewParent grandParent = ((android.view.ViewGroup) parent).getParent();
                if (grandParent instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) grandParent).removeView((android.view.View) parent);
                }
            }
            
            webView.onPause();
            webView.pauseTimers();
            webView.destroy();
        } catch (Exception e) {
            Log.e(TAG, "清理 TTL WebView 失败", e);
        }
    }
    
    public void returnWebViewForReuse(WebView webView) {
        if (webView == null) {
            return;
        }
        
        try {
            webView.onPause();
            webView.pauseTimers();
            
            if (webView.getParent() != null) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            
            webView.setVisibility(android.view.View.GONE);
            
            if (reusableWebView != null && reusableWebView != webView) {
                try {
                    reusableWebView.destroy();
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            reusableWebView = webView;
        } catch (Exception e) {
            Log.e(TAG, "放回 WebView 复用池失败", e);
            try {
                webView.destroy();
            } catch (Exception e2) {
                // Ignore
            }
        }
    }
    
    public WebView getReusableWebView() {
        if (reusableWebView != null) {
            WebView webView = reusableWebView;
            reusableWebView = null;
            
            try {
                webView.onResume();
                webView.resumeTimers();
                webView.setVisibility(android.view.View.VISIBLE);
                webView.stopLoading();
                webView.clearHistory();
            } catch (Exception e) {
                Log.e(TAG, "恢复可复用 WebView 状态失败", e);
            }
            
            return webView;
        }
        return null;
    }
    
    
    public WebSocketManager getPreloadedWebSocketManager() {
        if (preloadedWebSocketManager != null) {
            WebSocketManager manager = preloadedWebSocketManager;
            preloadedWebSocketManager = null;
            return manager;
        }
        return new WebSocketManager();
    }
    
    public boolean isPreloaded() {
        return isPreloaded.get();
    }
    
    public boolean isPreloading() {
        return isPreloading.get();
    }
    
    public boolean isRoomInfoPreloaded() {
        return isRoomInfoPreloaded.get();
    }
    
    public int getPreloadedRoomCount() {
        synchronized (preloadedRoomInfoCache) {
            return preloadedRoomInfoCache.size();
        }
    }
    
    public WebView getPreloadedWebView() {
        if (preloadedWebView != null) {
            synchronized (ttlCache) {
                for (TTLRoomCache cache : ttlCache.values()) {
                    if (cache.webView == preloadedWebView) {
                        return null;
                    }
                }
            }
            
            try {
                preloadedWebView.onResume();
                preloadedWebView.resumeTimers();
                preloadedWebView.setVisibility(android.view.View.VISIBLE);
                
                String currentUrl = preloadedWebView.getUrl();
                if (currentUrl != null && !currentUrl.isEmpty() && 
                    !currentUrl.equals("about:blank") && 
                    currentUrl.contains("player.html")) {
                    preloadedWebView.stopLoading();
                    preloadedWebView.clearHistory();
                    preloadedWebView.loadUrl("about:blank");
                }
            } catch (Exception e) {
                Log.e(TAG, "恢复预加载 WebView 状态失败", e);
            }
            
            return preloadedWebView;
        }
        return null;
    }
    
    public void reset() {
        isPreloading.set(false);
        isPreloaded.set(false);
        preloadedWebSocketManager = null;
        
        synchronized (preloadedStreamWebViews) {
            for (Map.Entry<String, WebView> entry : preloadedStreamWebViews.entrySet()) {
                WebView webView = entry.getValue();
                if (webView != null) {
                    try {
                        webView.onPause();
                        webView.pauseTimers();
                        webView.destroy();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            preloadedStreamWebViews.clear();
        }
        
        synchronized (ttlCache) {
            for (TTLRoomCache cache : ttlCache.values()) {
                cleanupTTLWebView(cache.webView);
            }
            ttlCache.clear();
        }
        
        synchronized (preloadedManifests) {
            preloadedManifests.clear();
        }
    }
    
    public void cleanupAll() {
        reset();
        
        if (preloadedWebView != null) {
            try {
                preloadedWebView.destroy();
            } catch (Exception e) {
                // Ignore
            }
            preloadedWebView = null;
        }
        
        if (reusableWebView != null) {
            try {
                reusableWebView.destroy();
            } catch (Exception e) {
                // Ignore
            }
            reusableWebView = null;
        }
        
        synchronized (preloadedRoomInfoCache) {
            preloadedRoomInfoCache.clear();
        }
    }
    
    public void cleanup() {
        reset();
    }
}

