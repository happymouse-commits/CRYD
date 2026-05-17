package com.happymouse.cryd.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据初始化 - 启动时创建默认账号和C语言题库
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigRepository systemConfigRepository;

    public DataInitializer(SysUserRepository sysUserRepository,
                           CourseRepository courseRepository,
                           ChapterRepository chapterRepository,
                           QuestionRepository questionRepository,
                           PasswordEncoder passwordEncoder,
                           SystemConfigRepository systemConfigRepository) {
        this.sysUserRepository = sysUserRepository;
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.questionRepository = questionRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void run(String... args) {
        initDefaultUsers();
        initCProgrammingBank();
        initSystemConfig();
    }

    /**
     * 初始化系统配置默认值（讯飞星火大模型参数）
     */
    private void initSystemConfig() {
        if (systemConfigRepository.count() > 0) return;

        List<SystemConfig> defaults = List.of(
            createConfig("llm.apiUrl", "wss://spark-api.xf-yun.com/v4.0/chat", "讯飞星火 API 地址", "llm"),
            createConfig("llm.apiKey", "14f0f6dd2dcd7ae6ca62aaed68035914", "讯飞星火 API Key", "llm"),
            createConfig("llm.apiSecret", "ZTQ0M2Q3MzRhNDY4ZDdlZmYzMTJjOWMz", "讯飞星火 API Secret", "llm"),
            createConfig("llm.appId", "9fc73775", "讯飞星火 APP ID", "llm"),
            createConfig("llm.model", "spark-ultra", "默认模型：讯飞星火 Ultra 4.0", "llm"),
            createConfig("llm.domain", "4.0Ultra", "星火模型域", "llm"),
            createConfig("llm.temperature", "0.5", "默认 Temperature", "llm"),
            createConfig("llm.maxTokens", "2048", "默认 Max Tokens", "llm"),
            createConfig("llm.topP", "0.9", "默认 Top-P", "llm"),
            createConfig("llm.timeout", "60", "请求超时（秒）", "llm")
        );

        systemConfigRepository.saveAll(defaults);
        System.out.println("⚙️  系统配置已初始化: 讯飞星火 Ultra 4.0 为默认模型");
    }

    private SystemConfig createConfig(String key, String value, String desc, String category) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(desc);
        config.setCategory(category);
        return config;
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
                user.setPassword(passwordEncoder.encode(d.password));
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

    /**
     * 初始化谭浩强《C语言程序设计》题库
     */
    private void initCProgrammingBank() {
        SysUser teacher = sysUserRepository.findByUsername("teacher1").orElse(null);
        Long teacherId = teacher != null ? teacher.getId() : 1L;

        try {
            ClassPathResource resource = new ClassPathResource("question-bank/c-programming.json");
            String jsonStr;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                jsonStr = sb.toString();
            }

            JSONArray chapters = JSON.parseArray(jsonStr);
            if (chapters.isEmpty()) return;

            // 获取或创建课程
            Course course = courseRepository.findByName("C语言程序设计");
            if (course == null) {
                course = new Course();
                course.setName("C语言程序设计");
                course.setCode("C001");
                course.setTeacherId(teacherId);
                course.setSemester("2025-2026-2");
                course.setClassName("计科2301"); // 设置授课班级
                course.setDescription("谭浩强《C语言程序设计》课后习题题库，每小节含4道选择题和1道代码编写题");
                course = courseRepository.save(course);
                System.out.println("📚 创建课程：C语言程序设计（班级：计科2301）");
            } else if (course.getClassName() == null || course.getClassName().isEmpty()) {
                // 如果课程已存在但没有设置班级，补充设置
                course.setClassName("计科2301");
                course = courseRepository.save(course);
                System.out.println("📚 更新课程班级：计科2301");
            }

            // 创建或更新章节（检查是否已存在，避免重复）
            int createdCount = 0;
            int updatedCount = 0;
            for (int i = 0; i < chapters.size(); i++) {
                JSONObject ch = chapters.getJSONObject(i);
                String chapterName = ch.getString("name");
                JSONArray questions = ch.getJSONArray("questions");

                // 查找是否已有同名章节
                List<Chapter> existing = chapterRepository.findByStatusOrderByOrderNum("published");
                Chapter chapter = existing.stream()
                        .filter(c -> c.getName().equals(chapterName))
                        .findFirst().orElse(null);

                if (chapter == null) {
                    chapter = new Chapter();
                    chapter.setCourseId(course.getId());
                    chapter.setTeacherId(teacherId);
                    chapter.setName(chapterName);
                    chapter.setDescription(ch.getString("description"));
                    chapter.setOrderNum(ch.getInteger("orderNum"));
                    chapter.setStatus("published");
                    chapter.setPublishedAt(LocalDateTime.now());
                    chapter.setQuestions(questions.toJSONString());
                    chapterRepository.save(chapter);
                    createdCount++;
                } else if (chapter.getQuestions() == null || getQuestionCount(chapter.getQuestions()) < questions.size()) {
                    // 更新已有章节的题目（JSON文件有更多题目时）
                    chapter.setQuestions(questions.toJSONString());
                    chapterRepository.save(chapter);
                    updatedCount++;
                }
            }

            System.out.println("✅ C语言题库初始化完成: 新增" + createdCount + "章, 更新" + updatedCount + "章, 共" + chapters.size() + "章");

            // 逐题导入题库表 (question)
            int importedQ = 0;
            for (int i = 0; i < chapters.size(); i++) {
                JSONObject ch = chapters.getJSONObject(i);
                String chapterName = ch.getString("name");
                int chapterOrder = ch.getInteger("orderNum");
                JSONArray questions = ch.getJSONArray("questions");

                for (int j = 0; j < questions.size(); j++) {
                    JSONObject q = questions.getJSONObject(j);
                    String type = q.getString("type");
                    String content = q.getString("content");

                    // 检查是否已存在（按内容去重）
                    boolean exists = questionRepository.findAll().stream()
                            .anyMatch(existing -> content.equals(existing.getContent())
                                    && chapterName.equals(existing.getChapterName()));
                    if (exists) continue;

                    Question question = new Question();
                    question.setType(type);
                    question.setContent(content);
                    if ("choice".equals(type) && q.containsKey("options")) {
                        question.setOptions(q.getJSONArray("options").toJSONString());
                    }
                    question.setAnswer(q.getString("answer"));
                    question.setKnowledgePoint(q.getString("knowledgePoint"));
                    question.setAnalysis(q.getString("analysis"));
                    question.setDifficulty("medium");
                    question.setChapterName(chapterName);
                    question.setChapterOrder(chapterOrder);
                    question.setCourseId(course.getId());
                    question.setSource("imported");
                    questionRepository.save(question);
                    importedQ++;
                }
            }
            System.out.println("📋 题库表导入完成: " + importedQ + " 道题目");

            System.out.println("   题库JSON路径: question-bank/c-programming.json");
            System.out.println("   如需扩展题目, 启动后端后调用: POST /api/teacher/expand-question-bank");
        } catch (Exception e) {
            System.err.println("⚠️ C语言题库初始化失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getQuestionCount(String questionsJson) {
        try { return JSON.parseArray(questionsJson).size(); } catch (Exception e) { return 0; }
    }

    private static class DefaultUser {
        String username, password, nickname, role, className, department, phone, studentId;
        DefaultUser(String u, String p, String n, String r, String c, String d, String ph, String sid) {
            username = u; password = p; nickname = n; role = r; className = c; department = d; phone = ph; studentId = sid;
        }
    }
}