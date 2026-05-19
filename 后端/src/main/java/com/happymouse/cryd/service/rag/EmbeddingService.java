package com.happymouse.cryd.service.rag;

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
 * 真正的向量化服务 — 调用嵌入模型API将文本转为语义向量
 * 支持 GLM 嵌入 和 讯飞星火嵌入，通过配置一键切换
 *
 * 配置切换方式（application.yml）：
 *   rag.embedding.provider: glm       → 智谱GLM嵌入
 *   rag.embedding.provider: spark     → 讯飞星火嵌入
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${rag.embedding.provider:glm}")
    private String provider;

    @Value("${rag.embedding.dimension:1024}")
    private int vectorDimension;

    // ===== GLM 嵌入配置 =====
    @Value("${rag.embedding.glm.url:https://open.bigmodel.cn/api/paas/v4/embeddings}")
    private String glmEmbeddingUrl;

    @Value("${rag.embedding.glm.key:}")
    private String glmApiKey;

    @Value("${rag.embedding.glm.model:embedding-3}")
    private String glmModel;

    // ===== 讯飞星火嵌入配置 =====
    @Value("${rag.embedding.spark.url:https://emb.xf-yun.com/v1/embeddings}")
    private String sparkEmbeddingUrl;

    @Value("${rag.embedding.spark.key:}")
    private String sparkApiKey;

    @Value("${rag.embedding.spark.model:embedding-v1}")
    private String sparkModel;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 将文本转为语义向量（调用嵌入模型API）
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) return new float[vectorDimension];

        try {
            switch (provider.toLowerCase()) {
                case "glm":
                    return callGlmEmbedding(text);
                case "spark":
                    return callSparkEmbedding(text);
                default:
                    log.warn("未知嵌入提供者: {}，回退到GLM", provider);
                    return callGlmEmbedding(text);
            }
        } catch (Exception e) {
            log.error("嵌入API调用失败: provider={}, text_len={}, error={}", provider, text.length(), e.getMessage());
            return new float[vectorDimension];
        }
    }

    /**
     * 批量嵌入
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (String text : texts) {
            result.add(embed(text));
        }
        return result;
    }

    /**
     * 计算余弦相似度
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ========== GLM 嵌入 ==========

    private float[] callGlmEmbedding(String text) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", glmModel);
        body.put("input", Collections.singletonList(text));

        Request request = new Request.Builder()
                .url(glmEmbeddingUrl)
                .addHeader("Authorization", "Bearer " + glmApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("GLM嵌入API错误[" + response.code() + "]: " + respBody);
            }
            JSONObject json = JSON.parseObject(respBody);
            JSONArray data = json.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONArray embeddingArr = data.getJSONObject(0).getJSONArray("embedding");
                float[] vector = new float[embeddingArr.size()];
                for (int i = 0; i < embeddingArr.size(); i++) {
                    vector[i] = embeddingArr.getFloatValue(i);
                }
                this.vectorDimension = vector.length; // 同步实际维度
                return vector;
            }
            throw new IOException("GLM嵌入返回空数据");
        }
    }

    // ========== 讯飞星火嵌入 ==========

    private float[] callSparkEmbedding(String text) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", sparkModel);
        body.put("input", Collections.singletonList(text));

        Request request = new Request.Builder()
                .url(sparkEmbeddingUrl)
                .addHeader("Authorization", "Bearer " + sparkApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("星火嵌入API错误[" + response.code() + "]: " + respBody);
            }
            JSONObject json = JSON.parseObject(respBody);
            JSONArray data = json.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONArray embeddingArr = data.getJSONObject(0).getJSONArray("embedding");
                float[] vector = new float[embeddingArr.size()];
                for (int i = 0; i < embeddingArr.size(); i++) {
                    vector[i] = embeddingArr.getFloatValue(i);
                }
                this.vectorDimension = vector.length;
                return vector;
            }
            throw new IOException("星火嵌入返回空数据");
        }
    }

    public int getDimension() { return vectorDimension; }
    public String getProvider() { return provider; }
}
