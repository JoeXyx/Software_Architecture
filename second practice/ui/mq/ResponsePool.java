package org.example.ui.mq;

import com.alibaba.fastjson2.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ResponsePool {

    // requestId -> CompletableFuture
    private static final Map<String, CompletableFuture<String>> pool = new ConcurrentHashMap<>();

    private static final MQManager mq = MQManager.getInstance();

    static {
        // UI 监听自己的响应队列
        mq.listenResponse(msg -> {
            System.out.println("我收到的消息是：" + msg);
            JSONObject json = JSON.parseObject(msg);

            String requestId = json.getString("requestId");

            if (requestId != null && !requestId.isEmpty()) {
                ResponsePool.offerResp(requestId, msg);

                // 移除并完成 future
                CompletableFuture<String> future = pool.remove(requestId);
                if (future != null) {
                    future.complete(msg);
                }
            } else {
                System.err.println("收到没有 requestId 的响应, action=" + json.getString("action"));
            }
        });
    }



    /**
     * 等待响应
     * @param requestId 请求ID
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 响应字符串，超时返回 null
     * @throws Exception
     */
    public static String waitResp(String requestId, long timeout, TimeUnit unit) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        pool.put(requestId, future);
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            pool.remove(requestId); // 超时清理
            return null;
        }
    }

    // 收到 MQ 消息后调用：把结果塞进去，唤醒等待的线程
    public static void offerResp(String requestId, String response) {
        CompletableFuture<String> future = pool.remove(requestId);
        if (future != null) {
            future.complete(response);  // 唤醒 waitResp
        }
    }
}
