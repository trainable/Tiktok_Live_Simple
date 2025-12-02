package com.bytedance.myapplication.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {
    private static final String WS_URL = "wss://echo.websocket.org/";
    private static final int RECONNECT_DELAY_MS = 3000; // 重连延迟3秒
    private static final int MAX_RECONNECT_ATTEMPTS = 5; // 最大重连次数
    
    private WebSocket webSocket;
    private OkHttpClient client;
    private WebSocketCallback callback;
    private Handler mainHandler;
    private Handler reconnectHandler;
    private boolean isConnecting = false;
    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;

    public interface WebSocketCallback {
        void onMessage(String message);
        void onOpen();
        void onFailure(Throwable t);
    }

    public WebSocketManager() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        mainHandler = new Handler(Looper.getMainLooper());
        reconnectHandler = new Handler(Looper.getMainLooper());
    }

    public void connect(WebSocketCallback callback) {
        this.callback = callback;
        this.shouldReconnect = true;
        this.reconnectAttempts = 0;
        connectInternal();
    }
    
    private void connectInternal() {
        if (isConnecting) {
            return;
        }
        
        isConnecting = true;
        
        Request request = new Request.Builder()
                .url(WS_URL)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnecting = false;
                reconnectAttempts = 0;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onOpen();
                    }
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onMessage(text);
                    }
                });
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isConnecting = false;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(t);
                    }
                });
                
                if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isConnecting = false;
            }
        });
    }
    
    private void scheduleReconnect() {
        reconnectAttempts++;
        reconnectHandler.postDelayed(() -> {
            if (shouldReconnect && !isConnecting) {
                connectInternal();
            }
        }, RECONNECT_DELAY_MS);
    }

    public void sendMessage(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    public void disconnect() {
        shouldReconnect = false;
        reconnectHandler.removeCallbacksAndMessages(null);
        
        if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
            webSocket = null;
        }
        isConnecting = false;
    }
    
    public boolean isConnected() {
        return webSocket != null && !isConnecting;
    }
}


