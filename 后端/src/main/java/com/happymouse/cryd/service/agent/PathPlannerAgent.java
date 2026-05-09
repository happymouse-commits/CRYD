package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.LearningPath;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.LearningPathRepository;
import com.happymouse.cryd.repository.StudentRepository;
import com.happymouse.cryd.service.spark.SparkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 路径规划师 - 根据画像和进度规划个性化学习路径
 */
@Service
public class PathPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(PathPlannerAgent.class);
    private static final String SYSTEM_PROMPT = """
            你是「路径规划师」，负责为C语言程序设计课程规划个性化学习路径。
            
            C语言课程知识体系（按顺序）：
            1. 基础语法（变量、数据类型、运算符）
            2. 控制结构（if/switch/for/while）
            3. 函数与作用域
            4. 数组与字符串
            5. 指针（核心难点）
            6. 结构体与联合
            7. 文件操作
            8. 动态内存管理
            9. 预处理器与多文件编译
            
            根据学生的知识水平和已学内容，推荐接下来应该学习的内容，以及为什么。
            给出3-5个具体步骤，每步包含：学习目标、预计时间、推荐资源类型。
            用Markdown格式输出。
            """;

    private final StudentRepository studentRepository;
    private final LearningPathRepository pathRepository;
    private final SparkClient sparkClient;

    public PathPlannerAgent(StudentRepository studentRepository,
                            LearningPathRepository pathRepository,
                            SparkClient sparkClient) {
        this.studentRepository = studentRepository;
        this.pathRepository = pathRepository;
        this.sparkClient = sparkClient;
    }

    public ChatResponse plan(ChatRequest request) {
        Student student = studentRepository.findById(request.getStudentId()).orElse(null);
        String message = request.getMessage();

        String userPrompt = buildUserPrompt(message, student);
        String content = sparkClient.chat(SYSTEM_PROMPT, userPrompt, 0.6f, 2048);

        // 保存学习路径
        LearningPath path = new LearningPath();
        path.setStudentId(request.getStudentId());
        path.setCurrentStep(student != null ? inferStep(student) : 1);
        path.setSteps(content);
        path.setStatus("active");
        pathRepository.save(path);

        ChatResponse response = new ChatResponse();
        response.setAgentName("路径规划师");
        response.setAgentDescription("为你规划个性化学习路径");
        response.setMessage(content);
        return response;
    }

    public void updateProgress(ChatRequest request) {
        Student student = studentRepository.findById(request.getStudentId()).orElse(null);
        if (student != null) {
            int current = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
            student.setKnowledgeLevel(Math.min(100, current + 2));
            studentRepository.save(student);
            log.info("学习进度已更新: studentId={}, newLevel={}", student.getId(), student.getKnowledgeLevel());
        }
    }

    private String buildUserPrompt(String message, Student student) {
        StringBuilder sb = new StringBuilder();
        if (student != null) {
            sb.append("学生画像：\n");
            sb.append("- 知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
            sb.append("- 认知风格：").append(student.getCognitiveStyle()).append("\n");
            sb.append("- 学习节奏：").append(student.getLearningPace()).append("\n");
        } else {
            sb.append("学生画像：知识水平30/100，视觉型，稳扎稳打\n");
        }
        sb.append("\n学生请求：").append(message);
        return sb.toString();
    }

    private int inferStep(Student student) {
        if (student.getKnowledgeLevel() == null) return 1;
        return Math.max(1, student.getKnowledgeLevel() / 12);
    }
}
