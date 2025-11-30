package com.chat.server.service;

import com.chat.server.db.DBHelper;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserService {
    private static final ConcurrentHashMap<String, Boolean> onlineUsers = new ConcurrentHashMap<>();


    public boolean register(String username, String password) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("注册失败：" + e.getMessage());
            return false;
        }
    }

    public boolean login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username=? AND password=?";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                onlineUsers.put(username, true);
                System.out.println(username + " 已登录");
                return true;
            }else{
                return false;
            }
        } catch (SQLException e) {
            System.out.println("登录失败：" + e.getMessage());
            return false;
        }
    }

    public static void logout(String username) {
        onlineUsers.remove(username);
    }

    public static  boolean isOnline(String username) {
        return onlineUsers.containsKey(username);
    }
}
