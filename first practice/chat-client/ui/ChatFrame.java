package com.chat.client.ui;

import com.chat.client.mq.MQConnection;
import com.chat.client.util.FileUploader;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Properties;

public class ChatFrame extends JFrame {
    private final String username;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendBtn, fileBtn,saveFileBtn;
    private JComboBox<String> modeBox; // 公共 or 私聊
    private JTextField targetField; // 私聊目标


    private MQConnection mq;

    public ChatFrame(String username) throws Exception {
        this.username = username;
        this.mq = MQConnection.getInstance();

        setTitle("聊天 - " + username);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane sp = new JScrollPane(chatArea);

        inputField = new JTextField();
        sendBtn = new JButton("发送");
        fileBtn = new JButton("发送文件");
        saveFileBtn=new JButton("接收文件");

        modeBox = new JComboBox<>(new String[]{"公共消息", "私聊"});
        targetField = new JTextField();
        targetField.setPreferredSize(new Dimension(120, 24));
        targetField.setToolTipText("私聊目标用户名");

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("模式:"));
        topPanel.add(modeBox);
        topPanel.add(new JLabel("目标:"));
        topPanel.add(targetField);
        topPanel.add(fileBtn);
        topPanel.add(saveFileBtn);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendBtn, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        mq.subscribe("client_" + username + "_private", "chat.user." + username, this::handleMessage);

        mq.subscribe("client_" + username + "_public", "chat.public", this::handleMessage);

        // 事件
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        fileBtn.addActionListener(e -> sendFile());
    }



    private void sendMessage() {
        try {
            String text = inputField.getText().trim();
            if (text.isEmpty()) return;

            String mode = (String) modeBox.getSelectedItem();
            if ("私聊".equals(mode)) {
                String to = targetField.getText().trim();
                if (to.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "请输入目标用户名");
                    return;
                }

                JSONObject j = new JSONObject();
                j.put("type", "private");
                j.put("from", username);
                j.put("to", to);
                j.put("content", text);
                // routing key = chat.user.<to>
                mq.sendMessage("chat.user." + to, j.toString());
                chatArea.append("[我→" + to + " 私聊] " + text + "\n");
            } else {
                JSONObject j = new JSONObject();
                j.put("type", "public");
                j.put("from", username);
                j.put("content", text);
                // routing key = chat.public
                mq.sendMessage("chat.public", j.toString());
                chatArea.append("[我 公聊] " + text + "\n");
            }
            inputField.setText("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void sendFile() {
        try {
            JFileChooser chooser = new JFileChooser();
            int r = chooser.showOpenDialog(this);
            if (r != JFileChooser.APPROVE_OPTION) return;

            File f = chooser.getSelectedFile();
            InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties");

            Properties prop = new Properties();
            prop.load(input);
            String host = prop.getProperty("mq.host");
            int filePort = Integer.parseInt(prop.getProperty("file.port", "9000"));

            // 上传文件（通过Socket）
            String filePath = FileUploader.uploadFile(host, filePort, f);


            // 构建文件消息并通过RabbitMQ发送
            String mode = (String) modeBox.getSelectedItem();
            JSONObject j = new JSONObject();
            j.put("type", "file");
            j.put("from", username);
            j.put("filename", f.getName());
            j.put("url", filePath);
            j.put("timestamp", System.currentTimeMillis());

            if ("私聊".equals(mode)) {
                String to = targetField.getText().trim();
                if (to.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "请输入目标用户名");
                    return;
                }
                j.put("to", to);
                mq.sendMessage("chat.user." + to, j.toString());
                chatArea.append("[我→" + to + " 发送文件] " + f.getName() + "\n");
            } else {
                mq.sendMessage("chat.public", j.toString());
                chatArea.append("[我 公共发送文件] " + f.getName() + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "文件发送失败：" + e.getMessage());
        }
    }

    /**
     * 消息处理方法：区分文本和文件
     */
    private void handleMessage(String msg) {
        try {
            JSONObject j = new JSONObject(msg);
            String type = j.optString("type", "text");
            String from = j.optString("from", j.optString("username", "unknown"));
            if ("file".equals(type)) {
                 String filename = j.getString("filename");
                String url = j.getString("url");
                String fileUrl = "http://42.193.223.68:9001" + url;

                // 修复乱码
                try {
                    final String new_filename = URLDecoder.decode(filename, "UTF-8");
                } catch (Exception ignored) {}

                // 追加文本 + 按钮
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("[" + from + " 发送文件] " + filename + "\n");

                    // 创建下载按钮
                    System.out.println(fileUrl);
                    saveFileBtn.addActionListener(e -> downloadFile(fileUrl, filename));

                    // 插入按钮到 JTextArea 所在位置
                    chatArea.append("\n\n");
                });

            } else {
                String content = j.optString("content", "");
                SwingUtilities.invokeLater(() -> chatArea.append("[消息] " + from + "： " + content + "\n"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void downloadFile(String url, String filename) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(filename));
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;

        File saveFile = chooser.getSelectedFile();

        new Thread(() -> {
            try {
                URL u = new URL(url);
                try (InputStream in = u.openStream();
                     FileOutputStream out = new FileOutputStream(saveFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                JOptionPane.showMessageDialog(null, "下载完成！");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "下载失败: " + ex.getMessage());
            }
        }).start();
    }
    
}