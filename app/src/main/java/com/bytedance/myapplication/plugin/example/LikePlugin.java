package com.bytedance.myapplication.plugin.example;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bytedance.myapplication.plugin.BasePlugin;

/**
 * 点赞/打赏插件
 * 功能：用户可以为主播点赞，显示点赞动画和累计点赞数
 */
public class LikePlugin extends BasePlugin {
    private static final String TAG = "LikePlugin";
    
    // 累计点赞数
    private MutableLiveData<Integer> totalLikes = new MutableLiveData<>();
    
    // 最近一次点赞时间（用于防抖）
    private long lastLikeTime = 0;
    private static final long LIKE_COOLDOWN = 300; // 300ms 冷却时间
    
    public LikePlugin() {
        super("LikePlugin", "1.0.0");
    }
    
    @Override
    public void onInit(Application context) {
        super.onInit(context);
        totalLikes.setValue(0);
    }
    
    @Override
    public void onActivate(LifecycleOwner lifecycleOwner) {
        super.onActivate(lifecycleOwner);
    }
    
    @Override
    public void onDeactivate(LifecycleOwner lifecycleOwner) {
        super.onDeactivate(lifecycleOwner);
    }
    
    /**
     * 点赞（带防抖）
     * @return 是否成功点赞
     */
    public boolean like() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLikeTime < LIKE_COOLDOWN) {
            return false;
        }
        
        lastLikeTime = currentTime;
        Integer currentLikes = totalLikes.getValue();
        if (currentLikes == null) {
            currentLikes = 0;
        }
        
        totalLikes.postValue(currentLikes + 1);
        return true;
    }
    
    /**
     * 批量点赞（连击）
     * @param count 点赞次数
     */
    public void likeMultiple(int count) {
        for (int i = 0; i < count; i++) {
            like();
        }
    }
    
    /**
     * 重置点赞数
     */
    public void resetLikes() {
        totalLikes.postValue(0);
    }
    
    /**
     * 获取累计点赞数 LiveData（暴露给 ViewModel）
     */
    public LiveData<Integer> getTotalLikes() {
        return totalLikes;
    }
    
    /**
     * 获取当前点赞数
     */
    public int getCurrentLikes() {
        Integer likes = totalLikes.getValue();
        return likes != null ? likes : 0;
    }
}

