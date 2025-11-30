import com.rabbitmq.client.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class MQManager {

    private static volatile MQManager instance;

    private Connection connection;
    private Channel channel;
    private ConnectionFactory factory;  // ★ 必须是类成员
    private String exchangeName;

    private MQManager() throws Exception {

        InputStream input = MQManager.class.getClassLoader().getResourceAsStream("db.properties");
        if (input == null) {
            throw new RuntimeException("找不到 db.properties 文件！");
        }

        Properties prop = new Properties();
        prop.load(input);

        factory = new ConnectionFactory();
        factory.setHost(prop.getProperty("host", "localhost"));
        factory.setPort(Integer.parseInt(prop.getProperty("port", "5672")));
        factory.setUsername(prop.getProperty("username", "guest"));
        factory.setPassword(prop.getProperty("password", "guest"));


        // 防止断线
        factory.setRequestedHeartbeat(60);
        factory.setConnectionTimeout(30000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);

        exchangeName = prop.getProperty("rabbitmq.exchange", "starbucks_exchange");

        // 初始化连接
        reconnect();

        System.out.println("RabbitMQ 连接成功！ Exchange = " + exchangeName);
    }

    /** 单例 */
    public static MQManager getInstance() {
        if (instance == null) {
            synchronized (MQManager.class) {
                if (instance == null) {
                    try {
                        instance = new MQManager();
                    } catch (Exception e) {
                        throw new RuntimeException("初始化 MQManager 失败", e);
                    }
                }
            }
        }
        return instance;
    }

    /** ★ 负责重连 */
    private synchronized void reconnect() throws Exception {

        if (connection != null && connection.isOpen() &&
                channel != null && channel.isOpen()) {
            return;
        }

        close();  // 清理旧连接

        connection = factory.newConnection();
        channel = connection.createChannel();

        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true);

        // 注册重连事件
        connection.addShutdownListener(cause -> {
            System.err.println("RabbitMQ 连接断开：" + cause.getMessage());
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        reconnect();
                        System.out.println("RabbitMQ 重连成功！");
                        break;
                    } catch (Exception e) {
                        System.err.println("重连失败，5秒后继续...");
                    }
                }
            }).start();
        });
    }

    /** 发送消息（routingKey） */
    public void sendToQueue(String queueName, String message) throws Exception {
        ensureChannelOpen();

        // 直接发到默认交换机（""），routingKey = 队列名 → 直达队列
        channel.basicPublish(
                "",                                 // 默认交换机
                queueName,                          // routingKey 就是队列名
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes(StandardCharsets.UTF_8)
        );

        System.out.println("发送到队列 [" + queueName + "] : " + message);
    }

    /** 监听队列 + 绑定 routingKey */
    public void listenQueue(String queueName, DeliverCallback callback) throws Exception {
        ensureChannelOpen();

        // 直接声明队列，不绑定任何 exchange（走默认交换机 ""）
        channel.queueDeclare(queueName, true, false, false, null);

        channel.basicQos(1);

        com.rabbitmq.client.DeliverCallback deliver = (consumerTag, delivery) -> {
            try {
                callback.handle(consumerTag, delivery);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                // 根据业务决定是否重回队列
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        channel.basicConsume(queueName, false, deliver, tag -> {});
        System.out.println("监听队列（默认交换机）: " + queueName);
    }

    /** 确保连接可用 */
    private void ensureChannelOpen() throws Exception {
        if (channel == null || !channel.isOpen()) {
            reconnect();
        }
    }

    /** 关闭资源 */
    public void close() {
        try { if (channel != null) channel.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }

    /** 外部实现的回调接口 */
    public interface DeliverCallback {
        void handle(String consumerTag, Delivery delivery) throws Exception;
    }
}
