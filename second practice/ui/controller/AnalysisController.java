package org.example.ui.controller;

import com.alibaba.fastjson2.JSON;
import org.example.ui.mq.MQManager;
import org.example.ui.mq.ResponsePool;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final MQManager mq = MQManager.getInstance();

    @GetMapping("/{type}")
    public Object getAnalysis(@PathVariable String type) throws Exception {
        String action;
        switch (type) {
            case "business": action = "business_hours_analysis"; break;
            case "expansion": action = "expansion_trend"; break;
            case "geo": action = "geographic_dispersion"; break;
            case "correlation": action = "hours_vs_age_correlation"; break;
            default: throw new IllegalArgumentException("未知分析类型: " + type);
        }

        String requestId = UUID.randomUUID().toString();
        JSONObject msg = new JSONObject();
        msg.put("action", action);
        msg.put("requestId", requestId);

        mq.sendToBLL(msg.toString());

        // 阻塞等待 BLL 返回结果
        String resp = ResponsePool.waitResp(requestId, 15, TimeUnit.SECONDS);
        if (resp == null) {
            JSONObject timeout = new JSONObject();
            timeout.put("status", "fail").put("msg", "请求超时").put("requestId", requestId);
            return timeout;
        }

        // BLL 返回的 JSON 里通常有 data 字段
        return JSON.parseObject(resp);
    }
}
