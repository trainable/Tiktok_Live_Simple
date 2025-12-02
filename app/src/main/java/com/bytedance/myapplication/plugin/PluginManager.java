package com.bytedance.myapplication.plugin;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理器
 * 负责插件的注册、初始化、激活和销毁
 */
public class PluginManager {
    private static final String TAG = "PluginManager";
    private static PluginManager instance;
    
    private Map<String, IPlugin> plugins = new HashMap<>();
    private Application application;
    private boolean initialized = false;
    
    private PluginManager() {
    }
    
    public static synchronized PluginManager getInstance() {
        if (instance == null) {
            instance = new PluginManager();
        }
        return instance;
    }
    
    /**
     * 初始化插件管理器
     * @param application Application 上下文
     */
    public void init(Application application) {
        if (initialized) {
            return;
        }
        
        this.application = application;
        initialized = true;
    }
    
    /**
     * 注册插件
     * @param plugin 插件实例
     */
    public void registerPlugin(IPlugin plugin) {
        if (plugin == null) {
            return;
        }
        
        String pluginName = plugin.getPluginName();
        if (plugins.containsKey(pluginName)) {
            return;
        }
        
        plugins.put(pluginName, plugin);
        
        // 如果已经初始化，立即初始化插件
        if (initialized && application != null) {
            plugin.onInit(application);
        }
    }
    
    /**
     * 注销插件
     * @param pluginName 插件名称
     */
    public void unregisterPlugin(String pluginName) {
        IPlugin plugin = plugins.remove(pluginName);
        if (plugin != null) {
            plugin.onDestroy();
        }
    }
    
    /**
     * 获取插件
     * @param pluginName 插件名称
     * @return 插件实例，如果不存在返回 null
     */
    public IPlugin getPlugin(String pluginName) {
        return plugins.get(pluginName);
    }
    
    /**
     * 获取所有插件
     * @return 插件列表
     */
    public List<IPlugin> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }
    
    /**
     * 获取所有启用的插件
     * @return 启用的插件列表
     */
    public List<IPlugin> getEnabledPlugins() {
        List<IPlugin> enabledPlugins = new ArrayList<>();
        for (IPlugin plugin : plugins.values()) {
            if (plugin.isEnabled()) {
                enabledPlugins.add(plugin);
            }
        }
        return enabledPlugins;
    }
    
    /**
     * 激活所有启用的插件（在 Activity 创建时调用）
     * @param lifecycleOwner Activity 生命周期拥有者
     */
    public void activateAll(LifecycleOwner lifecycleOwner) {
        for (IPlugin plugin : getEnabledPlugins()) {
            try {
                plugin.onActivate(lifecycleOwner);
            } catch (Exception e) {
                Log.e(TAG, "激活插件失败: " + plugin.getPluginName(), e);
            }
        }
    }
    
    /**
     * 停用所有插件（在 Activity 销毁时调用）
     * @param lifecycleOwner Activity 生命周期拥有者
     */
    public void deactivateAll(LifecycleOwner lifecycleOwner) {
        for (IPlugin plugin : getEnabledPlugins()) {
            try {
                plugin.onDeactivate(lifecycleOwner);
            } catch (Exception e) {
                Log.e(TAG, "停用插件失败: " + plugin.getPluginName(), e);
            }
        }
    }
    
    /**
     * 销毁所有插件
     */
    public void destroyAll() {
        for (IPlugin plugin : plugins.values()) {
            try {
                plugin.onDestroy();
            } catch (Exception e) {
                Log.e(TAG, "销毁插件失败: " + plugin.getPluginName(), e);
            }
        }
        plugins.clear();
        initialized = false;
    }
    
    /**
     * 检查插件是否已注册
     * @param pluginName 插件名称
     * @return 是否已注册
     */
    public boolean isPluginRegistered(String pluginName) {
        return plugins.containsKey(pluginName);
    }
}

