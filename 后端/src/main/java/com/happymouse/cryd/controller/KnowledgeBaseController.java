package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.KnowledgeBase;
import com.happymouse.cryd.model.entity.KnowledgeDocument;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.KnowledgeBaseRepository;
import com.happymouse.cryd.service.knowledge.KnowledgeBaseService;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);
    private final KnowledgeBaseService kbService;
    private final ErrorNotebookRepository errorRepo;
    private final KnowledgeBaseRepository kbRepo;
    private final SparkClient sparkClient;

    public KnowledgeBaseController(KnowledgeBaseService kbService,
                                    ErrorNotebookRepository errorRepo,
                                    KnowledgeBaseRepository kbRepo,
                                    SparkClient sparkClient) {
        this.kbService = kbService;
        this.errorRepo = errorRepo;
        this.kbRepo = kbRepo;
        this.sparkClient = sparkClient;
    }

    // 创建知识库
    @PostMapping
    public Result<KnowledgeBase> create(@RequestBody Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        Long teacherId = toLong(body.get("teacherId"));
        String name = (String) body.get("name");
        String desc = (String) body.get("description");
        return Result.success(kbService.createKB(courseId, teacherId, name, desc));
    }

    // 获取知识库详情
    @GetMapping("/{kbId}")
    public Result<KnowledgeBase> getById(@PathVariable Long kbId) {
        return Result.success(kbService.getKB(kbId));
    }

    // 按课程获取知识库
    @GetMapping("/course/{courseId}")
    public Result<List<KnowledgeBase>> getByCourse(@PathVariable Long courseId) {
        return Result.success(kbService.getByCourse(courseId));
    }

    // 按教师获取知识库
    @GetMapping("/teacher/{teacherId}")
    public Result<List<KnowledgeBase>> getByTeacher(@PathVariable Long teacherId) {
        return Result.success(kbService.getByTeacher(teacherId));
    }

    // 上传文档
    @PostMapping("/{kbId}/documents")
    public Result<KnowledgeDocument> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file) {
        try {
            KnowledgeDocument doc = kbService.uploadDocument(kbId, file);
            return Result.success(doc);
        } catch (Exception e) {
            log.error("上传文档失败: {}", e.getMessage());
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    // 上传文本内容作为文档
    @PostMapping("/{kbId}/documents/text")
    public Result<KnowledgeDocument> uploadTextDocument(
            @PathVariable Long kbId,
            @RequestBody Map<String, String> body) {
        String fileName = body.getOrDefault("fileName", "untitled.txt");
        String content = body.get("content");
        return Result.success(kbService.uploadTextDocument(kbId, fileName, content));
    }

    // 获取文档列表
    @GetMapping("/{kbId}/documents")
    public Result<List<KnowledgeDocument>> getDocuments(@PathVariable Long kbId) {
        return Result.success(kbService.getDocuments(kbId));
    }

    // 删除文档
    @DeleteMapping("/{kbId}/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        kbService.deleteDocument(docId);
        return Result.success(null);
    }

    // 搜索
    @GetMapping("/{kbId}/search")
    public Result<?> search(@PathVariable Long kbId,
                             @RequestParam("q") String query) {
        return Result.success(kbService.search(kbId, query, 5));
    }

    // 标签列表
    @GetMapping("/{kbId}/tags")
    public Result<List<String>> getTags(@PathVariable Long kbId) {
        return Result.success(kbService.getTags(kbId));
    }

    // 从错题自动生成知识库资料
    @PostMapping("/auto-generate/{courseId}")
    public Result<Map<String, Object>> autoGenerate(@PathVariable Long courseId) {
        // 查找该课程的知识库（或创建）
        KnowledgeBase kb = kbRepo.findByCourseId(courseId).stream().findFirst()
                .orElseGet(() -> {
                    KnowledgeBase newKb = new KnowledgeBase();
                    newKb.setCourseId(courseId);
                    newKb.setName("错题自动生成知识库");
                    newKb.setDescription("由学生错题驱动AI自动生成的知识资料");
                    return kbRepo.save(newKb);
                });

        // 从所有学生错题聚合高频错误知识点
        List<ErrorNotebook> allErrors = errorRepo.findAll();
        Map<String, Long> kpCount = new LinkedHashMap<>();
        for (ErrorNotebook e : allErrors) {
            if (e.getKnowledgePoint() != null && "active".equals(e.getStatus())) {
                kpCount.merge(e.getKnowledgePoint(), 1L, Long::sum);
            }
        }

        // 取错误次数≥3的高频知识点
        List<String> hotTopics = kpCount.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (hotTopics.isEmpty()) {
            return Result.success(Map.of("message", "暂无高频错误知识点（需≥3次），继续积累中", "topicsGenerated", 0));
        }

        int generatedCount = 0;
        for (String topic : hotTopics) {
            try {
                String prompt = """
                    你是C语言教学专家。请针对以下高频错误知识点，生成一份学习资料。

                    要求：
                    1. 知识点名称和概念讲解
                    2. 典型错误示例和正确做法
                    3. 2-3道相关练习题（含答案和解析）
                    4. 记忆口诀或解题技巧

                    知识点：%s
                    错误次数：%d次

                    输出格式：Markdown
                    """.formatted(topic, kpCount.get(topic));

                String content = sparkClient.chat(prompt, "知识点-" + topic, 0.3f, 1500);

                kbService.uploadTextDocument(kb.getId(), "【错题突破】" + topic + ".md", content);
                generatedCount++;
                log.info("AI已生成知识库资料: {}", topic);
            } catch (Exception e) {
                log.warn("生成知识库资料失败 [{}]: {}", topic, e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicsGenerated", generatedCount);
        result.put("hotTopics", hotTopics);
        result.put("message", "AI已为 " + generatedCount + " 个高频错误知识点生成学习资料");
        return Result.success(result);
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
