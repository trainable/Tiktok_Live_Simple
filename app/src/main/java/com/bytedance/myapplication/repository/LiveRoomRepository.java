package com.bytedance.myapplication.repository;

import com.bytedance.myapplication.model.Comment;
import com.bytedance.myapplication.model.Host;
import com.bytedance.myapplication.utils.ApiService;

import java.util.List;

public class LiveRoomRepository {
    private static LiveRoomRepository instance;

    private LiveRoomRepository() {
    }

    public static LiveRoomRepository getInstance() {
        if (instance == null) {
            instance = new LiveRoomRepository();
        }
        return instance;
    }

    public void getHostInfo(String hostId, ApiService.ApiCallback<Host> callback) {
        ApiService.getHostInfo(hostId, callback);
    }

    public void getComments(ApiService.ApiCallback<List<Comment>> callback) {
        ApiService.getComments(callback);
    }

    public void sendComment(String comment, ApiService.ApiCallback<Comment> callback) {
        ApiService.sendComment(comment, callback);
    }

    public void enterRoom(String roomId, ApiService.ApiCallback<String> callback) {
        ApiService.enterRoom(roomId, callback);
    }

    public void leaveRoom(String roomId, ApiService.ApiCallback<String> callback) {
        ApiService.leaveRoom(roomId, callback);
    }
}

