package com.happymouse.cryd.service.xunfei;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞语音听写 (IAT) 服务
 * 将 PCM 16kHz 16bit 单声道音频转为文字
 * API 文档: https://www.xfyun.cn/doc/asr/voicedictation
 */
@Service
public class XunfeiIatService {

    private static final Logger log = LoggerFactory.getLogger(XunfeiIatService.class);

    private static final String HOST = "iat-api.xfyun.cn";
    private static final String WS_URL = "wss://" + HOST + "/v2/iat";
    private static final int STATUS_FIRST = 0;
    private static final int STATUS_CONTINUE = 1;
    private static final int STATUS_LAST = 2;

    @Value("${xunfei.asr.app-id:}")
    private String appId;

    @Value("${xunfei.asr.api-key:}")
    private String apiKey;

    @Value("${xunfei.asr.api-secret:}")
    private String apiSecret;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    /**
     * 语音听写 — 将 PCM 16kHz 16bit 单声道音频转为文字
     * @param audioBytes 原始音频数据 (PCM/WAV)
     * @return 识别出的文字，失败返回空字符串
     */
    public String transcribe(byte[] audioBytes) {
        if (appId == null || appId.isEmpty() || apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            log.error("讯飞ASR未配置: appId/apiKey/apiSecret 为空，请在 application.yml 中配置 xunfei.asr.*");
            return "";
        }
        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("音频数据为空");
            return "";
        }

        // 如果是 WAV，去掉 44 字节头部，提取 PCM
        byte[] pcm = stripWavHeader(audioBytes);
        if (pcm.length == 0) {
            log.warn("音频PCM数据为空");
            return "";
        }

        // 每帧 1280 字节发送
        final int FRAME_SIZE = 1280;
        int totalFrames = (pcm.length + FRAME_SIZE - 1) / FRAME_SIZE;
        if (totalFrames == 0) {
            log.warn("无音频帧");
            return "";
        }

        // 构建认证 URL
        String wsUrl;
        try {
            wsUrl = buildAuthUrl();
        } catch (Exception e) {
            log.error("讯飞ASR认证参数构建失败", e);
            return "";
        }

        // 同步等待识别完成
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder resultText = new StringBuilder();
        Exception[] error = {null};

        WebSocket ws = httpClient.newWebSocket(
                new Request.Builder().url(wsUrl).build(),
                new WebSocketListener() {

                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        log.info("讯飞ASR WebSocket已连接");
                        // 逐帧发送音频
                        for (int i = 0; i < totalFrames; i++) {
                            int start = i * FRAME_SIZE;
                            int end = Math.min(start + FRAME_SIZE, pcm.length);
                            byte[] frame = new byte[end - start];
                            System.arraycopy(pcm, start, frame, 0, frame.length);

                            int status = (i == totalFrames - 1) ? STATUS_LAST : (i == 0 ? STATUS_FIRST : STATUS_CONTINUE);

                            try {
                                // 发送 JSON 参数帧
                                JSONObject params = new JSONObject();
                                params.put("common", buildCommonParams());
                                params.put("business", buildBusinessParams());
                                JSONObject data = new JSONObject();
                                data.put("status", status);
                                data.put("format", "audio/L16;rate=16000");
                                data.put("encoding", "raw");
                                data.put("audio", Base64.getEncoder().encodeToString(frame));
                                params.put("data", data);

                                webSocket.send(params.toJSONString());
                            } catch (Exception e) {
                                log.error("发送音频帧失败", e);
                                error[0] = e;
                                latch.countDown();
                                return;
                            }

                            if (i == totalFrames - 1) {
                                // 最后一帧后稍作延迟等待结果
                                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                            }
                        }
                        log.info("音频发送完成: {} 帧", totalFrames);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        try {
                            JSONObject msg = JSON.parseObject(text);
                            int code = msg.getIntValue("code", -1);
                            if (code != 0) {
                                log.warn("讯飞ASR返回错误: code={}, message={}", code, msg.getString("message"));
                                error[0] = new RuntimeException("讯飞ASR错误: " + msg.getString("message"));
                                return;
                            }

                            JSONObject data = msg.getJSONObject("data");
                            if (data == null) return;

                            int status = data.getIntValue("status", -1);

                            // 拼接识别结果
                            JSONObject result = data.getJSONObject("result");
                            if (result != null) {
                                JSONArray wsArr = result.getJSONArray("ws");
                                if (wsArr != null) {
                                    StringBuilder seg = new StringBuilder();
                                    for (int i = 0; i < wsArr.size(); i++) {
                                        JSONObject ws = wsArr.getJSONObject(i);
                                        JSONArray cw = ws.getJSONArray("cw");
                                        if (cw != null && !cw.isEmpty()) {
                                            seg.append(cw.getJSONObject(0).getString("w"));
                                        }
                                    }
                                    resultText.append(seg);
                                }
                            }

                            // status 2 = 识别结束
                            if (status == 2) {
                                latch.countDown();
                            }
                        } catch (Exception e) {
                            log.warn("解析讯飞ASR响应异常", e);
                        }
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        log.error("讯飞ASR WebSocket失败: {}", t.getMessage());
                        error[0] = new RuntimeException("WebSocket连接失败: " + t.getMessage());
                        latch.countDown();
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        log.info("讯飞ASR WebSocket关闭: code={}, reason={}", code, reason);
                        latch.countDown();
                    }
                }
        );

        try {
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("讯飞ASR识别超时");
                ws.close(1000, "timeout");
                return "";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ws.close(1000, "interrupted");
            return "";
        }

        if (error[0] != null) {
            log.error("讯飞ASR错误: {}", error[0].getMessage());
            return "";
        }

        String text = resultText.toString().trim();
        log.info("讯飞ASR识别结果: {}", text);
        return text;
    }

    /**
     * 构建业务参数
     */
    private JSONObject buildBusinessParams() {
        JSONObject business = new JSONObject();
        business.put("domain", "iat");          // 语音听写
        business.put("language", "zh_cn");       // 中文
        business.put("accent", "mandarin");      // 普通话
        business.put("vinfo", 1);                // 返回 VAD 信息
        business.put("dwa", "wpgs");             // 动态修正
        business.put("ptt", 0);                  // 不添加标点（可改为1）
        business.put("rlang", "zh-cn");          // 结果语言
        business.put("nbest", 1);                // 最佳结果数
        return business;
    }

    /**
     * 构建公共参数
     */
    private JSONObject buildCommonParams() {
        JSONObject common = new JSONObject();
        common.put("app_id", appId);
        return common;
    }

    /**
     * 构建带认证签名的 WebSocket URL
     */
    private String buildAuthUrl() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = sdf.format(new Date());

        // 签名原始字符串
        String signatureOrigin = "host: " + HOST + "\ndate: " + date + "\nGET /v2/iat HTTP/1.1";

        // HMAC-SHA256 签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signatureBytes = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signatureBytes);

        // authorization = base64( api_key="xxx", algorithm="hmac-sha256", headers="host date request-line", signature="xxx" )
        String authOrigin = "api_key=\"" + apiKey + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"" + signature + "\"";
        String authorization = Base64.getEncoder().encodeToString(authOrigin.getBytes(StandardCharsets.UTF_8));

        // 构建 URL
        return WS_URL + "?authorization=" + urlEncode(authorization)
                + "&date=" + urlEncode(date)
                + "&host=" + HOST;
    }

    /**
     * URL 编码
     */
    private String urlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 去除 WAV 文件头（44 字节），提取原始 PCM 数据
     * 如果数据以 "RIFF" 开头，判定为 WAV 格式
     */
    private byte[] stripWavHeader(byte[] data) {
        if (data.length < 44) {
            return data; // 太短，直接当 PCM
        }
        // 检查 RIFF 头
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            log.info("检测到WAV头，去除44字节，PCM数据: {} bytes", data.length - 44);
            byte[] pcm = new byte[data.length - 44];
            System.arraycopy(data, 44, pcm, 0, pcm.length);
            return pcm;
        }
        return data; // 直接当 PCM
    }
}
