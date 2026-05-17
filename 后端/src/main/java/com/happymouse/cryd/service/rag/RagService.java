package com.happymouse.cryd.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG检索增强生成服务 — 为Agent提供知识库上下文注入
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    private int defaultTopK = 5;
    private double similarityThreshold = 0.65;

    public RagService(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * 检索相关知识点片段
     */
    public List<KnowledgeFragment> retrieveRelevant(String query, int topK) {
        float[] queryVec = embeddingService.embed(query);
        List<VectorStore.SearchResult> results = vectorStore.search(queryVec, topK, similarityThreshold);

        return results.stream()
                .map(r -> new KnowledgeFragment(
                    r.getMetadata().getOrDefault("content", ""),
                    r.getMetadata().getOrDefault("tags", ""),
                    r.getMetadata().getOrDefault("source", ""),
                    r.getSimilarity()))
                .collect(Collectors.toList());
    }

    public List<KnowledgeFragment> retrieveRelevant(String query) {
        return retrieveRelevant(query, defaultTopK);
    }

    /**
     * 将检索到的知识片段注入到系统提示词中
     */
    public String injectContextToPrompt(String basePrompt, List<KnowledgeFragment> fragments) {
        if (fragments == null || fragments.isEmpty()) return basePrompt;

        StringBuilder context = new StringBuilder();
        context.append("\n\n【参考权威资料】\n");
        context.append("以下内容来自课程教材和教师上传的权威资料，请优先使用这些内容来回答问题：\n\n");

        for (int i = 0; i < fragments.size(); i++) {
            KnowledgeFragment f = fragments.get(i);
            context.append("--- 参考资料").append(i + 1).append(" ---\n");
            if (f.tags != null && !f.tags.isEmpty()) {
                context.append("标签: ").append(f.tags).append("\n");
            }
            context.append(f.content).append("\n\n");
        }

        context.append("请基于以上资料生成内容，确保准确性和权威性。如有引用，可标注出处。");

        return basePrompt + context.toString();
    }

    /**
     * 检索并构建增强Prompt（一步完成）
     */
    public String retrieveAndAugment(String basePrompt, String query) {
        List<KnowledgeFragment> fragments = retrieveRelevant(query);
        log.info("RAG检索: query='{}', 命中{}条", query.substring(0, Math.min(30, query.length())), fragments.size());
        return injectContextToPrompt(basePrompt, fragments);
    }

    /**
     * 知识片段
     */
    public static class KnowledgeFragment {
        public final String content;
        public final String tags;
        public final String source;
        public final double similarity;

        public KnowledgeFragment(String content, String tags, String source, double similarity) {
            this.content = content;
            this.tags = tags;
            this.source = source;
            this.similarity = similarity;
        }
    }

    public void setDefaultTopK(int topK) { this.defaultTopK = topK; }
    public void setSimilarityThreshold(double threshold) { this.similarityThreshold = threshold; }
}
