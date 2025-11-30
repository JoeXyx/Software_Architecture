package com.chat.server.mq;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

public class MQManager {
    private Connection connection;
    private Channel channel;
    private String exchangeName;

    public MQManager() throws Exception {
        InputStream input = MQManager.class.getClassLoader().getResourceAsStream("application.properties");
        if (input == null) {
            throw new RuntimeException("找不到 application.properties 文件");
        }
        Properties prop = new Properties();
        prop.load(input);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(prop.getProperty("mq.host"));
        factory.setPort(Integer.parseInt(prop.getProperty("mq.port")));
        factory.setUsername(prop.getProperty("mq.username"));
        factory.setPassword(prop.getProperty("mq.password"));
        connection = factory.newConnection();
        channel = connection.createChannel();

        exchangeName = prop.getProperty("mq.exchange");
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true);
        System.out.println("已连接到 RabbitMQ：" + exchangeName);
    }

    public void sendMessage(String routingKey, String message) throws IOException {
        channel.basicPublish(exchangeName, routingKey, null, message.getBytes(StandardCharsets.UTF_8));
        System.out.println("已发送消息到 [" + routingKey + "]：" + message);
    }

    public void close() throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }

    // 监听队列
    public void consumeQueue(String queueName, java.util.function.Consumer<String> callback) throws IOException {
        channel.queueDeclare(queueName, true, false, false, null);
        channel.queueBind(queueName, exchangeName, queueName);
        channel.basicConsume(queueName, true, (tag, msg) -> {
            try {
                String message = new String(msg.getBody(), StandardCharsets.UTF_8);
                callback.accept(message);
            } catch (Exception e) {
                System.err.println("消息处理异常：" + e.getMessage());
                e.printStackTrace();
            }
        }, tag -> {});
    }
}
