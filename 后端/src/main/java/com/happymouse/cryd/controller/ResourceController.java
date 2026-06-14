package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.OnboardingService;
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
    private final ErrorNotebookRepository errorRepo;
    private final StudentRepository studentRepo;
    private final SparkClient sparkClient;
    private final OnboardingService onboardingService;

    public ResourceController(LearningResourceRepository resourceRepo,
                               ErrorNotebookRepository errorRepo,
                               StudentRepository studentRepo,
                               SparkClient sparkClient,
                               OnboardingService onboardingService) {
        this.resourceRepo = resourceRepo;
        this.errorRepo = errorRepo;
        this.studentRepo = studentRepo;
        this.sparkClient = sparkClient;
        this.onboardingService = onboardingService;
    }

    /** 监控：未完成AI导学不能查看资源 */
    private boolean isOnboardingDone(Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        if (s == null) return false;
        return onboardingService.calcCompleteness(s) >= 80;
    }

    @GetMapping("/student/{studentId}")
    public Result<List<LearningResource>> getByStudent(@PathVariable Long studentId) {
        if (!isOnboardingDone(studentId))
            return Result.error(403, "请先完成AI导学，让我了解你的学习情况再生成资源");
        return Result.success(resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    @DeleteMapping("/student/{studentId}/clear")
    public Result<?> clearByStudent(@PathVariable Long studentId) {
        long count = resourceRepo.findByStudentIdOrderByCreatedAtDesc(studentId).size();
        resourceRepo.deleteAllByStudentId(studentId);
        log.info("清空学生{}资源: {}条", studentId, count);
        return Result.success("已清空 " + count + " 条资源");
    }

    @GetMapping("/student/{studentId}/type/{type}")
    public Result<List<LearningResource>> getByType(@PathVariable Long studentId, @PathVariable String type) {
        return Result.success(resourceRepo.findByStudentIdAndTypeOrderByCreatedAtDesc(studentId, type));
    }

    @GetMapping("/{id}")
    public Result<LearningResource> getById(@PathVariable Long id) {
        return Result.success(resourceRepo.findById(id).orElse(null));
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
        你是C语言辅导老师。根据学生错题涉及的知识点，生成5篇个性化辅导资料。

        每个资料包含：
        - title(String): 标题
        - type(String): article/exercise/explanation/mindmap/code 各1篇，共5篇
        - content(String): Markdown正文。要求如下：
          * article 类型：用 ## 分段标题，每段配文字说明，穿插代码示例(```c)，至少3个段落
          * exercise 类型：出3道练习题，每题用 ### 编号，含题干、代码示例(```c)、答案和解析
          * explanation 类型：用 ## 分知识点小节讲解，配对比表格、代码示例(```c)
          * mindmap 类型：用 ## 分主题 + 无序列表(- ) 列出知识树
          * code 类型：给出完整可运行示例，含注释，用 ```c 包裹
          * 所有类型：多用 - 无序列表、1. 有序列表、> 引用块、**加粗** 来增强可读性
          * 字数300-500字以上，禁止一大坨纯文本
        - knowledgePoint(String)
        - difficulty(String: easy/medium/hard)

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
            String aiReply = sparkClient.chat(prompt, "生成学习资源", 0.4f, 8192);

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
                lr.setIsShared("0");
                saved.add(resourceRepo.save(lr));
            }

            // 5. 附带B站视频资源
            resourceRepo.deleteByStudentIdAndGeneratedBy(studentId, "video-library");
            List<LearningResource> videos = buildVideoResources(studentId);
            videos.forEach(resourceRepo::save);
            saved.addAll(videos);

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

    private List<LearningResource> buildVideoResources(Long studentId) {
        String bvid = "BV1dr4y1n7vA";
        Object[][] data = {
            {"C语言变量定义与使用", "12", "变量", "easy"},
            {"C语言表达式与运算符", "17", "运算符", "easy"},
            {"C语言if判断语句", "25", "if语句", "easy"},
            {"C语言switch多路分支", "32", "switch", "medium"},
            {"C语言while循环", "34", "while循环", "medium"},
            {"C语言for循环入门", "39", "for循环", "medium"},
        };
        List<LearningResource> list = new ArrayList<>();
        for (Object[] v : data) {
            LearningResource lr = new LearningResource();
            lr.setTitle((String) v[0]);
            lr.setType("video");
            lr.setContent("https://player.bilibili.com/player.html?bvid=" + bvid + "&page=" + v[1]);
            lr.setKnowledgePoint((String) v[2]);
            lr.setDifficulty((String) v[3]);
            lr.setStudentId(studentId);
            lr.setCategory("B站视频教程");
            lr.setGeneratedBy("video-library");
            lr.setIsShared("1");
            list.add(lr);
        }
        return list;
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}