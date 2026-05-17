package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.ErrorNotebook;
import com.happymouse.cryd.model.entity.LearningPath;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.ErrorNotebookRepository;
import com.happymouse.cryd.repository.LearningPathRepository;
import com.happymouse.cryd.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 路径规划师 - 根据画像和进度规划个性化学习路径
 */
@Component("pathPlannerAgent")
public class PathPlannerAgent extends BaseAgent {

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
    private final ErrorNotebookRepository errorRepo;

    public PathPlannerAgent(StudentRepository studentRepository,
                            LearningPathRepository pathRepository,
                            ErrorNotebookRepository errorRepo) {
        this.studentRepository = studentRepository;
        this.pathRepository = pathRepository;
        this.errorRepo = errorRepo;
    }

    @Override
    public String getName() { return "路径规划师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("path:plan", "学习路径规划、课程推荐", 0.94),
            AgentCapability.of("path:progress", "进度跟踪、步骤调整", 0.88)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Student student = studentRepository.findByUsername("student_" + context.getStudentId()).orElse(null);
        String message = context.getOriginalMessage();
        String userPrompt = buildUserPrompt(message, student);

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        String content = sparkClient.chat(SYSTEM_PROMPT, userPrompt, 0.4f, 1024);

        LearningPath path = new LearningPath();
        path.setStudentId(context.getStudentId());
        path.setCurrentStep(student != null ? inferStep(student) : 1);
        path.setSteps(content);
        path.setStatus("active");
        pathRepository.save(path);

        return content;
    }

    /**
     * 供控制器调用的 generate 方法
     */
    public ChatResponse generate(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        String content = execute(ctx).getOutput();

        ChatResponse response = new ChatResponse();
        response.setAgentName("路径规划师");
        response.setAgentDescription("为你规划个性化学习路径");
        response.setMessage(content);
        return response;
    }

    /**
     * 保持向后兼容的旧接口
     */
    public ChatResponse plan(ChatRequest request) {
        return generate(request);
    }

    public void updateProgress(ChatRequest request) {
        Student student = studentRepository.findByUsername("student_" + request.getStudentId()).orElse(null);
        if (student != null) {
            int current = student.getKnowledgeLevel() != null ? student.getKnowledgeLevel() : 30;
            student.setKnowledgeLevel(Math.min(100, current + 2));
            studentRepository.save(student);
        }
    }

    private String buildUserPrompt(String message, Student student) {
        StringBuilder sb = new StringBuilder();
        if (student != null) {
            sb.append("学生画像：\n");
            sb.append("- 知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
            sb.append("- 认知风格：").append(student.getCognitiveStyle()).append("\n");
            sb.append("- 学习节奏：").append(student.getLearningPace()).append("\n");
            sb.append("- 薄弱环节：").append(student.getWeakAreas() != null ? student.getWeakAreas() : "未知").append("\n");

            // 加入错题数据
            List<ErrorNotebook> errors = errorRepo.findByStudentIdAndStatus(student.getId(), "active");
            if (!errors.isEmpty()) {
                Map<String, Long> kpCount = errors.stream()
                        .filter(e -> e.getKnowledgePoint() != null)
                        .collect(Collectors.groupingBy(ErrorNotebook::getKnowledgePoint, LinkedHashMap::new, Collectors.counting()));
                sb.append("- 错题知识点分布：\n");
                kpCount.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(5)
                        .forEach(e -> sb.append("  ").append(e.getKey()).append("(").append(e.getValue()).append("次)\n"));
            }
        } else {
            sb.append("学生画像：知识水平30/100，视觉型，稳扎稳打\n");
        }
        sb.append("\n学生请求：").append(message);
        return sb.toString();
    }

    private int inferStep(Student student) {
        if (student == null || student.getKnowledgeLevel() == null) return 1;
        return Math.max(1, student.getKnowledgeLevel() / 12);
    }
}
