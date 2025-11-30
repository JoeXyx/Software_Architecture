package com.chat.server;

import com.chat.server.mq.MQManager;
import com.chat.server.listener.UserListener;
import com.chat.server.util.FileServer;

public class ServerMain {
    public static void main(String[] args) {
        try {
            MQManager mqManager = new MQManager();
            UserListener listener = new UserListener(mqManager);
            listener.startListening();
            new Thread(new FileServer()).start();
            System.out.println("💡 聊天服务器已启动，等待客户端请求...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
