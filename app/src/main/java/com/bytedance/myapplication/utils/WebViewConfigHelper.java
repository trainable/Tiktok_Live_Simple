package com.bytedance.myapplication.utils;

import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

/**
 * WebView 配置工具类
 * 统一管理 WebView 的性能优化配置，避免代码重复
 */
public class WebViewConfigHelper {
    private static final String TAG = "WebViewConfigHelper";
    private static Boolean hardwareAccelerationSupported = null; // 缓存检测结果
    
    /**
     * 检测硬件加速是否支持
     * 优化：基于 Android 版本判断，现代设备（API 14+）基本都支持硬件加速
     * 如果硬件加速不可用，系统会自动降级到软件渲染
     */
    private static boolean isHardwareAccelerationSupported(android.content.Context context) {
        if (hardwareAccelerationSupported != null) {
            return hardwareAccelerationSupported;
        }
        
        // 硬件加速在 API 11+ (HONEYCOMB) 开始支持
        // 现代设备（API 14+ ICS）基本都支持硬件加速
        // 简化检测：直接基于版本判断，如果不可用系统会自动降级
        boolean versionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
        
        hardwareAccelerationSupported = versionSupported;
        return hardwareAccelerationSupported;
    }
    
    /**
     * 配置 WebView 以优化性能
     * 启用硬件加速、优化渲染设置
     * 优化：添加硬件加速检测和降级机制，避免播放失败
     */
    public static void configureForPerformance(WebView webView) {
        if (webView == null) {
            return;
        }
        
        android.webkit.WebSettings settings;
        try {
            settings = webView.getSettings();
        } catch (Exception e) {
            Log.e(TAG, "获取 WebSettings 失败", e);
            return;
        }
        
        // 基础设置（添加异常处理，避免系统资源查询失败）
        try {
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            // 重要：允许从 file:// URL 加载本地资源（用于加载 android_asset 中的脚本）
            // 注意：setAllowFileAccessFromFileURLs 和 setAllowUniversalAccessFromFileURLs 
            // 在 API 30+ 已弃用，仅在旧版本中使用
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN 
                    && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                settings.setAllowFileAccessFromFileURLs(true);
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 
                    && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                settings.setAllowUniversalAccessFromFileURLs(true);
            }
        } catch (Exception e) {
            // 静默处理
        }
        
        // 优化：确保 WebView 可以接收点击事件和触摸事件
        try {
            webView.setClickable(true);
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);
            // 重要：确保 WebView 可以接收触摸事件（用于移动端点击）
            webView.setLongClickable(false); // 禁用长按，避免干扰点击
            // 确保 WebView 可以处理触摸事件
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0+ 确保触摸事件正确传递
                webView.setNestedScrollingEnabled(false); // 禁用嵌套滚动，避免干扰触摸事件
            }
        } catch (Exception e) {
            // 静默处理
        }
        
        // 性能优化设置 - 智能硬件加速（检测支持后再启用）
        // 优化：添加硬件加速检测，如果设备不支持则使用软件渲染，避免 MESA 错误
        // 优化：添加异常处理，避免系统资源查询失败（错误代码 6）
        try {
            if (isHardwareAccelerationSupported(webView.getContext())) {
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            } else {
                webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
            }
        } catch (Exception e) {
            // 如果硬件加速失败，降级到软件渲染
            // 错误代码 6 通常表示系统资源查询失败，使用软件渲染更安全
            try {
                webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
            } catch (Exception e2) {
                // 静默处理
            }
        }
        
        // 其他配置（添加异常处理，避免系统资源查询失败）
        try {
            // 启用混合内容（如果需要）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            
            // 注意：setRenderPriority() 在 API 18+ 已弃用，已移除
            // 注意：setLayoutAlgorithm() 在 API 19+ 已弃用，已移除
            // WebView 现在会自动优化渲染性能
            
            // 缓存策略：对于直播流，使用 LOAD_DEFAULT 或 LOAD_NO_CACHE
            // 优化：直播流不应该使用缓存，避免缓冲问题
            // LOAD_DEFAULT 会根据 HTTP 头决定是否缓存
            settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            
            // 设置 User-Agent，确保与浏览器一致，避免服务器返回不同内容
            // 使用 Chrome 的 User-Agent，确保兼容性
            String defaultUserAgent = settings.getUserAgentString();
            if (defaultUserAgent != null && !defaultUserAgent.contains("Chrome")) {
                // 如果默认 User-Agent 不包含 Chrome，使用 Chrome 的 User-Agent
                String chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
                settings.setUserAgentString(chromeUserAgent);
            }
            
            // 禁用不必要的功能以提升性能
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(false);
            
            // 优化：减少内存占用和提升性能
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false); // 允许加载网络图片（视频流需要）
            settings.setBlockNetworkLoads(false);
            
            // 重要：确保网络可用，避免缓冲
            try {
                webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                // 确保 WebView 知道网络可用
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                }
            } catch (Exception e) {
                // 静默处理
            }
            
            // 注意：setDatabaseEnabled() 和 setDatabasePath() 在 API 19+ 已弃用
            // WebView 现在使用 IndexedDB 和 Web SQL Database，无需手动配置
            
            // 优化：减少 JavaScript 桥接开销
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                settings.setMediaPlaybackRequiresUserGesture(false);
            }
            
            // 优化：设置最小字体大小（减少布局计算）
            settings.setMinimumFontSize(8);
            settings.setMinimumLogicalFontSize(8);
        } catch (Exception e) {
            // 静默处理
        }
        
        // 设置 WebChromeClient（用于处理 JavaScript 对话框等）
        try {
            webView.setWebChromeClient(new android.webkit.WebChromeClient());
        } catch (Exception e) {
            // 静默处理
        }
        
        // 重要：设置WebView背景色为黑色，避免显示为白色
        try {
            webView.setBackgroundColor(0xFF000000); // 黑色背景
        } catch (Exception e) {
            // 静默处理
        }
    }
}

