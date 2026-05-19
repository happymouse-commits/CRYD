package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.TestRecord;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.TestRecordRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学习评估控制器 — 在线测试、错题分析、学习报告、薄弱点推荐
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final TestRecordRepository testRepo;
    private final ErrorNotebookRepository errorRepo;
    private final SparkClient sparkClient;

    public EvaluationController(TestRecordRepository testRepo,
                                 ErrorNotebookRepository errorRepo,
                                 SparkClient sparkClient) {
        this.testRepo = testRepo;
        this.errorRepo = errorRepo;
        this.sparkClient = sparkClient;
    }

    // 生成在线测试
    @PostMapping("/test/generate")
    public Result<TestRecord> generateTest(@RequestBody Map<String, Object> body) {
        Long studentId = toLong(body.get("studentId"));
        String topic = (String) body.getOrDefault("topic", "C语言综合");
        int questionCount = (int) body.getOrDefault("questionCount", 10);

        String prompt = "请生成" + questionCount + "道" + topic + "的测试题，包含选择题和填空题。" +
            "以JSON数组格式输出: [{\"type\":\"choice\",\"question\":\"...\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":\"A\"}]";

        try {
            String questionsJson = sparkClient.chat(prompt, "", 0.3f, 2048);
            String json = questionsJson.replaceAll("```json|```", "").trim();

            TestRecord test = new TestRecord();
            test.setStudentId(studentId);
            test.setTestName(topic + "测试");
            test.setQuestions(json);
            test.setStatus("in_progress");
            testRepo.save(test);

            return Result.success(test);
        } catch (Exception e) {
            return Result.error("测试生成失败: " + e.getMessage());
        }
    }

    // 提交测试
    @PostMapping("/test/{testId}/submit")
    public Result<TestRecord> submitTest(@PathVariable Long testId, @RequestBody Map<String, Object> body) {
        TestRecord test = testRepo.findById(testId).orElse(null);
        if (test == null) return Result.error("测试不存在");

        String answers = (String) body.get("answers");
        test.setAnswers(answers);
        test.setStatus("completed");
        test.setCompletedAt(LocalDateTime.now());
        testRepo.save(test);

        return Result.success(test);
    }

    // 获取测试记录
    @GetMapping("/test/student/{studentId}")
    public Result<List<TestRecord>> getTests(@PathVariable Long studentId) {
        return Result.success(testRepo.findByStudentIdOrderByStartedAtDesc(studentId));
    }

    // 获取错题本
    @GetMapping("/errors/student/{studentId}")
    public Result<List<ErrorNotebook>> getErrors(@PathVariable Long studentId) {
        return Result.success(errorRepo.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    // 添加错题
    @PostMapping("/errors")
    public Result<ErrorNotebook> addError(@RequestBody Map<String, Object> body) {
        ErrorNotebook error = new ErrorNotebook();
        error.setStudentId(toLong(body.get("studentId")));
        error.setQuestion((String) body.get("question"));
        error.setStudentAnswer((String) body.get("studentAnswer"));
        error.setCorrectAnswer((String) body.get("correctAnswer"));
        error.setKnowledgePoint((String) body.get("knowledgePoint"));
        error.setDifficulty((String) body.get("difficulty"));
        errorRepo.save(error);
        return Result.success(error);
    }

    // 错题分析
    @PostMapping("/errors/{errorId}/analyze")
    public Result<ErrorNotebook> analyzeError(@PathVariable Long errorId) {
        ErrorNotebook error = errorRepo.findById(errorId).orElse(null);
        if (error == null) return Result.error("错题不存在");

        String prompt = "分析这道C语言错题，给出错误原因和学习建议。"
            + "题目:" + error.getQuestion() + ", 学生答案:" + error.getStudentAnswer()
            + ", 正确答案:" + error.getCorrectAnswer();

        try {
            String analysis = sparkClient.chat(prompt, "", 0.3f, 256);
            error.setAnalysis(analysis);
            errorRepo.save(error);
            return Result.success(error);
        } catch (Exception e) {
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    // 薄弱点推荐
    @GetMapping("/weakness/{studentId}")
    public Result<?> getWeakness(@PathVariable Long studentId) {
        List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(studentId, "active");
        // 统计每个知识点的错误次数
        Map<String, Long> freq = new java.util.LinkedHashMap<>();
        for (ErrorNotebook e : errors) {
            String kp = e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知";
            freq.merge(kp, 1L, Long::sum);
        }
        // 按频率排序
        var sorted = freq.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(e -> Map.of("knowledgePoint", e.getKey(), "errorCount", e.getValue()))
            .toList();

        return Result.success(Map.of("weakPoints", sorted, "total", errors.size()));
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
