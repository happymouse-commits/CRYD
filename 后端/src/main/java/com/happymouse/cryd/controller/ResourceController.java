package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.ResourceComment;
import com.happymouse.cryd.model.entity.ResourceFavorite;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 资源中心控制器 — 资源管理、分类、搜索、收藏、评论、导出、AI生成
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);

    private final LearningResourceRepository resourceRepo;
    private final ResourceFavoriteRepository favRepo;
    private final ResourceCommentRepository commentRepo;
    private final ErrorNotebookRepository errorRepo;
    private final StudentRepository studentRepo;
    private final SparkClient sparkClient;

    public ResourceController(LearningResourceRepository resourceRepo,
                               ResourceFavoriteRepository favRepo,
                               ResourceCommentRepository commentRepo,
                               ErrorNotebookRepository errorRepo,
                               StudentRepository studentRepo,
                               SparkClient sparkClient) {
        this.resourceRepo = resourceRepo;
        this.favRepo = favRepo;
        this.commentRepo = commentRepo;
        this.errorRepo = errorRepo;
        this.studentRepo = studentRepo;
        this.sparkClient = sparkClient;
    }

    @GetMapping("/student/{studentId}")
    public Result<List<LearningResource>> getByStudent(@PathVariable Long studentId) {
        return Result.success(resourceRepo.findByStudentId(studentId));
    }

    @GetMapping("/student/{studentId}/type/{type}")
    public Result<List<LearningResource>> getByType(@PathVariable Long studentId, @PathVariable String type) {
        return Result.success(resourceRepo.findByStudentIdAndType(studentId, type));
    }

    @GetMapping("/{id}")
    public Result<LearningResource> getById(@PathVariable Long id) {
        return Result.success(resourceRepo.findById(id).orElse(null));
    }

    @PostMapping("/{id}/favorite")
    public Result<?> favorite(@PathVariable Long id, @RequestParam Long userId) {
        var existing = favRepo.findByResourceIdAndUserId(id, userId);
        if (existing.isPresent()) {
            favRepo.delete(existing.get());
            return Result.success(Map.of("favorited", false));
        }
        var fav = new ResourceFavorite();
        fav.setResourceId(id);
        fav.setUserId(userId);
        favRepo.save(fav);
        return Result.success(Map.of("favorited", true));
    }

    @GetMapping("/{id}/favorite/check")
    public Result<?> checkFavorite(@PathVariable Long id, @RequestParam Long userId) {
        return Result.success(Map.of("favorited", favRepo.findByResourceIdAndUserId(id, userId).isPresent()));
    }

    @GetMapping("/{id}/comments")
    public Result<List<ResourceComment>> getComments(@PathVariable Long id) {
        return Result.success(commentRepo.findByResourceIdOrderByCreatedAtDesc(id));
    }

    @PostMapping("/{id}/comments")
    public Result<ResourceComment> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ResourceComment comment = new ResourceComment();
        comment.setResourceId(id);
        comment.setUserId(toLong(body.get("userId")));
        comment.setUserName((String) body.get("userName"));
        comment.setContent((String) body.get("content"));
        return Result.success(commentRepo.save(comment));
    }

    @GetMapping("/{id}/export")
    public Result<?> exportResource(@PathVariable Long id) {
        var resource = resourceRepo.findById(id).orElse(null);
        if (resource == null) return Result.error("资源不存在");
        String exportContent = "# " + resource.getTitle() + "\n\n" +
            "**类型**: " + resource.getType() + "\n" +
            "**难度**: " + resource.getDifficulty() + "\n" +
            "**知识点**: " + resource.getKnowledgePoint() + "\n\n---\n\n" +
            resource.getContent();
        return Result.success(Map.of("content", exportContent, "title", resource.getTitle()));
    }

    // ==================== AI 生成资源 ====================

    private static final String RESOURCE_GEN_PROMPT = """
        你是C语言辅导老师。根据学生错题涉及的知识点，生成3-5篇个性化辅导资料。
        每个资料包含：title(String)、type(String: article/video/exercise)、
        content(String/Markdown正文，100-300字即可)、knowledgePoint(String)、
        difficulty(String: easy/medium/hard)。
        只输出纯JSON数组，不要```json标记，不要其他任何文字。

        学生错题知识点：%s
        薄弱环节：%s
        """;

    @PostMapping("/generate/{studentId}")
    public Result<?> generate(@PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        try {
            // 1. 收集学生错题知识点
            List<ErrorNotebook> errors = errorRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
            Set<String> knowledgePoints = new LinkedHashSet<>();
            for (ErrorNotebook e : errors) {
                if (e.getKnowledgePoint() != null && !e.getKnowledgePoint().isEmpty()) {
                    knowledgePoints.add(e.getKnowledgePoint());
                }
            }

            // 2. 获取薄弱环节
            String weakAreas = "";
            var studentOpt = studentRepo.findByUsername("student_" + studentId);
            if (studentOpt.isPresent()) {
                Student s = studentOpt.get();
                weakAreas = s.getWeakAreas() != null ? s.getWeakAreas() : "";
            }

            String kpList = knowledgePoints.isEmpty() ? "C语言基础" : String.join("、", knowledgePoints);
            if (kpList.length() > 200) kpList = kpList.substring(0, 200);

            // 3. 调用AI生成
            String prompt = String.format(RESOURCE_GEN_PROMPT, kpList,
                    weakAreas.isEmpty() ? "暂无数据" : weakAreas);
            String aiReply = sparkClient.chat(prompt, "生成学习资源", 0.4f, 1500);

            if (aiReply == null || aiReply.trim().isEmpty()) {
                return Result.error("AI生成失败，请重试");
            }

            // 4. 解析JSON并保存
            JSONArray generated = parseResourceJson(aiReply);
            if (generated == null || generated.isEmpty()) {
                return Result.error("AI返回格式异常，请重试");
            }

            List<LearningResource> saved = new ArrayList<>();
            for (int i = 0; i < generated.size(); i++) {
                JSONObject obj = generated.getJSONObject(i);
                LearningResource lr = new LearningResource();
                lr.setTitle(obj.getString("title"));
                lr.setType(obj.getString("type"));
                lr.setContent(obj.getString("content"));
                lr.setKnowledgePoint(obj.getString("knowledgePoint"));
                lr.setDifficulty(obj.getString("difficulty"));
                lr.setStudentId(studentId);
                lr.setGeneratedBy("ai-resource-center");
                lr.setCategory("AI个性化生成");
                lr.setTags(obj.getString("knowledgePoint"));
                lr.setFavoriteCount(0);
                lr.setCommentCount(0);
                lr.setIsShared("0");
                saved.add(resourceRepo.save(lr));
            }

            log.info("AI资源生成完成: studentId={}, count={}", studentId, saved.size());
            return Result.success(saved);
        } catch (Exception e) {
            log.error("AI资源生成失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    private JSONArray parseResourceJson(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        // 去掉markdown标记
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = cleaned.substring(start, end + 1);
            candidate = candidate.replaceAll(",\\s*]", "]").replaceAll(",\\s*}", "}");
            try { return JSON.parseArray(candidate); } catch (Exception ignored) {}
        }
        // 策略2: 整个文本
        try { return JSON.parseArray(text); } catch (Exception ignored) {}
        return null;
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
