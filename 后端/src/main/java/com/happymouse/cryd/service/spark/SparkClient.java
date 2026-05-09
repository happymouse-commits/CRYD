package com.happymouse.cryd.service.spark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 讯飞星火大模型 WebSocket 客户端
 * 支持同步调用和流式调用
 */
@Service
public class SparkClient {

    private static final Logger log = LoggerFactory.getLogger(SparkClient.class);

    @Value("${spark.api.url}")
    private String apiUrl;

    @Value("${spark.api.app-id}")
    private String appId;

    @Value("${spark.api.api-key}")
    private String apiKey;

    @Value("${spark.api.api-secret}")
    private String apiSecret;

    @Value("${spark.api.domain}")
    private String domain;

    /**
     * 同步调用星火API，返回完整回复
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, 0.5f, 2048);
    }

    public String chat(String systemPrompt, String userMessage, float temperature, int maxTokens) {
        try {
            String authUrl = buildAuthUrl();
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder fullResponse = new StringBuilder();

            WebSocketClient ws = new WebSocketClient(new URI(authUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.debug("WebSocket连接已建立");
                    List<Map<String, String>> messages = new ArrayList<>();
                    if (systemPrompt != null && !systemPrompt.isEmpty()) {
                        messages.add(Map.of("role", "system", "content", systemPrompt));
                    }
                    messages.add(Map.of("role", "user", "content", userMessage));

                    Map<String, Object> request = new LinkedHashMap<>();
                    Map<String, Object> header = Map.of("app_id", appId, "uid", "cryd-user");
                    Map<String, Object> parameter = Map.of(
                            "chat", Map.of(
                                    "domain", domain,
                                    "temperature", temperature,
                                    "max_tokens", maxTokens
                            )
                    );
                    Map<String, Object> payload = Map.of(
                            "message", Map.of("text", messages)
                    );
                    request.put("header", header);
                    request.put("parameter", parameter);
                    request.put("payload", payload);

                    this.send(JSON.toJSONString(request));
                }

                @Override
                public void onMessage(String message) {
                    JSONObject data = JSON.parseObject(message);
                    int code = data.getJSONObject("header").getIntValue("code");
                    if (code != 0) {
                        String errMsg = data.getJSONObject("header").getString("message");
                        log.error("星火API错误: code={}, msg={}", code, errMsg);
                        future.completeExceptionally(new RuntimeException("API错误[" + code + "]: " + errMsg));
                        this.close();
                        return;
                    }
                    String content = data.getJSONObject("payload")
                            .getJSONObject("choices")
                            .getJSONArray("text")
                            .getJSONObject(0)
                            .getString("content");
                    fullResponse.append(content);

                    int status = data.getJSONObject("header").getIntValue("status");
                    if (status == 2) {
                        future.complete(fullResponse.toString());
                        this.close();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (!future.isDone()) {
                        future.complete(fullResponse.toString());
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket错误", ex);
                    if (!future.isDone()) {
                        future.completeExceptionally(ex);
                    }
                }
            };

            ws.connect();
            return future.get(60, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            log.error("星火API调用超时");
            return "抱歉，AI响应超时，请稍后再试";
        } catch (Exception e) {
            log.error("星火API调用失败", e);
            return "抱歉，AI服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 多轮对话调用
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> history, String userMessage, float temperature, int maxTokens) {
        try {
            String authUrl = buildAuthUrl();
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder fullResponse = new StringBuilder();

            WebSocketClient ws = new WebSocketClient(new URI(authUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    List<Map<String, String>> messages = new ArrayList<>();
                    if (systemPrompt != null && !systemPrompt.isEmpty()) {
                        messages.add(Map.of("role", "system", "content", systemPrompt));
                    }
                    messages.addAll(history);
                    messages.add(Map.of("role", "user", "content", userMessage));

                    Map<String, Object> request = new LinkedHashMap<>();
                    request.put("header", Map.of("app_id", appId, "uid", "cryd-user"));
                    request.put("parameter", Map.of("chat", Map.of("domain", domain, "temperature", temperature, "max_tokens", maxTokens)));
                    request.put("payload", Map.of("message", Map.of("text", messages)));

                    this.send(JSON.toJSONString(request));
                }

                @Override
                public void onMessage(String message) {
                    JSONObject data = JSON.parseObject(message);
                    int code = data.getJSONObject("header").getIntValue("code");
                    if (code != 0) {
                        future.completeExceptionally(new RuntimeException("API错误[" + code + "]"));
                        this.close();
                        return;
                    }
                    String content = data.getJSONObject("payload")
                            .getJSONObject("choices").getJSONArray("text").getJSONObject(0).getString("content");
                    fullResponse.append(content);
                    if (data.getJSONObject("header").getIntValue("status") == 2) {
                        future.complete(fullResponse.toString());
                        this.close();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (!future.isDone()) future.complete(fullResponse.toString());
                }

                @Override
                public void onError(Exception ex) {
                    if (!future.isDone()) future.completeExceptionally(ex);
                }
            };

            ws.connect();
            return future.get(60, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("星火API多轮调用失败", e);
            return "AI服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 生成鉴权URL
     */
    private String buildAuthUrl() throws Exception {
        URI uri = new URI(apiUrl);
        String host = uri.getHost();
        String path = uri.getPath();

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = sdf.format(new Date());

        String signatureOrigin = "host: " + host + "\ndate: " + date + "\nGET " + path + " HTTP/1.1";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));

        String authorization = String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signature
        );

        return apiUrl
                + "?authorization=" + Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))
                + "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8)
                + "&host=" + host;
    }
}
