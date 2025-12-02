package com.bytedance.myapplication.utils;

import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

public class GlideImageLoadListener implements RequestListener<Drawable> {
    private static final String TAG = "GlideImageLoadListener";
    private final String pageId;
    
    public GlideImageLoadListener(String pageId) {
        this.pageId = pageId;
    }
    
    @Override
    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, 
                                @Nullable Object model, 
                                @NonNull Target<Drawable> target, 
                                boolean isFirstResource) {
        PerformanceMonitor.recordImageLoadComplete(pageId);
        return false;
    }
    
    @Override
    public boolean onResourceReady(@NonNull Drawable resource, 
                                   @NonNull Object model, 
                                   @Nullable Target<Drawable> target, 
                                   @NonNull com.bumptech.glide.load.DataSource dataSource, 
                                   boolean isFirstResource) {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> PerformanceMonitor.recordImageLoadComplete(pageId));
        return false;
    }
}

