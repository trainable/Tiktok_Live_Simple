package com.bytedance.myapplication.plugin.example;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bytedance.myapplication.plugin.BasePlugin;

/**
 * 聊天插件示例
 * 演示插件如何暴露 LiveData 给 ViewModel
 */
public class ChatPlugin extends BasePlugin {
    private static final String TAG = "ChatPlugin";
    
    // 插件暴露的 LiveData
    private MutableLiveData<String> chatMessage = new MutableLiveData<>();
    
    public ChatPlugin() {
        super("ChatPlugin", "1.0.0");
    }
    
    @Override
    public void onInit(Application context) {
        super.onInit(context);
    }
    
    @Override
    public void onActivate(LifecycleOwner lifecycleOwner) {
        super.onActivate(lifecycleOwner);
    }
    
    /**
     * 发送聊天消息
     */
    public void sendMessage(String message) {
        chatMessage.postValue(message);
    }
    
    /**
     * 获取聊天消息 LiveData（暴露给 ViewModel）
     */
    public LiveData<String> getChatMessage() {
        return chatMessage;
    }
}

