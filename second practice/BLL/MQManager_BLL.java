import com.rabbitmq.client.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class MQManager_BLL {

    private static volatile MQManager_BLL instance;

    private Connection connection;
    private Channel channel;
    private ConnectionFactory factory;  // 必须独立保存，否则无法 reconnect

    // 你的三层固定通信队列
    public static final String BLL_REQUEST_QUEUE = "bll_request_queue";
    public static final String UI_RESPONSE_QUEUE = "ui_response_queue";
    public static final String DAL_REQUEST_QUEUE = "dal_request_queue";
    public static final String DAL_RESPONSE_QUEUE = "dal_response_queue";
    private static final String FULL_ANALYSIS_RESPONSE_QUEUE = "full_analysis_response_queue";

    private MQManager_BLL() throws Exception {

        // 加载配置文件（RabbitMQ host、端口、用户等）
        InputStream in = MQManager_BLL.class.getClassLoader().getResourceAsStream("db.properties");
        if (in == null) throw new RuntimeException("找不到 db.properties");

        Properties p = new Properties();
        p.load(in);

        factory = new ConnectionFactory();
        factory.setHost(p.getProperty("host", "localhost"));
        factory.setPort(Integer.parseInt(p.getProperty("port", "5672")));
        factory.setUsername(p.getProperty("username", "guest"));
        factory.setPassword(p.getProperty("password", "guest"));

        // 自动恢复（强烈推荐开启）
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setRequestedHeartbeat(60);
        factory.setConnectionTimeout(30000);
        factory.setNetworkRecoveryInterval(5000);

        // ★ 建立连接
        reconnect();

        // ★ 声明所有队列
        declareQueue(BLL_REQUEST_QUEUE);
        declareQueue(UI_RESPONSE_QUEUE);
        declareQueue(DAL_REQUEST_QUEUE);
        declareQueue(DAL_RESPONSE_QUEUE);
        declareQueue(FULL_ANALYSIS_RESPONSE_QUEUE);


        System.out.println("RabbitMQ 初始化成功 —— 所有队列已就绪");
    }

    // 单例获取
    public static MQManager_BLL getInstance() {
        if (instance == null) {
            synchronized (MQManager_BLL.class) {
                if (instance == null) {
                    try {
                        instance = new MQManager_BLL();
                    } catch (Exception e) {
                        throw new RuntimeException("初始化 MQManager 失败", e);
                    }
                }
            }
        }
        return instance;
    }

    // 建立/重建连接
    private synchronized void reconnect() throws IOException, TimeoutException {

        closeConnection();

        connection = factory.newConnection();
        channel = connection.createChannel();

        connection.addShutdownListener(cause -> {
            System.err.println("RabbitMQ 连接中断: " + cause.getMessage());
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        reconnect();
                        System.out.println("RabbitMQ 重连成功！");
                        break;
                    } catch (Exception ex) {
                        System.err.println("重连失败，继续重试...");
                    }
                }
            }).start();
        });
    }

    // 声明队列
    private void declareQueue(String queue) throws IOException {
        channel.queueDeclare(queue, true, false, false, null);
    }

    private void ensureChannel() {
        try {
            if (channel == null || !channel.isOpen()) {
                reconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ channel 不可用", e);
        }
    }

    // 发送消息到队列（direct 模式）
    public void send(String queue, String message) {
        ensureChannel();
        try {
            channel.basicPublish("", queue, MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }

    // 监听队列（支持手动 ACK）
    public void listen(String queue, MessageHandler handler) {
        ensureChannel();
        try {
            channel.basicQos(1); // 公平分发

            DeliverCallback callback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                try {
                    handler.handle(msg, delivery);
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    System.err.println("处理失败，将重入队列，消息=" + msg);
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            };

            channel.basicConsume(queue, false, callback, consumerTag -> {});
            System.out.println("开始监听队列: " + queue);

        } catch (Exception e) {
            System.err.println("监听队列失败: " + queue);
            e.printStackTrace();
        }
    }

    // 回调接口（你 DAL / BLL 都要用）
    @FunctionalInterface
    public interface MessageHandler {
        void handle(String message, Delivery delivery) throws Exception;
    }

    public Channel getChannel() {
        ensureChannel();
        return channel;
    }

    public void close() {
        closeConnection();
    }

    private void closeConnection() {
        try {
            if (channel != null) channel.close();
            if (connection != null) connection.close();
        } catch (Exception ignored) {}
    }
}
