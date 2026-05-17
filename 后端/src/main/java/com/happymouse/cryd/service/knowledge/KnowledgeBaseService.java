package com.happymouse.cryd.service.knowledge;

import com.happymouse.cryd.model.entity.KnowledgeBase;
import com.happymouse.cryd.model.entity.KnowledgeChunk;
import com.happymouse.cryd.model.entity.KnowledgeDocument;
import com.happymouse.cryd.repository.KnowledgeBaseRepository;
import com.happymouse.cryd.repository.KnowledgeChunkRepository;
import com.happymouse.cryd.repository.KnowledgeDocumentRepository;
import com.happymouse.cryd.service.rag.DocumentChunker;
import com.happymouse.cryd.service.rag.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KnowledgeDocumentRepository docRepo;
    private final KnowledgeChunkRepository chunkRepo;
    private final VectorStore vectorStore;
    private final DocumentChunker chunker;
    private final TagExtractor tagExtractor;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                 KnowledgeDocumentRepository docRepo,
                                 KnowledgeChunkRepository chunkRepo,
                                 VectorStore vectorStore,
                                 DocumentChunker chunker,
                                 TagExtractor tagExtractor) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.tagExtractor = tagExtractor;
    }

    // --- KnowledgeBase CRUD ---
    public KnowledgeBase createKB(Long courseId, Long teacherId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setCourseId(courseId);
        kb.setTeacherId(teacherId);
        kb.setName(name);
        kb.setDescription(description);
        return kbRepo.save(kb);
    }

    public KnowledgeBase getKB(Long kbId) {
        return kbRepo.findById(kbId).orElse(null);
    }

    public List<KnowledgeBase> getByCourse(Long courseId) {
        return kbRepo.findByCourseId(courseId);
    }

    public List<KnowledgeBase> getByTeacher(Long teacherId) {
        return kbRepo.findByTeacherId(teacherId);
    }

    // --- Document upload & processing ---
    @Transactional
    public KnowledgeDocument uploadDocument(Long kbId, MultipartFile file) throws IOException {
        KnowledgeBase kb = getKB(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setOriginalContent(content);
        doc.setStatus("pending");
        docRepo.save(doc);

        // 异步处理
        processDocument(doc, content);

        return doc;
    }

    @Transactional
    public KnowledgeDocument uploadTextDocument(Long kbId, String fileName, String content) {
        KnowledgeBase kb = getKB(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setFileName(fileName);
        doc.setFileType("txt");
        doc.setOriginalContent(content);
        doc.setStatus("pending");
        docRepo.save(doc);

        processDocument(doc, content);
        return doc;
    }

    /**
     * 处理文档：清洗 -> 切片 -> 标签提取 -> 向量化
     */
    private void processDocument(KnowledgeDocument doc, String content) {
        try {
            doc.setStatus("processing");
            docRepo.save(doc);

            // 清洗内容
            String cleaned = cleanContent(content);
            doc.setProcessedContent(cleaned);

            // 提取标签
            List<String> tags = tagExtractor.extractTags(cleaned);
            doc.setTags(String.join(",", tags));

            // 切片
            List<String> chunks = chunker.chunk(cleaned);

            // 向量化存储
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);

                // 保存chunk到数据库
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocumentId(doc.getId());
                kc.setKbId(doc.getKbId());
                kc.setChunkIndex(i);
                kc.setContent(chunkText);
                kc.setTags(String.join(",", tags));
                kc = chunkRepo.save(kc);

                // 向量化并存储
                Map<String, String> metadata = new HashMap<>();
                metadata.put("content", chunkText);
                metadata.put("tags", String.join(",", tags));
                metadata.put("source", doc.getFileName());
                metadata.put("docId", doc.getId().toString());
                metadata.put("chunkId", kc.getId().toString());

                String vectorId = vectorStore.addText(chunkText, metadata);
                kc.setVectorId(vectorId);
                chunkRepo.save(kc);
            }

            doc.setStatus("vectorized");
            docRepo.save(doc);

            // 更新知识库chunk计数
            KnowledgeBase kb = getKB(doc.getKbId());
            if (kb != null) {
                kb.setChunkCount((kb.getChunkCount() != null ? kb.getChunkCount() : 0) + chunks.size());
                kbRepo.save(kb);
            }

            log.info("文档处理完成: {} -> {}个chunk", doc.getFileName(), chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: {}", e.getMessage());
            doc.setStatus("failed");
            docRepo.save(doc);
        }
    }

    public List<KnowledgeDocument> getDocuments(Long kbId) {
        return docRepo.findByKbId(kbId);
    }

    @Transactional
    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = docRepo.findById(docId).orElse(null);
        if (doc == null) return;

        // 删除chunks和向量
        List<KnowledgeChunk> chunks = chunkRepo.findByDocumentId(docId);
        for (KnowledgeChunk c : chunks) {
            if (c.getVectorId() != null) {
                vectorStore.remove(c.getVectorId());
            }
        }
        chunkRepo.deleteAll(chunks);
        docRepo.delete(doc);

        // 更新KB计数
        KnowledgeBase kb = getKB(doc.getKbId());
        if (kb != null) {
            kb.setChunkCount(Math.max(0, (kb.getChunkCount() != null ? kb.getChunkCount() : 0) - chunks.size()));
            kbRepo.save(kb);
        }
    }

    /**
     * 语义搜索
     */
    public List<KnowledgeChunk> search(Long kbId, String query, int topK) {
        // 使用VectorStore进行语义搜索
        List<com.happymouse.cryd.service.rag.VectorStore.SearchResult> results =
            vectorStore.search(query, topK, 0.5);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (var result : results) {
            String chunkId = result.getMetadata().get("chunkId");
            if (chunkId != null) {
                chunkRepo.findById(Long.parseLong(chunkId)).ifPresent(chunk -> {
                    if (chunk.getKbId().equals(kbId)) {
                        chunks.add(chunk);
                    }
                });
            }
        }
        return chunks;
    }

    public List<String> getTags(Long kbId) {
        Set<String> tags = new HashSet<>();
        List<KnowledgeDocument> docs = docRepo.findByKbId(kbId);
        for (KnowledgeDocument doc : docs) {
            if (doc.getTags() != null) {
                Collections.addAll(tags, doc.getTags().split(","));
            }
        }
        return new ArrayList<>(tags);
    }

    private String cleanContent(String content) {
        return content
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".doc")) return "doc";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "ppt";
        return "other";
    }
}
