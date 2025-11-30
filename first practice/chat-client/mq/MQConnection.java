package com.chat.client.mq;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class MQConnection {
    private static MQConnection instance;
    private Connection connection;
    private Channel publishChannel;
    private String exchangeName;

    private MQConnection() throws Exception {
        InputStream input = MQConnection.class.getClassLoader().getResourceAsStream("application.properties");
        if (input == null) throw new RuntimeException("找不到 application.properties");

        Properties prop = new Properties();
        prop.load(input);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(prop.getProperty("mq.host"));
        factory.setPort(Integer.parseInt(prop.getProperty("mq.port")));
        factory.setUsername(prop.getProperty("mq.username"));
        factory.setPassword(prop.getProperty("mq.password"));

        connection = factory.newConnection();
        // publishChannel 用于发布（单channel，串行发送；如果高并发可改为 channel 池）
        publishChannel = connection.createChannel();

        exchangeName = prop.getProperty("mq.exchange");
        // topic exchange
        publishChannel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true);
        System.out.println("已连接 RabbitMQ，exchange=" + exchangeName);
    }

    public static MQConnection getInstance() throws Exception {
        if (instance == null) {
            synchronized (MQConnection.class) {
                if (instance == null) instance = new MQConnection();
            }
        }
        return instance;
    }

    // 发送消息（routingKey 由业务层决定）
    public synchronized void sendMessage(String routingKey, String message) throws IOException {
        publishChannel.basicPublish(exchangeName, routingKey, null, message.getBytes(StandardCharsets.UTF_8));
        System.out.println("已发送到 [" + routingKey + "]：" + message);
    }

    // 订阅：为每个订阅创建独立 channel（避免线程安全问题）
    // queueName: 客户端自己要用的队列名（如果传 null 则会创建临时队列）
    // bindingKey: topic routingKey，例如 chat.user.joe 或 chat.public 或 chat.group.*
        public void subscribe(String queueName, String bindingKey, Consumer<String> callback) throws IOException {
        Channel ch = connection.createChannel();
        ch.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true);

        String q;
        if (queueName == null || queueName.isEmpty()) {
            q = ch.queueDeclare().getQueue(); // 临时独占队列
        } else {
            ch.queueDeclare(queueName, true, false, false, null);
            q = queueName;
        }

        ch.queueBind(q, exchangeName, bindingKey);

        // 自动 ack=true 简化（若需消息可靠性改为 false 并手动 ack）
        ch.basicConsume(q, true, (consumerTag, delivery) -> {
            String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try {
                callback.accept(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, consumerTag -> {});
        System.out.println("已订阅 queue=" + q + " bind=" + bindingKey);
    }

    // 关闭（程序退出时调用）
    public void close() {
        try {
            if (publishChannel != null && publishChannel.isOpen()) publishChannel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
}
