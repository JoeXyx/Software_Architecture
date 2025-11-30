package com.chat.server.util;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class FileServer implements Runnable {
    private final int PORT = 9000;
    private final String SAVE_DIR = "files/";  // 相对路径
    private final String HTTP_BASE = "http://127.0.0.1:8080"; // 服务器公网 IP 和端口

    @Override
    public void run() {
        new File(SAVE_DIR).mkdirs();
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("文件服务器已启动，监听端口：" + PORT);
            System.out.println("文件访问地址: " + HTTP_BASE + "/files/");

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(dos, "UTF-8"), true)) {

            // 1. 客户端直接发文件名（已编码），我们不再读命令
            String encodedName = dis.readUTF(); // 客户端发的是 URLEncoder.encode(name)
            String originalName = java.net.URLDecoder.decode(encodedName, "UTF-8");
            long fileSize = dis.readLong();

            // 2. 生成唯一 ID，保存文件
            String fileId = UUID.randomUUID().toString() + "_" + originalName;
            Path savePath = Paths.get(SAVE_DIR, fileId);

            try (FileOutputStream fos = new FileOutputStream(savePath.toFile())) {
                byte[] buffer = new byte[8192];
                int len;
                long received = 0;
                while (received < fileSize && (len = dis.read(buffer, 0, Math.min(buffer.length, (int)(fileSize - received)))) != -1) {
                    fos.write(buffer, 0, len);
                    received += len;
                }
            }

            // 3. 返回一行：SUCCESS /files/xxx_原名
            String relativeUrl = "/files/" + fileId;
            writer.println("SUCCESS " + relativeUrl); // 客户端用 readLine() 读取
            System.out.println("上传成功: " + originalName + " → " + HTTP_BASE + relativeUrl);

        } catch (Exception e) {
            try (PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println("ERROR " + e.getMessage());
            } catch (Exception ignored) {}
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}