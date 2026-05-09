package com.happymouse.cryd.config;

import com.happymouse.cryd.model.entity.SysUser;
import com.happymouse.cryd.repository.SysUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据初始化 - 启动时检查并创建默认账号
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;

    public DataInitializer(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    public void run(String... args) {
        initDefaultUsers();
    }

    private void initDefaultUsers() {
        List<DefaultUser> defaults = List.of(
            new DefaultUser("admin1", "123456", "系统管理员", "admin", "", "信息中心", "13800000001", null),
            new DefaultUser("student1", "123456", "张同学", "student", "计科2301", "", "13800000002", "2023010001"),
            new DefaultUser("teacher1", "123456", "李老师", "teacher", "", "计算机系", "13800000003", null),
            new DefaultUser("counselor1", "123456", "王辅导员", "counselor", "", "学生处", "13800000004", null)
        );

        for (DefaultUser d : defaults) {
            if (sysUserRepository.findByUsername(d.username).isEmpty()) {
                SysUser user = new SysUser();
                user.setUsername(d.username);
                user.setPassword(d.password);
                user.setNickname(d.nickname);
                user.setRole(d.role);
                user.setClassName(d.className);
                user.setDepartment(d.department);
                user.setPhone(d.phone);
                user.setStudentId(d.studentId);
                user.setStatus("active");
                sysUserRepository.save(user);
            }
        }
    }

    private static class DefaultUser {
        String username, password, nickname, role, className, department, phone, studentId;
        DefaultUser(String u, String p, String n, String r, String c, String d, String ph, String sid) {
            username = u; password = p; nickname = n; role = r; className = c; department = d; phone = ph; studentId = sid;
        }
    }
}
