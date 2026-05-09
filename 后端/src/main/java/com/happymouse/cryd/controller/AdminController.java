package com.happymouse.cryd.controller;

import com.happymouse.cryd.common.Result;
import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.SysUserRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.repository.TeacherRepository;
import com.happymouse.cryd.repository.CourseRepository;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员端API
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final SysUserRepository sysUserRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;

    public AdminController(SysUserRepository sysUserRepository,
                           StudentRepository studentRepository,
                           TeacherRepository teacherRepository,
                           CourseRepository courseRepository) {
        this.sysUserRepository = sysUserRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.courseRepository = courseRepository;
    }

    // ===== 系统状态 =====

    @GetMapping("/server-status")
    public Result<Map<String, Object>> getServerStatus() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        Map<String, Object> status = new LinkedHashMap<>();

        // CPU
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("availableProcessors", os.getAvailableProcessors());
        cpu.put("systemLoadAverage", os.getSystemLoadAverage());
        status.put("cpu", cpu);

        // 内存
        Map<String, Object> mem = new LinkedHashMap<>();
        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        mem.put("heapUsedMB", heapUsed);
        mem.put("heapMaxMB", heapMax);
        mem.put("heapUsagePercent", heapMax > 0 ? Math.round(heapUsed * 100.0 / heapMax) : 0);
        status.put("memory", mem);

        // JVM
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("springBoot", "3.2.5");
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        jvm.put("uptimeHours", Math.round(uptimeMs / 3600000.0 * 10) / 10.0);
        status.put("jvm", jvm);

        // 统计
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalStudents", studentRepository.count());
        stats.put("totalTeachers", teacherRepository.count());
        stats.put("totalCourses", courseRepository.count());
        stats.put("totalUsers", sysUserRepository.count());
        status.put("statistics", stats);

        status.put("status", heapUsed * 100.0 / heapMax < 85 ? "healthy" : "warning");
        status.put("timestamp", LocalDateTime.now().toString());

        return Result.success(status);
    }

    // ===== 用户管理 =====

    @GetMapping("/users")
    public Result<List<SysUser>> getUsers(@RequestParam(required = false) String role) {
        if (role != null && !role.isEmpty()) {
            return Result.success(sysUserRepository.findByRole(role));
        }
        return Result.success(sysUserRepository.findAll());
    }

    @PostMapping("/user")
    public Result<SysUser> createUser(@RequestBody Map<String, String> body) {
        // 检查用户名是否已存在
        if (sysUserRepository.findByUsername(body.get("username")).isPresent()) {
            return Result.error(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(body.get("username"));
        user.setPassword(body.getOrDefault("password", "123456")); // 生产环境需加密
        user.setNickname(body.getOrDefault("nickname", body.get("username")));
        user.setRole(body.getOrDefault("role", "student"));
        user.setClassName(body.getOrDefault("className", ""));
        user.setDepartment(body.getOrDefault("department", ""));
        return Result.success(sysUserRepository.save(user));
    }

    @PutMapping("/user/{id}")
    public Result<SysUser> updateUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return sysUserRepository.findById(id).map(user -> {
            if (body.containsKey("nickname")) user.setNickname(body.get("nickname"));
            if (body.containsKey("role")) user.setRole(body.get("role"));
            if (body.containsKey("className")) user.setClassName(body.get("className"));
            if (body.containsKey("department")) user.setDepartment(body.get("department"));
            if (body.containsKey("status")) user.setStatus(body.get("status"));
            return Result.success(sysUserRepository.save(user));
        }).orElse(Result.error(404, "用户不存在"));
    }

    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        if (sysUserRepository.existsById(id)) {
            sysUserRepository.deleteById(id);
            return Result.success();
        }
        return Result.error(404, "用户不存在");
    }

    // ===== 数据统计面板 =====

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        // 用户统计
        Map<String, Object> userStats = new LinkedHashMap<>();
        userStats.put("total", sysUserRepository.count());
        userStats.put("students", sysUserRepository.findByRole("student").size());
        userStats.put("teachers", sysUserRepository.findByRole("teacher").size());
        userStats.put("counselors", sysUserRepository.findByRole("counselor").size());
        userStats.put("admins", sysUserRepository.findByRole("admin").size());
        dashboard.put("users", userStats);

        // 学习统计
        Map<String, Object> learnStats = new LinkedHashMap<>();
        learnStats.put("totalStudents", studentRepository.count());
        learnStats.put("totalCourses", courseRepository.count());
        dashboard.put("learning", learnStats);

        return Result.success(dashboard);
    }
}
