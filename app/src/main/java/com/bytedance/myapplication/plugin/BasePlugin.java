package com.bytedance.myapplication.plugin;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;

/**
 * 插件基类
 * 提供默认实现，简化插件开发
 */
public abstract class BasePlugin implements IPlugin {
    protected String pluginName;
    protected String pluginVersion;
    protected boolean enabled = true;
    protected Application application;
    
    public BasePlugin(String pluginName, String pluginVersion) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }
    
    @Override
    public String getPluginName() {
        return pluginName;
    }
    
    @Override
    public String getPluginVersion() {
        return pluginVersion;
    }
    
    @Override
    public void onInit(Application context) {
        this.application = context;
        // 子类可以重写此方法进行初始化
    }
    
    @Override
    public void onActivate(LifecycleOwner lifecycleOwner) {
        // 子类可以重写此方法处理激活逻辑
    }
    
    @Override
    public void onDeactivate(LifecycleOwner lifecycleOwner) {
        // 子类可以重写此方法处理停用逻辑
    }
    
    @Override
    public void onDestroy() {
        // 子类可以重写此方法进行清理
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}


