package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 认证API - 登录/注册
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(SysUserRepository sysUserRepository, PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 登录 - 支持用户名/手机号/学号 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String account = body.get("username");
        String password = body.get("password");
        if (account == null || password == null) return Result.error(400, "账号和密码不能为空");

        Optional<SysUser> opt = sysUserRepository.findByUsername(account);
        if (opt.isEmpty()) opt = sysUserRepository.findByPhone(account);
        if (opt.isEmpty()) opt = sysUserRepository.findByStudentId(account);
        if (opt.isEmpty()) return Result.error(401, "账号或密码错误");

        SysUser user = opt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) return Result.error(401, "账号或密码错误");
        if ("disabled".equals(user.getStatus())) return Result.error(403, "账号已被禁用");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        data.put("className", user.getClassName());
        data.put("department", user.getDepartment());
        data.put("phone", user.getPhone());
        data.put("studentId", user.getStudentId());
        data.put("token", "mock-token-" + user.getId());
        return Result.success(data);
    }

    /** 注册 - 学生用学号登录，教师用手机号登录 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        String studentId = body.get("studentId");
        String role = body.getOrDefault("role", "student");

        if (password == null || password.isEmpty()) return Result.error(400, "密码不能为空");
        if (password.length() < 6) return Result.error(400, "密码至少6位");

        // 角色校验
        if ("teacher".equals(role)) {
            if (phone == null || phone.isEmpty()) return Result.error(400, "教师必须填写手机号");
            if (!phone.matches("^1[3-9]\\d{9}$")) return Result.error(400, "手机号格式不正确");
            if (sysUserRepository.findByPhone(phone).isPresent()) return Result.error(400, "该手机号已被注册");
        }

        if ("student".equals(role)) {
            if (studentId == null || studentId.isEmpty()) return Result.error(400, "学生必须填写学号");
            if (studentId.length() < 4) return Result.error(400, "学号至少4位");
            if (sysUserRepository.findByStudentId(studentId).isPresent()) return Result.error(400, "该学号已被注册");
            String className = body.get("className");
            if (className == null || className.isEmpty()) return Result.error(400, "学生必须填写班级");
            if (className.length() < 2) return Result.error(400, "班级名称至少2位");
        }

        SysUser user = new SysUser();
        user.setUsername("student".equals(role) ? studentId : phone);
        user.setPassword(passwordEncoder.encode(password));
        String nickname = body.get("nickname");
        user.setNickname((nickname != null && !nickname.isBlank()) ? nickname : "");
        user.setRole(role);
        user.setPhone("student".equals(role) ? "" : (phone != null ? phone : ""));
        user.setStudentId("student".equals(role) ? studentId : null);
        user.setClassName(body.getOrDefault("className", ""));
        user.setDepartment(body.getOrDefault("department", ""));
        SysUser saved = sysUserRepository.save(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("username", saved.getUsername());
        data.put("role", saved.getRole());
        data.put("phone", saved.getPhone());
        data.put("studentId", saved.getStudentId());
        return Result.success(data);
    }

    @GetMapping("/check-phone")
    public Result<Boolean> checkPhone(@RequestParam String phone) {
        return Result.success(sysUserRepository.findByPhone(phone).isPresent());
    }

    @GetMapping("/check-student-id")
    public Result<Boolean> checkStudentId(@RequestParam String studentId) {
        return Result.success(sysUserRepository.findByStudentId(studentId).isPresent());
    }
}