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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 语音听写 API 地址 */
    private static final String IAT_API_URL = "ws://iat-api.xfyun.cn/v2/iat";

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
     * 带图片的聊天调用
     */
    public String chatWithImage(String systemPrompt, String imageBase64) {
        return chatWithImage(systemPrompt, imageBase64, 0.5f, 2048);
    }

    public String chatWithImage(String systemPrompt, String imageBase64, float temperature, int maxTokens) {
        try {
            // 提取Base64数据部分
            String cleanBase64 = extractBase64(imageBase64);
            
            String authUrl = buildAuthUrl();
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder fullResponse = new StringBuilder();

            WebSocketClient ws = new WebSocketClient(new URI(authUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    List<Map<String, Object>> messages = new ArrayList<>();
                    
                    // 系统消息
                    if (systemPrompt != null && !systemPrompt.isEmpty()) {
                        messages.add(Map.of("role", "system", "content", systemPrompt));
                    }
                    
                    // 用户消息（包含图片）
                    List<Map<String, String>> content = new ArrayList<>();
                    content.add(Map.of("type", "text", "content", "请分析这张图片"));
                    content.add(Map.of("type", "image", "image", cleanBase64));
                    
                    Map<String, Object> userMessage = new LinkedHashMap<>();
                    userMessage.put("role", "user");
                    userMessage.put("content", content);
                    messages.add(userMessage);

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
            log.error("星火API图片分析失败", e);
            return "抱歉，图片分析服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 从Data URL中提取Base64数据
     */
    private String extractBase64(String dataUrl) {
        if (dataUrl == null || dataUrl.isEmpty()) {
            return "";
        }
        // 匹配data:image/xxx;base64,格式
        Pattern pattern = Pattern.compile("^data:image/[^;]+;base64,(.+)$");
        Matcher matcher = pattern.matcher(dataUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return dataUrl;
    }

    /**
     * 语音转文字 — 调用讯飞语音听写 API (iat-api.xfyun.cn)
     * 协议：音频分帧发送，每帧1280字节，status: 0→首帧, 1→中间帧, 2→结束帧
     */
    public String voiceToText(byte[] audioBytes) {
        try {
            // 去除WAV文件头（44字节），只保留PCM数据
            byte[] pcmData = stripWavHeader(audioBytes);
            String authUrl = buildIatAuthUrl();
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder resultText = new StringBuilder();
            final int FRAME_SIZE = 1280; // 每帧音频字节数

            WebSocketClient ws = new WebSocketClient(new URI(authUrl)) {
                private int frameIndex = 0;
                private int totalFrames;

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("语音听写WebSocket已连接, 音频大小={}字节, PCM大小={}字节",
                            audioBytes.length, pcmData.length);
                    totalFrames = (int) Math.ceil((double) pcmData.length / FRAME_SIZE);

                    // 第一帧：携带 common + business + 音频数据(status=0)
                    Map<String, Object> common = Map.of("app_id", appId);
                    Map<String, Object> business = new LinkedHashMap<>();
                    business.put("language", "zh_cn");
                    business.put("domain", "iat");
                    business.put("accent", "mandarin");
                    business.put("vad_eos", 5000);
                    business.put("pte", "1"); // 开启标点
                    business.put("pd", "game"); // 领域

                    byte[] firstChunk = getChunk(pcmData, 0, FRAME_SIZE);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("status", 0);
                    data.put("format", "audio/L16;rate=16000");
                    data.put("encoding", "raw");
                    data.put("audio", Base64.getEncoder().encodeToString(firstChunk));

                    Map<String, Object> frame = new LinkedHashMap<>();
                    frame.put("common", common);
                    frame.put("business", business);
                    frame.put("data", data);

                    this.send(JSON.toJSONString(frame));
                    frameIndex++;

                    // 发送中间帧 (status=1)
                    for (int offset = FRAME_SIZE; offset < pcmData.length; offset += FRAME_SIZE) {
                        byte[] chunk = getChunk(pcmData, offset, FRAME_SIZE);
                        sendAudioFrame(1, chunk);
                        frameIndex++;
                    }

                    // 发送结束帧 (status=2)
                    sendAudioFrame(2, new byte[0]);
                }

                private void sendAudioFrame(int status, byte[] chunk) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("status", status);
                    data.put("format", "audio/L16;rate=16000");
                    data.put("encoding", "raw");
                    data.put("audio", Base64.getEncoder().encodeToString(chunk));

                    Map<String, Object> frame = new LinkedHashMap<>();
                    frame.put("data", data);
                    this.send(JSON.toJSONString(frame));
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject msgJson = JSON.parseObject(message);
                        // 检查错误码
                        if (msgJson.containsKey("code") && msgJson.getIntValue("code") != 0) {
                            String errMsg = msgJson.getString("message");
                            log.error("语音听写API错误: code={}, msg={}", msgJson.getIntValue("code"), errMsg);
                            if (!future.isDone()) {
                                future.completeExceptionally(
                                        new RuntimeException("语音听写错误: " + errMsg));
                            }
                            this.close();
                            return;
                        }
                        // 解析识别结果
                        if (msgJson.containsKey("data")) {
                            JSONObject data = msgJson.getJSONObject("data");
                            if (data.containsKey("result")) {
                                JSONObject result = data.getJSONObject("result");
                                if (result != null && result.containsKey("ws")) {
                                    var ws = result.getJSONArray("ws");
                                    for (int i = 0; i < ws.size(); i++) {
                                        var cw = ws.getJSONObject(i).getJSONArray("cw");
                                        for (int j = 0; j < cw.size(); j++) {
                                            String word = cw.getJSONObject(j).getString("w");
                                            resultText.append(word);
                                        }
                                    }
                                }
                            }
                            // status=2 表示识别结束
                            if (data.containsKey("status") && data.getIntValue("status") == 2) {
                                future.complete(resultText.toString());
                                this.close();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析语音听写响应异常: {}", e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.debug("语音听写WebSocket关闭: code={}, reason={}", code, reason);
                    if (!future.isDone()) {
                        future.complete(resultText.toString());
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("语音听写WebSocket错误: {}", ex.getMessage());
                    if (!future.isDone()) {
                        future.completeExceptionally(ex);
                    }
                }
            };

            ws.connect();
            String result = future.get(60, TimeUnit.SECONDS);
            log.info("语音听写完成: {}", result);
            return result != null ? result : "";
        } catch (TimeoutException e) {
            log.error("语音听写API超时");
            return "";
        } catch (Exception e) {
            log.error("语音听写调用失败: {}", e.getMessage());
            return "";
        }
    }

    /** 从字节数组中截取指定范围的chunk */
    private byte[] getChunk(byte[] data, int offset, int size) {
        int end = Math.min(offset + size, data.length);
        byte[] chunk = new byte[end - offset];
        System.arraycopy(data, offset, chunk, 0, chunk.length);
        return chunk;
    }

    /** 检测并去除WAV文件头 */
    private byte[] stripWavHeader(byte[] data) {
        if (data == null || data.length < 44) return data;
        // WAV文件以 "RIFF" 开头且第8-15字节为 "WAVEfmt "
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') {
            log.info("检测到WAV文件头，剥离44字节，PCM数据={}字节", data.length - 44);
            byte[] pcm = new byte[data.length - 44];
            System.arraycopy(data, 44, pcm, 0, pcm.length);
            return pcm;
        }
        return data;
    }

    /**
     * 文本语义分析 — 用星火大模型提取关键词和语义向量（替代 TF-IDF）
     * @param text 待分析文本
     * @return 关键词列表，逗号分隔
     */
    public String extractKeywords(String text) {
        String prompt = """
            你是文本分析专家。请从以下文本中提取5-10个最关键的术语/关键词。
            只输出关键词，用逗号分隔。不要输出其他任何内容。
            文本内容：
            """ + text;
        try {
            String result = chat(prompt, text, 0.3f, 150);
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("关键词提取失败", e);
            return "";
        }
    }

    /**
     * 文本片段语义相似度评分 — 用星火大模型评估两段文本的相关性
     * @return 0-1 之间的相似度分数
     */
    public float semanticSimilarity(String query, String document) {
        String prompt = """
            你是语义相似度评估专家。请评估以下两段文本的语义相关度。
            只输出一个0到1之间的数字，表示相关度（1=完全相关，0=完全不相关）。
            不要输出其他任何内容。

            文本1（查询）：
            """ + query + """

            文本2（文档）：
            """ + (document.length() > 500 ? document.substring(0, 500) : document);
        try {
            String result = chat(prompt, "评估相关度", 0.1f, 10);
            if (result != null) {
                String cleaned = result.trim().replaceAll("[^0-9.]", "");
                if (!cleaned.isEmpty()) {
                    return Math.max(0, Math.min(1, Float.parseFloat(cleaned)));
                }
            }
        } catch (Exception e) {
            log.warn("语义相似度评估失败", e);
        }
        return 0.5f;
    }

    /**
     * 生成语音听写API鉴权URL
     */
    private String buildIatAuthUrl() throws Exception {
        URI uri = new URI(IAT_API_URL);
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

        return IAT_API_URL
                + "?authorization=" + Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))
                + "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8)
                + "&host=" + host;
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