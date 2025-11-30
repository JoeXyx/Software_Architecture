package com.chat.client.ui;

import com.chat.client.mq.MQConnection;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, registerButton;

    public LoginFrame() {
        setTitle("聊天客户端 - 登录/注册");
        setSize(400, 220);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        p.add(new JLabel("用户名:"));
        usernameField = new JTextField();
        p.add(usernameField);

        p.add(new JLabel("密码:"));
        passwordField = new JPasswordField();
        p.add(passwordField);

        loginButton = new JButton("登录");
        registerButton = new JButton("注册");
        p.add(loginButton);
        p.add(registerButton);
        add(p);

        loginButton.addActionListener(e -> sendLogin());
        registerButton.addActionListener(e -> sendRegister());
    }

    private void sendLogin() {
        try {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "用户名/密码不能为空");
                return;
            }

            JSONObject req = new JSONObject();
            req.put("type", "login");
            req.put("username", username);
            req.put("password", password);

            MQConnection mq = MQConnection.getInstance();

            // 先订阅 response 队列（只订阅一次），以便收到服务器响应
            String responseBind = "user.response." + username;
            mq.subscribe("client_" + username, responseBind, msg -> {
                try {
                    JSONObject resp = new JSONObject(msg);
                    String status = resp.optString("status", "fail");
                    String message = resp.optString("message", "");
                    System.out.println("收到登录响应: " + resp.toString());

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, message);
                        if ("success".equals(status)) {
                            // 登录成功：打开 ChatFrame
                            try {
                                new ChatFrame(username).setVisible(true);
                                this.dispose();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            // 发送登录请求到 server 处理的 routing key（server 监听 user.request.login）
            mq.sendMessage("user.request.login", req.toString());
            System.out.println("已发送登录请求: " + req.toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "发送登录失败：" + ex.getMessage());
        }
    }

    private void sendRegister() {
        try {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "用户名/密码不能为空");
                return;
            }

            JSONObject req = new JSONObject();
            req.put("type", "register");
            req.put("username", username);
            req.put("password", password);

            MQConnection mq = MQConnection.getInstance();

            // 订阅注册响应队列（同登录响应风格）
            String responseBind = "user.response." + username;
            mq.subscribe("client_" + username, responseBind, msg -> {
                try {
                    JSONObject resp = new JSONObject(msg);
                    String status = resp.optString("status", "fail");
                    String message = resp.optString("message", "");
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            mq.sendMessage("user.request.register", req.toString());
            System.out.println("已发送注册请求: " + req.toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "发送注册失败：" + ex.getMessage());
        }
    }
}
