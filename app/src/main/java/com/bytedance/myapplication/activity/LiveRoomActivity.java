package com.bytedance.myapplication.activity;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.myapplication.R;
import com.bytedance.myapplication.adapter.CommentAdapter;
import com.bytedance.myapplication.model.Comment;
import com.bytedance.myapplication.model.Host;
import com.bytedance.myapplication.plugin.PluginManager;
import com.bytedance.myapplication.utils.PerformanceMonitor;
import com.bytedance.myapplication.utils.PreloadManager;
import com.bytedance.myapplication.utils.SmoothnessMonitor;
import com.bytedance.myapplication.viewmodel.LiveRoomViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class LiveRoomActivity extends AppCompatActivity {
    private static final String PAGE_ID = "LiveRoomActivity";
    // 使用 DASH 直播流（与 dash_live_final.html 保持一致）
    private static final String DEFAULT_STREAM_URL = "https://akamaibroadcasteruseast.akamaized.net/cmaf/live/657078/akasource/out.mpd";
    
    private ImageView hostAvatar;
    private TextView hostName;
    private TextView hostFollowers;
    private TextView onlineCount;
    private RecyclerView commentRecyclerView;
    private EditText commentInput;
    private Button sendButton;
    private WebView videoView;
    
    // 点赞插件 UI（插件功能）- 使用 ViewStub 延迟加载
    private android.view.View likeContainer;
    private Button likeButton;
    private TextView likeCount;

    private CommentAdapter commentAdapter;
    private LiveRoomViewModel viewModel;
    private String roomId;
    private SmoothnessMonitor smoothnessMonitor;
    
    // 标记当前使用的 WebView 是否是预加载的（用于退出时判断是否需要放回预加载池）
    private boolean isUsingPreloadedWebView = false;
    private String preloadedRoomId = null; // 如果是预加载的 WebView，记录房间ID
    private boolean isUsingTTLCache = false; // 标记是否使用了TTL缓存

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        PerformanceMonitor.recordPageStartTime(PAGE_ID);
        smoothnessMonitor = new SmoothnessMonitor(PAGE_ID);
        
        // 优化：尝试使用预渲染的布局，减少首次渲染时间
        // 使用 getPreloadedLayoutAndReload 自动重新预加载，确保下次打开时也能使用
        com.bytedance.myapplication.utils.ActivityLayoutPreloader preloader = 
            com.bytedance.myapplication.utils.ActivityLayoutPreloader.getInstance();
        View preloadedLayout = preloader.getPreloadedLayoutAndReload(R.layout.activity_live_room);
        if (preloadedLayout != null) {
            // 使用预渲染的布局
            setContentView(preloadedLayout);
        } else {
            // 如果没有预渲染的布局，正常加载
            setContentView(R.layout.activity_live_room);
        }

        roomId = getIntent().getStringExtra("room_id");
        if (roomId == null) {
            roomId = "5";
        }

        viewModel = new ViewModelProvider(this).get(LiveRoomViewModel.class);

        initViews();
        setupRecyclerView();
        setupObservers();
        setupSendButton();
        PerformanceMonitor.initImageLoadMonitor(PAGE_ID, 1);
        
        // 如果使用TTL缓存，数据已经在setupVideoPlayer中恢复了，不需要重新加载
        if (!isUsingTTLCache) {
            // 不使用TTL缓存时，正常加载数据
            viewModel.loadHostInfo(roomId);
            viewModel.loadComments();
        }
        
        viewModel.setupWebSocket();
        
        // 激活所有启用的插件
        PluginManager.getInstance().activateAll(this);
    }

    private void initViews() {
        hostAvatar = findViewById(R.id.host_avatar);
        hostName = findViewById(R.id.host_name);
        hostFollowers = findViewById(R.id.host_followers);
        onlineCount = findViewById(R.id.online_count);
        commentRecyclerView = findViewById(R.id.comment_recycler_view);
        commentInput = findViewById(R.id.comment_input);
        sendButton = findViewById(R.id.send_button);
        
        // 点赞插件 UI（使用 ViewStub 延迟加载，不在这里初始化）
        // likeContainer 将在 setupObservers() 中通过 ViewStub.inflate() 加载
        
        setupVideoPlayer();
    }
    
    /**
     * 配置 WebView 以优化性能
     * 优化：使用统一的工具类，避免代码重复
     */
    private void configureWebViewForPerformance(WebView webView) {
        com.bytedance.myapplication.utils.WebViewConfigHelper.configureForPerformance(webView);
        // 添加 JavaScript 接口，用于接收视频性能指标
        webView.addJavascriptInterface(new VideoPerformanceInterface(), "Android");
    }
    
    /**
     * JavaScript 接口，用于接收视频性能指标
     */
    private class VideoPerformanceInterface {
        @android.webkit.JavascriptInterface
        public void onVideoPerformanceMetrics(String metricsJson) {
            try {
                JSONObject json = new JSONObject(metricsJson);
                
                long playerInitTime = json.optLong("playerInitTime", 0);
                long loadStartTime = json.optLong("loadStartTime", 0);
                long loadedDataTime = json.optLong("loadedDataTime", 0);
                long playingTime = json.optLong("playingTime", 0);
                long firstFrameDuration = json.optLong("firstFrameDuration", 0);
                
                // 如果 firstFrameDuration 为 0，但 playingTime 和 playerInitTime 都有值，则计算
                if (firstFrameDuration == 0 && playingTime > 0 && playerInitTime > 0) {
                    firstFrameDuration = playingTime - playerInitTime;
                }
                
                PerformanceMonitor.VideoPerformanceMetrics metrics = 
                    new PerformanceMonitor.VideoPerformanceMetrics(
                        playerInitTime,
                        loadStartTime,
                        loadedDataTime,
                        playingTime,
                        firstFrameDuration
                    );
                PerformanceMonitor.recordVideoPerformanceMetrics(PAGE_ID, metrics);
                
                // 只在有完整数据时输出日志
                if (firstFrameDuration > 0) {
                    long connectTime = loadStartTime > 0 && playerInitTime > 0 ? 
                        (loadStartTime - playerInitTime) : 0;
                    long dataLoadTime = loadedDataTime > 0 && loadStartTime > 0 ? 
                        (loadedDataTime - loadStartTime) : 0;
                    long renderTime = playingTime > 0 && loadedDataTime > 0 ? 
                        (playingTime - loadedDataTime) : 0;
                    Log.d("LiveRoomActivity", String.format(
                        "首帧渲染: %dms (连接:%dms 数据:%dms 渲染:%dms)",
                        firstFrameDuration, connectTime, dataLoadTime, renderTime
                    ));
                }
            } catch (JSONException e) {
                Log.e("LiveRoomActivity", "解析视频性能指标失败: " + metricsJson, e);
            } catch (Exception e) {
                Log.e("LiveRoomActivity", "处理性能指标失败", e);
            }
        }
    }
    
    private void setupVideoPlayer() {
        PreloadManager preloadManager = PreloadManager.getInstance();
        
        // 确保videoContainer存在
        View tempVideoView = findViewById(R.id.video_view);
        if (tempVideoView == null) {
            Log.e("LiveRoomActivity", "无法找到video_view，无法设置播放器");
            return;
        }
        android.view.ViewGroup videoContainer = (android.view.ViewGroup) tempVideoView.getParent();
        if (videoContainer == null) {
            Log.e("LiveRoomActivity", "video_view的父容器不存在，无法设置播放器");
            return;
        }

        PreloadManager.TTLRoomCache ttlCache = preloadManager.getTTLRoomCache(roomId);
        boolean useTTLCache = false;
        if (ttlCache != null && ttlCache.webView != null && videoContainer != null) {
            // 先检查WebView是否真的被销毁（使用更宽松的检查）
            boolean isDestroyed = false;
            try {
                ttlCache.webView.getUrl();
            } catch (Exception e) {
                isDestroyed = true;
            }
            
            // 检查WebView的Context是否有效（Activity被销毁后，Context可能失效）
            boolean contextValid = true;
            try {
                android.content.Context context = ttlCache.webView.getContext();
                if (context instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) context;
                    // 检查Activity是否被销毁
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        contextValid = false;
                        Log.w("LiveRoomActivity", "TTL缓存WebView的Activity已销毁: roomId=" + roomId);
                    }
                }
            } catch (Exception e) {
                contextValid = false;
                Log.w("LiveRoomActivity", "检查TTL缓存WebView Context失败", e);
            }
            
            if (isDestroyed || !contextValid) {
                Log.w("LiveRoomActivity", "TTL缓存中的WebView已销毁或Context无效，使用其他逻辑: roomId=" + roomId);
                // TTL缓存无效，不使用，继续执行后面的fallback逻辑
                useTTLCache = false;
            } else {
                // TTL缓存有效，可以使用
                useTTLCache = true;
            }
        }
        
        if (useTTLCache) {
            Log.d("LiveRoomActivity", "使用TTL缓存恢复WebView: roomId=" + roomId);
            isUsingPreloadedWebView = true;
            preloadedRoomId = roomId;
            isUsingTTLCache = true; // 标记使用了TTL缓存
            
            videoView = findViewById(R.id.video_view);
            if (videoView == null) {
                Log.e("LiveRoomActivity", "无法找到video_view，无法恢复TTL缓存");
                loadPlayer();
                return;
            }
            
            android.view.ViewGroup.LayoutParams params = videoView.getLayoutParams();
            int index = videoContainer.indexOfChild(videoView);
            if (index < 0) {
                index = 0;
            }
            videoContainer.removeView(videoView);
            
            // 从隐藏容器中移除 WebView（如果存在）
            android.view.ViewParent parent = ttlCache.webView.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(ttlCache.webView);
                // 如果父容器是隐藏容器，也移除它
                if (parent.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) parent.getParent()).removeView((android.view.View) parent);
                }
            }
            
            // 确保WebView配置正确
            configureWebViewForPerformance(ttlCache.webView);
            
            // 设置WebView可见性和状态
            ttlCache.webView.setVisibility(android.view.View.VISIBLE);
            ttlCache.webView.setAlpha(1.0f);
            
            // 恢复WebView状态
            try {
                ttlCache.webView.onResume();
                ttlCache.webView.resumeTimers();
            } catch (Exception e) {
                Log.w("LiveRoomActivity", "恢复WebView状态失败", e);
            }
            
            // 添加到容器
            try {
                videoContainer.addView(ttlCache.webView, index, params);
                Log.d("LiveRoomActivity", "TTL缓存WebView已添加到容器");
            } catch (Exception e) {
                Log.e("LiveRoomActivity", "添加WebView到容器失败", e);
                // 如果添加失败，重新加载
                loadPlayer();
                return;
            }
            
            videoView = ttlCache.webView;
            videoView.requestFocus();
            
            // 确保WebView可见（不需要bringToFront，避免覆盖其他组件）
            videoView.post(() -> {
                if (videoView != null && !isWebViewDestroyed(videoView)) {
                    videoView.setVisibility(android.view.View.VISIBLE);
                    videoView.setAlpha(1.0f);
                    // 不调用bringToFront()，避免覆盖其他组件（主播信息、在线人数等）
                }
            });
            
            // 恢复数据：如果TTL缓存中的数据不完整，重新加载
            if (ttlCache.host != null) {
                viewModel.setHostData(ttlCache.host);
            } else {
                // TTL缓存中主播信息为空，重新加载
                Log.w("LiveRoomActivity", "TTL缓存中主播信息为空，重新加载: roomId=" + roomId);
                viewModel.loadHostInfo(roomId);
            }
            
            if (ttlCache.comments != null) {
                viewModel.setCommentsData(ttlCache.comments);
            } else {
                // TTL缓存中评论为空，重新加载
                Log.w("LiveRoomActivity", "TTL缓存中评论为空，重新加载");
                viewModel.loadComments();
            }
            
            if (ttlCache.onlineCount != null) {
                viewModel.setOnlineCount(ttlCache.onlineCount);
            }
            
            // 恢复评论列表滚动位置（恢复完整的Activity状态）
            if (ttlCache.commentScrollPosition >= 0 && commentRecyclerView != null) {
                pendingScrollPosition = ttlCache.commentScrollPosition;
                commentRecyclerView.post(() -> {
                    if (commentRecyclerView != null && pendingScrollPosition >= 0) {
                        try {
                            RecyclerView.LayoutManager layoutManager = commentRecyclerView.getLayoutManager();
                            if (layoutManager instanceof LinearLayoutManager) {
                                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                                if (lastVisiblePosition < 0) {
                                    lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition();
                                }
                                if (lastVisiblePosition < pendingScrollPosition - 3) {
                                    commentRecyclerView.scrollToPosition(pendingScrollPosition);
                                }
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                        pendingScrollPosition = -1;
                    }
                });
            }
            
            // 延迟检查，确保WebView已经完全添加到容器并可见
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (videoView == null || isWebViewDestroyed(videoView)) {
                    Log.w("LiveRoomActivity", "TTL缓存恢复后，WebView为空或已销毁，重新加载");
                    loadPlayer();
                    return;
                }
                
                // 再次确保WebView可见（不需要bringToFront，避免覆盖其他组件）
                try {
                    videoView.setVisibility(android.view.View.VISIBLE);
                    videoView.setAlpha(1.0f);
                    // 不调用bringToFront()，避免覆盖其他组件（主播信息、在线人数等）
                } catch (Exception e) {
                    Log.w("LiveRoomActivity", "设置WebView可见性失败", e);
                }
                
                // 检查WebView是否在容器中
                if (videoView.getParent() == null) {
                    Log.w("LiveRoomActivity", "TTL缓存WebView不在容器中，重新添加到容器");
                    try {
                        android.view.ViewGroup.LayoutParams layoutParams = videoView.getLayoutParams();
                        if (layoutParams == null) {
                            layoutParams = new android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            );
                        }
                        videoContainer.addView(videoView, layoutParams);
                    } catch (Exception e) {
                        Log.e("LiveRoomActivity", "重新添加WebView到容器失败", e);
                        loadPlayer();
                        return;
                    }
                }
                
                // 检查URL和加载状态
                String checkUrl = null;
                try {
                    checkUrl = videoView.getUrl();
                } catch (Exception e) {
                    Log.w("LiveRoomActivity", "检查WebView URL失败，重新加载", e);
                    loadPlayer();
                    return;
                }
                
                // 如果URL不对，重新加载
                if (checkUrl == null || !checkUrl.contains("player.html")) {
                    Log.w("LiveRoomActivity", "WebView URL异常，重新加载: url=" + checkUrl);
                    loadPlayer();
                    return;
                }
                
                // URL正确，恢复播放（取消静音，确保播放）
                Log.d("LiveRoomActivity", "TTL缓存WebView状态正常，恢复播放: url=" + checkUrl);
                try {
                    // 由于TTL缓存中的WebView在隐藏容器中保持活跃播放（只是静音），
                    // 恢复时只需要取消静音即可，不需要重新播放
                    String jsCode = "(function(){" +
                                   "try{" +
                                   "if(window.player&&window.player.video){" +
                                   "var video=window.player.video;" +
                                   "video.muted=false;" +
                                   "if(video.paused){video.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(err){console.log('play error:',err);});}" +
                                   "}" +
                                   "}catch(e){console.log('restore error:',e);}" +
                                   "})();";
                    videoView.evaluateJavascript(jsCode, null);
                } catch (Exception e) {
                    Log.e("LiveRoomActivity", "恢复播放失败", e);
                    // 如果恢复失败，重新加载播放器
                    loadPlayer();
                }
            }, 300);
        } else {
            // TTL缓存不存在或无效，正常加载
            Log.d("LiveRoomActivity", "TTL缓存不存在或无效，正常加载: roomId=" + roomId);
            
            // 只有item1第一次进入时，检查预加载的WebView
            if ("1".equals(roomId)) {
                WebView preloadedStreamWebView = preloadManager.getPreloadedStreamWebView(roomId);
                if (preloadedStreamWebView != null && preloadedStreamWebView.getParent() == null && videoContainer != null) {
                    isUsingPreloadedWebView = true;
                    preloadedRoomId = roomId;
                    
                    videoView = findViewById(R.id.video_view);
                    android.view.ViewGroup.LayoutParams params = videoView.getLayoutParams();
                    int index = videoContainer.indexOfChild(videoView);
                    videoContainer.removeView(videoView);
                    configureWebViewForPerformance(preloadedStreamWebView);
                    preloadedStreamWebView.setVisibility(android.view.View.VISIBLE);
                    videoContainer.addView(preloadedStreamWebView, index, params);
                    videoView = preloadedStreamWebView;
                    videoView.requestFocus();
                    
                    String currentUrl = null;
                    try {
                        currentUrl = videoView.getUrl();
                    } catch (Exception e) {
                        loadPlayer();
                        return;
                    }
                    
                    if (currentUrl != null && currentUrl.contains("player.html")) {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            if (videoView == null || isWebViewDestroyed(videoView)) {
                                return;
                            }
                            try {
                                String streamUrl = Uri.encode(DEFAULT_STREAM_URL);
                                String jsCode = "(function(){" +
                                               "try{" +
                                               "if(window.player&&window.player.video){" +
                                               "var video=window.player.video;" +
                                               "if(video.readyState>=3&&video.paused){" +
                                               "video.muted=false;video.play().catch(function(){});" +
                                               "}else if(video.readyState<3){" +
                                               "video.addEventListener('canplay',function(){video.muted=false;if(video.paused){video.play().catch(function(){});}},{once:true});" +
                                               "}" +
                                               "}" +
                                               "}catch(e){}" +
                                               "})();";
                                videoView.evaluateJavascript(jsCode, null);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }, 200);
                    } else {
                        loadPlayer();
                    }
                    return;
                }
            }
            
            // 其他情况：直接使用布局中的WebView，正常加载
            videoView = findViewById(R.id.video_view);
            if (videoView == null) {
                Log.e("LiveRoomActivity", "无法找到video_view，创建新的WebView");
                // 获取原始WebView的布局参数
                View originalView = tempVideoView;
                if (originalView != null) {
                    android.view.ViewGroup.LayoutParams originalParams = originalView.getLayoutParams();
                    int originalIndex = videoContainer.indexOfChild(originalView);
                    if (originalIndex >= 0) {
                        videoContainer.removeView(originalView);
                    }
                    videoView = new WebView(this);
                    videoContainer.addView(videoView, originalIndex >= 0 ? originalIndex : 0, originalParams);
                } else {
                    videoView = new WebView(this);
                    android.view.ViewGroup.LayoutParams params = new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    videoContainer.addView(videoView, params);
                }
            }
            configureWebViewForPerformance(videoView);
            // 确保 WebView 可见且可获得焦点
            videoView.setVisibility(android.view.View.VISIBLE);
            videoView.setAlpha(1.0f);
            videoView.requestFocus();
            loadPlayer();
        }
    }
    
    /**
     * 如果预加载的 WebView 已经加载了播放器，检查是否需要更新流地址
     */
    private void updateStreamUrlIfNeeded() {
        if (videoView != null) {
            String streamUrlParam = getIntent().getStringExtra("stream_url");
            final String streamUrl = (streamUrlParam == null || streamUrlParam.isEmpty()) 
                    ? DEFAULT_STREAM_URL 
                    : streamUrlParam;
            
            // 检查 WebView 是否已经加载了内容（预加载的情况）
            String currentUrl = videoView.getUrl();
            if (currentUrl != null && currentUrl.contains("player.html")) {
                // WebView 已经加载了播放器，通过 JavaScript 更新流地址（如果需要）
                String currentStreamUrl = extractStreamUrlFromUrl(currentUrl);
                if (!streamUrl.equals(currentStreamUrl)) {
                    // 流地址不同，需要更新
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        // 使用安全的方法转义 JavaScript 字符串
                        String escapedUrl = escapeJsString(streamUrl);
                        String encodedUrl = Uri.encode(streamUrl);
                        // 使用单引号包裹转义后的 URL
                        String jsCode = "if (typeof window.switchStreamUrl === 'function') { " +
                                       "window.switchStreamUrl('" + escapedUrl + "'); " +
                                       "} else if (window.player && window.player.switchURL) { " +
                                       "window.player.switchURL('" + escapedUrl + "'); " +
                                       "} else { " +
                                       "location.href = 'file:///android_asset/player.html?url=" + encodedUrl + "'; " +
                                       "}";
                        videoView.evaluateJavascript(jsCode, null);
                    }, 500);
                } else {
                    // 流地址相同，预加载的 WebView 已经加载了正确的流，需要启动播放（预加载模式不自动播放）
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        // 启动播放（预加载模式只缓冲，不播放，现在需要开始播放）
                        // 优化：确保播放器真正开始播放，检查播放状态
                        String jsCode = "try { " +
                                       "if (window.player && window.player.video) { " +
                                       "  var video = window.player.video; " +
                                       "  video.muted = true; " +
                                       "  if (video.paused) { " +
                                       "    video.play().then(function() { " +
                                       "      console.log('✓ 预加载模式：开始播放'); " +
                                       "    }).catch(function(err) { " +
                                       "      console.error('播放失败:', err); " +
                                       "    }); " +
                                       "  } else { " +
                                       "    console.log('视频已在播放'); " +
                                       "  } " +
                                       "} " +
                                       "} catch(e) { console.error('启动播放失败:', e); }";
                        videoView.evaluateJavascript(jsCode, null);
                    }, 500);
                }
            } else {
                // WebView 未加载播放器，正常加载
                loadPlayer();
            }
        }
    }
    
    /**
     * 从 URL 中提取流地址
     */
    private String extractStreamUrlFromUrl(String url) {
        if (url != null && url.contains("player.html")) {
            try {
                int urlParamIndex = url.indexOf("url=");
                if (urlParamIndex != -1) {
                    String urlParam = url.substring(urlParamIndex + 4);
                    int nextParamIndex = urlParam.indexOf("&");
                    if (nextParamIndex != -1) {
                        urlParam = urlParam.substring(0, nextParamIndex);
                    }
                    return Uri.decode(urlParam);
                }
            } catch (Exception e) {
                Log.e("LiveRoomActivity", "提取流地址失败", e);
            }
        }
        return "";
    }
    
    private void loadPlayer() {
        if (videoView == null) {
            Log.w("LiveRoomActivity", "loadPlayer: videoView为null，无法加载播放器");
            return;
        }
        
        try {
            if (isWebViewDestroyed(videoView)) {
                Log.w("LiveRoomActivity", "loadPlayer: WebView已销毁，无法加载播放器");
                return;
            }
        } catch (Exception e) {
            // 如果是新创建的 WebView，getUrl() 可能返回 null，这是正常的，继续执行
        }
        
        // 确保WebView配置正确（包括背景色）
        configureWebViewForPerformance(videoView);
        
        // 确保WebView可见
        videoView.setVisibility(android.view.View.VISIBLE);
        videoView.setAlpha(1.0f);
        
        String streamUrlParam = getIntent().getStringExtra("stream_url");
        final String streamUrl = (streamUrlParam == null || streamUrlParam.isEmpty()) 
                ? DEFAULT_STREAM_URL 
                : streamUrlParam;
        
        String htmlUrl = "file:///android_asset/player.html?url=" + Uri.encode(streamUrl);
        Log.d("LiveRoomActivity", "loadPlayer: 加载播放器 URL=" + htmlUrl);
        try {
            videoView.loadUrl(htmlUrl);
        } catch (Exception e) {
            Log.e("LiveRoomActivity", "加载播放器失败", e);
            return;
        }
        
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (videoView == null) {
                return;
            }
            
            try {
                if (isWebViewDestroyed(videoView)) {
                    return;
                }
            } catch (Exception e) {
                // Ignore
            }
            
            try {
                String encodedUrl = Uri.encode(streamUrl);
                String jsCode = "(function(){" +
                               "try{" +
                               "if(window.player&&window.player.video){" +
                               "var video=window.player.video;" +
                               "if(video.readyState>=3&&video.paused){" +
                               "video.muted=false;video.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(){});" +
                               "}else if(video.readyState<3){" +
                               "video.addEventListener('canplay',function(){video.muted=false;if(video.paused){video.play().then(function(){if(window.recordTime){window.recordTime('playingTime');}}).catch(function(){});}},{once:true});" +
                               "}" +
                               "}else{" +
                               "setTimeout(function(){if(window.player&&window.player.video){var v=window.player.video;if(v.readyState>=3&&v.paused){v.muted=false;v.play().catch(function(){});}}},500);" +
                               "}" +
                               "}catch(e){}" +
                               "})();";
                videoView.evaluateJavascript(jsCode, null);
            } catch (Exception e) {
                // Ignore
            }
        }, 300);
    }

    private void setupRecyclerView() {
        commentAdapter = new CommentAdapter();
        commentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        commentRecyclerView.setAdapter(commentAdapter);
    }

    private void setupObservers() {
        // 观察主播信息
        viewModel.getHostData().observe(this, host -> {
            if (host != null) {
                updateHostInfo(host);
            }
        });

        viewModel.getCommentsData().observe(this, comments -> {
            if (comments != null && commentAdapter != null) {
                commentAdapter.setComments(comments);
                
                if (comments.size() > 0 && commentRecyclerView != null) {
                    int lastPosition = comments.size() - 1;
                    pendingScrollPosition = lastPosition;
                    
                    if (commentScrollRunnable != null) {
                        commentScrollHandler.removeCallbacks(commentScrollRunnable);
                    }
                    
                    commentScrollRunnable = () -> {
                        if (commentRecyclerView != null && pendingScrollPosition >= 0) {
                            try {
                                RecyclerView.LayoutManager layoutManager = commentRecyclerView.getLayoutManager();
                                if (layoutManager instanceof LinearLayoutManager) {
                                    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                                    int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                                    if (lastVisiblePosition < pendingScrollPosition - 3) {
                                        commentRecyclerView.scrollToPosition(pendingScrollPosition);
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                            pendingScrollPosition = -1;
                        }
                    };
                    commentScrollHandler.postDelayed(commentScrollRunnable, COMMENT_SCROLL_DELAY_MS);
                }
            }
        });

        // 观察发送评论错误
        viewModel.getSendCommentError().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, "发送失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        // 观察在线人数
        // 优化：减少 UI 更新频率，避免频繁的 setText 操作
        viewModel.getOnlineCount().observe(this, count -> {
            if (count != null && onlineCount != null) {
                String newText = "在线: " + count;
                String currentText = onlineCount.getText().toString();
                if (!newText.equals(currentText)) {
                    onlineCount.setText(newText);
                }
            }
        });
        
        // 观察点赞数（来自点赞插件）
        LiveData<Integer> totalLikes = viewModel.getTotalLikes();
        if (totalLikes != null) {
            // 优化：使用 ViewStub 延迟加载点赞 UI（只在需要时加载）
            android.view.ViewStub likeStub = findViewById(R.id.like_container_stub);
            if (likeStub != null) {
                // 延迟加载 ViewStub（在 UI 渲染完成后加载，避免阻塞首屏）
                findViewById(android.R.id.content).post(() -> {
                    try {
                        if (likeStub.getParent() != null) {
                            likeContainer = likeStub.inflate();
                            likeButton = likeContainer.findViewById(R.id.like_button);
                            likeCount = likeContainer.findViewById(R.id.like_count);
                            
                            // 观察点赞数变化
                            totalLikes.observe(this, count -> {
                                if (count != null && likeCount != null) {
                                    likeCount.setText(String.valueOf(count));
                                }
                            });
                            
                            // 设置点赞按钮点击事件
                            if (likeButton != null) {
                                likeButton.setOnClickListener(v -> {
                                    viewModel.like();
                                    // 可以添加点赞动画效果
                                    likeButton.animate()
                                        .scaleX(1.2f)
                                        .scaleY(1.2f)
                                        .setDuration(100)
                                        .withEndAction(() -> likeButton.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(100)
                                            .start())
                                        .start();
                                });
                            }
                        }
                    } catch (Exception e) {
                        // 静默处理
                    }
                });
            }
        }
    }
    

    private void updateHostInfo(Host host) {
        // 优化：只在值变化时才更新 UI，避免不必要的 setText 操作
        if (hostName != null && host.getName() != null) {
            String newName = host.getName();
            if (!newName.equals(hostName.getText().toString())) {
                hostName.setText(newName);
            }
        }
        if (hostFollowers != null) {
            String newFollowers = "关注: " + host.getFollowerNum();
            if (!newFollowers.equals(hostFollowers.getText().toString())) {
                hostFollowers.setText(newFollowers);
            }
        }
        if (hostAvatar != null && host.getAvatar() != null) {
            Glide.with(this)
                    .load(host.getAvatar())
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher)
                    .listener(new com.bytedance.myapplication.utils.GlideImageLoadListener(PAGE_ID))
                    .into(hostAvatar);
        } else {
            PerformanceMonitor.recordImageLoadComplete(PAGE_ID);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (smoothnessMonitor != null) {
            smoothnessMonitor.stopMonitoring();
        }
        // 停止播放监控
        stopPlaybackMonitoring();
        // 注意：对于直播流，不在 onPause 时暂停 WebView
        // WebView.onPause() 会暂停 JavaScript 执行和渲染，导致播放卡住
        // 直播流需要持续播放，让 WebView 自然运行（与浏览器打开 HTML 文件的行为一致）
    }
    
    private Handler playbackCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable playbackCheckRunnable;
    
    // 评论滚动防抖：减少滚动计算频率，避免频繁布局计算
    private Handler commentScrollHandler = new Handler(Looper.getMainLooper());
    private Runnable commentScrollRunnable;
    private int pendingScrollPosition = -1;
    private static final long COMMENT_SCROLL_DELAY_MS = 200; // 200ms 防抖
    
    @Override
    protected void onResume() {
        super.onResume();
        if (smoothnessMonitor != null) {
            smoothnessMonitor.startMonitoring();
        }
        
        // 重要：恢复 WebView 状态，确保播放正常
        // 即使不在 onPause 时暂停，系统也可能在后台时自动暂停了 WebView
        // 恢复操作是安全的，可以确保 WebView 处于活动状态
        resumeWebView();
        
        // 检查WebView是否在容器中（防止切回来时WebView丢失）
        if (videoView != null && !isWebViewDestroyed(videoView)) {
            View tempView = findViewById(R.id.video_view);
            android.view.ViewGroup videoContainer = null;
            if (tempView != null) {
                android.view.ViewParent parent = tempView.getParent();
                if (parent instanceof android.view.ViewGroup) {
                    videoContainer = (android.view.ViewGroup) parent;
                }
            }
            
            if (videoContainer != null && videoView.getParent() == null) {
                Log.w("LiveRoomActivity", "onResume: WebView不在容器中，重新添加");
                try {
                    android.view.ViewGroup.LayoutParams params = videoView.getLayoutParams();
                    if (params == null) {
                        params = new android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        );
                    }
                    int index = videoContainer.indexOfChild(tempView);
                    if (index >= 0) {
                        videoContainer.removeView(tempView);
                        videoContainer.addView(videoView, index, params);
                    } else {
                        videoContainer.addView(videoView, params);
                    }
                    videoView.setVisibility(android.view.View.VISIBLE);
                    videoView.setAlpha(1.0f);
                    // 不调用bringToFront()，避免覆盖其他组件（主播信息、在线人数等）
                } catch (Exception e) {
                    Log.e("LiveRoomActivity", "onResume: 重新添加WebView失败", e);
                    // 如果添加失败，重新加载播放器
                    loadPlayer();
                }
            } else if (videoView.getParent() != null) {
                // WebView在容器中，确保可见（不需要bringToFront，避免覆盖其他组件）
                videoView.setVisibility(android.view.View.VISIBLE);
                videoView.setAlpha(1.0f);
                // 不调用bringToFront()，避免覆盖其他组件（主播信息、在线人数等）
            } else {
                Log.w("LiveRoomActivity", "onResume: 无法找到videoContainer，WebView可能丢失");
            }
        }
        
        // 启动定期检查播放状态
        startPlaybackMonitoring();
    }
    
    /**
     * 恢复 WebView 状态，确保播放正常
     * 优化：使用轻量操作，避免阻塞主线程
     * 优化：分离恢复操作和播放检查，避免同时执行导致阻塞
     */
    /**
     * 检查 WebView 是否已被销毁
     */
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
    
    private void resumeWebView() {
        if (videoView == null) {
            return;
        }
        
        try {
            if (isWebViewDestroyed(videoView)) {
                return;
            }
            
            videoView.onResume();
            videoView.resumeTimers();
            
            if (!videoView.hasFocus()) {
                videoView.requestFocus();
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 重要：当窗口获得焦点时，恢复 WebView 状态
        // 系统可能在窗口失去焦点时自动暂停了 WebView
        if (hasFocus && videoView != null && !isWebViewDestroyed(videoView)) {
            resumeWebView();
        }
    }
    
    /**
     * 确保播放器继续播放
     * 优化：移除 evaluateJavascript 调用，避免阻塞主线程
     * 只使用 WebView 的 resumeTimers() 和 requestFocus()，这些操作更轻量
     */
    private void ensurePlaybackContinues() {
        if (videoView == null || isWebViewDestroyed(videoView)) {
            return;
        }
        try {
            videoView.resumeTimers();
            if (!videoView.hasFocus()) {
                videoView.requestFocus();
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * 启动播放监控，定期检查并恢复 WebView 状态
     * 优化：大幅减少操作频率，避免阻塞主线程
     * 优化：只使用轻量操作，不调用 evaluateJavascript
     */
    private void startPlaybackMonitoring() {
        // 停止之前的监控
        stopPlaybackMonitoring();
        
        // 优化：大幅延长监控间隔到 30 秒，减少主线程负担
        // 只使用轻量操作，不调用 evaluateJavascript
        Runnable[] runnableRef = new Runnable[1];
        playbackCheckRunnable = runnableRef[0] = () -> {
            if (videoView == null || isWebViewDestroyed(videoView)) {
                stopPlaybackMonitoring();
                return;
            }
            try {
                videoView.resumeTimers();
            } catch (Exception e) {
                stopPlaybackMonitoring();
                return;
            }
            playbackCheckHandler.postDelayed(runnableRef[0], 30000);
        };
        // 延迟启动，避免在 onCreate 时立即执行
        playbackCheckHandler.postDelayed(playbackCheckRunnable, 30000);
    }
    
    /**
     * 停止播放监控
     */
    private void stopPlaybackMonitoring() {
        if (playbackCheckRunnable != null) {
            playbackCheckHandler.removeCallbacks(playbackCheckRunnable);
            playbackCheckRunnable = null;
        }
        // 清理评论滚动防抖任务
        if (commentScrollRunnable != null) {
            commentScrollHandler.removeCallbacks(commentScrollRunnable);
            commentScrollRunnable = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停用所有插件
        PluginManager.getInstance().deactivateAll(this);
        
        // 停止播放监控
        stopPlaybackMonitoring();
        
        if (videoView != null) {
            PreloadManager preloadManager = PreloadManager.getInstance();
            String currentRoomId = roomId;
            if (currentRoomId == null || currentRoomId.isEmpty()) {
                currentRoomId = isUsingPreloadedWebView && preloadedRoomId != null ? preloadedRoomId : null;
            }
            
            try {
                if (!isWebViewDestroyed(videoView)) {
                    // 检查WebView是否正在加载中（缓冲状态）
                    boolean isLoading = false;
                    try {
                        isLoading = videoView.getProgress() < 100;
                    } catch (Exception e) {
                        // Ignore
                    }
                    
                    // 如果正在加载中，不保存到TTL缓存，直接回收到复用池
                    if (isLoading) {
                        Log.d("LiveRoomActivity", "WebView正在加载中，不保存到TTL缓存，回收到复用池");
                        // 停止加载
                        try {
                            videoView.stopLoading();
                        } catch (Exception e) {
                            // Ignore
                        }
                        // 确保WebView从容器中移除
                        android.view.ViewParent parent = videoView.getParent();
                        if (parent instanceof android.view.ViewGroup) {
                            ((android.view.ViewGroup) parent).removeView(videoView);
                        }
                        // 回收到复用池
                        preloadManager.returnWebViewForReuse(videoView);
                    } else {
                        // WebView已加载完成，可以保存到TTL缓存
                        Host host = viewModel.getHostData().getValue();
                        List<Comment> comments = viewModel.getCommentsData().getValue();
                        Integer onlineCount = viewModel.getOnlineCount().getValue();
                        
                        // 检查数据是否完整：如果host为null，说明主播信息还没加载完成，不保存到TTL缓存
                        // 因为TTL缓存应该保存完整的Activity状态，包括主播信息
                        if (host == null) {
                            Log.d("LiveRoomActivity", "主播信息未加载完成，不保存到TTL缓存，回收到复用池");
                            // 确保WebView从容器中移除
                            android.view.ViewParent parent = videoView.getParent();
                            if (parent instanceof android.view.ViewGroup) {
                                ((android.view.ViewGroup) parent).removeView(videoView);
                            }
                            // 回收到复用池
                            preloadManager.returnWebViewForReuse(videoView);
                        } else {
                            // 数据完整，可以保存到TTL缓存
                            // 获取评论列表滚动位置
                            int commentScrollPosition = -1;
                            if (commentRecyclerView != null) {
                                try {
                                    RecyclerView.LayoutManager layoutManager = commentRecyclerView.getLayoutManager();
                                    if (layoutManager instanceof LinearLayoutManager) {
                                        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                                        commentScrollPosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                                        if (commentScrollPosition < 0) {
                                            commentScrollPosition = linearLayoutManager.findLastVisibleItemPosition();
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
                            
                            // 确保WebView从容器中移除，但不要销毁
                            android.view.ViewParent parent = videoView.getParent();
                            if (parent instanceof android.view.ViewGroup) {
                                ((android.view.ViewGroup) parent).removeView(videoView);
                            }
                            
                            if (currentRoomId != null && !currentRoomId.isEmpty()) {
                                // 保存到TTL缓存（包含完整的Activity状态）
                                preloadManager.returnRoomWithTTL(currentRoomId, videoView, host, comments, onlineCount, commentScrollPosition, 0L);
                            } else {
                                // 如果没有roomId，尝试回收到复用池
                                WebView preloadedWebView = preloadManager.getPreloadedWebView();
                                if (preloadedWebView == videoView) {
                                    try {
                                        videoView.onPause();
                                        videoView.pauseTimers();
                                        videoView.setVisibility(android.view.View.GONE);
                                    } catch (Exception e) {
                                        // Ignore
                                    }
                                } else {
                                    preloadManager.returnWebViewForReuse(videoView);
                                }
                            }
                        }
                    }
                } else {
                    // WebView已销毁，清理引用
                    Log.w("LiveRoomActivity", "WebView已销毁，无法保存到TTL缓存");
                }
            } catch (Exception e) {
                Log.e("LiveRoomActivity", "保存TTL缓存失败", e);
            }
            videoView = null;
        }
        
        // 重置标记
        isUsingPreloadedWebView = false;
        preloadedRoomId = null;
        
        if (smoothnessMonitor != null) {
            smoothnessMonitor.stopMonitoring();
            smoothnessMonitor = null;
        }
        // 输出最终性能报告
        PerformanceMonitor.printFinalReport(PAGE_ID);
        PerformanceMonitor.clearPageData(PAGE_ID);
    }

    private void setupSendButton() {
        sendButton.setOnClickListener(v -> {
            String commentText = commentInput.getText().toString().trim();
            if (TextUtils.isEmpty(commentText)) {
                Toast.makeText(this, "请输入评论内容", Toast.LENGTH_SHORT).show();
                return;
            }

            // 通过 ViewModel 发送评论
            viewModel.sendComment(commentText);
            commentInput.setText("");
        });
    }
    
    /**
     * 安全地转义 JavaScript 字符串（用于单引号字符串）
     * 转义所有特殊字符，确保字符串可以安全地嵌入到 JavaScript 代码中
     */
    private String escapeJsString(String str) {
        if (str == null) {
            return "null";
        }
        // 转义所有可能破坏 JavaScript 字符串的特殊字符
        return str.replace("\\", "\\\\")      // 反斜杠必须最先转义
                  .replace("'", "\\'")        // 单引号
                  .replace("\"", "\\\"")      // 双引号
                  .replace("\n", "\\n")       // 换行符
                  .replace("\r", "\\r")       // 回车符
                  .replace("\t", "\\t")       // 制表符
                  .replace("\f", "\\f")       // 换页符
                  .replace("\b", "\\b");      // 退格符
    }

}
