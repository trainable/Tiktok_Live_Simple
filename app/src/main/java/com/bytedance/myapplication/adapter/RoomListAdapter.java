package com.bytedance.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bytedance.myapplication.R;
import com.bytedance.myapplication.model.Host;
import com.bytedance.myapplication.utils.ApiService;
import com.bytedance.myapplication.utils.GlideImageLoadListener;
import com.bytedance.myapplication.utils.ViewPoolManager;

import java.util.List;
import java.util.Map;

public class RoomListAdapter extends BaseAdapter {
    private Context context;
    private List<String> roomIds;
    private LayoutInflater inflater;
    private Map<String, Host> roomInfoCache; // 房间信息缓存
    private String pageId; // 页面ID，用于性能监控
    private ViewPoolManager viewPoolManager; // View 池管理器

    public RoomListAdapter(Context context, List<String> roomIds) {
        this.context = context;
        this.roomIds = roomIds;
        this.inflater = LayoutInflater.from(context);
        this.viewPoolManager = ViewPoolManager.getInstance();
    }
    
    public RoomListAdapter(Context context, List<String> roomIds, String pageId) {
        this.context = context;
        this.roomIds = roomIds;
        this.inflater = LayoutInflater.from(context);
        this.pageId = pageId;
        this.viewPoolManager = ViewPoolManager.getInstance();
    }
    
    public void updateRoomInfoCache(Map<String, Host> cache) {
        this.roomInfoCache = cache;
        notifyDataSetChanged();
    }
    
    /**
     * 更新房间 ID 列表
     */
    public void updateRoomIds(List<String> roomIds) {
        this.roomIds = roomIds;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return roomIds != null ? roomIds.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return roomIds.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        boolean isNewView = false;
        
        if (convertView == null) {
            // 优化：优先从 View 池获取 View，减少 inflate 开销
            convertView = viewPoolManager.getViewFromPool(R.layout.item_room);
            if (convertView == null) {
                // 如果池中没有，创建新 View
                convertView = inflater.inflate(R.layout.item_room, parent, false);
            } else {
                // 从池中获取的 View，需要确保布局参数正确
                // 确保 View 没有旧的父容器引用
                if (convertView.getParent() != null) {
                    ((ViewGroup) convertView.getParent()).removeView(convertView);
                }
                // 重要：确保布局参数与 GridView 的要求一致
                // GridView 的 item 应该使用 AbsListView.LayoutParams
                // item_room.xml 中 CardView 的高度是 200dp，需要转换为像素
                ViewGroup.LayoutParams params = convertView.getLayoutParams();
                if (params == null || !(params instanceof android.widget.AbsListView.LayoutParams)) {
                    // 获取 item 的固定高度（200dp）
                    float density = context.getResources().getDisplayMetrics().density;
                    int heightPx = (int) (200 * density);
                    // 创建 GridView 需要的布局参数，宽度 MATCH_PARENT，高度固定 200dp
                    android.widget.AbsListView.LayoutParams newParams = new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        heightPx
                    );
                    convertView.setLayoutParams(newParams);
                } else {
                    // 如果已经有 AbsListView.LayoutParams，确保高度正确
                    android.widget.AbsListView.LayoutParams absParams = (android.widget.AbsListView.LayoutParams) params;
                    float density = context.getResources().getDisplayMetrics().density;
                    int heightPx = (int) (200 * density);
                    if (absParams.height != heightPx) {
                        absParams.height = heightPx;
                        convertView.setLayoutParams(absParams);
                    }
                }
            }
            holder = new ViewHolder();
            holder.thumbnail = convertView.findViewById(R.id.room_thumbnail);
            holder.name = convertView.findViewById(R.id.room_name);
            convertView.setTag(holder);
            isNewView = true;
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        // 确保 ImageView 和 TextView 的可见性正确
        if (holder.thumbnail != null) {
            holder.thumbnail.setVisibility(View.VISIBLE);
        }
        if (holder.name != null) {
            holder.name.setVisibility(View.VISIBLE);
        }

        if (roomIds == null || position >= roomIds.size()) {
            return convertView;
        }
        String roomId = roomIds.get(position);
        holder.roomId = roomId;
        
        // 优化：如果是新 View 或从池中获取的 View，需要清理之前的状态
        // 确保显示正确的数据，避免显示之前的数据
        // 注意：不要在这里 clear Glide，因为会清除 placeholder，导致图片不显示
        
        // 判断是否是首屏可见的item（前6个）
        boolean isFirstScreenItem = position < 6 && pageId != null;
        
        if (roomInfoCache != null && roomInfoCache.containsKey(roomId)) {
            Host host = roomInfoCache.get(roomId);
            if (host != null) {
                holder.name.setText(host.getRoomName());
                if (host.getAvatar() != null && !host.getAvatar().isEmpty()) {
                    Glide.with(context)
                            .load(host.getAvatar())
                            .placeholder(R.mipmap.ic_launcher)
                            .error(R.mipmap.ic_launcher)
                            .fallback(R.mipmap.ic_launcher)
                            .listener(isFirstScreenItem ? new GlideImageLoadListener(pageId) : null)
                            .into(holder.thumbnail);
                } else {
                    holder.thumbnail.setImageResource(R.mipmap.ic_launcher);
                    if (isFirstScreenItem) {
                        // 没有头像URL，直接记录图片加载完成
                        com.bytedance.myapplication.utils.PerformanceMonitor.recordImageLoadComplete(pageId);
                    }
                }
                return convertView;
            }
        }
        
        holder.name.setText("直播间 " + roomId);
        holder.thumbnail.setImageResource(R.mipmap.ic_launcher);
        
        if (roomInfoCache == null || !roomInfoCache.containsKey(roomId)) {
            ApiService.getHostInfo(roomId, new ApiService.ApiCallback<Host>() {
                @Override
                public void onSuccess(Host host) {
                    if (holder.name != null && roomId.equals(holder.roomId)) {
                        holder.name.setText(host.getRoomName());
                        if (host.getAvatar() != null && !host.getAvatar().isEmpty()) {
                            Glide.with(context)
                                    .load(host.getAvatar())
                                    .placeholder(R.mipmap.ic_launcher)
                                    .error(R.mipmap.ic_launcher)
                                    .fallback(R.mipmap.ic_launcher)
                                    .listener(isFirstScreenItem ? new GlideImageLoadListener(pageId) : null)
                                    .into(holder.thumbnail);
                        } else {
                            holder.thumbnail.setImageResource(R.mipmap.ic_launcher);
                            if (isFirstScreenItem) {
                                // 没有头像URL，直接记录图片加载完成
                                com.bytedance.myapplication.utils.PerformanceMonitor.recordImageLoadComplete(pageId);
                            }
                        }
                    }
                }

                @Override
                public void onFailure(String error) {
                    // 加载失败，保持默认显示
                    if (isFirstScreenItem) {
                        // 加载失败也视为完成（避免阻塞渲染完成判断）
                        com.bytedance.myapplication.utils.PerformanceMonitor.recordImageLoadComplete(pageId);
                    }
                }
            });
        } else {
            // 如果缓存中没有数据且不在加载中，对于首屏item需要记录完成
            if (isFirstScreenItem) {
                com.bytedance.myapplication.utils.PerformanceMonitor.recordImageLoadComplete(pageId);
            }
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView thumbnail;
        TextView name;
        String roomId;
    }
}


