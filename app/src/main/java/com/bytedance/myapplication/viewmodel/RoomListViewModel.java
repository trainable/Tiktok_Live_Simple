package com.bytedance.myapplication.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bytedance.myapplication.LiveBoard;
import com.bytedance.myapplication.model.Host;
import com.bytedance.myapplication.utils.ApiService;
import com.bytedance.myapplication.utils.PreloadManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class RoomListViewModel extends AndroidViewModel {
    private static final String TAG = "RoomListViewModel";
    private MutableLiveData<List<String>> roomIds = new MutableLiveData<>();
    private MutableLiveData<Map<String, Host>> roomInfoCache = new MutableLiveData<>();
    private final ExecutorService executor = LiveBoard.getApplicationExecutor();

    public RoomListViewModel(@NonNull Application application) {
        super(application);
        loadRoomIds();
    }

    public LiveData<List<String>> getRoomIds() {
        return roomIds;
    }
    
    public LiveData<Map<String, Host>> getRoomInfoCache() {
        return roomInfoCache;
    }

    private void loadRoomIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ids.add(String.valueOf(i));
        }
        roomIds.postValue(ids);
        
        PreloadManager preloadManager = PreloadManager.getInstance();
        Map<String, Host> preloadedCache = preloadManager.getPreloadedRoomInfoCache();
        if (preloadedCache != null && !preloadedCache.isEmpty()) {
            roomInfoCache.setValue(new HashMap<>(preloadedCache));
        }
        
        preloadAllRoomInfo(ids);
    }
    
    private void preloadAllRoomInfo(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return;
        }
        
        Map<String, Host> cache = new HashMap<>();
        Map<String, Host> existingCache = roomInfoCache.getValue();
        if (existingCache != null) {
            cache.putAll(existingCache);
        }
        
        List<String> missingRoomIds = new ArrayList<>();
        for (String roomId : roomIds) {
            if (!cache.containsKey(roomId)) {
                missingRoomIds.add(roomId);
            }
        }
        
        if (missingRoomIds.isEmpty()) {
            return;
        }
        
        CountDownLatch latch = new CountDownLatch(missingRoomIds.size());
        
        for (String roomId : missingRoomIds) {
            ApiService.getHostInfo(roomId, new ApiService.ApiCallback<Host>() {
                @Override
                public void onSuccess(Host host) {
                    synchronized (cache) {
                        cache.put(roomId, host);
                        roomInfoCache.postValue(new HashMap<>(cache));
                    }
                    latch.countDown();
                }

                @Override
                public void onFailure(String error) {
                    latch.countDown();
                }
            });
        }
        
        executor.execute(() -> {
            try {
                latch.await();
                synchronized (cache) {
                    roomInfoCache.postValue(new HashMap<>(cache));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (cache) {
                    roomInfoCache.postValue(new HashMap<>(cache));
                }
            }
        });
    }
}

