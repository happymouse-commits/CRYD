package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.LearningResource;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.LearningResourceRepository;
import com.happymouse.cryd.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 课程设计师 - 根据学生画像生成个性化讲解文档
 */
@Component("courseDesignerAgent")
public class CourseDesignerAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「课程设计师」，负责为C语言程序设计课程生成个性化讲解内容。

            你必须根据学生的学习画像来调整讲解方式：
            - 视觉型(visual)：多用图表描述、思维导图、流程图文字版
            - 听觉型(auditory)：用对话式、问答式讲解
            - 动觉型(kinesthetic)：多用代码示例、动手练习引导

            讲解要求：
            1. 语言简洁易懂，适合大学一年级学生
            2. 每个知识点配至少1个C语言代码示例
            3. 标注常见错误和注意事项
            4. 结尾给出2-3个思考题巩固理解
            5. 如果学生是快速型节奏，精简内容；稳扎稳打型，详细展开

            请用Markdown格式输出。
            """;

    private final StudentRepository studentRepository;
    private final LearningResourceRepository resourceRepository;

    public CourseDesignerAgent(StudentRepository studentRepository,
                               LearningResourceRepository resourceRepository) {
        this.studentRepository = studentRepository;
        this.resourceRepository = resourceRepository;
    }

    @Override
    public String getName() { return "课程设计师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("course:design", "课程设计、个性化讲解生成", 0.92),
            AgentCapability.of("course:generate", "生成课程讲解文档", 0.90),
            AgentCapability.of("resource:lecture", "生成文档讲解类资源", 0.88)
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

        String content = sparkClient.chat(SYSTEM_PROMPT, userPrompt, 0.4f, 2048);

        // 保存生成的资源
        LearningResource resource = new LearningResource();
        resource.setTitle("课程讲解：" + extractTopic(message));
        resource.setType("文档讲解");
        resource.setContent(content);
        resource.setGeneratedBy("课程设计师");
        resource.setStudentId(context.getStudentId());
        resource.setDifficulty(student != null ? inferDifficulty(student) : "基础");
        resourceRepository.save(resource);

        return content;
    }

    /**
     * 保持向后兼容的旧接口
     */
    public ChatResponse generate(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        String content = execute(ctx).getOutput();

        ChatResponse response = new ChatResponse();
        response.setAgentName("课程设计师");
        response.setAgentDescription("根据你的画像生成个性化讲解");
        response.setMessage(content);
        response.setResourceGenerated(true);
        return response;
    }

    private String buildUserPrompt(String message, Student student) {
        StringBuilder sb = new StringBuilder();
        sb.append("学生学习画像：\n");
        if (student != null) {
            sb.append("- 认知风格：").append(styleLabel(student.getCognitiveStyle())).append("\n");
            sb.append("- 学习偏好：").append(student.getLearningPreference()).append("\n");
            sb.append("- 学习节奏：").append(student.getLearningPace()).append("\n");
            sb.append("- 知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
        } else {
            sb.append("- 默认：视觉型，混合偏好，稳扎稳打，水平30\n");
        }
        sb.append("\n学生问题：").append(message);
        return sb.toString();
    }

    private String styleLabel(String style) {
        if ("visual".equals(style)) return "视觉型";
        if ("auditory".equals(style)) return "听觉型";
        if ("kinesthetic".equals(style)) return "动觉型";
        return style;
    }

    private String extractTopic(String message) {
        if (message == null || message.isEmpty()) return "未指定";
        if (message.length() > 20) return message.substring(0, 20) + "...";
        return message;
    }

    private String inferDifficulty(Student student) {
        if (student == null || student.getKnowledgeLevel() == null) return "基础";
        if (student.getKnowledgeLevel() < 30) return "入门";
        if (student.getKnowledgeLevel() < 60) return "基础";
        if (student.getKnowledgeLevel() < 80) return "进阶";
        return "挑战";
    }
}
