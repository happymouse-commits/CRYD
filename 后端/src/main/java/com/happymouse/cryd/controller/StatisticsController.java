package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.repository.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据统计控制器 — 系统使用统计、功能使用率、异常监控
 */
@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController {

    private final SysUserRepository sysUserRepo;
    private final LearningResourceRepository resourceRepo;
    private final ChapterProgressRepository progressRepo;
    private final UsageLogRepository usageLogRepo;
    private final StudentRepository studentRepo;

    public StatisticsController(SysUserRepository sysUserRepo,
                                 LearningResourceRepository resourceRepo,
                                 ChapterProgressRepository progressRepo,
                                 UsageLogRepository usageLogRepo,
                                 StudentRepository studentRepo) {
        this.sysUserRepo = sysUserRepo;
        this.resourceRepo = resourceRepo;
        this.progressRepo = progressRepo;
        this.usageLogRepo = usageLogRepo;
        this.studentRepo = studentRepo;
    }

    // 系统整体统计
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", sysUserRepo.count());
        stats.put("students", sysUserRepo.findByRole("student").size());
        stats.put("teachers", sysUserRepo.findByRole("teacher").size());
        stats.put("admins", sysUserRepo.findByRole("admin").size());
        stats.put("totalResources", resourceRepo.count());
        stats.put("totalProgress", progressRepo.count());

        // 总学习时长（分钟）
        long totalMinutes = studentRepo.findAll().stream()
            .mapToInt(s -> s.getTotalStudyMinutes() != null ? s.getTotalStudyMinutes() : 0)
            .sum();
        stats.put("totalStudyMinutes", totalMinutes);

        return Result.success(stats);
    }

    // 功能使用率统计
    @GetMapping("/features")
    public Result<?> getFeatureUsage() {
        try {
            var featureCounts = usageLogRepo.countByFeature();
            Map<String, Long> result = new LinkedHashMap<>();
            for (Object[] row : featureCounts) {
                result.put((String) row[0], (Long) row[1]);
            }
            return Result.success(result);
        } catch (Exception e) {
            // UsageLog可能为空，返回空Map
            return Result.success(Map.of());
        }
    }

    // 今日统计
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayStats() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now();

        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("todayApiCalls", usageLogRepo.countByDateRange(todayStart, todayEnd));
            stats.put("todayErrors", usageLogRepo.countErrorsByDateRange(todayStart, todayEnd));
        } catch (Exception e) {
            stats.put("todayApiCalls", 0);
            stats.put("todayErrors", 0);
        }

        return Result.success(stats);
    }

    // 异常监控
    @GetMapping("/anomalies")
    public Result<?> getAnomalies() {
        Map<String, Object> anomalies = new LinkedHashMap<>();

        // 检查错误率
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        try {
            long total = usageLogRepo.countByDateRange(last24h, LocalDateTime.now());
            long errors = usageLogRepo.countErrorsByDateRange(last24h, LocalDateTime.now());
            double errorRate = total > 0 ? (double) errors / total : 0;
            anomalies.put("errorRate24h", String.format("%.2f%%", errorRate * 100));
            anomalies.put("status", errorRate > 0.1 ? "warning" : "normal");
        } catch (Exception e) {
            anomalies.put("status", "normal");
        }

        return Result.success(anomalies);
    }
}
