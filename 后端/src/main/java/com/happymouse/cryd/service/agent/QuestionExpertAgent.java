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
 * 出题专家 - 根据知识点和学生水平生成不同难度的练习题
 */
@Component("questionExpertAgent")
public class QuestionExpertAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「出题专家」，负责为C语言程序设计课程生成练习题。

            根据学生知识水平调整难度：
            - 水平<30：入门题（概念理解、简单语法）
            - 水平30-60：基础题（基本应用、小代码编写）
            - 水平60-80：进阶题（综合应用、算法设计）
            - 水平>80：挑战题（复杂算法、系统设计）

            每次出3道题，格式如下：

            **第1题 [难度级别]**
            题目内容

            <details>
            <summary>点击查看答案</summary>
            答案和解析
            </details>

            **第2题 [难度级别]**
            ...

            题目类型要多样：选择题、填空题、编程题混合。
            每题必须有详细解析。
            用Markdown格式输出。
            """;

    private final StudentRepository studentRepository;
    private final LearningResourceRepository resourceRepository;

    public QuestionExpertAgent(StudentRepository studentRepository,
                               LearningResourceRepository resourceRepository) {
        this.studentRepository = studentRepository;
        this.resourceRepository = resourceRepository;
    }

    @Override
    public String getName() { return "出题专家"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("question:gen", "生成练习题、题目设计", 0.93),
            AgentCapability.of("question:practice", "练习出题、难度适配", 0.90),
            AgentCapability.of("resource:exercise", "生成题库类资源", 0.88)
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

        LearningResource resource = new LearningResource();
        resource.setTitle("练习题：" + extractTopic(message));
        resource.setType("题库");
        resource.setContent(content);
        resource.setGeneratedBy("出题专家");
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
        response.setAgentName("出题专家");
        response.setAgentDescription("根据你的水平生成练习题");
        response.setMessage(content);
        response.setResourceGenerated(true);
        return response;
    }

    private String buildUserPrompt(String message, Student student) {
        StringBuilder sb = new StringBuilder();
        if (student != null) {
            sb.append("学生知识水平：").append(student.getKnowledgeLevel()).append("/100\n");
            sb.append("难度要求：").append(inferDifficulty(student)).append("\n");
        } else {
            sb.append("学生知识水平：30/100，难度要求：入门\n");
        }
        sb.append("出题要求：").append(message);
        return sb.toString();
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
