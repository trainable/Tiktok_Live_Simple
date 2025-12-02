package com.bytedance.myapplication.plugin.example;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bytedance.myapplication.plugin.BasePlugin;

/**
 * 礼物插件示例
 * 演示如何在 MVVM 架构中添加插件功能
 */
public class GiftPlugin extends BasePlugin {
    private static final String TAG = "GiftPlugin";
    
    // 插件自己的 LiveData（可以暴露给 ViewModel）
    private MutableLiveData<Integer> giftCount = new MutableLiveData<>();
    
    public GiftPlugin() {
        super("GiftPlugin", "1.0.0");
    }
    
    @Override
    public void onInit(Application context) {
        super.onInit(context);
        giftCount.setValue(0);
    }
    
    @Override
    public void onActivate(LifecycleOwner lifecycleOwner) {
        super.onActivate(lifecycleOwner);
        // 可以在这里初始化 UI、注册监听器等
    }
    
    @Override
    public void onDeactivate(LifecycleOwner lifecycleOwner) {
        super.onDeactivate(lifecycleOwner);
        // 可以在这里清理资源
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    
    /**
     * 发送礼物（插件功能）
     */
    public void sendGift(String giftId) {
        // 实现礼物发送逻辑
        Integer currentCount = giftCount.getValue();
        if (currentCount == null) {
            currentCount = 0;
        }
        giftCount.postValue(currentCount + 1);
    }
    
    /**
     * 获取礼物数量 LiveData（可以暴露给 ViewModel）
     */
    public LiveData<Integer> getGiftCount() {
        return giftCount;
    }
}

