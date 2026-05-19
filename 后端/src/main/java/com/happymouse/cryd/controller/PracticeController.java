package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.knowledge.KnowledgeBaseService;
import com.happymouse.cryd.service.spark.SparkClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习闯关控制器 — 刷题房+疑难突破合并模块
 * 三区功能：核心区(作业接收与作答)、错题区(自动沉淀与管理)、突破区(多智能体资源生成)
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final ChapterRepository chapterRepository;
    private final ChapterProgressRepository progressRepository;
    private final StudentRepository studentRepository;
    private final SysUserRepository sysUserRepository;
    private final CourseRepository courseRepository;
    private final ErrorNotebookRepository errorRepo;
    private final LearningResourceRepository resourceRepo;
    private final KnowledgeBaseRepository kbRepo;
    private final KnowledgeBaseService kbService;
    private final SparkClient sparkClient;

    public PracticeController(ChapterRepository chapterRepository,
                              ChapterProgressRepository progressRepository,
                              StudentRepository studentRepository,
                              SysUserRepository sysUserRepository,
                              CourseRepository courseRepository,
                              ErrorNotebookRepository errorRepo,
                              LearningResourceRepository resourceRepo,
                              KnowledgeBaseRepository kbRepo,
                              KnowledgeBaseService kbService,
                              SparkClient sparkClient) {
        this.chapterRepository = chapterRepository;
        this.progressRepository = progressRepository;
        this.studentRepository = studentRepository;
        this.sysUserRepository = sysUserRepository;
        this.courseRepository = courseRepository;
        this.errorRepo = errorRepo;
        this.resourceRepo = resourceRepo;
        this.kbRepo = kbRepo;
        this.kbService = kbService;
        this.sparkClient = sparkClient;
    }

    // ==================== 核心区：作业接收与作答 ====================

    /**
     * 获取学生作业列表，按状态分类
     */
    @GetMapping("/assignments/{studentId}")
    public Result<Map<String, Object>> getAssignments(@PathVariable Long studentId) {
        List<ChapterProgress> myProgress = progressRepository.findByStudentId(studentId);
        Map<Long, ChapterProgress> progressMap = new HashMap<>();
        for (ChapterProgress cp : myProgress) {
            progressMap.put(cp.getChapterId(), cp);
        }

        List<Chapter> publishedChapters = chapterRepository.findByStatusOrderByOrderNum("published");
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> completed = new ArrayList<>();
        List<Map<String, Object>> graded = new ArrayList<>();

        for (Chapter ch : publishedChapters) {
            ChapterProgress cp = progressMap.get(ch.getId());
            if (cp == null) continue;

            Map<String, Object> item = buildChapterItem(ch, cp);
            courseRepository.findById(ch.getCourseId()).ifPresent(c -> item.put("courseName", c.getName()));

            String status = cp.getStatus();
            if ("completed".equals(status) && cp.getGradedBy() != null) {
                graded.add(item);
            } else if ("completed".equals(status)) {
                completed.add(item);
            } else {
                pending.add(item);
            }
        }

        // 统计
        long errorCount = errorRepo.countByStudentIdAndStatus(studentId, "active");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAssignments", myProgress.size());
        summary.put("pendingCount", pending.size());
        summary.put("completedCount", completed.size() + graded.size());
        summary.put("gradedCount", graded.size());
        summary.put("errorCount", errorCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("pending", pending);
        result.put("completed", completed);
        result.put("graded", graded);
        return Result.success(result);
    }

    /**
     * 获取关卡题目（给学生答题用）
     * 当题库题目不足5题时，自动触发AI动态出题
     */
    @GetMapping("/assignment/{chapterId}/questions")
    public Result<Map<String, Object>> getQuestions(@PathVariable Long chapterId,
                                                    @RequestParam Long studentId) {
        return chapterRepository.findById(chapterId).map(ch -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chapterId", ch.getId());
            result.put("chapterName", ch.getName());

            // 检查题库是否充足，不足则AI动态生成
            String questionsJson = ch.getQuestions();
            int questionCount = questionsJson != null ? getQuestionCount(questionsJson) : 0;
            if (questionCount < 5) {
                log.info("章节「{}」题库不足(当前{}题)，触发AI动态出题", ch.getName(), questionCount);
                JSONArray aiQuestions = generateQuestionsWithAI(ch);
                if (aiQuestions != null && !aiQuestions.isEmpty()) {
                    JSONArray allQuestions = new JSONArray();
                    if (questionsJson != null && questionCount > 0) {
                        try { allQuestions.addAll(JSON.parseArray(questionsJson)); } catch (Exception ignored) {}
                    }
                    allQuestions.addAll(aiQuestions);
                    questionsJson = allQuestions.toJSONString();
                    ch.setQuestions(questionsJson);
                    chapterRepository.save(ch);
                    log.info("章节「{}」AI动态出题完成: +{}题, 共{}题", ch.getName(), aiQuestions.size(), allQuestions.size());
                } else {
                    log.warn("章节「{}」AI动态出题失败，使用现有{}题", ch.getName(), questionCount);
                }
            }
            result.put("questions", questionsJson);

            progressRepository.findByChapterIdAndStudentId(chapterId, studentId).ifPresent(cp -> {
                result.put("previousAnswers", cp.getAnswers());
                result.put("previousScore", cp.getScore());
                result.put("previousStatus", cp.getStatus());
                result.put("previousFeedback", cp.getFeedback());
            });
            return Result.success(result);
        }).orElse(Result.<Map<String, Object>>error(404, "关卡不存在"));
    }

    /**
     * 提交答案 — 自动评分 + 错题沉淀 + 画像更新
     */
    @PostMapping("/assignment/{chapterId}/submit")
    public Result<Map<String, Object>> submit(@PathVariable Long chapterId,
                                              @RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        String answers = body.get("answers").toString();

        Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) return Result.error(404, "关卡不存在");

        // 更新或创建进度记录
        ChapterProgress cp = progressRepository.findByChapterIdAndStudentId(chapterId, studentId)
                .orElse(new ChapterProgress());
        cp.setChapterId(chapterId);
        cp.setStudentId(studentId);
        cp.setAnswers(answers);
        cp.setStatus("submitted");
        cp.setSubmittedAt(LocalDateTime.now());
        progressRepository.save(cp);

        try {
            JSONArray questions = JSON.parseArray(chapter.getQuestions());
            JSONObject studentAnswers = JSON.parseObject(answers);
            int total = questions.size();
            int correct = 0;
            StringBuilder fb = new StringBuilder();
            List<String> newWeakAreas = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                JSONObject q = questions.getJSONObject(i);
                String type = q.getString("type");
                String correctAnswer = q.getString("answer");
                String studentAnswer = studentAnswers.getString(String.valueOf(i + 1));
                String knowledgePoint = q.getString("knowledgePoint");
                int qIndex = i + 1;

                boolean isCorrect;
                if ("code".equals(type)) {
                    if (studentAnswer != null && studentAnswer.length() >= 10) {
                        String aiFeedback = gradeCodeQuestion(q.getString("content"),
                                correctAnswer, studentAnswer);
                        isCorrect = aiFeedback.contains("通过") || aiFeedback.contains("正确");
                        fb.append(aiFeedback);
                    } else {
                        fb.append("✗第").append(qIndex).append("题(未作答) ");
                        isCorrect = false;
                    }
                } else {
                    isCorrect = studentAnswer != null && correctAnswer != null
                            && correctAnswer.equalsIgnoreCase(studentAnswer.trim());
                    if (isCorrect) {
                        correct++;
                        fb.append("✓第").append(qIndex).append("题 ");
                    } else {
                        fb.append("✗第").append(qIndex).append("题 ");
                    }
                }

                if (isCorrect) {
                    correct++;
                } else {
                    // 错题自动沉淀到 ErrorNotebook
                    saveErrorNotebook(studentId, chapterId, q, studentAnswer, correctAnswer, qIndex);
                    if (knowledgePoint != null && !knowledgePoint.isEmpty()) {
                        newWeakAreas.add(knowledgePoint);
                    }
                }
            }

            int score = (int) Math.round((double) correct / total * 100);
            cp.setScore(score);
            cp.setFeedback(fb.toString());
            cp.setStatus("completed");
            cp.setGradedBy("system");
            cp.setGradedAt(LocalDateTime.now());
            progressRepository.save(cp);

            // 更新学生画像
            updateStudentProfile(studentId, score, newWeakAreas);

            // 每次提交后立即触发AI错题解析（异步，不阻塞响应）
            final Long finalCourseId = chapter.getCourseId();
            final Long finalStudentId = studentId;
            final Long finalChapterId = chapterId;
            new Thread(() -> {
                try {
                    generateAiErrorAnalysis(finalStudentId, finalChapterId, finalCourseId);
                } catch (Exception ex) {
                    log.warn("AI错题解析异步生成失败: {}", ex.getMessage());
                }
            }).start();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", score);
            result.put("correctCount", correct);
            result.put("totalCount", total);
            result.put("feedback", fb.toString());
            return Result.success(result);
        } catch (Exception e) {
            log.error("自动评分失败", e);
            return Result.error(500, "评分失败：" + e.getMessage());
        }
    }

    // ==================== 错题区：自动沉淀与管理 ====================

    /**
     * 获取错题列表，支持筛选
     */
    @GetMapping("/errors/{studentId}")
    public Result<Map<String, Object>> getErrors(@PathVariable Long studentId,
                                                  @RequestParam(required = false) String knowledgePoint,
                                                  @RequestParam(required = false) String errorType,
                                                  @RequestParam(required = false) String status) {
        List<ErrorNotebook> errors;
        if (knowledgePoint != null && !knowledgePoint.isEmpty()) {
            errors = errorRepo.findByStudentIdAndKnowledgePoint(studentId, knowledgePoint);
        } else if (errorType != null && !errorType.isEmpty()) {
            errors = errorRepo.findByStudentIdAndErrorType(studentId, errorType);
        } else if (status != null && !status.isEmpty()) {
            errors = errorRepo.findByStudentIdAndStatus(studentId, status);
        } else {
            errors = errorRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
        }

        // 统计知识点和错误类型分布
        Map<String, Long> kpCount = errors.stream()
                .filter(e -> e.getKnowledgePoint() != null)
                .collect(Collectors.groupingBy(ErrorNotebook::getKnowledgePoint, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> etCount = errors.stream()
                .filter(e -> e.getErrorType() != null)
                .collect(Collectors.groupingBy(ErrorNotebook::getErrorType, LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errors", errors);
        result.put("total", errors.size());
        result.put("knowledgePointDistribution", kpCount);
        result.put("errorTypeDistribution", etCount);
        return Result.success(result);
    }

    /**
     * 标记错题已掌握
     */
    @PostMapping("/errors/{errorId}/resolve")
    public Result<ErrorNotebook> resolveError(@PathVariable Long errorId) {
        return errorRepo.findById(errorId).map(e -> {
            e.setStatus("resolved");
            e.setResolvedAt(LocalDateTime.now());
            errorRepo.save(e);
            return Result.success(e);
        }).orElse(Result.<ErrorNotebook>error(404, "错题不存在"));
    }

    /**
     * 手动添加错题
     */
    @PostMapping("/errors")
    public Result<ErrorNotebook> addError(@RequestBody Map<String, Object> body) {
        ErrorNotebook error = new ErrorNotebook();
        error.setStudentId(toLong(body.get("studentId")));
        error.setQuestion((String) body.get("question"));
        error.setStudentAnswer((String) body.get("studentAnswer"));
        error.setCorrectAnswer((String) body.get("correctAnswer"));
        error.setKnowledgePoint((String) body.get("knowledgePoint"));
        error.setErrorType((String) body.getOrDefault("errorType", "concept"));
        error.setErrorTag((String) body.getOrDefault("errorTag", "手动添加"));
        error.setChapterId(toLong(body.get("chapterId")));
        error.setQuestionIndex(body.get("questionIndex") != null
                ? Integer.valueOf(body.get("questionIndex").toString()) : null);
        errorRepo.save(error);
        return Result.success(error);
    }

    /**
     * 移除错题
     */
    @DeleteMapping("/errors/{errorId}")
    public Result<?> deleteError(@PathVariable Long errorId) {
        errorRepo.deleteById(errorId);
        return Result.success("已移除");
    }

    // ==================== AI动态出题：题库不足时自动生成 ====================

    private static final String AI_QUESTION_GEN_PROMPT = """
        你是C语言程序设计出题专家。请根据给定的章节信息，生成4道选择题和1道代码题。

        必须严格遵守以下要求：
        1. 选择题：4个选项(A/B/C/D)，每个选项有意义且有且仅有一个正确答案
        2. 代码题：有明确的输入输出要求，参考答案是完整可运行的C代码
        3. 每题必须标注知识点(knowledgePoint)和解析(analysis)
        4. 题目难度适中，适合大学本科生水平
        5. 选择题的options必须是包含key和value的对象数组

        必须输出纯JSON数组格式（不要```json标记，不要其他任何文字），格式如下：
        [{"type":"choice","content":"题目","options":[{"key":"A","value":"选项A"},{"key":"B","value":"选项B"},{"key":"C","value":"选项C"},{"key":"D","value":"选项D"}],"answer":"A","knowledgePoint":"知识点","analysis":"解析"},
         {"type":"code","content":"题目描述（含输入输出要求）","answer":"参考答案C代码","knowledgePoint":"知识点","analysis":"解题思路和考点分析"}]
        """;

    /**
     * 当题库题目不足时，AI动态生成题目。返回生成的JSONArray，失败返回null
     */
    private JSONArray generateQuestionsWithAI(Chapter chapter) {
        String chapterName = chapter.getName();
        String chapterDesc = chapter.getDescription() != null ? chapter.getDescription() : "";
        String prompt = AI_QUESTION_GEN_PROMPT
                + "\n\n章节名称：" + chapterName
                + "\n章节描述：" + chapterDesc
                + "\n\n请严格按照上述JSON格式生成题目。";

        try {
            String aiReply = sparkClient.chat(prompt, "请为「" + chapterName + "」生成题目", 0.3f, 2048);
            if (aiReply == null || aiReply.trim().isEmpty()) {
                log.warn("AI出题返回空内容: chapter={}", chapterName);
                return null;
            }
            String jsonStr = extractJsonArray(aiReply);
            if (jsonStr == null) {
                log.warn("AI出题首次解析失败，重试中: chapter={}", chapterName);
                aiReply = sparkClient.chat(prompt + "\n\n【重要】上次格式错误，只输出纯JSON数组，不要任何解释。",
                        "出题重试-" + chapterName, 0.3f, 2048);
                jsonStr = extractJsonArray(aiReply);
            }
            if (jsonStr == null) {
                log.error("AI出题重试仍失败: chapter={}", chapterName);
                return null;
            }
            JSONArray questions = JSON.parseArray(jsonStr);
            log.info("AI动态出题成功: chapter={}, count={}", chapterName, questions.size());
            return questions;
        } catch (Exception e) {
            log.error("AI动态出题异常: chapter={}, error={}", chapterName, e.getMessage());
            return null;
        }
    }

    /**
     * 从AI回复中提取JSON数组（多策略容错，适配 GLM/Spark 输出特性）
     */
    private String extractJsonArray(String raw) {
        if (raw == null) return null;
        String text = raw.trim();

        // 策略1: 去掉markdown标记后提取
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = cleaned.substring(start, end + 1);
            if (isValidJsonArray(candidate)) return candidate;
        }

        // 策略2: 修复常见JSON格式问题 + GLM常见输出修复
        start = text.indexOf('[');
        end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            candidate = candidate.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            // 尾逗号修复
            candidate = candidate.replaceAll(",\\s*]", "]");
            candidate = candidate.replaceAll(",\\s*}", "}");
            // GLM 常见: 字符串值中含未转义换行
            candidate = candidate.replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n");
            if (isValidJsonArray(candidate)) return candidate;
            // GLM 常见: 中文引号混入
            candidate = candidate.replace('“', '"').replace('”', '"');
            candidate = candidate.replace('‘', '\'').replace('’', '\'');
            if (isValidJsonArray(candidate)) return candidate;
        }

        // 策略3: 修复缺失的逗号（GLM有时在数组元素间漏掉逗号）
        start = text.indexOf('[');
        end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            candidate = candidate.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            candidate = candidate.replaceAll("}\\s*\\{", "},{");
            candidate = candidate.replaceAll(",\\s*]", "]");
            candidate = candidate.replaceAll(",\\s*}", "}");
            if (isValidJsonArray(candidate)) return candidate;
        }

        // 策略4: 逐个提取JSON对象组装
        try {
            List<String> objects = new ArrayList<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            boolean inString = false;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '"' && (i == 0 || text.charAt(i - 1) != '\\')) inString = !inString;
                if (!inString) {
                    if (ch == '{') {
                        if (depth == 0) current = new StringBuilder();
                        depth++;
                    }
                    if (depth > 0) current.append(ch);
                    if (ch == '}') {
                        depth--;
                        if (depth == 0) objects.add(current.toString());
                    }
                } else if (depth > 0) {
                    current.append(ch);
                }
            }
            if (!objects.isEmpty()) return "[" + String.join(",", objects) + "]";
        } catch (Exception ignored) {}

        return null;
    }

    private boolean isValidJsonArray(String str) {
        try { JSON.parseArray(str); return true; } catch (Exception e) { return false; }
    }

    // ==================== 突破区：多智能体资源生成 ====================

    private static final Map<String, String> BREAKTHROUGH_PROMPTS = Map.of(
        "explanation", """
            你是「课程导师」，擅长用清晰的语言讲解知识。
            请针对学生做错的题目，生成一份结构化的课程讲解文档。
            要求：
            1. 先指出题目的知识点
            2. 讲解核心概念和原理
            3. 给出典型例题和解析
            4. 总结易错点和解题技巧
            输出格式：Markdown
            """,
        "mindmap", """
            你是「知识架构师」，擅长用思维导图组织知识。
            请针对学生做错的题目，生成一份知识点思维导图。
            要求：
            1. 使用文本缩进表示层级
            2. 中心主题是该知识点
            3. 分支包括：概念、原理、应用、易错点
            输出格式：文本形式的思维导图（用 # ## - 等标记层级）
            """,
        "exercise", """
            你是「出题专家」，擅长设计练习题。
            请针对学生做错的题目，生成3-5道同类练习题。
            要求：
            1. 题型与原题相似
            2. 每题附答案和解析
            3. 难度递进
            输出格式：Markdown
            """,
        "code", """
            你是「实践指导」，擅长设计代码实操案例。
            请针对学生做错的题目，设计与知识点相关的代码实操案例。
            要求：
            1. 提供可运行的代码示例
            2. 代码需有详细注释
            3. 说明运行结果和原理
            输出格式：Markdown（代码块用```语言标记）
            """
    );

    private static final Map<String, String> RESOURCE_TYPE_NAMES = Map.of(
        "explanation", "知识点讲解",
        "mindmap", "思维导图",
        "exercise", "练习题",
        "code", "代码演示"
    );

    /**
     * 一键突破 — 为某道错题生成4类学习资源
     */
    @PostMapping("/errors/{errorId}/breakthrough")
    public Result<Map<String, Object>> breakthrough(@PathVariable Long errorId) {
        ErrorNotebook error = errorRepo.findById(errorId).orElse(null);
        if (error == null) return Result.error(404, "错题不存在");

        String knowledgePoint = error.getKnowledgePoint() != null ? error.getKnowledgePoint() : "";
        String questionContent = error.getQuestion() != null ? error.getQuestion() : "";
        String wrongAnswer = error.getStudentAnswer() != null ? error.getStudentAnswer() : "";
        String correctAnswer = error.getCorrectAnswer() != null ? error.getCorrectAnswer() : "";

        String context = String.format("""
            【题目】%s
            【知识点】%s
            【学生错误答案】%s
            【正确答案】%s
            """, questionContent, knowledgePoint, wrongAnswer, correctAnswer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errorId", errorId);
        result.put("knowledgePoint", knowledgePoint);

        List<Map<String, Object>> resources = new ArrayList<>();
        List<String> resourceTypes = List.of("explanation", "mindmap", "exercise", "code");

        for (String resourceType : resourceTypes) {
            String promptTemplate = BREAKTHROUGH_PROMPTS.get(resourceType);
            if (promptTemplate == null) continue;

            String prompt = promptTemplate + "\n\n" + context;
            try {
                String content = sparkClient.chat(prompt, context, 0.5f, 2048);
                log.info("突破资源生成 [{}] 成功", resourceType);

                // 保存到资源中心
                LearningResource lr = new LearningResource();
                lr.setTitle("【错题突破】" + knowledgePoint + " - " + RESOURCE_TYPE_NAMES.get(resourceType));
                lr.setType(resourceType);
                lr.setKnowledgePoint(knowledgePoint);
                lr.setContent(content);
                lr.setStudentId(error.getStudentId());
                lr.setGeneratedBy("breakthrough");
                lr.setCategory("错题突破生成");
                lr.setTags(knowledgePoint);
                lr.setDifficulty("medium");
                lr.setFavoriteCount(0);
                lr.setCommentCount(0);
                lr.setIsShared("0");
                resourceRepo.save(lr);

                Map<String, Object> resItem = new LinkedHashMap<>();
                resItem.put("resourceType", resourceType);
                resItem.put("resourceName", RESOURCE_TYPE_NAMES.get(resourceType));
                resItem.put("content", content);
                resItem.put("resourceId", lr.getId());
                resources.add(resItem);
            } catch (Exception e) {
                log.error("资源生成失败 [{}]: {}", resourceType, e.getMessage());
                Map<String, Object> resItem = new LinkedHashMap<>();
                resItem.put("resourceType", resourceType);
                resItem.put("resourceName", RESOURCE_TYPE_NAMES.get(resourceType));
                resItem.put("content", "生成失败: " + e.getMessage());
                resources.add(resItem);
            }
        }

        // 标记错题已有突破资源
        error.setAnalysis("已生成突破资源");
        errorRepo.save(error);

        result.put("resources", resources);
        return Result.success(result);
    }

    /**
     * 获取突破记录（从资源中心查询）
     */
    @GetMapping("/breakthrough/history/{studentId}")
    public Result<List<LearningResource>> getBreakthroughHistory(@PathVariable Long studentId) {
        List<LearningResource> all = resourceRepo.findByStudentId(studentId);
        List<LearningResource> history = all.stream()
                .filter(r -> "breakthrough".equals(r.getGeneratedBy()))
                .collect(Collectors.toList());
        return Result.success(history);
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> buildChapterItem(Chapter ch, ChapterProgress cp) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", ch.getId());
        item.put("name", ch.getName());
        item.put("description", ch.getDescription());
        item.put("orderNum", ch.getOrderNum());
        item.put("status", cp.getStatus());
        item.put("score", cp.getScore());
        item.put("feedback", cp.getFeedback());
        item.put("submittedAt", cp.getSubmittedAt());
        item.put("gradedAt", cp.getGradedAt());
        item.put("questionCount", getQuestionCount(ch.getQuestions()));
        return item;
    }

    private void saveErrorNotebook(Long studentId, Long chapterId, JSONObject question,
                                   String studentAnswer, String correctAnswer, int qIndex) {
        // 避免重复添加同一道错题
        if (errorRepo.findByStudentIdAndChapterIdAndQuestionIndex(studentId, chapterId, qIndex).isPresent()) {
            return;
        }
        ErrorNotebook error = new ErrorNotebook();
        error.setStudentId(studentId);
        error.setChapterId(chapterId);
        error.setQuestionIndex(qIndex);
        error.setQuestion(question.getString("content"));
        error.setStudentAnswer(studentAnswer);
        error.setCorrectAnswer(correctAnswer);
        error.setKnowledgePoint(question.getString("knowledgePoint"));
        error.setDifficulty(question.getString("difficulty"));
        error.setStatus("active");
        error.setWrongCount(1);
        // AI 分析错误原因
        String et = analyzeErrorType(question.getString("content"), studentAnswer, correctAnswer);
        error.setErrorType(et);
        error.setErrorTag(mapErrorTag(et));
        errorRepo.save(error);
    }

    private String analyzeErrorType(String question, String studentAnswer, String correctAnswer) {
        String prompt = "分析以下错题的错误类型，只回复一个单词：concept(概念不清)、calculation(计算错误)、misread(审题偏差)、logic(逻辑错误)。\n"
                + "题目：" + (question != null ? question : "") + "\n"
                + "学生答案：" + (studentAnswer != null ? studentAnswer : "") + "\n"
                + "正确答案：" + (correctAnswer != null ? correctAnswer : "");
        try {
            String reply = sparkClient.chat(prompt, "分析错误类型", 0.3f, 50);
            if (reply.contains("concept") || reply.contains("概念")) return "concept";
            if (reply.contains("calculation") || reply.contains("计算")) return "calculation";
            if (reply.contains("misread") || reply.contains("审题")) return "misread";
            if (reply.contains("logic") || reply.contains("逻辑")) return "logic";
            return "concept";
        } catch (Exception e) {
            return "concept";
        }
    }

    private String mapErrorTag(String errorType) {
        return switch (errorType) {
            case "concept" -> "概念不清";
            case "calculation" -> "计算错误";
            case "misread" -> "审题偏差";
            case "logic" -> "代码逻辑错误";
            default -> "概念不清";
        };
    }

    private void updateStudentProfile(Long studentId, int score, List<String> newWeakAreas) {
        String studentUsername = "student_" + studentId;
        studentRepository.findByUsername(studentUsername).ifPresent(student -> {
            // 更新进度
            int currentProgress = student.getProgress() != null ? student.getProgress() : 0;
            student.setProgress(Math.min(100, currentProgress + 5));

            // 更新知识水平
            int currentLevel = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
            int adjustment = score >= 80 ? 3 : score >= 60 ? 1 : -1;
            student.setKnowledgeLevel(Math.max(0, Math.min(100, currentLevel + adjustment)));

            // 更新薄弱环节
            if (!newWeakAreas.isEmpty()) {
                String existingWeak = student.getWeakAreas() != null ? student.getWeakAreas() : "";
                Set<String> areas = new LinkedHashSet<>();
                if (!existingWeak.isEmpty()) {
                    areas.addAll(Arrays.asList(existingWeak.split(",\\s*")));
                }
                areas.addAll(newWeakAreas);
                student.setWeakAreas(String.join(", ", areas));
            }

            // 累加学习时长
            int minutes = student.getTotalStudyMinutes() != null ? student.getTotalStudyMinutes() : 0;
            student.setTotalStudyMinutes(minutes + 15);

            studentRepository.save(student);
            log.info("学生画像已更新: studentId={}, score={}", studentId, score);
        });
    }

    private String gradeCodeQuestion(String questionContent, String referenceAnswer, String studentAnswer) {
        String prompt = "你是C语言代码评阅老师。\n"
            + "题目：" + questionContent + "\n"
            + "参考答案：\n" + referenceAnswer + "\n"
            + "学生代码：\n" + studentAnswer + "\n"
            + "请判断学生代码是否正确实现了题目要求。\n"
            + "如果正确，回复：代码正确，通过！\n"
            + "如果有小问题，回复：基本正确，但有小问题\n"
            + "如果错误，回复：代码未通过\n"
            + "只回复评语，不要重复代码。";
        try {
            String feedback = sparkClient.chat(prompt, studentAnswer, 0.3f, 200);
            return feedback.trim() + " ";
        } catch (Exception e) {
            log.warn("AI代码评阅失败：{}", e.getMessage());
            return "? 代码题待老师评阅 ";
        }
    }

    /**
     * 每次学生提交后立即触发AI错题解析 — 为本次提交产生的错题生成分析
     * 同时生成知识库学习资料，学生可在刷题房「AI错题解析」Tab查看
     */
    private void generateAiErrorAnalysis(Long studentId, Long chapterId, Long courseId) {
        try {
            // 获取该学生在该章节的所有活跃错题
            List<ErrorNotebook> errors = errorRepo.findByStudentIdAndChapterId(studentId, chapterId);
            if (errors.isEmpty()) {
                log.info("学生{}在章节{}无错题，跳过AI解析", studentId, chapterId);
                return;
            }

            // 过滤出还没有AI分析的错题
            List<ErrorNotebook> unanalyzed = errors.stream()
                    .filter(e -> e.getAnalysis() == null || e.getAnalysis().isEmpty()
                            || !e.getAnalysis().contains("AI解析"))
                    .toList();

            if (unanalyzed.isEmpty()) {
                log.info("学生{}在章节{}的错题都已有AI解析", studentId, chapterId);
                return;
            }

            log.info("开始为studentId={}的{}道错题生成AI解析", studentId, unanalyzed.size());

            // 为每道未分析的错题生成AI解析
            for (ErrorNotebook error : unanalyzed) {
                try {
                    String analysisPrompt = String.format("""
                        你是C语言教学专家。学生做错了以下题目，请分析错误原因并给出学习建议。

                        【题目】%s
                        【知识点】%s
                        【学生答案】%s
                        【正确答案】%s

                        请按以下格式回复（200字以内）：
                        错误原因：[分析学生为什么会错]
                        正确思路：[讲解正确的解题思路]
                        学习建议：[给出针对性的学习建议]
                        """,
                        error.getQuestion() != null ? error.getQuestion() : "",
                        error.getKnowledgePoint() != null ? error.getKnowledgePoint() : "",
                        error.getStudentAnswer() != null ? error.getStudentAnswer() : "",
                        error.getCorrectAnswer() != null ? error.getCorrectAnswer() : "");

                    String aiAnalysis = sparkClient.chat(analysisPrompt,
                            "分析错题-" + error.getKnowledgePoint(), 0.3f, 200);
                    if (aiAnalysis != null && !aiAnalysis.trim().isEmpty()) {
                        error.setAnalysis("【AI解析】\n" + aiAnalysis.trim());
                        errorRepo.save(error);
                        log.info("错题{} AI解析生成成功", error.getId());
                    }
                } catch (Exception ex) {
                    log.warn("错题{} AI解析生成失败: {}", error.getId(), ex.getMessage());
                }
            }

            // 汇总错题知识点，生成知识库学习资料
            Map<String, List<ErrorNotebook>> byKnowledgePoint = unanalyzed.stream()
                    .filter(e -> e.getKnowledgePoint() != null && !e.getKnowledgePoint().isEmpty())
                    .collect(Collectors.groupingBy(ErrorNotebook::getKnowledgePoint));

            for (var entry : byKnowledgePoint.entrySet()) {
                try {
                    String kp = entry.getKey();
                    List<ErrorNotebook> kpErrors = entry.getValue();

                    StringBuilder errorSummary = new StringBuilder();
                    for (ErrorNotebook e : kpErrors) {
                        errorSummary.append("- 题目：").append(e.getQuestion() != null ? e.getQuestion() : "")
                                .append("\n  学生答案：").append(e.getStudentAnswer() != null ? e.getStudentAnswer() : "")
                                .append("\n  正确答案：").append(e.getCorrectAnswer() != null ? e.getCorrectAnswer() : "")
                                .append("\n");
                    }

                    String kbPrompt = String.format("""
                        你是C语言教学专家。请针对学生的高频错误知识点，生成一份学习资料。

                        【知识点】%s
                        【错误次数】%d次
                        【错误详情】
                        %s

                        请生成一份Markdown格式的学习资料，包括：
                        1. 概念讲解
                        2. 常见错误示例
                        3. 正确做法
                        4. 练习题（1-2道）
                        """, kp, kpErrors.size(), errorSummary.toString());

                    String kbContent = sparkClient.chat(kbPrompt, "学习资料-" + kp, 0.3f, 1500);
                    if (kbContent != null && !kbContent.trim().isEmpty() && courseId != null) {
                        // 保存到知识库
                        var kb = kbRepo.findByCourseId(courseId).stream().findFirst()
                                .orElseGet(() -> {
                                    var newKb = new KnowledgeBase();
                                    newKb.setCourseId(courseId);
                                    newKb.setName("错题自动生成");
                                    newKb.setDescription("学生错题驱动AI自动生成");
                                    return kbRepo.save(newKb);
                                });
                        kbService.uploadTextDocument(kb.getId(),
                                "【错题解析】" + kp + ".md", kbContent);
                        log.info("知识库资料生成成功: {}", kp);
                    }
                } catch (Exception ex) {
                    log.warn("知识库资料生成失败[{}]: {}", entry.getKey(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("AI错题解析整体流程失败: {}", ex.getMessage());
        }
    }

    private int getQuestionCount(String questionsJson) {
        try { return JSON.parseArray(questionsJson).size(); } catch (Exception e) { return 0; }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String && !((String) val).isEmpty()) return Long.parseLong((String) val);
        return null;
    }
}
