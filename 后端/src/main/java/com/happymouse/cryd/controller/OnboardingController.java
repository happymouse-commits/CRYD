package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.SysUserRepository;
import com.happymouse.cryd.service.OnboardingService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 引导式对话控制器
 *
 * GET  /api/onboarding/status/{sysUserId}      — 画像状态
 * GET  /api/onboarding/dimensions/{sysUserId}  — 维度详情
 * POST /api/onboarding/generate/{sysUserId}    — 手动触发知识库资源生成
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;
    private final StudentRepository studentRepo;
    private final SysUserRepository sysUserRepo;

    public OnboardingController(OnboardingService onboarding,
                                StudentRepository studentRepo,
                                SysUserRepository sysUserRepo) {
        this.onboarding = onboarding;
        this.studentRepo = studentRepo;
        this.sysUserRepo = sysUserRepo;
    }

    /** 获取画像完整度 + 引导信息 */
    @GetMapping("/status/{sysUserId}")
    public Result<Map<String, Object>> status(@PathVariable Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        if (s == null) {
            result.put("isNew", true);
            result.put("completeness", 0);
            result.put("needOnboarding", true);
            result.put("welcomeMessage", "你好呀！我是小智老师 🎓 来聊聊吧！");
        } else {
            int completeness = onboarding.calcCompleteness(s);
            String nextQ = onboarding.getNextUnfilledQuestion(s);
            result.put("isNew", false);
            result.put("completeness", completeness);
            result.put("needOnboarding", completeness < 70);
            result.put("unfilledQuestion", nextQ);
            result.put("dimensionCount", onboarding.getDimensionStatus(s).stream().filter(d -> (Boolean) d.get("filled")).count());
            result.put("totalDimensions", onboarding.DIMENSIONS.size());
            if (s.getNickname() != null) {
                result.put("welcomeMessage", s.getNickname() + "回来啦！最近学得怎么样？");
            } else {
                result.put("welcomeMessage", "欢迎回来！有什么想学的？");
            }
        }
        return Result.success(result);
    }

    /** 获取所有维度详情（哪些已填/未填、权重、问题） */
    @GetMapping("/dimensions/{sysUserId}")
    public Result<Map<String, Object>> dimensions(@PathVariable Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId).orElse(null);
        SysUser user = sysUserRepo.findById(sysUserId).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completeness", s != null ? onboarding.calcCompleteness(s) : 0);
        result.put("dimensions", s != null ? onboarding.getDimensionStatus(s) : onboarding.DIMENSIONS.stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("field", d.field());
                m.put("label", d.label());
                m.put("weight", d.weight());
                m.put("filled", false);
                m.put("question", d.question());
                return m;
            }).toList());
        if (user != null) {
            result.put("className", user.getClassName());
            result.put("department", user.getDepartment());
        }
        return Result.success(result);
    }

    /**
     * 手动触发知识库增强资源生成
     * 画像完整度达到阈值后，从前端手动触发
     */
    @PostMapping("/generate/{sysUserId}")
    public Result<Map<String, Object>> generateResources(@PathVariable Long sysUserId) {
        int count = onboarding.generateResourcesFromKnowledgeBase(sysUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", count);
        result.put("message", count > 0 ? "已从知识库生成 " + count + " 个个性化资源" : "知识库暂无相关内容，请先导入知识库");
        return Result.success(result);
    }
}
