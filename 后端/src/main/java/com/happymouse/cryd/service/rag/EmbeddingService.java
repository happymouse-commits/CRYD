package com.happymouse.cryd.service.rag;

import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 向量化服务 — 将文本转为向量
 * 使用星火大模型做语义关键词提取 + 语义相似度评估
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int VECTOR_DIM = 256;

    private final SparkClient sparkClient;

    public EmbeddingService(SparkClient sparkClient) {
        this.sparkClient = sparkClient;
    }

    /**
     * 将文本映射为语义向量
     * 使用星火大模型提取关键词，然后映射到固定维度向量
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) return new float[VECTOR_DIM];

        // 使用星火大模型提取关键词（替代本地TF-IDF）
        String keywords = "";
        try {
            keywords = sparkClient.extractKeywords(text);
        } catch (Exception e) {
            log.warn("AI关键词提取失败，回退到本地分词: {}", e.getMessage());
        }

        // 如果AI提取失败，回退到本地分词
        String[] words;
        if (keywords != null && !keywords.isEmpty()) {
            words = keywords.split("[,，\\s]+");
        } else {
            words = tokenize(text);
        }

        // 词频统计
        Map<String, Integer> tf = new LinkedHashMap<>();
        for (String w : words) {
            String cleaned = w.trim();
            if (!cleaned.isEmpty()) {
                tf.merge(cleaned, 1, Integer::sum);
            }
        }

        // 映射到固定维度向量
        float[] vector = new float[VECTOR_DIM];
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            int idx = Math.abs(entry.getKey().hashCode()) % VECTOR_DIM;
            double weight = Math.log1p(entry.getValue());
            vector[idx] += (float) weight;
        }

        // L2归一化
        float norm = 0;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < VECTOR_DIM; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    /**
     * 批量embed
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
        if (a == null || b == null || a.length != VECTOR_DIM || b.length != VECTOR_DIM) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < VECTOR_DIM; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 语义相似度评估 — 使用星火大模型直接评估两段文本相关性
     */
    public double semanticSimilarity(String query, String document) {
        try {
            return sparkClient.semanticSimilarity(query, document);
        } catch (Exception e) {
            log.warn("AI语义相似度评估失败，回退到向量余弦相似度: {}", e.getMessage());
            return cosineSimilarity(embed(query), embed(document));
        }
    }

    /**
     * 简单中文分词（回退方案）
     */
    private String[] tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String cleaned = text.toLowerCase().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ");

        for (char c : cleaned.toCharArray()) {
            if (c != ' ') tokens.add(String.valueOf(c));
        }
        for (int i = 0; i < cleaned.length() - 1; i++) {
            String gram = cleaned.substring(i, i + 2);
            if (!gram.contains(" ")) tokens.add(gram);
        }

        return tokens.toArray(new String[0]);
    }

    public int getDimension() { return VECTOR_DIM; }
}
