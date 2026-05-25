package com.happymouse.cryd.service.spark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 大模型 HTTP 客户端（OpenAI 兼容接口）
 * OpenAI 兼容接口，支持 DeepSeek 等后端切换
 * 同步调用和多轮对话
 */
@Service
public class SparkClient {

    private static final Logger log = LoggerFactory.getLogger(SparkClient.class);

    @Value("${llm.api.url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.model:deepseek-chat}")
    private String model;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 同步调用 LLM，返回完整回复
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, 0.5f, 2048);
    }

    public String chat(String systemPrompt, String userMessage, float temperature, int maxTokens) {
        try {
            JSONArray messages = new JSONArray();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                messages.add(sys);
            }
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userMessage);
            messages.add(user);

            return callApi(messages, temperature, maxTokens);
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            return "抱歉，AI服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 多轮对话调用
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> history, String userMessage, float temperature, int maxTokens) {
        try {
            JSONArray messages = new JSONArray();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                messages.add(sys);
            }
            // 添加历史对话
            if (history != null) {
                for (Map<String, String> msg : history) {
                    JSONObject h = new JSONObject();
                    h.put("role", msg.get("role"));
                    h.put("content", msg.get("content"));
                    messages.add(h);
                }
            }
            // 添加当前用户消息
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userMessage);
            messages.add(user);

            return callApi(messages, temperature, maxTokens);
        } catch (Exception e) {
            log.error("LLM多轮调用失败", e);
            return "AI服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 带图片的聊天调用（多模态模型）
     * 如果当前模型不支持图片，回退到纯文本
     */
    public String chatWithImage(String systemPrompt, String imageBase64) {
        return chatWithImage(systemPrompt, imageBase64, 0.5f, 2048);
    }

    public String chatWithImage(String systemPrompt, String imageBase64, float temperature, int maxTokens) {
        try {
            JSONArray messages = new JSONArray();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                messages.add(sys);
            }

            // 构建多模态消息
            JSONArray content = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", "请分析这张图片");
            content.add(textPart);

            JSONObject imagePart = new JSONObject();
            imagePart.put("type", "image_url");
            JSONObject imageUrl = new JSONObject();
            // 如果已经是完整 data URL 直接用，否则补前缀
            if (imageBase64 != null && imageBase64.startsWith("data:")) {
                imageUrl.put("url", imageBase64);
            } else {
                imageUrl.put("url", "data:image/jpeg;base64," + imageBase64);
            }
            imagePart.put("image_url", imageUrl);
            content.add(imagePart);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", content);
            messages.add(userMsg);

            // 使用当前配置的模型处理图片（需模型支持多模态）
            return callApi(messages, temperature, maxTokens);
        } catch (Exception e) {
            log.error("LLM图片分析失败", e);
            return "抱歉，图片分析服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 语音转文字 — 保留接口，暂不支持
     */
    public String voiceToText(byte[] audioBytes) {
        log.warn("语音转文字功能暂未接入，请配置语音识别服务");
        return "";
    }

    /**
     * 文本关键词提取
     */
    public String extractKeywords(String text) {
        String prompt = "你是文本分析专家。请从以下文本中提取5-10个最关键的术语/关键词。\n只输出关键词，用逗号分隔。不要输出其他任何内容。\n文本内容：\n" + text;
        try {
            String result = chat(prompt, text, 0.3f, 150);
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("关键词提取失败", e);
            return "";
        }
    }

    /**
     * 语义相似度评分
     */
    public float semanticSimilarity(String query, String document) {
        String prompt = "你是语义相似度评估专家。请评估以下两段文本的语义相关度。\n只输出一个0到1之间的数字，表示相关度（1=完全相关，0=完全不相关）。\n不要输出其他任何内容。\n\n文本1（查询）：\n" + query + "\n\n文本2（文档）：\n" + (document.length() > 500 ? document.substring(0, 500) : document);
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
     * 核心API调用方法
     */
    private String callApi(JSONArray messages, float temperature, int maxTokens) throws IOException {
        return callApi(messages, temperature, maxTokens, null);
    }

    private String callApi(JSONArray messages, float temperature, int maxTokens, String modelOverride) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", modelOverride != null ? modelOverride : model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("LLM API HTTP错误: code={}, body={}", response.code(), respBody);
                throw new IOException("API错误[" + response.code() + "]: " + respBody);
            }
            JSONObject json = JSON.parseObject(respBody);
            // 检查 API 业务错误
            if (json.containsKey("error")) {
                JSONObject err = json.getJSONObject("error");
                String errMsg = err != null ? err.getString("message") : respBody;
                log.error("LLM API业务错误: {}", errMsg);
                throw new IOException("LLM API错误: " + (errMsg != null ? errMsg : respBody));
            }
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                String finishReason = choice.getString("finish_reason");
                if ("length".equals(finishReason)) {
                    log.warn("LLM输出被截断(finish_reason=length), 当前max_tokens不足, 需增大");
                }
                JSONObject message = choice.getJSONObject("message");
                if (message != null) {
                    String content = message.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return content;
                    }
                }
            }
            log.warn("LLM返回空内容: {}", respBody.substring(0, Math.min(300, respBody.length())));
            return "";
        }
    }
}
