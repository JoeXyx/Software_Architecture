package com.chat.client.util;

import java.io.*;
import java.net.Socket;
import java.net.URLEncoder;

public class FileUploader {

    /**
     * 上传文件到服务端（Socket），成功返回 **完整可访问的 HTTP URL**
     *
     * @param host 服务端 IP
     * @param port 文件上传专用端口（Socket）
     * @param file 要上传的文件
     * @return http://host:port/uploads/xxx
     * @throws IOException 上传失败（包括服务器返回错误）
     */
    public static String uploadFile(String host, int port, File file) throws IOException {
        Socket socket = null;
        DataOutputStream dos = null;
        BufferedReader reader = null;

        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(30_000); // 防止卡死

            dos = new DataOutputStream(socket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            // 1. 发送文件名（必须编码）
            String encodedName = URLEncoder.encode(file.getName(), "UTF-8");
            dos.writeUTF(encodedName);
            dos.writeLong(file.length());

            // 2. 发送文件内容
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, len);
                }
            }
            dos.flush(); // 关键！确保数据全部发出

            // 3. 等待服务端响应（不要关闭 socket！）
            String response = reader.readLine();
            if (response == null) {
                throw new IOException("服务器无响应");
            }
            if (!response.startsWith("SUCCESS ")) {
                throw new IOException("上传失败: " + response);
            }

            // 返回相对路径（如 /files/abc123_xxx）
            return response.substring(8);

        } catch (Exception e) {
            throw new IOException("上传失败", e);
        } finally {
            // 安全关闭
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (dos != null) try { dos.close(); } catch (Exception ignored) {}
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
        }
    }
}