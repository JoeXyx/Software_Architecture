package org.example.ui.controller;

import org.example.ui.mq.MQManager;
import org.example.ui.mq.ResponsePool;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Set;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final MQManager mq = MQManager.getInstance();

    @GetMapping("/city")
    public String getByCity(
            @RequestParam String city) throws Exception {
        String requestId = UUID.randomUUID().toString();

        String normalizedCity = city.trim();

        // 这些直辖市和特殊城市不需要也不应该加“市”
        if ("北京".equals(normalizedCity) || "北京市".equals(normalizedCity)) {
            normalizedCity = "北京市";
        } else if ("上海".equals(normalizedCity) || "上海市".equals(normalizedCity)) {
            normalizedCity = "上海市";
        } else if ("天津".equals(normalizedCity) || "天津市".equals(normalizedCity)) {
            normalizedCity = "天津市";
        } else if ("重庆".equals(normalizedCity) || "重庆市".equals(normalizedCity)) {
            normalizedCity = "重庆市";
        }
        // 其他城市：如果没以“市”结尾，就自动加上
        else if (!normalizedCity.endsWith("市")) {
            normalizedCity = normalizedCity + "市";
        }

        JSONObject msg = new JSONObject();
        msg.put("action", "get_store_by_city");

        // 关键修改：把分页参数也传进去！
        JSONObject params = new JSONObject();
        params.put("city", normalizedCity);
        msg.put("params", params);

        msg.put("requestId", requestId);

        mq.sendToBLL(msg.toString());

        String resp = ResponsePool.waitResp(requestId, 10, TimeUnit.SECONDS);
        if (resp == null) {
            JSONObject timeout = new JSONObject();
            timeout.put("status", "fail")
                    .put("msg", "请求超时")
                    .put("requestId", requestId);
            return timeout.toString();
        }

        return resp;
    }
}
