package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.SysUserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证API - 登录/注册/忘记密码
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final SysUserRepository sysUserRepository;
    private final ConcurrentHashMap<String, String> captchaStore = new ConcurrentHashMap<>();

    public AuthController(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
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
        if (!password.equals(user.getPassword())) return Result.error(401, "账号或密码错误");
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

    /** 注册 - 手机号即用户名，学生需学号 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        String studentId = body.get("studentId");
        String role = body.getOrDefault("role", "student");

        if (phone == null || phone.isEmpty()) return Result.error(400, "手机号不能为空");
        if (!phone.matches("^1[3-9]\\d{9}$")) return Result.error(400, "手机号格式不正确");
        if (password == null || password.isEmpty()) return Result.error(400, "密码不能为空");
        if (password.length() < 6) return Result.error(400, "密码至少6位");

        // 手机号即用户名
        if (sysUserRepository.findByPhone(phone).isPresent()) return Result.error(400, "该手机号已被注册");

        if ("student".equals(role)) {
            if (studentId == null || studentId.isEmpty()) return Result.error(400, "学生必须填写学号");
            if (studentId.length() < 4) return Result.error(400, "学号至少4位");
            if (sysUserRepository.findByStudentId(studentId).isPresent()) return Result.error(400, "该学号已被注册");
        }

        SysUser user = new SysUser();
        user.setUsername(phone); // 手机号当用户名
        user.setPassword(password);
        user.setNickname(phone);  // 默认昵称也是手机号
        user.setRole(role);
        user.setPhone(phone);
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

    /** 发送验证码（模拟） */
    @PostMapping("/send-captcha")
    public Result<String> sendCaptcha(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isEmpty()) return Result.error(400, "手机号不能为空");
        if (!phone.matches("^1[3-9]\\d{9}$")) return Result.error(400, "手机号格式不正确");

        // 生成6位验证码
        String code = String.format("%06d", new Random().nextInt(1000000));
        captchaStore.put(phone, code);
        // TODO: 接短信服务，现在先返回验证码方便测试
        return Result.success(code);
    }

    /** 忘记密码 - 手机号+验证码+新密码 */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String captcha = body.get("captcha");
        String newPassword = body.get("newPassword");

        if (phone == null || phone.isEmpty()) return Result.error(400, "手机号不能为空");
        if (captcha == null || captcha.isEmpty()) return Result.error(400, "验证码不能为空");
        if (newPassword == null || newPassword.isEmpty()) return Result.error(400, "新密码不能为空");
        if (newPassword.length() < 6) return Result.error(400, "新密码至少6位");

        // 验证码校验
        String savedCode = captchaStore.get(phone);
        if (savedCode == null || !savedCode.equals(captcha)) return Result.error(400, "验证码错误");

        Optional<SysUser> opt = sysUserRepository.findByPhone(phone);
        if (opt.isEmpty()) return Result.error(404, "该手机号未注册");

        SysUser user = opt.get();
        user.setPassword(newPassword);
        sysUserRepository.save(user);
        captchaStore.remove(phone); // 用完即删

        return Result.success("密码重置成功");
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
