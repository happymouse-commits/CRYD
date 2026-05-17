package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.*;
import com.happymouse.cryd.service.spark.SparkClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 班级学情分析控制器 — 班级概览、个体画像、对比分析、PDF导出
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final SysUserRepository sysUserRepo;
    private final StudentRepository studentRepo;
    private final ChapterProgressRepository progressRepo;
    private final LearningResourceRepository resourceRepo;
    private final ErrorNotebookRepository errorRepo;
    private final SparkClient sparkClient;

    public AnalyticsController(SysUserRepository sysUserRepo,
                                StudentRepository studentRepo,
                                ChapterProgressRepository progressRepo,
                                LearningResourceRepository resourceRepo,
                                ErrorNotebookRepository errorRepo,
                                SparkClient sparkClient) {
        this.sysUserRepo = sysUserRepo;
        this.studentRepo = studentRepo;
        this.progressRepo = progressRepo;
        this.resourceRepo = resourceRepo;
        this.errorRepo = errorRepo;
        this.sparkClient = sparkClient;
    }

    // 班级整体学情概览
    @GetMapping("/class/{className}/overview")
    public Result<Map<String, Object>> getClassOverview(@PathVariable String className) {
        var students = sysUserRepo.findByClassName(className).stream()
            .filter(u -> "student".equals(u.getRole()))
            .toList();

        List<Student> profiles = new ArrayList<>();
        for (var user : students) {
            studentRepo.findByUsername("student_" + user.getId()).ifPresent(profiles::add);
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("className", className);
        overview.put("totalStudents", students.size());

        if (profiles.isEmpty()) {
            overview.put("avgKnowledgeLevel", 0);
            overview.put("avgProgress", 0);
            overview.put("errorTop10", List.of());
            return Result.success(overview);
        }

        double avgLevel = profiles.stream()
            .mapToInt(s -> s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 0)
            .average().orElse(0);
        double avgProgress = profiles.stream()
            .mapToInt(s -> s.getProgress() != null ? s.getProgress() : 0)
            .average().orElse(0);

        // 认知风格分布
        Map<String, Long> styleDist = profiles.stream()
            .collect(Collectors.groupingBy(
                s -> s.getCognitiveStyle() != null ? s.getCognitiveStyle() : "unknown",
                Collectors.counting()));

        // 资源使用统计
        long totalResources = 0;
        for (var user : students) {
            totalResources += resourceRepo.findByStudentId(user.getId()).size();
        }

        overview.put("avgKnowledgeLevel", Math.round(avgLevel * 10) / 10.0);
        overview.put("avgProgress", Math.round(avgProgress * 10) / 10.0);
        overview.put("cognitiveStyleDistribution", styleDist);
        overview.put("totalResourcesGenerated", totalResources);
        overview.put("errorTop10", getTopErrors(className, 10));

        return Result.success(overview);
    }

    // 老师查看学生个体画像
    @GetMapping("/student/{studentId}/profile")
    public Result<Map<String, Object>> getStudentProfile(@PathVariable Long studentId) {
        var user = sysUserRepo.findById(studentId).orElse(null);
        var student = studentRepo.findByUsername("student_" + studentId).orElse(null);

        Map<String, Object> profile = new LinkedHashMap<>();
        if (user != null) {
            profile.put("username", user.getUsername());
            profile.put("nickname", user.getNickname());
            profile.put("className", user.getClassName());
            profile.put("studentId", user.getStudentId());
        }
        if (student != null) {
            profile.put("knowledgeLevel", student.getKnowledgeLevel());
            profile.put("cognitiveStyle", student.getCognitiveStyle());
            profile.put("learningPreference", student.getLearningPreference());
            profile.put("learningPace", student.getLearningPace());
            profile.put("weakAreas", student.getWeakAreas());
            profile.put("progress", student.getProgress());
            profile.put("totalStudyMinutes", student.getTotalStudyMinutes());
            profile.put("streakDays", student.getStreakDays());
        }
        return Result.success(profile);
    }

    // 对比分析
    @GetMapping("/comparison")
    public Result<?> getComparison(
            @RequestParam(required = false) String type, // "horizontal" or "vertical"
            @RequestParam(required = false) String className,
            @RequestParam(required = false) Long studentId) {

        if ("horizontal".equals(type) && className != null) {
            // 横向对比：同班级学生按知识点
            var students = sysUserRepo.findByClassName(className);
            List<Map<String, Object>> data = new ArrayList<>();
            for (var user : students) {
                if (!"student".equals(user.getRole())) continue;
                var profile = studentRepo.findByUsername("student_" + user.getId()).orElse(null);
                if (profile != null) {
                    data.add(Map.of(
                        "name", user.getNickname() != null ? user.getNickname() : user.getUsername(),
                        "level", profile.getKnowledgeLevel() != null ? profile.getKnowledgeLevel() : 0,
                        "progress", profile.getProgress() != null ? profile.getProgress() : 0
                    ));
                }
            }
            return Result.success(Map.of("type", "horizontal", "data", data));
        } else if ("vertical".equals(type) && studentId != null) {
            // 纵向对比：同一学生不同时间段
            return Result.success(Map.of("type", "vertical", "studentId", studentId, "data", List.of()));
        }

        return Result.error("请指定type(horizontal/vertical)和相应参数");
    }

    // AI班级学情分析
    @GetMapping("/class/{className}/ai-analysis")
    public Result<?> getAiAnalysis(@PathVariable String className) {
        var overview = (Map<String, Object>) getClassOverview(className).getData();

        StringBuilder context = new StringBuilder();
        context.append("班级: ").append(className).append("\n");
        context.append("平均知识水平: ").append(overview.get("avgKnowledgeLevel")).append("\n");
        context.append("平均进度: ").append(overview.get("avgProgress")).append("\n");

        try {
            String analysis = sparkClient.chat(
                "你是教学分析专家，请根据以下班级数据给出学情分析和教学建议。",
                context.toString(), 0.5f, 1024);
            return Result.success(Map.of("analysis", analysis, "overview", overview));
        } catch (Exception e) {
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    // 班级学生答题详情列表
    @GetMapping("/class/{className}/students")
    public Result<List<Map<String, Object>>> getClassStudentDetails(@PathVariable String className) {
        List<com.happymouse.cryd.model.entity.SysUser> students = sysUserRepo.findByClassName(className).stream()
                .filter(u -> "student".equals(u.getRole()))
                .toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (com.happymouse.cryd.model.entity.SysUser user : students) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", user.getStudentId() != null ? user.getStudentId() : String.valueOf(user.getId()));
            item.put("sysUserId", user.getId());
            item.put("studentName", user.getNickname() != null ? user.getNickname() : user.getUsername());
            item.put("username", user.getUsername());

            // 统计答题情况
            List<com.happymouse.cryd.model.entity.ChapterProgress> allProgress =
                    progressRepo.findByStudentId(user.getId());
            int totalQuestions = allProgress.size() * 5; // 每章约5题
            long gradedCount = allProgress.stream().filter(p -> p.getScore() != null).count();
            double avgScore = allProgress.stream()
                    .filter(p -> p.getScore() != null)
                    .mapToInt(com.happymouse.cryd.model.entity.ChapterProgress::getScore)
                    .average().orElse(0);
            long correctRate = Math.round(avgScore);

            // 获取薄弱知识点（从错题本）
            List<com.happymouse.cryd.model.entity.ErrorNotebook> errors =
                    errorRepo.findByStudentIdAndStatus(user.getId(), "active");
            List<String> weakPoints = errors.stream()
                    .map(e -> e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知")
                    .distinct().limit(5).toList();

            // 最近活跃时间
            com.happymouse.cryd.model.entity.ChapterProgress latest = allProgress.stream()
                    .filter(p -> p.getSubmittedAt() != null)
                    .max((a, b) -> a.getSubmittedAt().compareTo(b.getSubmittedAt()))
                    .orElse(null);
            String lastActive = latest != null ? latest.getSubmittedAt().toString() : "暂无";

            item.put("totalQuestions", totalQuestions);
            item.put("correctRate", correctRate);
            item.put("weakPoints", weakPoints);
            item.put("lastActive", lastActive);
            result.add(item);
        }
        return Result.success(result);
    }

    // 导出班级报告（简化版PDF内容，返回Markdown文本）
    @GetMapping("/class/{className}/export")
    public ResponseEntity<String> exportReport(@PathVariable String className) {
        var overview = (Map<String, Object>) getClassOverview(className).getData();

        StringBuilder report = new StringBuilder();
        report.append("# 班级学情分析报告\n\n");
        report.append("**班级**: ").append(className).append("\n\n");
        report.append("## 整体概览\n\n");
        report.append("- 学生总数: ").append(overview.get("totalStudents")).append("\n");
        report.append("- 平均知识水平: ").append(overview.get("avgKnowledgeLevel")).append("\n");
        report.append("- 平均学习进度: ").append(overview.get("avgProgress")).append("\n");
        report.append("- 生成资源总数: ").append(overview.get("totalResourcesGenerated")).append("\n");
        report.append("\n## 易错点TOP10\n\n");
        var errors = (List<?>) overview.get("errorTop10");
        if (errors != null) {
            for (Object e : errors) {
                report.append("- ").append(e).append("\n");
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + className + ".md")
            .contentType(MediaType.TEXT_PLAIN)
            .body(report.toString());
    }

    private List<String> getTopErrors(String className, int limit) {
        // 从 ErrorNotebook 表聚合真实错误数据
        List<Long> studentIds = sysUserRepo.findByClassName(className).stream()
                .filter(u -> "student".equals(u.getRole()))
                .map(com.happymouse.cryd.model.entity.SysUser::getId)
                .toList();
        Map<String, Long> kpCount = new java.util.LinkedHashMap<>();
        for (Long sid : studentIds) {
            List<com.happymouse.cryd.model.entity.ErrorNotebook> errors =
                    errorRepo.findByStudentIdAndStatus(sid, "active");
            for (com.happymouse.cryd.model.entity.ErrorNotebook e : errors) {
                String kp = e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "未知";
                kpCount.merge(kp, 1L, Long::sum);
            }
        }
        return kpCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .toList();
    }
}
