import com.alibaba.fastjson2.*;

import java.sql.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DALService {

    private static final HikariDataSource ds;
    
    /** 建立 MySQL 连接（推荐写成方法，方便以后做自动重连） */
    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://42.193.223.68:3306/starbucks?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        config.setUsername("joe");
        config.setPassword("741432541");

        // 关键配置！！！
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);           // 10分钟空闲就回收
        config.setMaxLifetime(1800000);          // 30分钟强制换新连接
        config.setConnectionTestQuery("SELECT 1");  // 每次取连接前都测试一下

        ds = new HikariDataSource(config);
        System.out.println("HikariCP 连接池初始化成功！");
    }

    // 每次执行 SQL 都从池子里拿一个健康的连接
    private Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    /** 启动 DAL 服务，监听 MQ 请求 */
    public void start() throws Exception {

        MQManager.getInstance().listenQueue(
                "dal_request_queue",
                  // 绑定到所有 DAL 请求路由
                (tag, delivery) -> {

                    String message = new String(delivery.getBody(), "UTF-8");
                    System.out.println("收到 DAL 请求：" );

                    JSONObject req = JSON.parseObject(message);
                    String action = req.getString("action");
                    JSONObject params = req.getJSONObject("params");
                    String requestId = req.getString("requestId");

                    JSONObject resp = new JSONObject();

                    try {
                        switch (action) {
                            case "get_store_by_city":
                                resp.put("data", getStoreByCity(params.getString("city")));
                                break;
                            case "business_hours_analysis", "expansion_trend", "geographic_dispersion",
                                 "hours_vs_age_correlation":
                                resp.put("data",getAllStores());
                                break;
                            default:
                                throw new RuntimeException("未知 action: " + action);
                        }

                        resp.put("status", "success");

                    } catch (Exception e) {
                        resp.put("status", "fail");
                        resp.put("msg", e.getMessage());
                    }

                    resp.put("requestId", requestId);
                    resp.put("action", action);

                    // 回复消息
                    MQManager.getInstance().sendToQueue("dal_response_queue", resp.toString());
                }
        );

        System.out.println("DAL Service started. 监听队列：dal_request_queue");
    }

    /** 根据城市查询星巴克门店 */
    private JSONArray getStoreByCity(String city) throws Exception {
        String sql = "SELECT store_name, address, province, city, latitude, longitude, open_time, close_time,created_at " +
                "FROM starbucks_store " +
                "WHERE city = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // 设置参数、执行查询
            ps.setString(1, city);
            ResultSet rs = ps.executeQuery();

            JSONArray arr = new JSONArray();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("store_name", rs.getString("store_name"));
                obj.put("address", rs.getString("address"));
                obj.put("longitude", rs.getString("longitude"));
                obj.put("latitude", rs.getString("latitude"));
                obj.put("open_time", rs.getString("open_time"));
                obj.put("close_time", rs.getString("close_time"));
                obj.put("created_at", rs.getString("created_at"));
                obj.put("province", rs.getString("province"));
                obj.put("city", rs.getString("city"));
                arr.add(obj);
            }
            rs.close();
            ps.close();
            return arr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    private JSONArray getAllStores() throws Exception {
        String sql = "SELECT province,latitude, longitude, " +
                "open_time, close_time " +
                "FROM starbucks_store " +
                "WHERE latitude IS NOT NULL AND longitude IS NOT NULL ";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // 设置参数、执行查询

            ResultSet rs = ps.executeQuery();

            JSONArray arr = new JSONArray();
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("province", rs.getString("province"));
                o.put("latitude", rs.getString("latitude"));
                o.put("longitude", rs.getString("longitude"));
                o.put("open_time", rs.getString("open_time"));
                o.put("close_time", rs.getString("close_time"));
                arr.add(o);
            }
            rs.close();
            ps.close();
            return arr;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
