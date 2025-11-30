package org.example.ui.mq;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;// 万能导入，一劳永逸

public class MQManager {

    private static volatile MQManager instance;

    private Connection connection;
    private Channel channel;

    /**
     * UI → BLL
     */
    public static final String BLL_REQUEST_QUEUE = "bll_request_queue";

    /**
     * BLL → UI（UI 专属的响应队列）
     */
    public static final String UI_RESPONSE_QUEUE = "ui_response_queue";

    private final ConnectionFactory factory;

    // ------------------ 单例 ------------------
    public static MQManager getInstance() {
        if (instance == null) {
            synchronized (MQManager.class) {
                if (instance == null) {
                    instance = new MQManager();
                }
            }
        }
        return instance;
    }

    private MQManager() {
        factory = new ConnectionFactory();
        loadConfig();
        configureFactory();
        reconnect();
        declareQueue(UI_RESPONSE_QUEUE);
        declareQueue(BLL_REQUEST_QUEUE);
        System.out.println("【UI MQManager】初始化完成，监听队列: ui_response_queue");
    }

    // ------------------ 配置加载 ------------------
    private void loadConfig() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("找不到 db.properties");
            }

            Properties p = new Properties();
            p.load(in);

            factory.setHost(p.getProperty("host", "42.193.223.68"));
            factory.setPort(Integer.parseInt(p.getProperty("port", "5672")));
            factory.setUsername(p.getProperty("username", "guest"));
            factory.setPassword(p.getProperty("password", "guest"));

        } catch (Exception e) {
            throw new RuntimeException("加载 RabbitMQ 配置失败", e);
        }
    }

    private void configureFactory() {
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        factory.setRequestedHeartbeat(60);
    }

    // ------------------ 自动重连机制 ------------------
    private synchronized void reconnect() {
        closeQuietly();
        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            System.out.println("【UI MQ】连接成功");

            connection.addShutdownListener(cause -> {
                System.out.println("【UI MQ】连接断开，尝试重连..." + cause.getMessage());
                new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(3000);
                            reconnect();
                            System.out.println("【UI MQ】重连成功！");
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
            });

        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 连接失败", e);
        }
    }

    private void ensureChannel() {
        if (channel == null || !channel.isOpen()) {
            reconnect();
        }
    }

    // ------------------ 队列声明 ------------------
    private void declareQueue(String queueName) {
        try {
            channel.queueDeclare(queueName, true, false, false, null);
        } catch (Exception e) {
            System.err.println("声明队列失败: " + queueName);
        }
    }

    // ------------------ UI → BLL ------------------
    public void sendToBLL(String message) {
        ensureChannel();
        try {
            channel.basicPublish(
                    "",
                    BLL_REQUEST_QUEUE,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes(StandardCharsets.UTF_8)
            );
            System.out.println("【UI → BLL】发送业务请求");
        } catch (Exception e) {
            System.err.println("【UI MQ】发送失败: " + e.getMessage());
        }
    }

    // ------------------ UI 监听自身的响应队列 ------------------
    public void listenResponse(ResponseHandler handler) {
        ensureChannel();

        try {
            channel.basicQos(1);

            DeliverCallback callback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);

                try {
                    JSONObject resp = JSON.parseObject(msg);

                    // 关键修复1：安全取 requestId（不存在返回 null）
                    String requestId = resp.getString("requestId");  // fastjson2 中 key 不存在返回 null，不会抛异常！

                    // 关键修复2：只有同步查询才需要唤醒阻塞线程
                    if (requestId != null && !requestId.isEmpty()) {
                        ResponsePool.offerResp(requestId, msg);  // 正确唤醒阻塞的 /api/store/city 接口
                    }

                    String action = resp.getString("action");

                    switch (action) {
                        case "get_store_by_city" -> handler.handleStoreSearchResult(resp);
                        case "business_hours_analysis" -> handler.handleBusinessHoursAnalysis(resp);
                        case "expansion_trend" -> handler.handleExpansionTrend(resp);
                        case "geographic_dispersion" -> handler.handleGeographicDispersion(resp);
                        case "hours_vs_age_correlation" -> handler.handleHoursVsAgeCorrelation(resp);
                        default -> handler.handleUnknown(resp);
                    }


                    // 成功处理 → 必须 ack
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                } catch (Exception ex) {
                    // 关键修复5：异常时不要重入队列！否则死循环！
                    System.err.println("【UI MQ】处理消息失败（不再重试）: " + msg);
                    ex.printStackTrace();
                    try {
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false); // 丢弃或进死信
                    } catch (IOException ignored) {
                    }
                }
            };

            channel.basicConsume(UI_RESPONSE_QUEUE, false, callback, tag -> {
                System.out.println("【UI】消费者被取消");
            });

            System.out.println("【UI】开始监听 → ui_response_queue（支持多类型分发）");

        } catch (Exception e) {
            System.err.println("监听 UI 响应失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------ 工具方法 ------------------
    @FunctionalInterface
    public interface ResponseHandler {

        // 你原来的查门店方法（保持不变）
        void handle(String message);

        // 新增：专门处理不同类型的分析结果（推荐这样拆开，代码最清晰）
        default void handleStoreSearchResult(Object data) {
            // 默认实现：降级到老方法，兼容旧代码
            JSONObject resp = (JSONObject) data;
            // 直接调用 handle，把 data JSON 字符串传出去
            handle(resp.toString());
        }

        default void handleBusinessHoursAnalysis(Object data) {
            JSONObject resp = (JSONObject) data;
            handle(resp.toString());
        }

        default void handleExpansionTrend(Object data) {
            JSONObject resp = (JSONObject) data;
            handle(resp.toString());
        }

        default void handleGeographicDispersion(Object data) {
            JSONObject resp = (JSONObject) data;
            handle(resp.toString());
        }

        default void handleHoursVsAgeCorrelation(Object data) {
            JSONObject resp = (JSONObject) data;
            handle(resp.toString());
        }

        default void handleUnknown(JSONObject resp) {
            System.out.println("收到未知类型消息: " + resp.getString("type"));
        }
    }

    private void closeQuietly() {
        try {
            if (channel != null) channel.close();
        } catch (Exception ignored) {
        }
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        }
    }

    public void close() {
        closeQuietly();
    }
}
