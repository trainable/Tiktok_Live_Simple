package com.bytedance.myapplication.utils;

import android.os.Handler;
import android.os.Looper;

import com.bytedance.myapplication.LiveBoard;
import com.bytedance.myapplication.model.Comment;
import com.bytedance.myapplication.model.Host;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {
    private static final String BASE_URL = "https://691ec8ffbb52a1db22bf1066.mockapi.io/api/v1";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService parseExecutor = LiveBoard.getApplicationExecutor();

    public interface ApiCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }

    public static void getHostInfo(String hostId, ApiCallback<Host> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/hosts/" + hostId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // 读取响应体（需要在IO线程）
                    String json = response.body().string();
                    
                    // JSON解析移到子线程，避免阻塞UI线程
                    parseExecutor.execute(() -> {
                        try {
                            Host host = gson.fromJson(json, Host.class);
                            mainHandler.post(() -> callback.onSuccess(host));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onFailure("解析失败: " + e.getMessage()));
                        }
                    });
                } else {
                    mainHandler.post(() -> callback.onFailure("请求失败: " + response.code()));
                }
            }
        });
    }

    public static void getComments(ApiCallback<List<Comment>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/comments_4")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // 读取响应体（需要在IO线程）
                    String json = response.body().string();
                    
                    // JSON解析移到子线程，避免阻塞UI线程
                    parseExecutor.execute(() -> {
                        try {
                            List<Comment> comments = gson.fromJson(json, new TypeToken<List<Comment>>(){}.getType());
                            mainHandler.post(() -> callback.onSuccess(comments));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onFailure("解析失败: " + e.getMessage()));
                        }
                    });
                } else {
                    mainHandler.post(() -> callback.onFailure("请求失败: " + response.code()));
                }
            }
        });
    }

    public static void sendComment(String comment, ApiCallback<Comment> callback) {
        if (comment == null || comment.trim().isEmpty()) {
            mainHandler.post(() -> callback.onFailure("评论内容不能为空"));
            return;
        }

        String trimmedComment = comment.trim();
        RequestBody formBody = new FormBody.Builder()
                .add("comment", trimmedComment)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/comments_4")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure("网络错误: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    parseExecutor.execute(() -> {
                        try {
                            Comment comment = gson.fromJson(responseBody, Comment.class);
                            mainHandler.post(() -> callback.onSuccess(comment));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onFailure("解析响应失败: " + e.getMessage()));
                        }
                    });
                } else {
                    final String errorMsg = responseBody != null && !responseBody.isEmpty() 
                        ? "请求失败: " + response.code() + ", 响应: " + responseBody
                        : "请求失败: " + response.code();
                    mainHandler.post(() -> callback.onFailure(errorMsg));
                }
            }
        });
    }

    public static void enterRoom(String roomId, ApiCallback<String> callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("room_id", roomId)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/rooms/enter")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure("网络错误: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    mainHandler.post(() -> callback.onSuccess(responseBody));
                } else {
                    mainHandler.post(() -> callback.onFailure("请求失败: " + response.code()));
                }
            }
        });
    }

    public static void leaveRoom(String roomId, ApiCallback<String> callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("room_id", roomId)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/rooms/leave")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onFailure("网络错误: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    mainHandler.post(() -> callback.onSuccess(responseBody));
                } else {
                    mainHandler.post(() -> callback.onFailure("请求失败: " + response.code()));
                }
            }
        });
    }
}
