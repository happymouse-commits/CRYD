package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.agent.core.AgentCapability;
import com.happymouse.cryd.agent.core.AgentContext;
import com.happymouse.cryd.agent.core.BaseAgent;
import com.happymouse.cryd.model.dto.ChatRequest;
import com.happymouse.cryd.model.dto.ChatResponse;
import com.happymouse.cryd.model.entity.Student;
import com.happymouse.cryd.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 辅导老师 - 即时答疑解惑，提供多模态辅导
 */
@Component("tutorAgent")
public class TutorAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是「辅导老师」，一位耐心友好的C语言程序设计辅导老师。

            你的职责：
            1. 回答学生关于C语言的任何问题
            2. 用通俗易懂的语言解释概念
            3. 提供代码示例帮助理解
            4. 指出常见错误和陷阱
            5. 根据学生画像调整讲解深度

            辅导风格：
            - 如果学生是初学者，用最简单的语言，避免过多术语
            - 如果学生有一定基础，可以更深入，提及相关原理
            - 每个回答都尽量包含一个可运行的C语言代码示例
            - 鼓励学生，多用"你可以试试""很好的问题"等正面反馈

            用Markdown格式输出，代码用```c代码块。
            """;

    private final StudentRepository studentRepository;

    public TutorAgent(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public String getName() { return "辅导老师"; }

    @Override
    public List<AgentCapability> getCapabilities() {
        return List.of(
            AgentCapability.of("tutor:answer", "辅导答疑、C语言知识解答", 0.95),
            AgentCapability.of("tutor:explain", "概念讲解、代码示例", 0.90),
            AgentCapability.of("tutor:debug", "代码调试、错误分析", 0.85)
        );
    }

    @Override
    protected String getSystemPrompt() { return SYSTEM_PROMPT; }

    @Override
    protected String doExecute(AgentContext context) {
        Student student = studentRepository.findByUsername("student_" + context.getStudentId()).orElse(null);
        String message = context.getOriginalMessage();
        String mode = context.getAttribute("mode", String.class);
        if (mode == null) mode = "fast";

        String userPrompt = buildUserPrompt(message, student);

        // 根据模式调整 system prompt 和参数
        float temperature;
        int maxTokens;
        if ("expert".equals(mode)) {
            userPrompt += "\n\n【请深入分析，给出详细的解答和示例代码，不必简短。】";
            temperature = 0.3f;
            maxTokens = 2048;
        } else {
            // 快速模式
            userPrompt += "\n\n【请简短回答，控制在200字以内，直接给结论和关键代码。】";
            temperature = 0.5f;
            maxTokens = 512;
        }

        if (sparkClient == null) {
            return "AI服务暂不可用，请稍后再试";
        }

        List<Map<String, String>> history = context.getConversationHistory();
        if (history != null && !history.isEmpty()) {
            return sparkClient.chatWithHistory(SYSTEM_PROMPT, history, userPrompt, temperature, maxTokens);
        }
        return sparkClient.chat(SYSTEM_PROMPT, userPrompt, temperature, maxTokens);
    }

    /**
     * 保持向后兼容的旧接口
     */
    public ChatResponse answer(ChatRequest request) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), null);
        String output = execute(ctx).getOutput();
        ChatResponse response = new ChatResponse();
        response.setAgentName("辅导老师");
        response.setAgentDescription("即时答疑，帮你搞懂知识点");
        response.setMessage(output);
        response.setProfileUpdated(true);
        return response;
    }

    public ChatResponse answerWithHistory(ChatRequest request, List<Map<String, String>> history) {
        AgentContext ctx = new AgentContext(request.getStudentId(), request.getMessage(), history);
        String output = doExecute(ctx);
        ChatResponse response = new ChatResponse();
        response.setAgentName("辅导老师");
        response.setAgentDescription("即时答疑，帮你搞懂知识点");
        response.setMessage(output);
        return response;
    }

    private String buildUserPrompt(String message, Student student) {
        if (student == null) return message;
        return String.format("[学生水平：%d/100，风格：%s]\n\n%s",
                student.getKnowledgeLevel(), student.getCognitiveStyle(), message);
    }
}
