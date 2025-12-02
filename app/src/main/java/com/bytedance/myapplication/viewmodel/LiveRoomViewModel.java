package com.bytedance.myapplication.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bytedance.myapplication.model.Comment;
import com.bytedance.myapplication.model.Host;
import com.bytedance.myapplication.plugin.PluginManager;
import com.bytedance.myapplication.plugin.example.LikePlugin;
import com.bytedance.myapplication.repository.LiveRoomRepository;
import com.bytedance.myapplication.utils.ApiService;
import com.bytedance.myapplication.utils.PreloadManager;
import com.bytedance.myapplication.utils.WebSocketManager;

import java.util.ArrayList;
import java.util.List;

public class LiveRoomViewModel extends AndroidViewModel {
    private static final String TAG = "LiveRoomViewModel";
    
    private LiveRoomRepository repository;
    private WebSocketManager webSocketManager;
    
    // LiveData for Host info
    private MutableLiveData<Host> hostData = new MutableLiveData<>();
    
    // LiveData for Comments
    private MutableLiveData<List<Comment>> commentsData = new MutableLiveData<>();
    
    // LiveData for sending comment error
    private MutableLiveData<String> sendCommentError = new MutableLiveData<>();
    
    // LiveData for online count
    private MutableLiveData<Integer> onlineCount = new MutableLiveData<>();
    
    // 点赞插件（通过插件管理器获取）
    private LikePlugin likePlugin;
    
    // 在线人数更新防抖：累积待更新的数量，延迟更新
    private Handler onlineCountUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable onlineCountUpdateRunnable;
    private int pendingOnlineCountIncrement = 0;
    private static final long ONLINE_COUNT_UPDATE_DELAY_MS = 500; // 500ms 防抖

    public LiveRoomViewModel(@NonNull Application application) {
        super(application);
        repository = LiveRoomRepository.getInstance();
        onlineCount.setValue(0);
        
        // 获取点赞插件（如果已注册）
        likePlugin = (LikePlugin) PluginManager.getInstance().getPlugin("LikePlugin");
    }

    public LiveData<Host> getHostData() {
        return hostData;
    }

    public LiveData<List<Comment>> getCommentsData() {
        return commentsData;
    }

    public LiveData<String> getSendCommentError() {
        return sendCommentError;
    }

    public LiveData<Integer> getOnlineCount() {
        return onlineCount;
    }
    
    public void setHostData(Host host) {
        if (host != null) {
            hostData.postValue(host);
        }
    }
    
    public void setCommentsData(List<Comment> comments) {
        if (comments != null) {
            commentsData.postValue(comments);
        }
    }
    
    public void setOnlineCount(Integer count) {
        if (count != null) {
            onlineCount.postValue(count);
        }
    }
    
    /**
     * 获取点赞数 LiveData（来自点赞插件）
     * 如果插件未启用，返回 null
     */
    public LiveData<Integer> getTotalLikes() {
        if (likePlugin != null && likePlugin.isEnabled()) {
            return likePlugin.getTotalLikes();
        }
        return null;
    }
    
    /**
     * 点赞（通过插件）
     */
    public void like() {
        if (likePlugin != null && likePlugin.isEnabled()) {
            likePlugin.like();
        }
    }
    
    /**
     * 批量点赞（通过插件）
     */
    public void likeMultiple(int count) {
        if (likePlugin != null && likePlugin.isEnabled()) {
            likePlugin.likeMultiple(count);
        }
    }

    public void loadHostInfo(String roomId) {
        repository.getHostInfo(roomId, new ApiService.ApiCallback<Host>() {
            @Override
            public void onSuccess(Host host) {
                hostData.postValue(host);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "loadHostInfo failed: " + error);
            }
        });
    }

    public void loadComments() {
        repository.getComments(new ApiService.ApiCallback<List<Comment>>() {
            @Override
            public void onSuccess(List<Comment> comments) {
                commentsData.postValue(comments);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "loadComments failed: " + error);
            }
        });
    }

    public void sendComment(String commentText) {
        repository.sendComment(commentText, new ApiService.ApiCallback<Comment>() {
            @Override
            public void onSuccess(Comment comment) {
                List<Comment> currentComments = commentsData.getValue();
                if (currentComments == null) {
                    currentComments = new ArrayList<>();
                }
                currentComments.add(comment);
                commentsData.postValue(currentComments);
            }

            @Override
            public void onFailure(String error) {
                sendCommentError.postValue(error);
            }
        });
    }

    public void setupWebSocket() {
        try {
            PreloadManager preloadManager = PreloadManager.getInstance();
            webSocketManager = preloadManager.getPreloadedWebSocketManager();
            
            webSocketManager.connect(new WebSocketManager.WebSocketCallback() {
                @Override
                public void onMessage(String message) {
                    // 优化：使用防抖机制，累积多次消息后一次性更新
                    // 避免频繁的 UI 更新导致主线程阻塞
                    pendingOnlineCountIncrement++;
                    
                    // 取消之前的更新任务
                    if (onlineCountUpdateRunnable != null) {
                        onlineCountUpdateHandler.removeCallbacks(onlineCountUpdateRunnable);
                    }
                    
                    // 延迟更新（防抖）
                    onlineCountUpdateRunnable = new Runnable() {
                        @Override
                        public void run() {
                            Integer currentCount = onlineCount.getValue();
                            if (currentCount == null) {
                                currentCount = 0;
                            }
                            onlineCount.postValue(currentCount + pendingOnlineCountIncrement);
                            pendingOnlineCountIncrement = 0; // 重置累积值
                        }
                    };
                    onlineCountUpdateHandler.postDelayed(onlineCountUpdateRunnable, ONLINE_COUNT_UPDATE_DELAY_MS);
                }

                @Override
                public void onOpen() {
                    if (webSocketManager != null) {
                        webSocketManager.sendMessage("test");
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "WebSocket onFailure", t);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "setupWebSocket: exception", e);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // 清理防抖任务
        if (onlineCountUpdateRunnable != null) {
            onlineCountUpdateHandler.removeCallbacks(onlineCountUpdateRunnable);
            onlineCountUpdateRunnable = null;
        }
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
    }
}

