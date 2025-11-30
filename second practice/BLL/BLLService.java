import com.rabbitmq.client.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.MultipleLinearRegression;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;


import com.alibaba.fastjson2.*;

import java.time.temporal.ChronoUnit;

import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BLLService {

    // 保存所有等待 DAL 返回的 UI 请求回调
    private ConcurrentHashMap<String, JSONObject> pendingMap = new ConcurrentHashMap<>();


    public void start() throws Exception {

        System.out.println("【BLL】业务逻辑层已启动，等待 UI 请求...");

        // 只监听一次 DAL → BLL
        listenDAL();

        // 监听 UI → BLL
        MQManager_BLL.getInstance().listen("bll_request_queue", (msg, delivery) -> {
            JSONObject dalResp = JSON.parseObject(msg);
            String requestId = dalResp.getString("requestId");
            String action = dalResp.getString("action");
            System.out.println("【BLL】收到 UI 请求：");

//          统一直接发送给DAL层，让其根据操作来解决问题
//            MQManager.getInstance().send("dal_request_queue", msg);
            forwardToDAL(dalResp);

        });
    }


    /**
     * -------------------------- 监听 DAL 返回（只需要启动一次） --------------------------
     */
    private void listenDAL() throws Exception {

        System.out.println("【BLL】开始监听 DAL 回包队列...");

        MQManager_BLL.getInstance().listen("dal_response_queue", (dalMsg, delivery) -> {

            JSONObject dalResp = JSON.parseObject(dalMsg);
            String requestId = dalResp.getString("requestId");
            String action = dalResp.getString("action");
            String msg=dalResp.getString("msg");

            // 构造返回给 UI 的响应包
            JSONObject uiResp = new JSONObject();
            uiResp.put("requestId", requestId);

            if("business_hours_analysis".equals(action)) {
                JSONArray dataArr=dalResp.getJSONArray("data");
                List<JSONObject> list = dataArr.toJavaList(JSONObject.class);
                uiResp.put("data", analyzeBusinessHours(list));
            }else if("expansion_trend".equals(action)) {
                JSONArray dataArr=dalResp.getJSONArray("data");
                List<JSONObject> list = dataArr.toJavaList(JSONObject.class);
                uiResp.put("data", analyzeExpansionTrend(list));
            } else if ("geographic_dispersion".equals(action)) {
                JSONArray dataArr=dalResp.getJSONArray("data");
                List<JSONObject> list = dataArr.toJavaList(JSONObject.class);
                uiResp.put("data", analyzeGeographicDispersion(list));
            }else if("hours_vs_age_correlation".equals(action)) {
                JSONArray dataArr=dalResp.getJSONArray("data");
                List<JSONObject> list = dataArr.toJavaList(JSONObject.class);
                uiResp.put("data", analyzeHoursByProvince(list));
            }else {
                uiResp.put("data", dalResp.get("data"));
            }

            uiResp.put("status", dalResp.get("status"));
            uiResp.put("msg", dalResp.get("msg"));
            uiResp.put("action", dalResp.get("action"));

            // listenDAL() 里只干这一件事：把 DAL 的结果原封不动转发给 UI
            MQManager_BLL.getInstance().send("ui_response_queue", uiResp.toString());
            System.out.println("【BLL】已把结果返回给 UI: ");
        });
    }


    private double round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * Math.pow(10, scale)) / Math.pow(10, scale);
    }

    private List<Double> getMode(List<Double> list) {
        if (list.isEmpty()) return Collections.emptyList();

        Map<Double, Long> freq = list.stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));

        long maxCount = freq.values().stream()
                .mapToLong(v -> v)
                .max()
                .orElse(0);

        return freq.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    // ============ 1. 门店营业时长统计分析 ============
    public JSONObject analyzeBusinessHours(List<JSONObject> stores) {

        List<Double> hoursList = stores.stream()
                .mapToDouble(store -> {
                    LocalTime open = LocalTime.parse(store.getString("open_time"));
                    LocalTime close = LocalTime.parse(store.getString("close_time"));
                    // 处理跨夜情况（比如 22:00 - 06:00）
                    long minutes = ChronoUnit.MINUTES.between(open, close);
                    if (minutes < 0) minutes += 24 * 60;
                    return minutes / 60.0;
                })
                .boxed()
                .collect(Collectors.toList());

        DescriptiveStatistics stats = new DescriptiveStatistics();
        hoursList.forEach(stats::addValue);

        JSONObject r = new JSONObject();
        r.put("avgHours", round(stats.getMean(), 2));
        r.put("medianHours", round(stats.getPercentile(50), 2));
        r.put("modeHours", getMode(hoursList));  // 可能有多个众数
        r.put("stdDev", round(stats.getStandardDeviation(), 2));
        r.put("q1", round(stats.getPercentile(25), 2));
        r.put("q3", round(stats.getPercentile(75), 2));
        r.put("iqr", round(stats.getPercentile(75) - stats.getPercentile(25), 2));
        r.put("p90", round(stats.getPercentile(90), 2));
        r.put("p95", round(stats.getPercentile(95), 2));
        return r;
    }

    // ============ 2. 开店扩张趋势 + 线性回归预测 ============
    public JSONObject analyzeExpansionTrend(List<JSONObject> stores) {
        // 按月份统计开店数量
        Map<String, Long> monthlyCount = stores.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getString("created_at").substring(0, 7),  // "2025-11"
                        TreeMap::new,
                        Collectors.counting()
                ));

        SimpleRegression regression = new SimpleRegression();
        List<String> months = new ArrayList<>(monthlyCount.keySet());
        System.out.println(months.size());
        for (int i = 0; i < months.size(); i++) {
            regression.addData(i + 1, monthlyCount.get(months.get(i)));
        }

        JSONObject r = new JSONObject();
        r.fluentPut("monthlyData", monthlyCount);
        r.fluentPut("regression", new JSONObject()
                .fluentPut("slope", round(regression.getSlope(), 3))
                .fluentPut("intercept", round(regression.getIntercept(), 2))
                .fluentPut("r", round(regression.getR(), 4))
                .fluentPut("rSquared", round(regression.getRSquare(), 4))
                .fluentPut("equation", String.format("y = %.3fx + %.2f", regression.getSlope(), regression.getIntercept()))
        );
        r.fluentPut("nextMonthPredict", Math.round(regression.predict(months.size() + 1)));
        return r;
    }

    // ============ 3. 门店地理分布离散度分析 ============
    public JSONObject analyzeGeographicDispersion(List<JSONObject> stores) {
        DescriptiveStatistics latStats = new DescriptiveStatistics();
        DescriptiveStatistics lngStats = new DescriptiveStatistics();

        stores.forEach(s -> {
            latStats.addValue(s.getDouble("latitude"));
            lngStats.addValue(s.getDouble("longitude"));
        });

        JSONObject r = new JSONObject();
        r.fluentPut("center", new JSONObject()
                .fluentPut("lat", round(latStats.getMean(), 6))
                .fluentPut("lng", round(lngStats.getMean(), 6))
        );
        r.fluentPut("stdLat", round(latStats.getStandardDeviation(), 4));
        r.fluentPut("stdLng", round(lngStats.getStandardDeviation(), 4));
        r.fluentPut("iqrLat", round(latStats.getPercentile(75) - latStats.getPercentile(25), 4));
        r.fluentPut("iqrLng", round(lngStats.getPercentile(75) - lngStats.getPercentile(25), 4));
        return r;
    }

    // ============ 4. 营业时长 vs 地理位置 相关性分析 ============
    public JSONObject analyzeHoursByProvince(List<JSONObject> stores) {
        // 按省份分组
        Map<String, List<JSONObject>> provinceGroups = stores.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getString("province") // 假设有 province 字段
                ));

        // 计算每个省的统计信息
        List<JSONObject> provinceStats = new ArrayList<>();

        for (Map.Entry<String, List<JSONObject>> entry : provinceGroups.entrySet()) {
            String province = entry.getKey();
            List<JSONObject> provinceStores = entry.getValue();

            // 计算该省的总营业时间和平均值
            double totalHours = 0;
            int storeCount = 0;

            for (JSONObject store : provinceStores) {
                double hours = calculateBusinessHours(store);
                if (hours > 0) { // 只计算有效数据
                    totalHours += hours;
                    storeCount++;
                }
            }

            if (storeCount > 0) {
                double avgHours = totalHours / storeCount;

                JSONObject stat = new JSONObject();
                stat.put("province", province);
                stat.put("avgHours", round(avgHours, 2));
                stat.put("storeCount", storeCount);
                stat.put("totalHours", round(totalHours, 2));
                stat.put("minHours", findMinHours(provinceStores));
                stat.put("maxHours", findMaxHours(provinceStores));

                provinceStats.add(stat);
            }
        }

        // 按平均营业时间排序（从高到低）
        provinceStats.sort((a, b) -> Double.compare(
                b.getDouble("avgHours"), a.getDouble("avgHours")
        ));

        // 准备图表数据
        JSONArray chartData = prepareChartData(provinceStats);

        JSONObject result = new JSONObject();
        result.put("provinces", new JSONArray(provinceStats));
        result.put("chartData", chartData);
        result.put("summary", createSummary(provinceStats));

        return result;
    }

    private double calculateBusinessHours(JSONObject store) {
        try {
            LocalTime open = LocalTime.parse(store.getString("open_time"));
            LocalTime close = LocalTime.parse(store.getString("close_time"));

            long minutes = ChronoUnit.MINUTES.between(open, close);
            if (minutes < 0) {
                minutes += 1440; // 跨天营业
            }
            return minutes / 60.0;
        } catch (Exception e) {
            return 0; // 解析失败返回0
        }
    }

    /**
     * 查找省内最短营业时间
     */
    private double findMinHours(List<JSONObject> stores) {
        return stores.stream()
                .mapToDouble(this::calculateBusinessHours)
                .filter(h -> h > 0)
                .min()
                .orElse(0);
    }

    /**
     * 查找省内最长营业时间
     */
    private double findMaxHours(List<JSONObject> stores) {
        return stores.stream()
                .mapToDouble(this::calculateBusinessHours)
                .filter(h -> h > 0)
                .max()
                .orElse(0);
    }

    /**
     * 准备图表数据格式
     */
    private JSONArray prepareChartData(List<JSONObject> stats) {
        JSONArray chartData = new JSONArray();

        // 省份名称数组
        JSONArray labels = new JSONArray();
        // 营业时间数组
        JSONArray data = new JSONArray();

        for (JSONObject stat : stats) {
            labels.add(stat.getString("province"));
            data.add(stat.getDouble("avgHours"));
        }

        JSONObject chart = new JSONObject();
        chart.put("labels", labels);
        chart.put("data", data);
        chart.put("title", "各省平均营业时间对比");
        chart.put("xAxisLabel", "省份");
        chart.put("yAxisLabel", "平均营业时间 (小时)");

        chartData.add(chart);
        return chartData;
    }

    /**
     * 创建统计摘要
     */
    private JSONObject createSummary(List<JSONObject> stats) {
        if (stats.isEmpty()) return new JSONObject();

        double overallAvg = stats.stream()
                .mapToDouble(s -> s.getDouble("avgHours"))
                .average().orElse(0);

        JSONObject summary = new JSONObject();
        summary.put("totalProvinces", stats.size());
        summary.put("totalStores", stats.stream().mapToInt(s -> s.getIntValue("storeCount")).sum());
        summary.put("nationalAvgHours", round(overallAvg, 2));
        summary.put("maxAvgProvince", stats.get(0).getString("province"));
        summary.put("maxAvgHours", stats.get(0).getDouble("avgHours"));
        summary.put("minAvgProvince", stats.get(stats.size() - 1).getString("province"));
        summary.put("minAvgHours", stats.get(stats.size() - 1).getDouble("avgHours"));

        return summary;
    }


    /**
     * -------------------------- BLL 转发 UI 请求 → DAL --------------------------
     */
    private void forwardToDAL(JSONObject uiReq) throws Exception {

        String requestId = uiReq.getString("requestId");

        // 缓存请求，等待 DAL 回包时匹配
        pendingMap.put(requestId, uiReq);

        System.out.println("【BLL】已转发到 DAL：");

        MQManager_BLL.getInstance().send("dal_request_queue", uiReq.toString());
    }

}


