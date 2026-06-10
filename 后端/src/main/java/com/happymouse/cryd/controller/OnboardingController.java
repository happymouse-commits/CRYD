package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.OnboardingService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 引导式对话控制器 — 登录后主动对话
 *
 * GET /api/onboarding/status/{sysUserId}
 *   → 返回画像完整度、是否需要引导、欢迎语
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;
    private final StudentRepository studentRepo;

    public OnboardingController(OnboardingService onboarding, StudentRepository studentRepo) {
        this.onboarding = onboarding;
        this.studentRepo = studentRepo;
    }

    /**
     * 获取学生画像状态 + 引导信息
     * 前端登录后第一时间调用这个
     */
    @GetMapping("/status/{sysUserId}")
    public Result<Map<String, Object>> status(@PathVariable Long sysUserId) {
        Student s = studentRepo.findByUsername("student_" + sysUserId)
            .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        if (s == null) {
            result.put("isNew", true);
            result.put("completeness", 0);
            result.put("needOnboarding", true);
            result.put("welcomeMessage", "你好呀！我是小智老师，你的 AI 学习助手 🎓 来聊聊吧，我帮你规划学习！");
        } else {
            int completeness = onboarding.calcCompleteness(s);
            String nextQ = onboarding.getNextUnfilledQuestion(s);
            result.put("isNew", false);
            result.put("completeness", completeness);
            result.put("needOnboarding", completeness < 70);
            result.put("unfilledQuestion", nextQ);
            if (s.getNickname() != null) {
                result.put("welcomeMessage", s.getNickname() + "回来啦！最近学得怎么样？");
            } else {
                result.put("welcomeMessage", "欢迎回来！有什么想学的？");
            }
        }
        return Result.success(result);
    }
}
