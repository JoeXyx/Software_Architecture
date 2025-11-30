package com.chat.server.listener;

import com.chat.server.mq.MQManager;
import com.chat.server.service.UserService;
import org.json.JSONObject;

public class UserListener {

    private final MQManager mqManager;
    private final UserService userService;


    public UserListener(MQManager mqManager) {
        this.mqManager = mqManager;
        this.userService = new UserService();
    }

    public void startListening() throws Exception {
        // 监听注册队列
        mqManager.consumeQueue("user.request.register", message -> {
            System.out.println(" 收到注册消息：" + message);
            JSONObject req = new JSONObject(message);
            String username = req.getString("username");
            String password = req.getString("password");

            JSONObject resp = new JSONObject();
            if (userService.register(username, password)) {
                resp.put("status", "success");
                resp.put("message", "注册成功");
            } else {
                resp.put("status", "fail");
                resp.put("message", "用户名已存在或数据库错误");
            }

            try {
                mqManager.sendMessage("user.response."+username, resp.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 监听登录队列
        mqManager.consumeQueue("user.request.login", message -> {
            System.out.println("收到登录消息：" + message);
            JSONObject req = new JSONObject(message);
            String username = req.getString("username");
            String password = req.getString("password");

            JSONObject resp = new JSONObject();
            if (userService.login(username, password)) {
                resp.put("status", "success");
                resp.put("message", "登录成功");
            } else {
                resp.put("status", "fail");
                resp.put("message", "用户名或密码错误");
            }

            try {
                mqManager.sendMessage("user.response."+username, resp.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("✅ 用户注册与登录监听已启动...");
    }
}
