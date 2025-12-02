package com.bytedance.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AbsListView;
import android.widget.GridView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bytedance.myapplication.R;
import com.bytedance.myapplication.adapter.RoomListAdapter;
import com.bytedance.myapplication.utils.PerformanceMonitor;
import com.bytedance.myapplication.utils.PreloadManager;
import com.bytedance.myapplication.utils.SmoothnessMonitor;
import com.bytedance.myapplication.viewmodel.RoomListViewModel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoomListActivity extends AppCompatActivity {
    private static final String PAGE_ID = "RoomListActivity";

    private GridView gridView;
    private RoomListAdapter adapter;
    private RoomListViewModel viewModel;
    private SmoothnessMonitor smoothnessMonitor;
    private final Handler preloadHandler = new Handler(Looper.getMainLooper());
    private Runnable preloadRunnable;
    private final Set<String> preloadedRoomIds = new HashSet<>();
    private static final long PRELOAD_DELAY_MS = 100;
    private static final int MAX_PRELOAD_COUNT = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PerformanceMonitor.recordPageStartTime(PAGE_ID);
        smoothnessMonitor = new SmoothnessMonitor(PAGE_ID);
        setContentView(R.layout.activity_room_list);

        viewModel = new ViewModelProvider(this).get(RoomListViewModel.class);
        gridView = findViewById(R.id.room_grid_view);

        viewModel.getRoomIds().observe(this, roomIds -> {
            if (roomIds != null && !roomIds.isEmpty()) {
                int visibleItemCount = Math.min(roomIds.size(), 6);
                PerformanceMonitor.initImageLoadMonitor(PAGE_ID, visibleItemCount);
                
                if (adapter == null) {
                    adapter = new RoomListAdapter(this, roomIds, PAGE_ID);
                    gridView.setAdapter(adapter);

                    gridView.setOnItemClickListener((parent, view, position, id) -> {
                        String roomId = roomIds.get(position);
                        Intent intent = new Intent(RoomListActivity.this, LiveRoomActivity.class);
                        intent.putExtra("room_id", roomId);
                        startActivity(intent);
                    });
                    
                    setupPreloadOnScroll();
                } else {
                    adapter.updateRoomIds(roomIds);
                }
            }
        });
        
        viewModel.getRoomInfoCache().observe(this, cache -> {
            if (cache != null && adapter != null) {
                adapter.updateRoomInfoCache(cache);
            }
        });
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
    
    private void setupPreloadOnScroll() {
        gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    schedulePreload();
                } else {
                    cancelPreload();
                }
            }
            
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, 
                               int visibleItemCount, int totalItemCount) {
                cancelPreload();
                schedulePreload();
            }
        });
        
        schedulePreload();
    }
    
    private void schedulePreload() {
        cancelPreload();
        preloadRunnable = this::preloadVisibleRooms;
        preloadHandler.postDelayed(preloadRunnable, PRELOAD_DELAY_MS);
    }
    
    private void cancelPreload() {
        if (preloadRunnable != null) {
            preloadHandler.removeCallbacks(preloadRunnable);
            preloadRunnable = null;
        }
    }
    
    private void preloadVisibleRooms() {
        if (adapter == null || viewModel == null) {
            return;
        }
        
        List<String> roomIds = viewModel.getRoomIds().getValue();
        if (roomIds == null || roomIds.isEmpty()) {
            return;
        }
        
        PreloadManager preloadManager = PreloadManager.getInstance();
        int firstVisible = gridView.getFirstVisiblePosition();
        int visibleCount = gridView.getLastVisiblePosition() - firstVisible + 1;
        int preloadCount = Math.min(MAX_PRELOAD_COUNT, Math.min(visibleCount, roomIds.size() - firstVisible));
        
        for (int i = 0; i < preloadCount; i++) {
            int position = firstVisible + i;
            if (position < roomIds.size()) {
                String roomId = roomIds.get(position);
                if (!preloadedRoomIds.contains(roomId)) {
                    preloadedRoomIds.add(roomId);
                    preloadManager.preloadStreamForRoom(RoomListActivity.this, roomId);
                }
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPreload();
        preloadedRoomIds.clear();
        
        if (smoothnessMonitor != null) {
            smoothnessMonitor.stopMonitoring();
            smoothnessMonitor = null;
        }
        PerformanceMonitor.clearPageData(PAGE_ID);
    }
}
