package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.LeaveRequest;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.model.entity.AiAnalysis;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.LeaveRequestRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.AiAnalysisRepository;
import com.happymouse.cryd.repository.SysUserRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 辅导员端API
 */
@RestController
@RequestMapping("/api/counselor")
@CrossOrigin(origins = "*")
public class CounselorController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final StudentRepository studentRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final SysUserRepository sysUserRepository;

    public CounselorController(LeaveRequestRepository leaveRequestRepository,
                                StudentRepository studentRepository,
                                AiAnalysisRepository aiAnalysisRepository,
                                SysUserRepository sysUserRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.studentRepository = studentRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.sysUserRepository = sysUserRepository;
    }

    // ===== 请假审批 =====

    @GetMapping("/leave-requests")
    public Result<List<LeaveRequest>> getLeaveRequests(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return Result.success(leaveRequestRepository.findByStatus(status));
        }
        return Result.success(leaveRequestRepository.findAll());
    }

    @PostMapping("/leave-request/{id}/approve")
    public Result<LeaveRequest> approveLeave(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        return leaveRequestRepository.findById(id).map(lr -> {
            String action = (String) body.getOrDefault("action", "approved");
            lr.setStatus(action);
            lr.setApproverId(Long.valueOf(body.getOrDefault("approverId", "0").toString()));
            lr.setApproverComment((String) body.getOrDefault("comment", ""));
            lr.setProcessedAt(LocalDateTime.now());
            return Result.success(leaveRequestRepository.save(lr));
        }).orElse(Result.error(404, "请假申请不存在"));
    }

    // ===== 学业预警 =====

    @GetMapping("/warning/{className}")
    public Result<List<Map<String, Object>>> getWarnings(@PathVariable String className) {
        List<SysUser> users = sysUserRepository.findByClassName(className);
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (SysUser u : users) {
            if (!"student".equals(u.getRole())) continue;
            studentRepository.findById(u.getId()).ifPresent(student -> {
                // 知识水平低于30视为预警
                if (student.getKnowledgeLevel() != null && student.getKnowledgeLevel() < 30) {
                    Map<String, Object> w = new LinkedHashMap<>();
                    w.put("studentId", student.getId());
                    w.put("nickname", student.getNickname());
                    w.put("knowledgeLevel", student.getKnowledgeLevel());
                    w.put("warningLevel", student.getKnowledgeLevel() < 15 ? "high" : "medium");
                    w.put("suggestion", "建议安排辅导，重点补基础知识点");
                    warnings.add(w);
                }
            });
        }

        // 按预警等级排序
        warnings.sort((a, b) -> {
            String la = (String) a.get("warningLevel");
            String lb = (String) b.get("warningLevel");
            return "high".equals(la) ? -1 : 1;
        });

        return Result.success(warnings);
    }

    // ===== 班级画像 =====

    @GetMapping("/class/{className}/profiles")
    public Result<List<Student>> getClassProfiles(@PathVariable String className) {
        List<SysUser> users = sysUserRepository.findByClassName(className);
        List<Student> students = new ArrayList<>();
        for (SysUser u : users) {
            if ("student".equals(u.getRole())) {
                studentRepository.findById(u.getId()).ifPresent(students::add);
            }
        }
        return Result.success(students);
    }

    @GetMapping("/class/{className}/analysis")
    public Result<Map<String, Object>> getClassOverview(@PathVariable String className) {
        List<SysUser> users = sysUserRepository.findByClassName(className);
        int totalStudents = 0;
        int avgLevel = 0;
        int warningCount = 0;
        Map<String, Integer> styleDistribution = new LinkedHashMap<>();

        for (SysUser u : users) {
            if (!"student".equals(u.getRole())) continue;
            totalStudents++;
            var opt = studentRepository.findById(u.getId());
            if (opt.isPresent()) {
                Student s = opt.get();
                avgLevel += s.getKnowledgeLevel() != null ? s.getKnowledgeLevel() : 0;
                if (s.getKnowledgeLevel() != null && s.getKnowledgeLevel() < 30) warningCount++;
                String style = s.getCognitiveStyle() != null ? s.getCognitiveStyle() : "unknown";
                styleDistribution.merge(style, 1, Integer::sum);
            }
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalStudents", totalStudents);
        overview.put("avgKnowledgeLevel", totalStudents > 0 ? avgLevel / totalStudents : 0);
        overview.put("warningCount", warningCount);
        overview.put("styleDistribution", styleDistribution);
        return Result.success(overview);
    }

    // ===== 学生请假提交 =====

    @PostMapping("/leave-request")
    public Result<LeaveRequest> submitLeaveRequest(@RequestBody Map<String, String> body) {
        LeaveRequest lr = new LeaveRequest();
        lr.setStudentId(Long.valueOf(body.getOrDefault("studentId", "0")));
        lr.setStartDate(body.getOrDefault("startDate", ""));
        lr.setEndDate(body.getOrDefault("endDate", ""));
        lr.setReason(body.getOrDefault("reason", ""));
        return Result.success(leaveRequestRepository.save(lr));
    }
}
