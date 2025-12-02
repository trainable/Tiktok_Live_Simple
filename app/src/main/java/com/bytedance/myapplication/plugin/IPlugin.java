package com.bytedance.myapplication.plugin;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;

/**
 * 插件接口
 * 所有插件必须实现此接口
 */
public interface IPlugin {
    /**
     * 获取插件名称
     */
    String getPluginName();
    
    /**
     * 获取插件版本
     */
    String getPluginVersion();
    
    /**
     * 插件初始化
     * @param context Application 上下文
     */
    void onInit(Application context);
    
    /**
     * 插件激活（在 Activity 创建时调用）
     * @param lifecycleOwner Activity 生命周期拥有者
     */
    void onActivate(LifecycleOwner lifecycleOwner);
    
    /**
     * 插件停用（在 Activity 销毁时调用）
     * @param lifecycleOwner Activity 生命周期拥有者
     */
    void onDeactivate(LifecycleOwner lifecycleOwner);
    
    /**
     * 插件销毁
     */
    void onDestroy();
    
    /**
     * 是否启用此插件
     */
    boolean isEnabled();
    
    /**
     * 设置插件启用状态
     */
    void setEnabled(boolean enabled);
}


