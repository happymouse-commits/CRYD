package com.happymouse.cryd.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 本地哈希向量化服务 — 基于 n-gram 哈希投影，无需外部 Embedding API。
 * DeepSeek 不提供 Embedding 端点，因此用字符 n-gram + SHA-256 哈希生成固定维度向量，
 * 余弦相似度可近似反映文本间的字符级重叠程度。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${rag.embedding.dimension:1024}")
    private int vectorDimension;

    private static final int NGRAM_MIN = 2;
    private static final int NGRAM_MAX = 5;

    /**
     * 将文本转为语义向量（本地 n-gram 哈希投影）
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) return new float[vectorDimension];

        float[] vector = new float[vectorDimension];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            Set<String> seen = new HashSet<>();

            for (int n = NGRAM_MIN; n <= NGRAM_MAX; n++) {
                for (int i = 0; i <= text.length() - n; i++) {
                    String ngram = text.substring(i, i + n);
                    if (seen.add(ngram)) {
                        byte[] hash = md.digest(ngram.getBytes(StandardCharsets.UTF_8));
                        for (int j = 0; j < vectorDimension; j++) {
                            int byteIdx = j % hash.length;
                            float val = ((hash[byteIdx] & 0xFF) - 127.5f) / 127.5f;
                            vector[j] += val;
                        }
                    }
                }
            }

            // L2 normalize
            double norm = 0;
            for (float v : vector) norm += v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) (vector[i] / norm);
                }
            }
        } catch (Exception e) {
            log.warn("向量化失败: text_len={}, error={}", text.length(), e.getMessage());
        }
        return vector;
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

    public int getDimension() { return vectorDimension; }
}
