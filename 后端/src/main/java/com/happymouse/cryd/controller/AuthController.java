package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.SysUserRepository;
import com.happymouse.cryd.service.OnboardingService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 认证API - 登录/注册（仅学生端）
 * 注册只需用户名+密码，角色固定为 student
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserRepository sysUserRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final OnboardingService onboardingService;

    public AuthController(SysUserRepository sysUserRepository, StudentRepository studentRepository,
                          PasswordEncoder passwordEncoder, OnboardingService onboardingService) {
        this.sysUserRepository = sysUserRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.onboardingService = onboardingService;
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String account = body.get("username");
        String password = body.get("password");
        if (account == null || password == null) return Result.error(400, "用户名和密码不能为空");

        Optional<SysUser> opt = sysUserRepository.findByUsername(account);
        if (opt.isEmpty()) return Result.error(401, "用户名或密码错误");

        SysUser user = opt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) return Result.error(401, "用户名或密码错误");
        if ("disabled".equals(user.getStatus())) return Result.error(403, "账号已被禁用");

        // 检查是否完成AI导学
        boolean onboardingDone = isOnboardingDone(user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        data.put("onboardingDone", onboardingDone);
        data.put("token", "mock-token-" + user.getId());
        return Result.success(data);
    }

    /** 注册 - 只需用户名+密码 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank()) return Result.error(400, "用户名不能为空");
        if (username.length() < 2 || username.length() > 20) return Result.error(400, "用户名2-20个字符");
        if (password == null || password.isEmpty()) return Result.error(400, "密码不能为空");
        if (password.length() < 6) return Result.error(400, "密码至少6位");

        // 检查用户名是否已存在
        if (sysUserRepository.findByUsername(username).isPresent()) {
            return Result.error(400, "该用户名已被注册");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username); // 默认昵称=用户名
        user.setRole("student");
        SysUser saved = sysUserRepository.save(user);

        // 自动创建 Student 记录
        Student student = new Student();
        student.setUsername("student_" + saved.getId());
        student.setKnowledgeLevel(0);
        student.setLearningPreference("mixed");
        student.setLearningPace("steady");
        studentRepository.save(student);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("username", saved.getUsername());
        data.put("role", saved.getRole());
        return Result.success(data);
    }

    /** 检查用户名是否已注册 */
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        return Result.success(sysUserRepository.findByUsername(username).isPresent());
    }

    /** 判断用户是否完成AI导学 */
    private boolean isOnboardingDone(Long sysUserId) {
        Student student = studentRepository.findByUsername("student_" + sysUserId).orElse(null);
        if (student == null) return false;
        return onboardingService.calcCompleteness(student) >= 80;
    }
}
